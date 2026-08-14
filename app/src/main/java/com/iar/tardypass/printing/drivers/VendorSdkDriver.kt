package com.iar.tardypass.printing.drivers

import android.content.Context
import com.iar.tardypass.printing.AckLevel
import com.iar.tardypass.printing.PrintResult
import com.iar.tardypass.printing.PrinterCapabilities
import com.iar.tardypass.printing.PrinterDriver

/**
 * Placeholder for the H10 vendor printer interface. Phase 0 hardware discovery
 * (adb inspection of the preinstalled printer app — see docs/hardware-h10.md)
 * determines whether the device exposes an SDK .aar, an AIDL print service, or
 * only the serial device (in which case SerialEscPosDriver is the real path and
 * this driver stays unavailable).
 */
class VendorSdkDriver : PrinterDriver {
    override val id = "vendor"
    override val displayName = "Built-in printer (vendor SDK)"
    override val capabilities = PrinterCapabilities(ack = AckLevel.PRINTED)

    override suspend fun isAvailable(context: Context): Boolean = false

    override suspend fun print(context: Context, job: ByteArray): PrintResult =
        PrintResult.Failed("Vendor SDK driver not implemented (pending H10 hardware discovery)")
}
