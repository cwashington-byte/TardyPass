package com.iar.tardypass.printing.drivers

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.iar.tardypass.printing.AckLevel
import com.iar.tardypass.printing.EscPos
import com.iar.tardypass.printing.PrintResult
import com.iar.tardypass.printing.PrinterCapabilities
import com.iar.tardypass.printing.PrinterDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fallback driver using the RAWBT app's URL scheme (rawbt:base64,...), exactly
 * like the PWA. No delivery confirmation is possible — result is always Sent.
 */
class RawbtDriver : PrinterDriver {
    override val id = "rawbt"
    override val displayName = "RAWBT app"
    override val capabilities = PrinterCapabilities(ack = AckLevel.NONE)

    override suspend fun isAvailable(context: Context): Boolean = withContext(Dispatchers.IO) {
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("rawbt:base64,"))
        probe.resolveActivity(context.packageManager) != null
    }

    override suspend fun print(context: Context, job: ByteArray): PrintResult {
        return try {
            val uri = Uri.parse("rawbt:base64," + EscPos.toBase64(job))
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            PrintResult.Sent
        } catch (e: Exception) {
            PrintResult.Failed("RAWBT hand-off failed: ${e.message}", e)
        }
    }
}
