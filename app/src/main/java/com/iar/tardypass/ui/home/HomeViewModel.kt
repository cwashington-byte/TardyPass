package com.iar.tardypass.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iar.tardypass.data.repo.PassRepository
import com.iar.tardypass.data.repo.SettingsRepository
import com.iar.tardypass.data.repo.StudentRepository
import com.iar.tardypass.domain.Pass
import com.iar.tardypass.domain.PassReceipt
import com.iar.tardypass.domain.Student
import com.iar.tardypass.printing.EscPos
import com.iar.tardypass.printing.PrinterManager
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

val REASONS = listOf("Late", "Restroom", "DOS", "Main Office", "Break", "Other")

data class BannerMessage(val text: String, val isError: Boolean = false)

data class PassFormState(
    val student: Student,
    val period: String,
    val reason: String = "Late",
    val customReason: String = "",
    val termPassCount: Int = 0,
    val recentPasses: List<Pass> = emptyList(),
    val pendingDuplicateExcused: Boolean? = null  // set when duplicate guard wants confirmation
)

data class HomeUiState(
    val query: String = "",
    val results: List<Student> = emptyList(),
    val form: PassFormState? = null,
    val initials: String = "",
    val printing: Boolean = false,
    val banner: BannerMessage? = null,
    val rosterCount: Int = 0
)

@OptIn(FlowPreview::class)
class HomeViewModel(
    private val students: StudentRepository,
    private val passes: PassRepository,
    private val settings: SettingsRepository,
    private val printer: PrinterManager,
    private val auth: com.iar.tardypass.data.repo.AuthRepository? = null,
    private val sync: com.iar.tardypass.data.remote.FirestoreSync? = null
) : ViewModel() {

    val currentStaff = auth?.currentUser

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

    val printerStatus = printer.status

    private val queryFlow = MutableStateFlow("")

    init {
        viewModelScope.launch {
            _state.value = _state.value.copy(initials = settings.current().staffInitials)
        }
        viewModelScope.launch {
            queryFlow.debounce(150).collect { q ->
                val results = if (q.isBlank()) emptyList() else students.search(q)
                _state.value = _state.value.copy(results = results)
            }
        }
        viewModelScope.launch {
            students.activeCount.collect { c ->
                _state.value = _state.value.copy(rosterCount = c)
            }
        }
    }

    fun onQueryChange(q: String) {
        _state.value = _state.value.copy(query = q)
        queryFlow.value = q
    }

    fun onInitialsChange(v: String) {
        val up = v.uppercase().take(6)
        _state.value = _state.value.copy(initials = up)
        viewModelScope.launch { settings.setStaffInitials(up) }
    }

    fun selectStudent(s: Student) {
        // Form state is rebuilt per student — the PWA's stale-period bug can't happen.
        val firstPeriod = s.schedule.keys.sortedWith(periodComparator).firstOrNull() ?: ""
        _state.value = _state.value.copy(
            form = PassFormState(student = s, period = firstPeriod)
        )
        viewModelScope.launch {
            val start = startOfSchoolYear()
            val count = passes.countForStudentSince(s.id, start)
            val recent = passes.recentForStudent(s.id)
            _state.value.form?.takeIf { it.student.id == s.id }?.let { f ->
                _state.value = _state.value.copy(
                    form = f.copy(termPassCount = count, recentPasses = recent)
                )
            }
        }
    }

    fun clearSelection() {
        _state.value = _state.value.copy(form = null)
    }

    fun onPeriodChange(p: String) = updateForm { it.copy(period = p) }
    fun onReasonChange(r: String) = updateForm { it.copy(reason = r) }
    fun onCustomReasonChange(r: String) = updateForm { it.copy(customReason = r) }
    fun dismissBanner() {
        _state.value = _state.value.copy(banner = null)
    }

    fun cancelDuplicate() = updateForm { it.copy(pendingDuplicateExcused = null) }

    fun confirmDuplicatePrint() {
        val excused = _state.value.form?.pendingDuplicateExcused ?: return
        updateForm { it.copy(pendingDuplicateExcused = null) }
        doPrint(excused, skipDuplicateCheck = true)
    }

    fun print(excused: Boolean) = doPrint(excused, skipDuplicateCheck = false)

    private fun doPrint(excused: Boolean, skipDuplicateCheck: Boolean) {
        val st = _state.value
        val form = st.form ?: return
        if (st.printing) return
        viewModelScope.launch {
            if (!skipDuplicateCheck && form.period.isNotBlank() &&
                passes.isLikelyDuplicate(form.student.id, form.period)
            ) {
                updateForm { it.copy(pendingDuplicateExcused = excused) }
                return@launch
            }

            _state.value = _state.value.copy(printing = true, banner = null)
            val cfg = settings.current()
            val now = Date() // captured at button press — the printed and logged time agree
            val entry = form.student.schedule[form.period]
            val reason = if (form.reason == "Other") form.customReason.trim().ifEmpty { "Other" }
            else form.reason

            val receipt = PassReceipt(
                title = cfg.passTitle,
                name = form.student.fullName,
                id = form.student.id,
                date = SimpleDateFormat("MM/dd/yyyy", Locale.US).format(now),
                time = SimpleDateFormat("hh:mm a", Locale.US).format(now),
                period = if (cfg.hidePeriod) null else form.period.ifBlank { null },
                course = if (cfg.hideCourse) null else entry?.course?.ifBlank { null },
                room = if (cfg.hideRoom) null else entry?.room?.ifBlank { null },
                reason = reason,
                staff = st.initials.trim().ifEmpty { null },
                status = if (excused) "Excused" else "Unexcused"
            )
            val job = EscPos.buildReceipt(receipt, cfg.encoding, cfg.selectCodePage)
            val (result, driverId) = printer.print(job)

            if (result.succeeded) {
                if (cfg.enableLogging) {
                    val staff = auth?.currentUser?.value
                    val pass = Pass(
                        studentId = form.student.id,
                        studentFirstName = form.student.firstName,
                        studentLastName = form.student.lastName,
                        timestampMillis = now.time,
                        period = form.period,
                        course = entry?.course ?: "",
                        room = entry?.room ?: "",
                        reason = reason,
                        staffInitials = st.initials.trim().ifEmpty { null },
                        staffUid = staff?.uid,
                        staffName = staff?.displayName,
                        excused = excused,
                        printDriver = driverId,
                        printConfirmed = result.confirmed
                    )
                    passes.record(pass)
                    // Queues into Firestore's offline cache; flushes when online.
                    if (staff != null) sync?.recordPass(pass, staff.uid, staff.displayName)
                }
                val confirmNote = if (result.confirmed) "printed" else "sent to printer"
                _state.value = _state.value.copy(
                    printing = false,
                    query = "",
                    results = emptyList(),
                    banner = BannerMessage("Pass $confirmNote for ${form.student.fullName}")
                )
                queryFlow.value = ""
                refreshFormCounts(form.student)
            } else {
                val reasonText = (result as com.iar.tardypass.printing.PrintResult.Failed).reason
                _state.value = _state.value.copy(
                    printing = false,
                    banner = BannerMessage("Print failed: $reasonText", isError = true)
                )
            }
        }
    }

    private fun refreshFormCounts(student: Student) {
        viewModelScope.launch {
            val count = passes.countForStudentSince(student.id, startOfSchoolYear())
            val recent = passes.recentForStudent(student.id)
            updateForm { it.copy(termPassCount = count, recentPasses = recent) }
        }
    }

    private fun updateForm(f: (PassFormState) -> PassFormState) {
        _state.value.form?.let { _state.value = _state.value.copy(form = f(it)) }
    }

    /** School years start Aug 1 — good enough for a tardy counter. */
    private fun startOfSchoolYear(): Long = Calendar.getInstance().apply {
        if (get(Calendar.MONTH) < Calendar.AUGUST) add(Calendar.YEAR, -1)
        set(Calendar.MONTH, Calendar.AUGUST)
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private val periodComparator = Comparator<String> { a, b ->
        val na = a.filter { it.isDigit() }.toIntOrNull()
        val nb = b.filter { it.isDigit() }.toIntOrNull()
        when {
            na != null && nb != null -> na.compareTo(nb)
            else -> a.compareTo(b)
        }
    }
}
