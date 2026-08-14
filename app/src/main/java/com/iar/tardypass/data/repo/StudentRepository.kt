package com.iar.tardypass.data.repo

import com.iar.tardypass.data.csv.RosterCsv
import com.iar.tardypass.data.csv.RosterImportResult
import com.iar.tardypass.data.local.StudentDao
import com.iar.tardypass.data.local.StudentEntity
import com.iar.tardypass.domain.ScheduleEntry
import com.iar.tardypass.domain.Student
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class StudentRepository(
    private val dao: StudentDao,
    private val sync: com.iar.tardypass.data.remote.FirestoreSync? = null
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val scheduleSerializer = MapSerializer(String.serializer(), ScheduleEntry.serializer())

    val activeCount: Flow<Int> = dao.activeCount()

    /**
     * Search mirrors the PWA behavior: digit queries hit student number (prefix),
     * everything else is accent-insensitive token-prefix name search via FTS.
     */
    suspend fun search(query: String, limit: Int = 50): List<Student> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val entities = if (q.all { it.isDigit() }) {
            dao.searchById(q, limit)
        } else {
            val fts = q.split(Regex("\\s+"))
                .mapNotNull { sanitizeFtsToken(it) }
                .joinToString(" ") { "$it*" }
            if (fts.isBlank()) emptyList() else dao.searchByName(fts, limit)
        }
        return entities.map { it.toDomain() }
    }

    suspend fun getById(id: String): Student? = dao.getById(id)?.toDomain()

    data class ImportOutcome(
        val imported: Int,
        val errors: List<com.iar.tardypass.data.csv.RowError>,
        val deactivated: Boolean,
        val syncedToCloud: Boolean = false,
        val syncError: String? = null
    )

    /** Non-destructive merge: upsert by student number. fullReplace deactivates absent students. */
    suspend fun importCsv(text: String, fullReplace: Boolean = false): ImportOutcome {
        val result: RosterImportResult = RosterCsv.parse(text)
        if (result.students.isEmpty()) return ImportOutcome(0, result.errors, false)
        val now = System.currentTimeMillis()
        dao.upsertAll(result.students.map { it.toEntity(now) })
        if (fullReplace) dao.deactivateMissing(result.students.map { it.id })

        // Local import always succeeds; cloud push is best-effort so a device
        // with no network still gets a working roster.
        var synced = false
        var syncError: String? = null
        if (sync?.isAvailable == true) {
            sync.pushRoster(result.students)
                .onSuccess { synced = true }
                .onFailure { syncError = it.message }
        }
        return ImportOutcome(result.students.size, result.errors, fullReplace, synced, syncError)
    }

    /** Parse-only pass for the import preview screen. */
    fun preview(text: String): RosterImportResult = RosterCsv.parse(text)

    /**
     * FTS MATCH tokens must be bare for the `*` prefix operator to apply —
     * quoting makes it an exact-phrase match, so "gar*" would stop matching
     * "Garcia". Strip everything that isn't a letter or digit (which also
     * neutralizes FTS operators like - " ^ : ()).
     */
    private fun sanitizeFtsToken(t: String): String? =
        t.replace(Regex("[^\\p{L}\\p{N}]"), "").takeIf { it.isNotBlank() }

    private fun StudentEntity.toDomain() = Student(
        id = id,
        firstName = firstName,
        lastName = lastName,
        grade = grade,
        schedule = runCatching { json.decodeFromString(scheduleSerializer, scheduleJson) }
            .getOrDefault(emptyMap()),
        active = active
    )

    private fun Student.toEntity(now: Long) = StudentEntity(
        id = id,
        firstName = firstName,
        lastName = lastName,
        grade = grade,
        scheduleJson = json.encodeToString(scheduleSerializer, schedule),
        active = true,
        updatedAt = now
    )
}
