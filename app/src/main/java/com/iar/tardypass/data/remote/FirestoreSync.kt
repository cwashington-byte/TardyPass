package com.iar.tardypass.data.remote

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import com.iar.tardypass.BuildConfig
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

    /**
     * Live view of passes from every device, newest first. Bounded by [limit] and
     * only collected while the Logs screen asks for it — an unbounded listener on
     * this collection is the one thing that would blow past the free tier.
     */
    fun observeAllPasses(limit: Long = 200): Flow<List<Pass>> = callbackFlow {
        val school = schoolDoc()
        if (school == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val registration = school.collection("passes")
            .orderBy("localCreatedAt", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _status.value = _status.value.copy(lastError = error.message)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.map { it.toPass() } ?: emptyList())
            }
        awaitClose { registration.remove() }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toPass() = Pass(
        id = 0,
        remoteId = id,
        studentId = getString("studentId") ?: "",
        studentFirstName = getString("studentFirstName") ?: "",
        studentLastName = getString("studentLastName") ?: "",
        // localCreatedAt is the device's own clock — the real hallway time.
        // Fall back to the server stamp if an older doc predates that field.
        timestampMillis = getLong("localCreatedAt")
            ?: getTimestamp("createdAt")?.toDate()?.time
            ?: 0L,
        period = getString("period") ?: "",
        course = getString("course") ?: "",
        room = getString("room") ?: "",
        reason = getString("reason"),
        staffInitials = getString("staffInitials"),
        staffUid = getString("staffUid"),
        staffName = getString("staffName"),
        excused = getBoolean("excused") ?: false,
        printDriver = getString("printDriver") ?: "",
        printConfirmed = getBoolean("printConfirmed") ?: false
    )

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
                    // A listen error (notably PERMISSION_DENIED) kills the listener
                    // for good. Drop the handle so a later retry can re-subscribe
                    // instead of being blocked by the "already listening" guard.
                    rosterListener?.remove()
                    rosterListener = null
                    _status.value = _status.value.copy(lastError = error.message, rosterListening = false)
                    onError(error.message ?: "Roster sync error")
                    return@addSnapshotListener
                }
                val now = System.currentTimeMillis()
                // An empty collection is a healthy state (no roster imported yet),
                // so report the connection as live before bailing out of the upsert.
                if (snapshot == null || snapshot.isEmpty) {
                    _status.value = _status.value.copy(
                        rosterListening = true,
                        lastRosterSyncMillis = now,
                        lastError = null
                    )
                    return@addSnapshotListener
                }
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
                onRosterSnapshot?.invoke(entities)
            }
    }

    /** Set by the repository layer, which owns the coroutine scope for DB writes. */
    var onRosterSnapshot: ((List<StudentEntity>) -> Unit)? = null

    /** Re-subscribes after a failure (e.g. permissions fixed server-side). */
    fun retryRosterListener() {
        stopRosterListener()
        _status.value = _status.value.copy(lastError = null)
        startRosterListener()
    }

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
