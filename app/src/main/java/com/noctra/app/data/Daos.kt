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

    @Query("SELECT * FROM tasks WHERE runningSince IS NOT NULL")
    suspend fun running(): List<TaskEntity>

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

    @Query("SELECT * FROM subtasks ORDER BY id")
    suspend fun allSubtasksOnce(): List<SubtaskEntity>

    @Query("DELETE FROM subtasks")
    suspend fun deleteAllSubtasks()

    @Query("DELETE FROM tasks")
    suspend fun deleteAllTasks()
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

    @Query("SELECT * FROM sessions WHERE date = :date AND wakeAt IS NULL AND wakeCheckSentAt IS NULL AND endedAt IS NOT NULL ORDER BY id LIMIT 1")
    suspend fun getManualSession(date: String): DaySession?

    @Query("SELECT * FROM activity_logs WHERE note LIKE 'manual study%' ORDER BY startAt DESC")
    fun observeManualStudy(): Flow<List<ActivityLog>>

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

    @Query("SELECT * FROM reports ORDER BY id")
    suspend fun allReportsOnce(): List<DailyReport>

    @Query("SELECT * FROM sessions ORDER BY id")
    suspend fun allSessionsOnce(): List<DaySession>

    @Query("DELETE FROM reports")
    suspend fun deleteAllReports()

    @Query("DELETE FROM checkins")
    suspend fun deleteAllCheckIns()

    @Query("DELETE FROM activity_logs")
    suspend fun deleteAllLogs()

    @Query("DELETE FROM sessions")
    suspend fun deleteAllSessions()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: DailyReport): Long
}

@Dao
interface NocDao {
    @Query("SELECT * FROM noc_messages ORDER BY id")
    suspend fun all(): List<NocMessageEntity>

    @Insert
    suspend fun insert(m: NocMessageEntity): Long

    @Query("DELETE FROM noc_messages")
    suspend fun clear()

    /** Keeps the table from growing forever: drops everything but the newest [keep] rows. */
    @Query("DELETE FROM noc_messages WHERE id NOT IN (SELECT id FROM noc_messages ORDER BY id DESC LIMIT :keep)")
    suspend fun trim(keep: Int)
}
