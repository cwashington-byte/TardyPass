package com.iar.tardypass.data.repo

import com.iar.tardypass.data.csv.LogCsv
import com.iar.tardypass.data.local.PassDao
import com.iar.tardypass.data.local.PassEntity
import com.iar.tardypass.domain.Pass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar

class PassRepository(private val dao: PassDao) {

    fun latest(limit: Int = 200): Flow<List<Pass>> =
        dao.latest(limit).map { list -> list.map { it.toDomain() } }

    fun today(limit: Int = 500): Flow<List<Pass>> =
        dao.since(startOfToday(), limit).map { list -> list.map { it.toDomain() } }

    val count: Flow<Int> = dao.count()

    suspend fun record(pass: Pass): Long = dao.insert(pass.toEntity())

    suspend fun recentForStudent(studentId: String, limit: Int = 3): List<Pass> =
        dao.recentForStudent(studentId, limit).map { it.toDomain() }

    suspend fun countForStudentSince(studentId: String, sinceMillis: Long): Int =
        dao.countForStudentSince(studentId, sinceMillis)

    /** Duplicate guard: same student + same period within the window. */
    suspend fun isLikelyDuplicate(studentId: String, period: String, windowMinutes: Int = 10): Boolean =
        dao.duplicateCount(
            studentId, period,
            System.currentTimeMillis() - windowMinutes * 60_000L
        ) > 0

    suspend fun exportCsv(): String = LogCsv.build(dao.allForExport().map { it.toDomain() })

    suspend fun clearAll() = dao.deleteAll()

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun PassEntity.toDomain() = Pass(
        id = id, studentId = studentId,
        studentFirstName = studentFirstName, studentLastName = studentLastName,
        timestampMillis = timestampMillis, period = period, course = course, room = room,
        reason = reason, staffInitials = staffInitials, staffUid = staffUid, staffName = staffName,
        excused = excused, printDriver = printDriver, printConfirmed = printConfirmed
    )

    private fun Pass.toEntity() = PassEntity(
        id = 0, studentId = studentId,
        studentFirstName = studentFirstName, studentLastName = studentLastName,
        timestampMillis = timestampMillis, period = period, course = course, room = room,
        reason = reason, staffInitials = staffInitials, staffUid = staffUid, staffName = staffName,
        excused = excused, printDriver = printDriver, printConfirmed = printConfirmed
    )
}
