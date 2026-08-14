package com.iar.tardypass.data.csv

import com.iar.tardypass.domain.Pass
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Log export with the same column contract as the PWA's exportLogsCsv:
 * Student_Number,First_Name,Last_Name,Date,Time,Period,Course,Room,Reason,Recorded_By,Excused
 */
object LogCsv {

    fun build(passes: List<Pass>): String {
        val sb = StringBuilder()
        sb.append("Student_Number,First_Name,Last_Name,Date,Time,Period,Course,Room,Reason,Recorded_By,Excused\r\n")
        val dateFmt = SimpleDateFormat("MM/dd/yyyy", Locale.US)
        val timeFmt = SimpleDateFormat("hh:mm a", Locale.US)
        for (p in passes) {
            val d = Date(p.timestampMillis)
            val recordedBy = p.staffName ?: p.staffInitials ?: ""
            sb.append(
                listOf(
                    p.studentId, p.studentFirstName, p.studentLastName,
                    dateFmt.format(d), timeFmt.format(d),
                    p.period, p.course, p.room, p.reason ?: "",
                    recordedBy, if (p.excused) "TRUE" else "FALSE"
                ).joinToString(",") { esc(it) }
            )
            sb.append("\r\n")
        }
        return sb.toString()
    }

    private fun esc(v: String): String =
        if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + v.replace("\"", "\"\"") + "\""
        } else v
}
