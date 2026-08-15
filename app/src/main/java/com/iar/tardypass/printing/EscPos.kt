package com.iar.tardypass.printing

import com.iar.tardypass.domain.PassReceipt
import java.io.ByteArrayOutputStream
import java.text.Normalizer

/** Printer byte encoding. Must match the H10's "default code system" hardware setting. */
enum class PrinterEncoding { CP437, GB18030, UTF8 }

/**
 * ESC/POS receipt builder — byte-compatible port of the PWA's escpos.ts
 * (Tardy-Pass-PWA/src/services/escpos.ts). For pure-ASCII input and any
 * encoding, output is byte-for-byte identical to the PWA's builder.
 */
object EscPos {
    private const val ESC = 0x1b

    fun buildReceipt(
        data: PassReceipt,
        encoding: PrinterEncoding = PrinterEncoding.CP437,
        selectCodePage: Boolean = false
    ): ByteArray {
        val out = ByteArrayOutputStream()
        fun a(vararg b: Int) = b.forEach { out.write(it) }
        fun txt(s: String) = out.write(encode(s, encoding))
        fun line(label: String, value: String?) {
            if (!value.isNullOrEmpty()) txt("$label: $value\n")
        }

        a(ESC, 0x40)              // init
        if (selectCodePage && encoding == PrinterEncoding.CP437) {
            a(ESC, 0x74, 0x00)    // ESC t 0 — select CP437 (gated: some firmwares choke)
        }
        a(ESC, 0x61, 0x01)        // center
        a(ESC, 0x45, 0x01); txt(data.title + "\n"); a(ESC, 0x45, 0x00)  // bold title
        // A reissued slip has to be distinguishable at a glance, or two identical
        // passes can circulate as two. Nullable, so a normal pass is unchanged.
        if (data.notice != null) {
            a(ESC, 0x45, 0x01); txt(data.notice + "\n"); a(ESC, 0x45, 0x00)
            data.noticeDetail?.let { txt(it + "\n") }
        }
        a(ESC, 0x61, 0x00)        // left

        line("Student", "${data.name} (${data.id})")
        line("Date", data.date)
        line("Time", data.time)
        line("Period", data.period)
        line("Class", data.course)
        line("Room", data.room)
        line("Reason", data.reason)
        line("Staff", data.staff)
        line("Status", data.status ?: "Unexcused")

        txt("------------------------------\n")
        a(ESC, 0x61, 0x01); txt(data.footer + "\n"); a(ESC, 0x61, 0x00)
        a(ESC, 0x64, 0x03)        // feed 3 lines
        return out.toByteArray()
    }

    /**
     * Encode text for the printer. Characters the target encoding can't represent
     * are diacritic-stripped first ("García" -> "Garcia" rather than "Garc?a").
     */
    fun encode(s: String, encoding: PrinterEncoding): ByteArray = when (encoding) {
        PrinterEncoding.UTF8 -> s.toByteArray(Charsets.UTF_8)
        PrinterEncoding.GB18030 -> s.toByteArray(charset("GB18030"))
        PrinterEncoding.CP437 -> {
            val cp437 = charset("IBM437")
            val enc = cp437.newEncoder()
            val safe = buildString(s.length) {
                for (ch in s) {
                    when {
                        ch.code < 0x80 || enc.canEncode(ch) -> append(ch)
                        else -> append(stripDiacritics(ch))
                    }
                }
            }
            safe.toByteArray(cp437)
        }
    }

    // Letters with no NFD decomposition that still have an obvious ASCII fold.
    private val FOLD = mapOf(
        'Ł' to "L", 'ł' to "l", 'Ø' to "O", 'ø' to "o", 'Đ' to "D", 'đ' to "d",
        'Æ' to "AE", 'æ' to "ae", 'Œ' to "OE", 'œ' to "oe", 'ß' to "ss",
        'Þ' to "Th", 'þ' to "th", 'Ð' to "D", 'ð' to "d", 'İ' to "I", 'ı' to "i"
    )

    private fun stripDiacritics(ch: Char): String {
        FOLD[ch]?.let { return it }
        val folded = Normalizer.normalize(ch.toString(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return if (folded.isNotEmpty() && folded[0].code < 0x80) folded else "?"
    }

    fun toBase64(bytes: ByteArray): String =
        java.util.Base64.getEncoder().encodeToString(bytes)
}
