package com.iar.tardypass.printing

import android.content.Context
import com.iar.tardypass.data.repo.SettingsRepository
import com.iar.tardypass.printing.drivers.BluetoothEscPosDriver
import com.iar.tardypass.printing.drivers.RawbtDriver
import com.iar.tardypass.printing.drivers.SerialEscPosDriver
import com.iar.tardypass.printing.drivers.VendorSdkDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

data class PrinterStatus(
    val activeDriverId: String?,     // null = nothing available
    val activeDriverName: String,
    val probing: Boolean
)

class PrinterManager(
    private val appContext: Context,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope
) {
    private var lastSerialPath = ""
    private var lastBtAddress = ""

    val drivers: List<PrinterDriver> = listOf(
        VendorSdkDriver(),
        SerialEscPosDriver { lastSerialPath },
        BluetoothEscPosDriver { lastBtAddress },
        RawbtDriver()
    )

    private val _status = MutableStateFlow(PrinterStatus(null, "Probing…", probing = true))
    val status: StateFlow<PrinterStatus> = _status

    fun startProbe() {
        scope.launch(Dispatchers.IO) { probe() }
    }

    private suspend fun probe() {
        _status.value = _status.value.copy(probing = true)
        val s = settings.current()
        lastSerialPath = s.serialDevicePath
        lastBtAddress = s.bluetoothAddress
        val pinned = s.printerDriverId.takeIf { it != "auto" }
        val active = if (pinned != null) {
            drivers.firstOrNull { it.id == pinned }?.takeIf { it.isAvailable(appContext) }
        } else {
            firstAvailable()
        }
        _status.value = PrinterStatus(
            activeDriverId = active?.id,
            activeDriverName = active?.displayName ?: "No printer found",
            probing = false
        )
    }

    private suspend fun firstAvailable(): PrinterDriver? =
        drivers.firstOrNull { it.isAvailable(appContext) }

    /**
     * Print with the selected driver; on failure in auto mode, try the next
     * available driver once. Returns the result plus the driver that ran it.
     */
    suspend fun print(job: ByteArray): Pair<PrintResult, String> {
        val s = settings.current()
        lastSerialPath = s.serialDevicePath
        lastBtAddress = s.bluetoothAddress
        val pinned = s.printerDriverId.takeIf { it != "auto" }

        val primary = if (pinned != null) drivers.firstOrNull { it.id == pinned }
        else firstAvailable()
            ?: return PrintResult.Failed("No printer available") to "none"
        primary ?: return PrintResult.Failed("Driver '$pinned' not found") to "none"

        val result = primary.print(appContext, job)
        if (result.succeeded || pinned != null) {
            if (result.succeeded) refreshStatus(primary)
            return result to primary.id
        }

        // Auto mode: one fallback to the next available driver.
        val fallback = drivers
            .dropWhile { it.id != primary.id }.drop(1)
            .firstOrNull { it.isAvailable(appContext) }
            ?: return result to primary.id
        val second = fallback.print(appContext, job)
        if (second.succeeded) refreshStatus(fallback)
        return second to fallback.id
    }

    suspend fun testPrint(driverId: String, job: ByteArray): PrintResult {
        val driver = drivers.firstOrNull { it.id == driverId }
            ?: return PrintResult.Failed("Unknown driver $driverId")
        return driver.print(appContext, job)
    }

    suspend fun availability(): Map<String, Boolean> =
        drivers.associate { it.id to it.isAvailable(appContext) }

    private fun refreshStatus(driver: PrinterDriver) {
        _status.value = PrinterStatus(driver.id, driver.displayName, probing = false)
    }
}
