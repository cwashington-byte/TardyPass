package com.iar.tardypass.printing

import android.content.Context

/** How sure the driver can be that paper actually came out. */
enum class AckLevel { NONE, DELIVERED, PRINTED }

data class PrinterCapabilities(
    val ack: AckLevel,
    val supportsCut: Boolean = false
)

sealed class PrintResult {
    /** Bytes handed off, no confirmation possible (e.g. RAWBT intent). */
    data object Sent : PrintResult()

    /** Printer confirmed the job (status byte / SDK callback). */
    data object Confirmed : PrintResult()

    data class Failed(val reason: String, val cause: Throwable? = null) : PrintResult()

    val succeeded: Boolean get() = this !is Failed
    val confirmed: Boolean get() = this is Confirmed
}

interface PrinterDriver {
    val id: String
    val displayName: String
    val capabilities: PrinterCapabilities

    /** Cheap, side-effect-free availability probe. */
    suspend fun isAvailable(context: Context): Boolean

    /** Send an ESC/POS job. Must be called off the main thread. */
    suspend fun print(context: Context, job: ByteArray): PrintResult
}
