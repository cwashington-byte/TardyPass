package com.iar.tardypass.domain

import com.iar.tardypass.data.repo.AppSettings
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val DATE_FMT get() = SimpleDateFormat("MM/dd/yyyy", Locale.US)
private val TIME_FMT get() = SimpleDateFormat("hh:mm a", Locale.US)

/**
 * The single place a receipt is composed, used by both the original print and a
 * reprint. Two builders would drift within a term; one means a reissued slip is
 * provably the same pass.
 *
 * [reprintAt] non-null marks this as a reissue: the slip gains a REPRINT banner
 * but keeps the *original* date and time, because a reprint is the same pass,
 * not a new one.
 */
fun receiptFor(
    pass: Pass,
    settings: AppSettings,
    reprintAt: Long? = null,
    bell: BellSchedule = BellSchedule.EMPTY
): PassReceipt {
    val issued = Date(pass.timestampMillis)

    // Recomputed from the pass rather than read off it, so a reprint shows the
    // same number as the original slip even though minutesLate is never stored
    // locally. Measured against the period ON THE PASS, not the wall clock —
    // staff can override the dropdown and the printed number must match the
    // printed period.
    val minutesLate = if (pass.period.isBlank()) null else {
        val cal = Calendar.getInstance().apply { time = issued }
        val minuteOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        bell.minutesLate(pass.period, minuteOfDay)
            // Early arrival prints nothing: "0 minutes late" on an on-time
            // student is worse than silence. An absurd value means clock skew.
            ?.takeIf { it > 0 && it <= MAX_SENSIBLE_MINUTES_LATE }
    }
    return PassReceipt(
        title = settings.passTitle,
        name = "${pass.studentFirstName} ${pass.studentLastName}".trim(),
        id = pass.studentId,
        date = DATE_FMT.format(issued),
        time = TIME_FMT.format(issued),
        period = if (settings.hidePeriod) null else pass.period.ifBlank { null },
        course = if (settings.hideCourse) null else pass.course.ifBlank { null },
        room = if (settings.hideRoom) null else pass.room.ifBlank { null },
        reason = pass.reason,
        // Whatever identified the issuer at the time; fall back to deriving from
        // the recorded name so an older pass still prints something.
        staff = pass.staffInitials
            ?: pass.staffName?.let { initialsFrom(it) }?.takeIf { it.isNotBlank() },
        status = if (pass.excused) "Excused" else "Unexcused",
        minutesLate = minutesLate,
        notice = reprintAt?.let { "*** REPRINT ***" },
        noticeDetail = reprintAt?.let { "Reprinted ${TIME_FMT.format(Date(it))}" },
        footer = settings.passFooter.ifBlank { DEFAULT_PASS_FOOTER }
    )
}
