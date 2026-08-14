package com.iar.tardypass.printing.drivers

import android.content.Context
import com.iar.tardypass.printing.AckLevel
import com.iar.tardypass.printing.PrintResult
import com.iar.tardypass.printing.PrinterCapabilities
import com.iar.tardypass.printing.PrinterDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Writes ESC/POS bytes straight to the internal printer's character device.
 * Many generic Android PDAs (like the H10) expose the built-in printer as a
 * world-writable /dev/tty* node — Phase 0 discovery confirms the exact path,
 * which is then stored in settings. Until then this driver probes candidates.
 */
class SerialEscPosDriver(
    private val configuredPath: () -> String
) : PrinterDriver {

    override val id = "serial"
    override val displayName = "Internal printer (serial)"
    override val capabilities = PrinterCapabilities(ack = AckLevel.PRINTED)

    private val candidatePaths = listOf(
        "/dev/ttyS1", "/dev/ttyS0", "/dev/ttyS2", "/dev/ttyS3",
        "/dev/ttyHSL0", "/dev/ttyHSL1", "/dev/ttyMT1", "/dev/ttyMT2",
        "/dev/ttyGS0", "/dev/pos_printer", "/dev/usb_printer"
    )

    fun resolvePath(): String? {
        configuredPath().takeIf { it.isNotBlank() }?.let { p ->
            return p.takeIf { canReallyOpen(it) }
        }
        return candidatePaths.firstOrNull { canReallyOpen(it) }
    }

    /**
     * canWrite() only reflects DAC bits, which lie under SELinux — on SENRAISE
     * H10s /dev/ttyS1 is mode 666 yet open() is denied for untrusted apps.
     * Actually opening the node is the only honest probe (harmless: opening a
     * tty prints nothing).
     */
    private fun canReallyOpen(path: String): Boolean {
        val f = File(path)
        if (!f.exists()) return false
        return try {
            FileOutputStream(path).use { true }
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun isAvailable(context: Context): Boolean = withContext(Dispatchers.IO) {
        resolvePath() != null
    }

    override suspend fun print(context: Context, job: ByteArray): PrintResult = withContext(Dispatchers.IO) {
        val path = resolvePath()
            ?: return@withContext PrintResult.Failed("No writable printer device found")
        try {
            FileOutputStream(path).use { out ->
                out.write(job)
                out.flush()
            }
            // Real-time status request: DLE EOT 1. If the firmware answers within
            // 500 ms we can report a confirmed print; otherwise degrade to Sent.
            queryStatus(path) ?: return@withContext PrintResult.Sent
            PrintResult.Confirmed
        } catch (e: Exception) {
            PrintResult.Failed("Serial write to $path failed: ${e.message}", e)
        }
    }

    private fun queryStatus(path: String): Int? {
        return try {
            FileOutputStream(path).use { out ->
                out.write(byteArrayOf(0x10, 0x04, 0x01)) // DLE EOT 1
                out.flush()
            }
            FileInputStream(path).use { input ->
                val deadline = System.currentTimeMillis() + 500
                while (System.currentTimeMillis() < deadline) {
                    if (input.available() > 0) return input.read()
                    Thread.sleep(20)
                }
                null
            }
        } catch (_: Exception) {
            null // status channel not supported on this device — not an error
        }
    }
}
