package com.iar.tardypass.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iar.tardypass.data.repo.AppSettings
import com.iar.tardypass.data.repo.PassRepository
import com.iar.tardypass.data.repo.SettingsRepository
import com.iar.tardypass.data.repo.StudentRepository
import com.iar.tardypass.domain.StaffMember
import com.iar.tardypass.domain.BellSchedule
import com.iar.tardypass.domain.Pass
import com.iar.tardypass.domain.Student
import com.iar.tardypass.domain.receiptFor
import com.iar.tardypass.domain.initialsFrom
import com.iar.tardypass.printing.EscPos
import com.iar.tardypass.printing.PrinterManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

val REASONS = listOf("Tardy", "Restroom", "DOS", "Main Office", "Break", "Other")

/** Used when the reason picker is hidden — every pass is simply a tardy. */
const val DEFAULT_REASON = "Tardy"

data class BannerMessage(val text: String, val isError: Boolean = false)

data class PassFormState(
    val student: Student,
    val period: String,
    val reason: String = DEFAULT_REASON,
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
    val rosterCount: Int = 0,
    val hideReason: Boolean = false,
    /** True when signed in as a shared printer, so staff pick their own name. */
    val deviceMode: Boolean = false,
    val selectedStaff: SelectedStaff? = null,
    val staffChoices: List<StaffMember> = emptyList(),
    val staffPickerOpen: Boolean = false,
    /** A print interrupted to ask who is issuing it; resumes once they choose. */
    val pendingPrintExcused: Boolean? = null,
    /** Bumped after a print to send focus back to the search field for the next student. */
    val focusSearchTick: Int = 0,
    /**
     * Initials derived from the signed-in account's name. When present there is
     * nothing to type — the account already says who this is.
     */
    val accountInitials: String? = null,
    val bell: BellSchedule = BellSchedule.EMPTY
)

/**
 * Who is currently claiming passes on a shared printer.
 *
 * A blank [id] means the name was typed by hand — a substitute, or someone who
 * doesn't normally issue passes. Those stay distinguishable from a picked name
 * all the way into Firestore, so a typo can never merge into a real person's
 * totals.
 */
data class SelectedStaff(val id: String, val name: String) {
    val initials: String get() = initialsFrom(name)
    val isAdHoc: Boolean get() = id.isBlank()
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
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
            // memberRole is null for a moment on cold start; the cached role keeps
            // the app from briefly looking like a personal account and attributing
            // an early press to the printer itself.
            val roleFlow = sync?.memberRole ?: flowOf(null)
            combine(settings.settings, roleFlow) { cfg, role -> cfg to role }
                .collect { (cfg, role) ->
                    val effectiveRole = role ?: cfg.lastKnownRole.takeIf { it.isNotEmpty() }
                    val expired = isExpired(cfg)
                    if (expired) launch { settings.clearSelectedStaff() }
                    _state.value = _state.value.copy(
                        initials = _state.value.initials.ifEmpty { cfg.staffInitials },
                        hideReason = cfg.hideReason,
                        deviceMode = effectiveRole == "device",
                        bell = BellSchedule.fromJson(cfg.bellScheduleJson),
                        // Keyed on the NAME, not the id: an ad-hoc selection has
                        // a blank id and must still survive a restart.
                        selectedStaff = if (expired || cfg.selectedStaffName.isEmpty()) null
                        else SelectedStaff(cfg.selectedStaffUid, cfg.selectedStaffName)
                    )
                }
        }
        // Only a shared printer needs the staff directory, so a personal install
        // never opens this listener.
        viewModelScope.launch {
            (sync?.memberRole ?: flowOf(null))
                .flatMapLatest { role ->
                    if (role == "device") sync?.observeStaff() ?: flowOf(emptyList())
                    else flowOf(emptyList())
                }
                .collect { staff ->
                    _state.value = _state.value.copy(staffChoices = staff)
                }
        }
        // Nudges the UI so an idle selection visibly expires without a print.
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                val cfg = settings.current()
                if (isExpired(cfg)) {
                    settings.clearSelectedStaff()
                }
            }
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
        viewModelScope.launch {
            (auth?.currentUser ?: flowOf(null)).collect { user ->
                _state.value = _state.value.copy(
                    accountInitials = user?.displayName
                        ?.let { initialsFrom(it) }
                        ?.takeIf { it.isNotBlank() }
                )
            }
        }
    }

    fun onQueryChange(q: String) {
        _state.value = _state.value.copy(query = q)
        queryFlow.value = q
    }

    /**
     * Recomputed from the stored wall-clock stamp rather than a live timer, so
     * process death and reboot are handled for free. Wall clock (not
     * elapsedRealtime) because that one resets on reboot and would make an
     * overnight selection look fresh.
     */
    private fun isExpired(cfg: AppSettings, now: Long = System.currentTimeMillis()): Boolean {
        if (cfg.selectedStaffUid.isEmpty()) return false
        if (cfg.staffTimeoutMinutes <= 0) return false
        val age = now - cfg.selectedStaffAtMillis
        return age >= cfg.staffTimeoutMinutes * 60_000L || age < 0 // negative = clock moved back
    }

    fun openStaffPicker() {
        _state.value = _state.value.copy(staffPickerOpen = true)
    }

    fun dismissStaffPicker() {
        // Abandons any print that was waiting on a name rather than leaving it armed.
        _state.value = _state.value.copy(staffPickerOpen = false, pendingPrintExcused = null)
    }

    fun onStaffSelected(member: StaffMember) = selectStaff(member.id, member.name)

    /** Free-text fallback for a name that isn't on the admin-managed list. */
    fun onStaffTyped(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        // If it happens to match a real entry, prefer that — it keeps the id and
        // the reporting linkage rather than creating a lookalike.
        val match = _state.value.staffChoices
            .firstOrNull { it.name.equals(clean, ignoreCase = true) }
        selectStaff(match?.id ?: "", match?.name ?: clean)
    }

    private fun selectStaff(id: String, name: String) {
        val resume = _state.value.pendingPrintExcused
        _state.value = _state.value.copy(
            selectedStaff = SelectedStaff(id, name),
            staffPickerOpen = false,
            pendingPrintExcused = null
        )
        viewModelScope.launch {
            settings.setSelectedStaff(id, name)
            // The press that opened the picker continues on its own.
            if (resume != null) doPrint(resume, skipDuplicateCheck = true)
        }
    }

    fun clearSelectedStaff() {
        _state.value = _state.value.copy(
            selectedStaff = null, staffPickerOpen = false, pendingPrintExcused = null
        )
        viewModelScope.launch { settings.clearSelectedStaff() }
    }

    fun onInitialsChange(v: String) {
        val up = v.uppercase().take(6)
        _state.value = _state.value.copy(initials = up)
        viewModelScope.launch { settings.setStaffInitials(up) }
    }

    fun selectStudent(s: Student) {
        // Form state is rebuilt per student — the PWA's stale-period bug can't happen.
        //
        // With a bell schedule, prefer the period they're arriving for: during
        // passing time that's the one about to start, not the one that ended. The
        // suggestion is only used if this student actually has that period, so
        // free periods and half-day timetables fall back to the old behaviour —
        // which is also exactly what happens when no schedule is configured.
        val suggested = _state.value.bell
            .suggestedPeriodAt(minutesOfDayNow())
            ?.name
            ?.takeIf { name -> s.schedule.keys.any { it.equals(name, ignoreCase = true) } }
        val firstPeriod = suggested
            ?: s.schedule.keys.sortedWith(periodComparator).firstOrNull()
            ?: ""
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

            // Re-check expiry at the moment of the press: a press landing between
            // expiry and the next 30s tick must not attribute to whoever left.
            val cfgNow = settings.current()
            val selected = _state.value.selectedStaff?.takeUnless { isExpired(cfgNow) }
            if (_state.value.deviceMode && selected == null) {
                if (selected == null && _state.value.selectedStaff != null) {
                    settings.clearSelectedStaff()
                }
                _state.value = _state.value.copy(
                    selectedStaff = null,
                    pendingPrintExcused = excused,
                    staffPickerOpen = true
                )
                return@launch
            }

            _state.value = _state.value.copy(printing = true, banner = null)
            val cfg = settings.current()
            val now = Date() // captured at button press — the printed and logged time agree
            val entry = form.student.schedule[form.period]
            val reason = when {
                cfg.hideReason -> DEFAULT_REASON
                form.reason == "Other" -> form.customReason.trim().ifEmpty { "Other" }
                else -> form.reason
            }

            // Picked staff on a shared printer, else the signed-in account's own
            // name, else typed initials — only reachable when nobody is signed in.
            val effectiveInitials = selected?.initials
                ?: _state.value.accountInitials
                ?: st.initials.trim().ifEmpty { null }

            val account = auth?.currentUser?.value
            val deviceMode = _state.value.deviceMode
            // In device mode the pass belongs to the person who picked their name
            // off the staff list; the signed-in account is only the machine, and
            // staffUid stays null because nothing proved who it was. A personal
            // account keeps the proven staffUid.
            val attributedUid = if (deviceMode) null else account?.uid
            val attributedStaffId = if (deviceMode) selected?.id?.takeIf { it.isNotBlank() } else null
            val attributedName = if (deviceMode) selected?.name else account?.displayName

            // Built before printing so the slip and the recorded row are composed
            // from one object; the driver and confirmation are stamped on after.
            val draft = Pass(
                studentId = form.student.id,
                studentFirstName = form.student.firstName,
                studentLastName = form.student.lastName,
                timestampMillis = now.time,
                period = form.period,
                course = entry?.course ?: "",
                room = entry?.room ?: "",
                reason = reason,
                // On a shared printer the picked person's initials are authoritative;
                // the typed field is only a fallback for unsynced devices.
                staffInitials = effectiveInitials,
                staffUid = attributedUid,
                staffId = attributedStaffId,
                staffName = attributedName,
                excused = excused,
                printDriver = "",
                printConfirmed = false
            )

            val job = EscPos.buildReceipt(
                receiptFor(draft, cfg, bell = _state.value.bell),
                cfg.encoding, cfg.selectCodePage
            )
            val (result, driverId) = printer.print(job)

            if (result.succeeded) {
                if (cfg.enableLogging) {
                    val pass = draft.copy(
                        printDriver = driverId,
                        printConfirmed = result.confirmed,
                        minutesLate = receiptFor(draft, cfg, bell = _state.value.bell).minutesLate
                    )
                    passes.record(pass)

                    // Queues into Firestore's offline cache; flushes when online.
                    // Skipped when we have no attribution — the rules would reject
                    // it anyway, and a failed write just shows a red banner.
                    val canSync = account != null &&
                        (if (deviceMode) attributedName != null else attributedUid != null)
                    if (canSync) {
                        sync?.recordPass(
                            pass, attributedUid, attributedName, form.student.grade,
                            deviceUid = if (deviceMode) account!!.uid else null,
                            deviceName = if (deviceMode) account!!.displayName else null,
                            staffId = attributedStaffId
                        )
                    }
                    if (deviceMode) settings.touchSelectedStaff()
                }
                val confirmNote = if (result.confirmed) "printed" else "sent to printer"
                // Close the finished student and put the cursor back in search —
                // the next thing anyone does is look up another student.
                _state.value = _state.value.copy(
                    printing = false,
                    query = "",
                    results = emptyList(),
                    form = null,
                    focusSearchTick = _state.value.focusSearchTick + 1,
                    banner = BannerMessage("Pass $confirmNote for ${form.student.fullName}")
                )
                queryFlow.value = ""
                // No refreshFormCounts here: the form is closed, and re-selecting
                // this student recomputes their counts from scratch anyway.
            } else {
                val reasonText = (result as com.iar.tardypass.printing.PrintResult.Failed).reason
                _state.value = _state.value.copy(
                    printing = false,
                    banner = BannerMessage("Print failed: $reasonText", isError = true)
                )
            }
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

    private fun minutesOfDayNow(): Int = Calendar.getInstance().let {
        it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)
    }

    private val periodComparator = Comparator<String> { a, b ->
        val na = a.filter { it.isDigit() }.toIntOrNull()
        val nb = b.filter { it.isDigit() }.toIntOrNull()
        when {
            na != null && nb != null -> na.compareTo(nb)
            else -> a.compareTo(b)
        }
    }
}
