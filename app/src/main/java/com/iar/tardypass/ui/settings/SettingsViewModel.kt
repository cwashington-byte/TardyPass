package com.iar.tardypass.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iar.tardypass.data.repo.AppSettings
import com.iar.tardypass.data.repo.SettingsRepository
import com.iar.tardypass.domain.PassReceipt
import com.iar.tardypass.printing.EscPos
import com.iar.tardypass.printing.PrintResult
import com.iar.tardypass.printing.PrinterEncoding
import com.iar.tardypass.printing.PrinterManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsViewModel(
    private val settingsRepo: SettingsRepository,
    private val printer: PrinterManager
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    /** Which driver actually won the probe — the at-a-glance dot in the Printer card. */
    val printerStatus = printer.status

    val availability = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val testResult = MutableStateFlow<String?>(null)

    val driverChoices = listOf("auto" to "Auto-detect") +
        printer.drivers.map { it.id to it.displayName }

    fun refreshAvailability() {
        viewModelScope.launch { availability.value = printer.availability() }
    }

    fun setDriver(id: String) = viewModelScope.launch {
        settingsRepo.setDriver(id)
        printer.startProbe()
    }

    fun setEncoding(e: PrinterEncoding) = viewModelScope.launch { settingsRepo.setEncoding(e) }
    fun setSelectCodePage(v: Boolean) = viewModelScope.launch { settingsRepo.setSelectCodePage(v) }
    fun setHidePeriod(v: Boolean) = viewModelScope.launch { settingsRepo.setHidePeriod(v) }
    fun setHideCourse(v: Boolean) = viewModelScope.launch { settingsRepo.setHideCourse(v) }
    fun setHideRoom(v: Boolean) = viewModelScope.launch { settingsRepo.setHideRoom(v) }
    fun setHideReason(v: Boolean) = viewModelScope.launch { settingsRepo.setHideReason(v) }
    fun setStaffTimeoutMinutes(v: Int) = viewModelScope.launch { settingsRepo.setStaffTimeoutMinutes(v) }
    fun setEnableLogging(v: Boolean) = viewModelScope.launch { settingsRepo.setEnableLogging(v) }
    fun setPassTitle(v: String) = viewModelScope.launch { settingsRepo.setPassTitle(v) }
    fun setSerialPath(v: String) = viewModelScope.launch { settingsRepo.setSerialDevicePath(v) }

    fun testPrint(driverId: String) {
        viewModelScope.launch {
            testResult.value = "Printing test receipt…"
            val cfg = settingsRepo.current()
            val now = Date()
            val receipt = PassReceipt(
                title = "PRINTER TEST",
                name = "Test Student", id = "00000",
                date = SimpleDateFormat("MM/dd/yyyy", Locale.US).format(now),
                time = SimpleDateFormat("hh:mm a", Locale.US).format(now),
                period = "P1", course = "Test Course", room = "101",
                reason = "Test", staff = "TEST", status = "Excused"
            )
            val job = EscPos.buildReceipt(receipt, cfg.encoding, cfg.selectCodePage)
            val result = printer.testPrint(driverId, job)
            testResult.value = when (result) {
                is PrintResult.Confirmed -> "Test print confirmed ✓"
                is PrintResult.Sent -> "Test sent (no confirmation available)"
                is PrintResult.Failed -> "Failed: ${result.reason}"
            }
        }
    }
}
