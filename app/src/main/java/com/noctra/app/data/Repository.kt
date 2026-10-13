package com.noctra.app.data

import android.content.Context
import com.noctra.app.notify.Notifier
import com.noctra.app.notify.Scheduler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class CheckInInput(
    val response: CheckInResponse,
    val studiedMin: Int = 0,
    val wastedMin: Int = 0,
    val note: String = "",
    val progress: Int? = null,
    val estimateMin: Int? = null,
    val confirmComplete: Boolean = false
)

data class CheckInOutcome(
    val message: String,
    val nextTask: TaskEntity? = null,
    val splitTaskId: Long? = null
)

data class CheckInContext(
    val session: DaySession,
    val task: TaskEntity?,
    val scheduledAt: Long?,
    val availableMin: Int,
    val intervalMin: Int
)

class NoctraRepository(
    private val ctx: Context,
    db: AppDatabase,
    val settings: SettingsStore,
    private val scheduler: Scheduler
) {
    private val taskDao = db.tasks()
    private val sessionDao = db.sessions()
    private val mutex = Mutex()
    private val timeUpEvents = MutableSharedFlow<Long>(extraBufferCapacity = 4)

    /** Emits a task id when that task's timer reaches its planned time. */
    val timeUp: SharedFlow<Long> = timeUpEvents.asSharedFlow()

    fun tasks() = taskDao.observeAll()
    fun allSubtasks() = taskDao.observeAllSubtasks()
    fun openSession() = sessionDao.observeOpen()
    fun session(id: Long) = sessionDao.observe(id)
    fun allSessions() = sessionDao.observeAll()
    fun logs(sessionId: Long) = sessionDao.observeLogs(sessionId)
    fun allLogs() = sessionDao.observeAllLogs()
    fun checkIns(sessionId: Long) = sessionDao.observeCheckIns(sessionId)
    fun allCheckIns() = sessionDao.observeAllCheckIns()
    fun reports() = sessionDao.observeReports()

    suspend fun getTask(id: Long) = taskDao.get(id)
    suspend fun getSubtasks(taskId: Long) = taskDao.subtasksOnce(taskId)
    suspend fun tasksSnapshot(): List<TaskEntity> = taskDao.allOnce()
    suspend fun openSessionOnce(): DaySession? = sessionDao.getOpen()
    suspend fun sessionLogs(sessionId: Long): List<ActivityLog> = sessionDao.getLogs(sessionId)

    // ---------------------------------------------------------------- task timer

    private fun remainingMs(t: TaskEntity, now: Long): Long = t.estimateMin * 60_000L - t.workedAt(now)

    /** Keeps exactly one alarm: the moment the running task reaches its planned time. */
    private suspend fun syncTimer() {
        val now = System.currentTimeMillis()
        val running = taskDao.running().firstOrNull()
        val remaining = running?.let { remainingMs(it, now) } ?: 0L
        if (running != null && remaining > 0) scheduler.scheduleTaskTimer(now + remaining) else scheduler.cancelTaskTimer()
    }

    /**
     * Stops the clock on running tasks (those matching [filter]) and books the time worked as STUDY.
     * Returns the updated session; the caller is responsible for saving it.
     */
    private suspend fun settleRunning(
        s: DaySession?,
        now: Long,
        reason: PauseReason?,
        keepPaused: Boolean = true,
        filter: (TaskEntity) -> Boolean = { true }
    ): DaySession? {
        var session = s
        for (t in taskDao.running()) {
            if (!filter(t)) continue
            val since = t.runningSince ?: continue
            val sess = session
            val wake = sess?.wakeAt
            if (sess != null && wake != null) {
                val from = maxOf(since, sess.accountedUntil ?: wake)
                if (now - from >= 60_000L) {
                    sessionDao.insertLog(
                        ActivityLog(sessionId = sess.id, kind = ActivityKind.STUDY, taskId = t.id, startAt = from, endAt = now, note = "timer")
                    )
                    session = sess.copy(accountedUntil = now)
                }
            }
            taskDao.update(
                t.copy(
                    workedMs = t.workedMs + (now - since).coerceAtLeast(0),
                    runningSince = null,
                    pausedAt = if (keepPaused) now else null,
                    pauseReason = if (keepPaused) reason else null
                )
            )
        }
        return session
    }

    suspend fun pauseTask(id: Long, reason: PauseReason) = mutex.withLock {
        val now = System.currentTimeMillis()
        var s = settleRunning(sessionDao.getOpen(), now, reason) { it.id == id }
        if (reason == PauseReason.BREAK && s != null && s.wakeAt != null) s = startBreakInternal(s, now)
        if (s != null && s.wakeAt != null) sessionDao.update(s)
        syncTimer()
        Notifier.cancel(ctx, Notifier.TIMEUP_ID)
    }

    suspend fun extendTask(id: Long, minutes: Int) = mutex.withLock {
        val t = taskDao.get(id) ?: return@withLock
        taskDao.update(t.copy(estimateMin = t.estimateMin + minutes.coerceAtLeast(1)))
        syncTimer()
        Notifier.cancel(ctx, Notifier.TIMEUP_ID)
    }

    /** Called by the alarm. Returns the task if it really is out of time. */
    suspend fun onTaskTimerFired(): TaskEntity? = mutex.withLock {
        val now = System.currentTimeMillis()
        val t = taskDao.running().firstOrNull() ?: return@withLock null
        if (remainingMs(t, now) > 5_000L) {
            syncTimer()
            return@withLock null
        }
        timeUpEvents.tryEmit(t.id)
        t
    }

    private fun intervalMs(st: AppSettings) = st.intervalMin * 60_000L

    suspend fun saveTask(task: TaskEntity, subtasks: List<SubtaskEntity>): Long = mutex.withLock {
        if (task.id != 0L && task.status == TaskStatus.COMPLETED) {
            val s = settleRunning(sessionDao.getOpen(), System.currentTimeMillis(), null, keepPaused = false) { it.id == task.id }
            if (s != null && s.wakeAt != null) sessionDao.update(s)
        }
        // The edit form holds an older copy of the task: never let it overwrite the live timer state.
        val live = if (task.id != 0L) taskDao.get(task.id) else null
        val base = if (live != null) {
            task.copy(runningSince = live.runningSince, workedMs = live.workedMs, pausedAt = live.pausedAt, pauseReason = live.pauseReason)
        } else task
        val normalized = if (base.status == TaskStatus.COMPLETED) {
            base.copy(
                progress = 100,
                completedAt = base.completedAt ?: System.currentTimeMillis(),
                runningSince = null, pausedAt = null, pauseReason = null
            )
        } else {
            base.copy(completedAt = null)
        }
        val id = if (task.id == 0L) {
            taskDao.insert(normalized.copy(sortOrder = taskDao.maxOrder() + 1))
        } else {
            taskDao.update(normalized)
            task.id
        }
        val existing = taskDao.subtasksOnce(id)
        existing.filter { e -> subtasks.none { it.id == e.id } }.forEach { taskDao.deleteSubtask(it.id) }
        subtasks.forEachIndexed { i, s ->
            if (s.id == 0L) taskDao.insertSubtask(s.copy(taskId = id, sortOrder = i))
            else taskDao.updateSubtask(s.copy(sortOrder = i))
        }
        if (normalized.status == TaskStatus.COMPLETED) clearCurrentIf(id)
        syncTimer()
        id
    }

    suspend fun toggleSubtask(s: SubtaskEntity) = mutex.withLock {
        taskDao.updateSubtask(s.copy(done = !s.done))
    }

    suspend fun deleteTask(id: Long) = mutex.withLock {
        val s = settleRunning(sessionDao.getOpen(), System.currentTimeMillis(), null, keepPaused = false) { it.id == id }
        if (s != null && s.wakeAt != null) sessionDao.update(s)
        taskDao.delete(id)
        clearCurrentIf(id)
        syncTimer()
    }

    suspend fun swapOrder(a: TaskEntity, b: TaskEntity) = mutex.withLock {
        taskDao.update(a.copy(sortOrder = b.sortOrder))
        taskDao.update(b.copy(sortOrder = a.sortOrder))
    }

    suspend fun postpone(id: Long) = mutex.withLock {
        val s = settleRunning(sessionDao.getOpen(), System.currentTimeMillis(), null, keepPaused = false) { it.id == id }
        if (s != null && s.wakeAt != null) sessionDao.update(s)
        val t = taskDao.get(id) ?: return@withLock
        taskDao.update(
            t.copy(
                planDate = TimeUtil.tomorrowStr(),
                status = TaskStatus.POSTPONED,
                postponeCount = t.postponeCount + 1,
                completedAt = null,
                runningSince = null, pausedAt = null, pauseReason = null
            )
        )
        clearCurrentIf(id)
        syncTimer()
    }

    suspend fun completeTask(id: Long) = mutex.withLock {
        val s = settleRunning(sessionDao.getOpen(), System.currentTimeMillis(), null, keepPaused = false) { it.id == id }
        if (s != null && s.wakeAt != null) sessionDao.update(s)
        val t = taskDao.get(id) ?: return@withLock
        markCompleted(t)
        clearCurrentIf(id)
        syncTimer()
        Notifier.cancel(ctx, Notifier.TIMEUP_ID)
    }

    suspend fun startTask(id: Long) = mutex.withLock {
        val now = System.currentTimeMillis()
        val t = taskDao.get(id) ?: return@withLock
        val today = TimeUtil.todayStr()
        // Only one task runs at a time: park any other running task.
        var s = settleRunning(sessionDao.getOpen(), now, null) { it.id != id }
        if (t.status != TaskStatus.COMPLETED) {
            // Resuming after a "distracted" pause: that gap was lost time.
            val pausedAt = t.pausedAt
            val wake = s?.wakeAt
            if (s != null && wake != null && pausedAt != null && t.pauseReason == PauseReason.DISTRACTED) {
                val from = maxOf(pausedAt, s.accountedUntil ?: wake)
                if (now - from >= 60_000L) {
                    sessionDao.insertLog(
                        ActivityLog(sessionId = s.id, kind = ActivityKind.DISTRACTION, taskId = id, startAt = from, endAt = now, note = "paused: distracted")
                    )
                    s = s.copy(accountedUntil = now)
                }
            }
            taskDao.update(
                t.copy(
                    status = TaskStatus.IN_PROGRESS,
                    completedAt = null,
                    planDate = if (t.planDate > today) today else t.planDate,
                    runningSince = t.runningSince ?: now,
                    pausedAt = null,
                    pauseReason = null
                )
            )
        }
        if (s != null && s.wakeAt != null) {
            // Starting work ends any break that is still open.
            if (sessionDao.getOpenBreak(s.id) != null) s = endBreakInternal(s, now)
            sessionDao.update(s.copy(currentTaskId = id))
        }
        syncTimer()
        Notifier.cancel(ctx, Notifier.TIMEUP_ID)
    }

    suspend fun setProgress(id: Long, progress: Int, estimateMin: Int) = mutex.withLock {
        val t = taskDao.get(id) ?: return@withLock
        applyProgress(t, progress, estimateMin)
    }

    private suspend fun applyProgress(t: TaskEntity, progress: Int?, estimate: Int?) {
        if (t.status == TaskStatus.COMPLETED) return
        val p = (progress ?: t.progress).coerceIn(0, 100)
        val status = if (p > 0 && t.status != TaskStatus.IN_PROGRESS) TaskStatus.IN_PROGRESS else t.status
        taskDao.update(t.copy(progress = p, status = status, estimateMin = estimate ?: t.estimateMin))
    }

    private suspend fun markCompleted(t: TaskEntity) {
        taskDao.update(
            t.copy(
                status = TaskStatus.COMPLETED,
                progress = 100,
                completedAt = System.currentTimeMillis(),
                runningSince = null, pausedAt = null, pauseReason = null
            )
        )
    }

    private suspend fun clearCurrentIf(id: Long) {
        val s = sessionDao.getOpen()
        if (s != null && s.currentTaskId == id) sessionDao.update(s.copy(currentTaskId = null))
    }

    private suspend fun pickCurrent(excludeId: Long? = null): TaskEntity? =
        taskDao.openUntil(TimeUtil.todayStr()).firstOrNull { it.id != excludeId }

    private suspend fun freshSession(now: Long): DaySession {
        val s = sessionDao.getOpen()
        if (s != null && s.wakeAt != null) return s
        if (s != null && s.date == TimeUtil.todayStr()) return s
        if (s != null) sessionDao.update(s.copy(endedAt = s.wakeCheckSentAt ?: now))
        val n = DaySession(date = TimeUtil.todayStr())
        return n.copy(id = sessionDao.insert(n))
    }

    private fun scheduleDailyWake(st: AppSettings) {
        if (st.wakeEnabled) {
            scheduler.scheduleWake(
                TimeUtil.nextOccurrence(st.wakeHour, st.wakeMinute, System.currentTimeMillis() + 1000)
            )
        } else {
            scheduler.cancelWake()
        }
    }

    suspend fun onWakeCheckFired(): Boolean = mutex.withLock {
        val now = System.currentTimeMillis()
        val st = settings.current()
        val open = sessionDao.getOpen()
        scheduleDailyWake(st)
        if (open != null && open.wakeAt != null) return@withLock false
        val cur = freshSession(now)
        sessionDao.update(cur.copy(wakeCheckSentAt = now, wakeRetryAt = null))
        true
    }

    suspend fun notWakeYet(): Long = mutex.withLock {
        val now = System.currentTimeMillis()
        val retry = now + 15 * 60_000L
        val s = freshSession(now)
        if (s.wakeAt == null) sessionDao.update(s.copy(wakeRetryAt = retry))
        scheduler.scheduleWake(retry)
        Notifier.cancel(ctx, Notifier.WAKE_ID)
        retry
    }

    suspend fun confirmWake(wakeAtMs: Long? = null): Boolean = mutex.withLock {
        val now = System.currentTimeMillis()
        val chosen = (wakeAtMs ?: now).coerceIn(now - 24 * 3_600_000L, now)
        val st = settings.current()
        val base = freshSession(now)
        if (base.wakeAt != null) return@withLock false
        val next = now + intervalMs(st)
        sessionDao.update(
            base.copy(
                wakeAt = chosen,
                wakeRetryAt = null,
                accountedUntil = chosen,
                nextCheckInAt = next,
                currentTaskId = pickCurrent()?.id,
                paused = false
            )
        )
        scheduler.scheduleCheckIn(next)
        scheduleDailyWake(st)
        Notifier.cancel(ctx, Notifier.WAKE_ID)
        true
    }

    suspend fun updateWakeTime(newWake: Long) = mutex.withLock {
        val now = System.currentTimeMillis()
        val s = sessionDao.getOpen() ?: return@withLock
        if (s.wakeAt == null) return@withLock
        val w = newWake.coerceIn(now - 24 * 3_600_000L, now)
        val noLogs = sessionDao.getLogs(s.id).isEmpty()
        sessionDao.update(s.copy(wakeAt = w, accountedUntil = if (noLogs) w else s.accountedUntil))
    }

    suspend fun onCheckInFired(): CheckIn? = mutex.withLock {
        val now = System.currentTimeMillis()
        val st = settings.current()
        val s = sessionDao.getOpen() ?: return@withLock null
        if (s.wakeAt == null || s.paused) return@withLock null
        if (sessionDao.getOpenBreak(s.id) != null) return@withLock null
        val scheduled = s.nextCheckInAt ?: now
        val next = now + intervalMs(st)
        sessionDao.update(s.copy(nextCheckInAt = next))
        scheduler.scheduleCheckIn(next)
        val id = sessionDao.insertCheckIn(CheckIn(sessionId = s.id, taskId = s.currentTaskId, scheduledAt = scheduled))
        if (id == -1L) null else sessionDao.getCheckIn(id)
    }

    suspend fun checkInContext(checkInId: Long): CheckInContext? {
        val s = sessionDao.getOpen() ?: return null
        val wake = s.wakeAt ?: return null
        val ci = if (checkInId > 0) sessionDao.getCheckIn(checkInId) else null
        val start = s.accountedUntil ?: wake
        val avail = ((System.currentTimeMillis() - start) / 60000).toInt().coerceAtLeast(0)
        return CheckInContext(
            s,
            s.currentTaskId?.let { taskDao.get(it) },
            ci?.scheduledAt,
            avail,
            settings.current().intervalMin
        )
    }

    private suspend fun recordMinutes(s: DaySession, kind: ActivityKind, minutes: Int, taskId: Long?, now: Long): DaySession {
        if (minutes <= 0) return s
        val start = s.accountedUntil ?: s.wakeAt ?: return s
        val m = minOf(minutes, ((now - start) / 60000).toInt())
        if (m <= 0) return s
        val end = start + m * 60_000L
        sessionDao.insertLog(ActivityLog(sessionId = s.id, kind = kind, taskId = taskId, startAt = start, endAt = end))
        return s.copy(accountedUntil = end)
    }

    private suspend fun startBreakInternal(s: DaySession, now: Long): DaySession {
        if (sessionDao.getOpenBreak(s.id) != null) return s
        // A running task timer must not keep counting during a break.
        val settled = settleRunning(s, now, PauseReason.BREAK) ?: s
        syncTimer()
        sessionDao.insertLog(ActivityLog(sessionId = settled.id, kind = ActivityKind.BREAK, startAt = now))
        scheduler.cancelCheckIn()
        Notifier.showBreak(ctx, now)
        return settled.copy(nextCheckInAt = null)
    }

    suspend fun respond(checkInId: Long, input: CheckInInput): CheckInOutcome = mutex.withLock {
        val now = System.currentTimeMillis()
        var s = sessionDao.getOpen()
        if (s == null || s.wakeAt == null) return@withLock CheckInOutcome("No active day. Confirm you are awake first.")
        val task = s.currentTaskId?.let { taskDao.get(it) }
        var ci = if (checkInId > 0) sessionDao.getCheckIn(checkInId) else null
        if (ci == null) {
            val c = CheckIn(sessionId = s.id, taskId = s.currentTaskId, scheduledAt = now, manual = true)
            ci = c.copy(id = sessionDao.insertCheckIn(c))
        }
        s = recordMinutes(s, ActivityKind.STUDY, input.studiedMin, task?.id, now)
        if (input.response == CheckInResponse.DISTRACTED) {
            s = recordMinutes(s, ActivityKind.DISTRACTION, input.wastedMin, task?.id, now)
        }
        var next: TaskEntity? = null
        var split: Long? = null
        val title = task?.title ?: "your task"
        val message: String
        when (input.response) {
            CheckInResponse.COMPLETED -> {
                if (input.confirmComplete && task != null) {
                    markCompleted(task)
                    s = s.copy(currentTaskId = null)
                    next = pickCurrent(task.id)
                    message = "\"$title\" is marked completed."
                } else {
                    message = "Logged. The task stays open until you confirm completion."
                }
            }
            CheckInResponse.STILL_WORKING -> {
                if (task != null) applyProgress(task, input.progress, input.estimateMin)
                message = "Logged. Keep going."
            }
            CheckInResponse.DISTRACTED ->
                message = "Distraction logged. Next action: close whatever pulled you away, reopen \"$title\" and do the next small step now."
            CheckInResponse.STUCK -> {
                split = task?.id
                message = "Logged. Split \"$title\" into smaller subtasks, or switch to another task and come back to it."
            }
            CheckInResponse.BREAK -> {
                s = startBreakInternal(s, now)
                message = "Break started. End it from the Plan screen or the notification when you are back."
            }
        }
        sessionDao.update(s)
        sessionDao.updateCheckIn(
            ci.copy(
                respondedAt = now,
                response = input.response,
                studiedMin = input.studiedMin,
                wastedMin = input.wastedMin,
                note = input.note
            )
        )
        Notifier.cancel(ctx, Notifier.CHECKIN_ID)
        CheckInOutcome(message, next, split)
    }

    suspend fun startBreak() = mutex.withLock {
        val s = sessionDao.getOpen() ?: return@withLock
        if (s.wakeAt == null) return@withLock
        sessionDao.update(startBreakInternal(s, System.currentTimeMillis()))
    }

    private suspend fun endBreakInternal(s: DaySession, now: Long): DaySession {
        val b = sessionDao.getOpenBreak(s.id) ?: return s
        val st = settings.current()
        sessionDao.updateLog(b.copy(endAt = now))
        val next = if (s.paused) null else now + intervalMs(st)
        if (next != null) scheduler.scheduleCheckIn(next)
        Notifier.cancel(ctx, Notifier.BREAK_ID)
        return s.copy(accountedUntil = maxOf(s.accountedUntil ?: now, now), nextCheckInAt = next)
    }

    suspend fun endBreak() = mutex.withLock {
        val s = sessionDao.getOpen() ?: return@withLock
        if (sessionDao.getOpenBreak(s.id) == null) return@withLock
        sessionDao.update(endBreakInternal(s, System.currentTimeMillis()))
    }

    suspend fun pause() = mutex.withLock {
        val s = sessionDao.getOpen() ?: return@withLock
        if (s.wakeAt == null) return@withLock
        sessionDao.update(s.copy(paused = true, nextCheckInAt = null))
        scheduler.cancelCheckIn()
    }

    suspend fun resume() = mutex.withLock {
        val now = System.currentTimeMillis()
        val st = settings.current()
        val s = sessionDao.getOpen() ?: return@withLock
        if (s.wakeAt == null) return@withLock
        if (sessionDao.getOpenBreak(s.id) != null) {
            sessionDao.update(s.copy(paused = false))
            return@withLock
        }
        val next = now + intervalMs(st)
        sessionDao.update(s.copy(paused = false, nextCheckInAt = next))
        scheduler.scheduleCheckIn(next)
    }

    suspend fun finalizeDay(sessionId: Long) = mutex.withLock {
        val now = System.currentTimeMillis()
        var s = sessionDao.get(sessionId) ?: return@withLock
        if (s.endedAt != null) return@withLock
        s = settleRunning(s, now, null) ?: s
        val b = sessionDao.getOpenBreak(s.id)
        if (b != null) sessionDao.updateLog(b.copy(endAt = now))
        s = s.copy(endedAt = now, nextCheckInAt = null)
        sessionDao.update(s)
        scheduler.cancelCheckIn()
        Notifier.cancel(ctx, Notifier.CHECKIN_ID)
        Notifier.cancel(ctx, Notifier.BREAK_ID)
        Notifier.cancel(ctx, Notifier.TIMEUP_ID)
        syncTimer()
        buildReport(s)
    }

    private suspend fun buildReport(s: DaySession) {
        val wake = s.wakeAt ?: return
        val end = s.endedAt ?: return
        val stats = Stats.day(wake, end, end, sessionDao.getLogs(s.id))
        val tally = Stats.tally(taskDao.allOnce(), s.date, wake, end)
        val cis = sessionDao.getCheckIns(s.id)
        sessionDao.upsertReport(
            DailyReport(
                sessionId = s.id,
                date = s.date,
                wakeAt = wake,
                endedAt = end,
                elapsedMin = stats.elapsedMin,
                studyMin = stats.studyMin,
                breakMin = stats.breakMin,
                distractionMin = stats.distractionMin,
                unclassifiedMin = stats.unclassifiedMin,
                tasksPlanned = tally.planned,
                tasksCompleted = tally.completed,
                checkInsAnswered = cis.count { it.respondedAt != null },
                checkInsUnanswered = cis.count { it.respondedAt == null },
                generatedAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun refreshReport(sessionId: Long) {
        val s = sessionDao.get(sessionId)
        if (s != null && s.endedAt != null && s.wakeAt != null) buildReport(s)
    }

    suspend fun updateLogMinutes(logId: Long, minutes: Int) = mutex.withLock {
        val log = sessionDao.getLog(logId) ?: return@withLock
        sessionDao.updateLog(log.copy(endAt = log.startAt + minutes.coerceAtLeast(0) * 60_000L, verified = true))
        refreshReport(log.sessionId)
    }

    suspend fun deleteLog(logId: Long) = mutex.withLock {
        val log = sessionDao.getLog(logId) ?: return@withLock
        sessionDao.deleteLog(logId)
        refreshReport(log.sessionId)
    }

    suspend fun addManualLog(sessionId: Long, kind: ActivityKind, minutes: Int, taskId: Long?) = mutex.withLock {
        val s = sessionDao.get(sessionId) ?: return@withLock
        val wake = s.wakeAt ?: return@withLock
        val end = s.endedAt ?: System.currentTimeMillis()
        val m = minOf(minutes.toLong(), (end - wake) / 60000)
        if (m <= 0) return@withLock
        sessionDao.insertLog(
            ActivityLog(sessionId = s.id, kind = kind, taskId = taskId, startAt = end - m * 60_000L, endAt = end, note = "manual entry")
        )
        refreshReport(sessionId)
    }

    suspend fun rescheduleAll() = mutex.withLock {
        val now = System.currentTimeMillis()
        val st = settings.current()
        val s = sessionDao.getOpen()
        if (!st.onboarded) return@withLock
        if (s != null && s.wakeAt == null && s.wakeRetryAt != null) {
            scheduler.scheduleWake(maxOf(s.wakeRetryAt, now + 5000))
        } else {
            scheduleDailyWake(st)
        }
        if (s != null && s.wakeAt != null) {
            val br = sessionDao.getOpenBreak(s.id)
            if (br != null) {
                Notifier.showBreak(ctx, br.startAt)
            } else if (!s.paused) {
                val at = s.nextCheckInAt?.takeIf { it > now } ?: (now + 60_000L)
                sessionDao.update(s.copy(nextCheckInAt = at))
                scheduler.scheduleCheckIn(at)
            }
        }
        syncTimer()
    }

    suspend fun setWakeTime(hour: Int, minute: Int) {
        settings.setWakeTime(hour, minute)
        rescheduleAll()
    }

    suspend fun setWakeEnabled(enabled: Boolean) {
        settings.setWakeEnabled(enabled)
        rescheduleAll()
    }

    suspend fun setInterval(min: Int) = mutex.withLock {
        settings.setInterval(min)
        val s = sessionDao.getOpen() ?: return@withLock
        if (s.wakeAt == null || s.paused || sessionDao.getOpenBreak(s.id) != null) return@withLock
        val next = System.currentTimeMillis() + min * 60_000L
        sessionDao.update(s.copy(nextCheckInAt = next))
        scheduler.scheduleCheckIn(next)
    }

    suspend fun finishOnboarding() {
        settings.setOnboarded(true)
        rescheduleAll()
    }

    suspend fun exportCsv(): String {
        fun q(v: String) = "\"" + v.replace("\"", "\"\"") + "\""
        val sb = StringBuilder()
        sb.appendLine("TASKS")
        sb.appendLine("id,title,subject,topic,estimateMin,priority,status,progress,planDate,postponeCount,completedAt")
        taskDao.allOnce().forEach {
            sb.appendLine("${it.id},${q(it.title)},${q(it.subjectLabel)},${q(it.topic)},${it.estimateMin},${it.priority},${it.status},${it.progress},${it.planDate},${it.postponeCount},${it.completedAt ?: ""}")
        }
        sb.appendLine()
        sb.appendLine("ACTIVITY")
        sb.appendLine("id,sessionId,kind,taskId,startAt,endAt,verified,note")
        sessionDao.allLogsOnce().forEach {
            sb.appendLine("${it.id},${it.sessionId},${it.kind},${it.taskId ?: ""},${it.startAt},${it.endAt ?: ""},${it.verified},${q(it.note)}")
        }
        sb.appendLine()
        sb.appendLine("CHECKINS")
        sb.appendLine("id,sessionId,taskId,scheduledAt,respondedAt,response,studiedMin,wastedMin,note")
        sessionDao.allCheckInsOnce().forEach {
            sb.appendLine("${it.id},${it.sessionId},${it.taskId ?: ""},${it.scheduledAt},${it.respondedAt ?: ""},${it.response ?: ""},${it.studiedMin},${it.wastedMin},${q(it.note)}")
        }
        return sb.toString()
    }
}
