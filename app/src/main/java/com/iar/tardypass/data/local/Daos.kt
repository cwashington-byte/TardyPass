package com.iar.tardypass.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {

    /** Name search via FTS (accent-insensitive, token-prefix). Query string like "gar* ali*". */
    @Query(
        """SELECT s.* FROM students s
           JOIN student_fts f ON s.rowid = f.rowid
           WHERE student_fts MATCH :ftsQuery AND s.active = 1
           ORDER BY s.lastName COLLATE NOCASE, s.firstName COLLATE NOCASE
           LIMIT :limit"""
    )
    suspend fun searchByName(ftsQuery: String, limit: Int): List<StudentEntity>

    /** ID lookup by prefix for all-digit queries. */
    @Query(
        """SELECT * FROM students WHERE id LIKE :idPrefix || '%' AND active = 1
           ORDER BY id LIMIT :limit"""
    )
    suspend fun searchById(idPrefix: String, limit: Int): List<StudentEntity>

    @Query("SELECT * FROM students WHERE id = :id")
    suspend fun getById(id: String): StudentEntity?

    @Query("SELECT COUNT(*) FROM students WHERE active = 1")
    fun activeCount(): Flow<Int>

    @Upsert
    suspend fun upsertAll(students: List<StudentEntity>)

    @Query("UPDATE students SET active = 0 WHERE id NOT IN (:keepIds)")
    suspend fun deactivateMissing(keepIds: List<String>)

    @Query("DELETE FROM students")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(students: List<StudentEntity>) {
        deleteAll()
        upsertAll(students)
    }
}

@Dao
interface PassDao {

    @Insert
    suspend fun insert(pass: PassEntity): Long

    @Query("SELECT * FROM passes ORDER BY timestampMillis DESC LIMIT :limit")
    fun latest(limit: Int): Flow<List<PassEntity>>

    @Query(
        """SELECT * FROM passes WHERE timestampMillis >= :sinceMillis
           ORDER BY timestampMillis DESC LIMIT :limit"""
    )
    fun since(sinceMillis: Long, limit: Int): Flow<List<PassEntity>>

    @Query("SELECT COUNT(*) FROM passes WHERE studentId = :studentId AND timestampMillis >= :sinceMillis")
    suspend fun countForStudentSince(studentId: String, sinceMillis: Long): Int

    @Query(
        """SELECT * FROM passes WHERE studentId = :studentId
           ORDER BY timestampMillis DESC LIMIT :limit"""
    )
    suspend fun recentForStudent(studentId: String, limit: Int): List<PassEntity>

    @Query(
        """SELECT COUNT(*) FROM passes WHERE studentId = :studentId AND period = :period
           AND timestampMillis >= :sinceMillis"""
    )
    suspend fun duplicateCount(studentId: String, period: String, sinceMillis: Long): Int

    @Query("SELECT * FROM passes ORDER BY timestampMillis DESC")
    suspend fun allForExport(): List<PassEntity>

    @Query("DELETE FROM passes")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM passes")
    fun count(): Flow<Int>
}
