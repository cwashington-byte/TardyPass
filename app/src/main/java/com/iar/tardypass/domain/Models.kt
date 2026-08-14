package com.iar.tardypass.domain

import kotlinx.serialization.Serializable

/** One period's class assignment, e.g. schedule["P1"] = ScheduleEntry("Algebra I", "204"). */
@Serializable
data class ScheduleEntry(
    val course: String = "",
    val room: String = ""
)

typealias Schedule = Map<String, ScheduleEntry>

data class Student(
    val id: String,               // SIS Student_Number — stable primary key
    val firstName: String,
    val lastName: String,
    val grade: String? = null,
    val schedule: Schedule = emptyMap(),
    val active: Boolean = true
) {
    val fullName: String get() = "$firstName $lastName"
    val displayName: String get() = "$lastName, $firstName"
}

data class Pass(
    val id: Long = 0,
    val studentId: String,
    val studentFirstName: String,
    val studentLastName: String,
    val timestampMillis: Long,    // captured at print-button press, not render time
    val period: String,
    val course: String,
    val room: String,
    val reason: String?,
    val staffInitials: String?,
    val staffUid: String? = null, // Phase 2: authenticated staff
    val staffName: String? = null,
    val excused: Boolean,
    val printDriver: String,
    val printConfirmed: Boolean
)

/** Everything the receipt needs; mirrors the PWA's buildEscPosReceipt input. */
data class PassReceipt(
    val title: String,
    val name: String,
    val id: String,
    val date: String,
    val time: String,
    val period: String? = null,
    val course: String? = null,
    val room: String? = null,
    val reason: String? = null,
    val staff: String? = null,
    val status: String? = null
)
