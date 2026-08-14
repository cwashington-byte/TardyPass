package com.iar.tardypass.data.csv

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import com.iar.tardypass.domain.ScheduleEntry
import com.iar.tardypass.domain.Student
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class RowError(val row: Int, val message: String)

data class RosterImportResult(
    val students: List<Student>,
    val errors: List<RowError>
) {
    val ok: Boolean get() = students.isNotEmpty()
}

/**
 * Parses the SIS roster CSV. Same column contract as the PWA
 * (Tardy-Pass-PWA/src/services/csv.ts): Student_Number|studentId|id,
 * First_Name|firstName|first, Last_Name|lastName|last, Grade|grade,
 * Schedule|schedule (a JSON object cell: {"P1":{"course":"...","room":"..."}}).
 * Unlike the PWA, a bad row is reported and skipped instead of failing the import.
 */
object RosterCsv {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Header names are compared with punctuation and case stripped, so
     * "Student_Number", "student number" and "StudentNumber" all match. Keep
     * this list in sync with the web uploader in public/index.html.
     */
    private fun norm(h: String) = h.lowercase().filter { it.isLetterOrDigit() }

    private val ID_ALIASES = listOf("studentnumber", "studentid", "id", "localid", "number", "studentnum")
    private val FIRST_ALIASES = listOf("firstname", "first", "studentfirstname", "fname")
    private val LAST_ALIASES = listOf("lastname", "last", "studentlastname", "lname")
    private val GRADE_ALIASES = listOf("grade", "gradelevel", "gr")
    private val SCHEDULE_ALIASES = listOf("schedule")

    fun parse(text: String): RosterImportResult {
        val rows = try {
            csvReader().readAllWithHeader(text)
        } catch (e: Exception) {
            return RosterImportResult(emptyList(), listOf(RowError(0, "Not a readable CSV: ${e.message}")))
        }
        if (rows.isEmpty()) {
            return RosterImportResult(emptyList(), listOf(RowError(0, "CSV has no data rows")))
        }

        val students = ArrayList<Student>(rows.size)
        val errors = ArrayList<RowError>()

        rows.forEachIndexed { i, r ->
            val rowNum = i + 2 // 1-based + header row
            // Match on normalized header names so any export spelling works.
            fun col(aliases: List<String>): String? = r.entries
                .firstOrNull { norm(it.key) in aliases }
                ?.value?.trim()?.takeIf { it.isNotEmpty() }

            val id = col(ID_ALIASES)
            val first = col(FIRST_ALIASES)
            val last = col(LAST_ALIASES)
            if (id == null) {
                // Blank trailing lines are common in SIS exports; only report rows with some content
                if (r.values.any { it.isNotBlank() }) errors.add(RowError(rowNum, "Missing student number"))
                return@forEachIndexed
            }
            if (first == null || last == null) {
                errors.add(RowError(rowNum, "Missing name for student $id"))
                return@forEachIndexed
            }

            val scheduleRaw = col(SCHEDULE_ALIASES)
            val schedule = if (scheduleRaw == null) emptyMap() else try {
                parseSchedule(scheduleRaw)
            } catch (e: Exception) {
                errors.add(RowError(rowNum, "Bad schedule for student $id: ${e.message?.take(80)}"))
                emptyMap<String, ScheduleEntry>()
            }

            students.add(
                Student(
                    id = id,
                    firstName = first,
                    lastName = last,
                    grade = col(GRADE_ALIASES),
                    schedule = schedule
                )
            )
        }
        return RosterImportResult(students, errors)
    }

    private fun parseSchedule(raw: String): Map<String, ScheduleEntry> {
        val obj = json.parseToJsonElement(raw).jsonObject
        return obj.entries.associate { (period, v) ->
            val entry = v.jsonObject
            period to ScheduleEntry(
                course = entry["course"]?.jsonPrimitive?.content ?: "",
                room = entry["room"]?.jsonPrimitive?.content ?: ""
            )
        }
    }
}
