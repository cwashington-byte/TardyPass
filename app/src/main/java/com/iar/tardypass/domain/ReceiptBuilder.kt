package com.iar.tardypass.domain

import com.iar.tardypass.data.repo.AppSettings
import java.text.SimpleDateFormat
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
    reprintAt: Long? = null
): PassReceipt {
    val issued = Date(pass.timestampMillis)
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
        notice = reprintAt?.let { "*** REPRINT ***" },
        noticeDetail = reprintAt?.let { "Reprinted ${TIME_FMT.format(Date(it))}" },
        footer = settings.passFooter.ifBlank { DEFAULT_PASS_FOOTER }
    )
}
