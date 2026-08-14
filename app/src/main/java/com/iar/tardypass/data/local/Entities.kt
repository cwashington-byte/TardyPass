package com.iar.tardypass.data.local

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "students")
data class StudentEntity(
    @PrimaryKey val id: String,          // SIS Student_Number
    val firstName: String,
    val lastName: String,
    val grade: String?,
    val scheduleJson: String,            // JSON: {"P1":{"course":"...","room":"..."},...}
    val active: Boolean = true,
    val updatedAt: Long
)

/**
 * External-content FTS over the roster. unicode61 with remove_diacritics gives
 * accent-insensitive prefix search ("gar*" matches García) — remove_diacritics=1
 * is used because =2 needs SQLite 3.27+ (not present on minSdk 26 devices).
 */
@Fts4(
    contentEntity = StudentEntity::class,
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
    tokenizerArgs = ["remove_diacritics=1"]
)
@Entity(tableName = "student_fts")
data class StudentFtsEntity(
    val firstName: String,
    val lastName: String
)

@Entity(
    tableName = "passes",
    indices = [Index("timestampMillis"), Index("studentId")]
)
data class PassEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: String,
    val studentFirstName: String,
    val studentLastName: String,
    val timestampMillis: Long,
    val period: String,
    val course: String,
    val room: String,
    val reason: String?,
    val staffInitials: String?,
    val staffUid: String?,
    val staffName: String?,
    val excused: Boolean,
    val printDriver: String,
    val printConfirmed: Boolean
)
