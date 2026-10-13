package com.noctra.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY sortOrder, id")
    suspend fun allOnce(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE status != 'COMPLETED' AND planDate <= :date ORDER BY CASE status WHEN 'IN_PROGRESS' THEN 0 ELSE 1 END, sortOrder, id")
    suspend fun openUntil(date: String): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): TaskEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM tasks")
    suspend fun maxOrder(): Int

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM subtasks ORDER BY sortOrder, id")
    fun observeAllSubtasks(): Flow<List<SubtaskEntity>>

    @Query("SELECT * FROM subtasks WHERE taskId = :taskId ORDER BY sortOrder, id")
    suspend fun subtasksOnce(taskId: Long): List<SubtaskEntity>

    @Insert
    suspend fun insertSubtask(subtask: SubtaskEntity): Long

    @Update
    suspend fun updateSubtask(subtask: SubtaskEntity)

    @Query("DELETE FROM subtasks WHERE id = :id")
    suspend fun deleteSubtask(id: Long)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions WHERE endedAt IS NULL ORDER BY id DESC LIMIT 1")
    fun observeOpen(): Flow<DaySession?>

    @Query("SELECT * FROM sessions WHERE endedAt IS NULL ORDER BY id DESC LIMIT 1")
    suspend fun getOpen(): DaySession?

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun get(id: Long): DaySession?

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observe(id: Long): Flow<DaySession?>

    @Query("SELECT * FROM sessions ORDER BY id")
    fun observeAll(): Flow<List<DaySession>>

    @Insert
    suspend fun insert(session: DaySession): Long

    @Update
    suspend fun update(session: DaySession)

    @Query("SELECT * FROM activity_logs WHERE sessionId = :sessionId ORDER BY startAt")
    fun observeLogs(sessionId: Long): Flow<List<ActivityLog>>

    @Query("SELECT * FROM activity_logs WHERE sessionId = :sessionId ORDER BY startAt")
    suspend fun getLogs(sessionId: Long): List<ActivityLog>

    @Query("SELECT * FROM activity_logs ORDER BY startAt")
    fun observeAllLogs(): Flow<List<ActivityLog>>

    @Query("SELECT * FROM activity_logs ORDER BY startAt")
    suspend fun allLogsOnce(): List<ActivityLog>

    @Query("SELECT * FROM activity_logs WHERE id = :id")
    suspend fun getLog(id: Long): ActivityLog?

    @Query("SELECT * FROM activity_logs WHERE sessionId = :sessionId AND kind = 'BREAK' AND endAt IS NULL LIMIT 1")
    suspend fun getOpenBreak(sessionId: Long): ActivityLog?

    @Insert
    suspend fun insertLog(log: ActivityLog): Long

    @Update
    suspend fun updateLog(log: ActivityLog)

    @Query("DELETE FROM activity_logs WHERE id = :id")
    suspend fun deleteLog(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCheckIn(checkIn: CheckIn): Long

    @Update
    suspend fun updateCheckIn(checkIn: CheckIn)

    @Query("SELECT * FROM checkins WHERE id = :id")
    suspend fun getCheckIn(id: Long): CheckIn?

    @Query("SELECT * FROM checkins WHERE sessionId = :sessionId ORDER BY scheduledAt")
    fun observeCheckIns(sessionId: Long): Flow<List<CheckIn>>

    @Query("SELECT * FROM checkins WHERE sessionId = :sessionId ORDER BY scheduledAt")
    suspend fun getCheckIns(sessionId: Long): List<CheckIn>

    @Query("SELECT * FROM checkins ORDER BY scheduledAt")
    fun observeAllCheckIns(): Flow<List<CheckIn>>

    @Query("SELECT * FROM checkins ORDER BY scheduledAt")
    suspend fun allCheckInsOnce(): List<CheckIn>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReport(report: DailyReport): Long

    @Query("SELECT * FROM reports ORDER BY endedAt DESC")
    fun observeReports(): Flow<List<DailyReport>>
}
