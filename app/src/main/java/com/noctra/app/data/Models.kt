package com.noctra.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class Subject(val label: String) {
    PHYSICS("Physics"),
    CHEMISTRY("Chemistry"),
    BIOLOGY("Biology"),
    CUSTOM("Custom")
}

enum class Priority(val label: String) {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High")
}

enum class TaskStatus(val label: String) {
    PENDING("Not started"),
    IN_PROGRESS("In progress"),
    COMPLETED("Completed"),
    POSTPONED("Postponed")
}

enum class ActivityKind { STUDY, BREAK, DISTRACTION }

enum class CheckInResponse { COMPLETED, STILL_WORKING, DISTRACTED, STUCK, BREAK }

@Entity(tableName = "tasks", indices = [Index("planDate")])
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val subject: Subject = Subject.PHYSICS,
    val customSubject: String = "",
    val topic: String = "",
    val estimateMin: Int = 60,
    val priority: Priority = Priority.MEDIUM,
    val deadlineEpochDay: Long? = null,
    val notes: String = "",
    val status: TaskStatus = TaskStatus.PENDING,
    val progress: Int = 0,
    val sortOrder: Int = 0,
    val planDate: String,
    val postponeCount: Int = 0,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val subjectLabel: String
        get() = if (subject == Subject.CUSTOM && customSubject.isNotBlank()) customSubject else subject.label
}

@Entity(
    tableName = "subtasks",
    foreignKeys = [ForeignKey(
        entity = TaskEntity::class,
        parentColumns = ["id"],
        childColumns = ["taskId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("taskId")]
)
data class SubtaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long = 0,
    val title: String,
    val done: Boolean = false,
    val sortOrder: Int = 0
)

@Entity(tableName = "sessions")
data class DaySession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val wakeCheckSentAt: Long? = null,
    val wakeRetryAt: Long? = null,
    val wakeAt: Long? = null,
    val endedAt: Long? = null,
    val currentTaskId: Long? = null,
    val paused: Boolean = false,
    val nextCheckInAt: Long? = null,
    val accountedUntil: Long? = null
)

@Entity(
    tableName = "activity_logs",
    foreignKeys = [ForeignKey(
        entity = DaySession::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionId")]
)
data class ActivityLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val kind: ActivityKind,
    val taskId: Long? = null,
    val startAt: Long,
    val endAt: Long? = null,
    val verified: Boolean = true,
    val note: String = ""
)

@Entity(
    tableName = "checkins",
    foreignKeys = [ForeignKey(
        entity = DaySession::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["sessionId", "scheduledAt"], unique = true)]
)
data class CheckIn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val taskId: Long? = null,
    val scheduledAt: Long,
    val respondedAt: Long? = null,
    val response: CheckInResponse? = null,
    val studiedMin: Int = 0,
    val wastedMin: Int = 0,
    val note: String = "",
    val manual: Boolean = false
)

@Entity(tableName = "reports", indices = [Index(value = ["sessionId"], unique = true)])
data class DailyReport(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val date: String,
    val wakeAt: Long,
    val endedAt: Long,
    val elapsedMin: Long,
    val studyMin: Long,
    val breakMin: Long,
    val distractionMin: Long,
    val unclassifiedMin: Long,
    val tasksPlanned: Int,
    val tasksCompleted: Int,
    val checkInsAnswered: Int,
    val checkInsUnanswered: Int,
    val generatedAt: Long
)
