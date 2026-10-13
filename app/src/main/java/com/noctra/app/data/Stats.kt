package com.noctra.app.data

data class DayStats(
    val elapsedMin: Long,
    val studyMin: Long,
    val breakMin: Long,
    val distractionMin: Long,
    val unclassifiedMin: Long
)

data class TaskTally(val planned: Int, val completed: Int)

object Stats {
    fun minutes(log: ActivityLog, fallbackEnd: Long): Long =
        ((log.endAt ?: fallbackEnd) - log.startAt).coerceAtLeast(0) / 60000

    fun day(wakeAt: Long?, endedAt: Long?, now: Long, logs: List<ActivityLog>): DayStats {
        if (wakeAt == null) return DayStats(0, 0, 0, 0, 0)
        val end = endedAt ?: now
        val elapsed = ((end - wakeAt).coerceAtLeast(0)) / 60000
        fun sum(kind: ActivityKind): Long =
            logs.filter { it.kind == kind }
                .sumOf { ((it.endAt ?: end) - it.startAt).coerceAtLeast(0) } / 60000
        val study = sum(ActivityKind.STUDY)
        val brk = sum(ActivityKind.BREAK)
        val dis = sum(ActivityKind.DISTRACTION)
        return DayStats(elapsed, study, brk, dis, (elapsed - study - brk - dis).coerceAtLeast(0))
    }

    fun tally(tasks: List<TaskEntity>, date: String, since: Long, until: Long = Long.MAX_VALUE): TaskTally {
        val completed = tasks.count { t -> t.completedAt?.let { it in since..until } == true }
        val open = tasks.count { it.status != TaskStatus.COMPLETED && it.planDate <= date }
        return TaskTally(completed + open, completed)
    }
}
