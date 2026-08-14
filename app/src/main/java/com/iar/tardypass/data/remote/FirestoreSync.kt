package com.iar.tardypass.data.remote

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.iar.tardypass.BuildConfig
import com.iar.tardypass.data.local.StudentDao
import com.iar.tardypass.data.local.StudentEntity
import com.iar.tardypass.domain.Pass
import com.iar.tardypass.domain.ScheduleEntry
import com.iar.tardypass.domain.Student
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.coroutines.tasks.await
import java.util.Calendar

data class SyncStatus(
    val enabled: Boolean = false,
    val rosterListening: Boolean = false,
    val lastRosterSyncMillis: Long? = null,
    val pendingWrites: Boolean = false,
    val lastError: String? = null
)

/**
 * Firestore mirror of the local data.
 *
 * Room stays the query source of truth (FTS search must be instant on weak
 * hardware); Firestore is the sync transport. Offline persistence is on by
 * default, so a pass written with no network queues locally and flushes on
 * reconnect — the hallway workflow never depends on connectivity.
 */
class FirestoreSync(
    private val studentDao: StudentDao,
    private val schoolId: () -> String
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val scheduleSerializer = MapSerializer(String.serializer(), ScheduleEntry.serializer())

    private val db: FirebaseFirestore? by lazy {
        if (!BuildConfig.FIREBASE_CONFIGURED) null
        else runCatching { FirebaseFirestore.getInstance() }.getOrNull()
    }

    private val _status = MutableStateFlow(SyncStatus(enabled = BuildConfig.FIREBASE_CONFIGURED))
    val status: StateFlow<SyncStatus> = _status

    private var rosterListener: ListenerRegistration? = null

    val isAvailable: Boolean get() = db != null

    private fun schoolDoc() = db?.collection("schools")?.document(schoolId())

    // ---- Passes -------------------------------------------------------------

    /**
     * Queues a pass for sync. Deliberately does NOT await the server: the write
     * lands in Firestore's local cache immediately and the UI must not block on
     * the network. Returns false only if sync isn't configured at all.
     */
    fun recordPass(pass: Pass, staffUid: String?, staffName: String?): Boolean {
        val doc = schoolDoc()?.collection("passes")?.document() ?: return false
        val data = hashMapOf(
            "studentId" to pass.studentId,
            "studentFirstName" to pass.studentFirstName,
            "studentLastName" to pass.studentLastName,
            // Server time for ordering across devices with drifting clocks...
            "createdAt" to FieldValue.serverTimestamp(),
            // ...and the device's own clock, which is the real hallway time and
            // is what we display, since serverTimestamp resolves only on flush.
            "localCreatedAt" to pass.timestampMillis,
            "period" to pass.period,
            "course" to pass.course,
            "room" to pass.room,
            "reason" to pass.reason,
            "excused" to pass.excused,
            "staffUid" to staffUid,
            "staffName" to staffName,
            "staffInitials" to pass.staffInitials,
            "printDriver" to pass.printDriver,
            "printConfirmed" to pass.printConfirmed,
            "schoolYear" to currentSchoolYear()
        )
        doc.set(data)
            .addOnFailureListener { e ->
                _status.value = _status.value.copy(lastError = e.message)
            }
        return true
    }

    // ---- Roster -------------------------------------------------------------

    /** Pushes an imported roster to Firestore so sibling devices pick it up. */
    suspend fun pushRoster(students: List<Student>): Result<Int> {
        val school = schoolDoc() ?: return Result.failure(IllegalStateException("Sync not configured"))
        val firestore = db ?: return Result.failure(IllegalStateException("Sync not configured"))
        return runCatching {
            var written = 0
            // Firestore caps a batch at 500 ops; 400 leaves headroom.
            students.chunked(400).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { s ->
                    val ref = school.collection("students").document(s.id)
                    batch.set(
                        ref,
                        hashMapOf(
                            "firstName" to s.firstName,
                            "lastName" to s.lastName,
                            "grade" to s.grade,
                            "schedule" to s.schedule.mapValues { (_, e) ->
                                hashMapOf("course" to e.course, "room" to e.room)
                            },
                            "active" to true,
                            "updatedAt" to FieldValue.serverTimestamp(),
                            "source" to "csv"
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                }
                batch.commit().await()
                written += chunk.size
            }
            written
        }.onFailure { e ->
            _status.value = _status.value.copy(lastError = e.message)
        }
    }

    /**
     * Mirrors the roster into Room. One listener per device: the first callback
     * costs a read per student, subsequent ones only deltas.
     */
    fun startRosterListener(onError: (String) -> Unit = {}) {
        val school = schoolDoc() ?: return
        if (rosterListener != null) return
        rosterListener = school.collection("students")
            .addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
                if (error != null) {
                    _status.value = _status.value.copy(lastError = error.message, rosterListening = false)
                    onError(error.message ?: "Roster sync error")
                    return@addSnapshotListener
                }
                if (snapshot == null || snapshot.isEmpty) return@addSnapshotListener
                val now = System.currentTimeMillis()
                val entities = snapshot.documents.mapNotNull { doc ->
                    val first = doc.getString("firstName") ?: return@mapNotNull null
                    val last = doc.getString("lastName") ?: return@mapNotNull null
                    @Suppress("UNCHECKED_CAST")
                    val rawSchedule = doc.get("schedule") as? Map<String, Map<String, Any?>> ?: emptyMap()
                    val schedule = rawSchedule.mapValues { (_, v) ->
                        ScheduleEntry(
                            course = v["course"] as? String ?: "",
                            room = v["room"] as? String ?: ""
                        )
                    }
                    StudentEntity(
                        id = doc.id,
                        firstName = first,
                        lastName = last,
                        grade = doc.getString("grade"),
                        scheduleJson = json.encodeToString(scheduleSerializer, schedule),
                        active = doc.getBoolean("active") ?: true,
                        updatedAt = now
                    )
                }
                _status.value = _status.value.copy(
                    rosterListening = true,
                    lastRosterSyncMillis = now,
                    pendingWrites = snapshot.metadata.hasPendingWrites(),
                    lastError = null
                )
                pendingRosterUpsert = entities
                onRosterSnapshot?.invoke(entities)
            }
    }

    /** Set by the repository layer, which owns the coroutine scope for DB writes. */
    var onRosterSnapshot: ((List<StudentEntity>) -> Unit)? = null
    private var pendingRosterUpsert: List<StudentEntity> = emptyList()

    fun stopRosterListener() {
        rosterListener?.remove()
        rosterListener = null
        _status.value = _status.value.copy(rosterListening = false)
    }

    private fun currentSchoolYear(): String {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        // School years roll over in August.
        val start = if (cal.get(Calendar.MONTH) >= Calendar.AUGUST) year else year - 1
        return "$start-${start + 1}"
    }
}
