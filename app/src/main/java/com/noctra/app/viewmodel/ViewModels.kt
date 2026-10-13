package com.noctra.app.viewmodel

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.noctra.app.NoctraApp
import com.noctra.app.data.ActivityKind
import com.noctra.app.data.ActivityLog
import com.noctra.app.data.AppSettings
import com.noctra.app.data.CheckIn
import com.noctra.app.data.CheckInContext
import com.noctra.app.data.CheckInInput
import com.noctra.app.data.CheckInOutcome
import com.noctra.app.data.DailyReport
import com.noctra.app.data.DaySession
import com.noctra.app.data.DayStats
import com.noctra.app.data.NocAgent
import com.noctra.app.data.NocConfig
import com.noctra.app.data.NoctraRepository
import com.noctra.app.data.PauseReason
import com.noctra.app.data.Gemini
import com.noctra.app.data.Stats
import com.noctra.app.data.SubtaskEntity
import com.noctra.app.data.TaskEntity
import com.noctra.app.data.TaskStatus
import com.noctra.app.data.TaskTally
import com.noctra.app.data.TimeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

private fun ticker(periodMs: Long) = flow {
    while (true) {
        emit(System.currentTimeMillis())
        delay(periodMs)
    }
}

private fun openToday(tasks: List<TaskEntity>): List<TaskEntity> {
    val today = TimeUtil.todayStr()
    return tasks.filter { it.status != TaskStatus.COMPLETED && it.planDate <= today }
}

data class HomeUi(
    val loaded: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val session: DaySession? = null,
    val current: TaskEntity? = null,
    val upcoming: List<TaskEntity> = emptyList(),
    val tally: TaskTally = TaskTally(0, 0),
    val stats: DayStats? = null,
    val breakSince: Long? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo: NoctraRepository = (app as NoctraApp).repo
    private val sessionFlow = repo.openSession()
    private val logsFlow = sessionFlow.flatMapLatest { s -> if (s == null) flowOf(emptyList<ActivityLog>()) else repo.logs(s.id) }

    val ui: StateFlow<HomeUi> = combine(
        repo.settings.flow, repo.tasks(), sessionFlow, logsFlow, ticker(30_000)
    ) { settings, tasks, session, logs, now ->
        val open = openToday(tasks)
        val current = session?.currentTaskId?.let { id -> open.firstOrNull { it.id == id } }
            ?: open.firstOrNull { it.status == TaskStatus.IN_PROGRESS }
        val since = session?.wakeAt ?: TimeUtil.startOfDay(TimeUtil.todayStr())
        HomeUi(
            loaded = true,
            settings = settings,
            session = session,
            current = current,
            upcoming = open.filter { it.id != current?.id }.take(2),
            tally = Stats.tally(tasks, TimeUtil.todayStr(), since),
            stats = session?.let { cur -> cur.wakeAt?.let { Stats.day(it, cur.endedAt, now, logs) } },
            breakSince = logs.firstOrNull { it.kind == ActivityKind.BREAK && it.endAt == null }?.startAt
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUi())

    fun startTask(id: Long) = viewModelScope.launch { repo.startTask(id) }
    fun pauseTask(id: Long, reason: PauseReason) = viewModelScope.launch { repo.pauseTask(id, reason) }
    fun completeTask(id: Long) = viewModelScope.launch { repo.completeTask(id) }
    fun setProgress(id: Long, p: Int, est: Int) = viewModelScope.launch { repo.setProgress(id, p, est) }
    fun startBreak() = viewModelScope.launch { repo.startBreak() }
    fun endBreak() = viewModelScope.launch { repo.endBreak() }
    fun pause() = viewModelScope.launch { repo.pause() }
    fun resume() = viewModelScope.launch { repo.resume() }
}

enum class PlanFilter(val label: String) { TODAY("Today"), LATER("Later"), DONE("Done") }

data class TaskRow(val task: TaskEntity, val subDone: Int, val subTotal: Int)

data class PlannerUi(val filter: PlanFilter = PlanFilter.TODAY, val rows: List<TaskRow> = emptyList(), val loaded: Boolean = false)

class PlannerViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as NoctraApp).repo
    private val filter = kotlinx.coroutines.flow.MutableStateFlow(PlanFilter.TODAY)

    val ui: StateFlow<PlannerUi> = combine(repo.tasks(), repo.allSubtasks(), filter) { tasks, subs, f ->
        val today = TimeUtil.todayStr()
        val filtered = when (f) {
            PlanFilter.TODAY -> tasks.filter { it.status != TaskStatus.COMPLETED && it.planDate <= today }
            PlanFilter.LATER -> tasks.filter { it.status != TaskStatus.COMPLETED && it.planDate > today }
            PlanFilter.DONE -> tasks.filter { it.status == TaskStatus.COMPLETED }.sortedByDescending { it.completedAt }
        }
        PlannerUi(f, filtered.map { t ->
            val own = subs.filter { it.taskId == t.id }
            TaskRow(t, own.count { it.done }, own.size)
        }, true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlannerUi())

    fun setFilter(f: PlanFilter) {
        filter.value = f
    }

    fun move(rows: List<TaskRow>, index: Int, up: Boolean) {
        val other = if (up) index - 1 else index + 1
        if (other !in rows.indices) return
        viewModelScope.launch { repo.swapOrder(rows[index].task, rows[other].task) }
    }

    fun postpone(id: Long) = viewModelScope.launch { repo.postpone(id) }
    fun complete(id: Long) = viewModelScope.launch { repo.completeTask(id) }
    fun start(id: Long) = viewModelScope.launch { repo.startTask(id) }
    fun pause(id: Long, reason: PauseReason) = viewModelScope.launch { repo.pauseTask(id, reason) }
    fun delete(id: Long) = viewModelScope.launch { repo.deleteTask(id) }
}

class TaskEditViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    private val repo = (app as NoctraApp).repo
    val taskId: Long = handle.get<Long>("taskId") ?: 0L
    var task by mutableStateOf<TaskEntity?>(null)
        private set
    var subtasks by mutableStateOf<List<SubtaskEntity>>(emptyList())
        private set
    var missing by mutableStateOf(false)
        private set
    val customSubjects: StateFlow<List<String>> = repo.settings.flow
        .map { it.customSubjects }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            if (taskId == 0L) {
                task = TaskEntity(title = "", planDate = TimeUtil.todayStr())
            } else {
                val t = repo.getTask(taskId)
                if (t == null) missing = true else {
                    subtasks = repo.getSubtasks(taskId)
                    task = t
                }
            }
        }
    }

    fun save(t: TaskEntity, subs: List<SubtaskEntity>, done: () -> Unit) {
        viewModelScope.launch {
            repo.saveTask(t, subs)
            done()
        }
    }

    fun delete(done: () -> Unit) {
        viewModelScope.launch {
            repo.deleteTask(taskId)
            done()
        }
    }
}

data class CheckInUi(val loaded: Boolean = false, val context: CheckInContext? = null)

class CheckInViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    private val repo = (app as NoctraApp).repo
    private val checkInId: Long = handle.get<Long>("checkInId") ?: 0L
    var ui by mutableStateOf(CheckInUi())
        private set

    init {
        viewModelScope.launch { ui = CheckInUi(true, repo.checkInContext(checkInId)) }
    }

    fun submit(input: CheckInInput, onResult: (CheckInOutcome) -> Unit) {
        viewModelScope.launch { onResult(repo.respond(checkInId, input)) }
    }

    fun startTask(id: Long) = viewModelScope.launch { repo.startTask(id) }
}

class WakeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as NoctraApp).repo
    val session: StateFlow<DaySession?> = repo.openSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun confirm(at: Long?, done: () -> Unit) {
        viewModelScope.launch {
            repo.confirmWake(at)
            done()
        }
    }

    fun changeWake(at: Long, done: () -> Unit = {}) {
        viewModelScope.launch {
            repo.updateWakeTime(at)
            done()
        }
    }

    fun later(done: () -> Unit) {
        viewModelScope.launch {
            repo.notWakeYet()
            done()
        }
    }
}

data class DayBar(val label: String, val study: Long, val brk: Long, val distraction: Long)

data class InsightsUi(
    val loaded: Boolean = false,
    val days: List<DayBar> = emptyList(),
    val subjectStudy: List<Pair<String, Long>> = emptyList(),
    val subjectEstimated: List<Pair<String, Long>> = emptyList(),
    val planned: Int = 0,
    val completed: Int = 0,
    val plannedMin: Long = 0,
    val completedMin: Long = 0,
    val breakCount: Int = 0,
    val breakTotal: Long = 0,
    val distractionTotal: Long = 0,
    val answered: Int = 0,
    val unanswered: Int = 0,
    val missedWakes: Int = 0,
    val history: List<DailyReport> = emptyList(),
    val hasData: Boolean = false
)

class InsightsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as NoctraApp).repo

    val ui: StateFlow<InsightsUi> = combine(
        repo.allLogs(), repo.tasks(), repo.allCheckIns(), repo.allSessions(), repo.reports()
    ) { logs, tasks, checkIns, sessions, reports ->
        compute(logs, tasks, checkIns, sessions, reports)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InsightsUi())

    /** Study entered by hand for days that were not tracked live, newest first. */
    val manualStudy: StateFlow<List<ActivityLog>> = repo.manualStudy()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customSubjects: StateFlow<List<String>> = repo.settings.flow
        .map { it.customSubjects }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun logStudy(date: LocalDate, minutes: Int, subject: String, note: String) {
        viewModelScope.launch { repo.logStudy(date, minutes, subject, note) }
    }

    fun deleteManualStudy(id: Long) {
        viewModelScope.launch { repo.deleteManualStudy(id) }
    }

    private fun compute(
        logs: List<ActivityLog>,
        tasks: List<TaskEntity>,
        checkIns: List<CheckIn>,
        sessions: List<DaySession>,
        reports: List<DailyReport>
    ): InsightsUi {
        val now = System.currentTimeMillis()
        val today = LocalDate.now()
        val from = today.minusDays(6)
        val weekLogs = logs.filter { TimeUtil.dateOf(it.startAt) in from..today }
        val days = (0..6).map { i ->
            val d = from.plusDays(i.toLong())
            val dl = weekLogs.filter { TimeUtil.dateOf(it.startAt) == d }
            fun sum(k: ActivityKind) = dl.filter { it.kind == k }.sumOf { Stats.minutes(it, now) }
            DayBar(d.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }, sum(ActivityKind.STUDY), sum(ActivityKind.BREAK), sum(ActivityKind.DISTRACTION))
        }
        val taskById = tasks.associateBy { it.id }
        val subjectStudy = weekLogs.filter { it.kind == ActivityKind.STUDY && (it.taskId != null || it.subject.isNotBlank()) }
            .groupBy { log -> if (log.taskId != null) taskById[log.taskId]?.subjectLabel ?: "Deleted task" else log.subject }
            .map { (k, v) -> k to v.sumOf { Stats.minutes(it, now) } }
            .sortedByDescending { it.second }
        val weekTasks = tasks.filter { it.planDate >= from.toString() && it.planDate <= today.toString() }
        val estimated = weekTasks.filter { it.progress > 0 }
            .groupBy { it.subjectLabel }
            .map { (k, v) -> k to v.sumOf { it.estimateMin.toLong() * it.progress / 100 } }
            .sortedByDescending { it.second }
        val breaks = weekLogs.filter { it.kind == ActivityKind.BREAK }
        val weekCheckIns = checkIns.filter { TimeUtil.dateOf(it.scheduledAt) in from..today }
        return InsightsUi(
            loaded = true,
            days = days,
            subjectStudy = subjectStudy,
            subjectEstimated = estimated,
            planned = weekTasks.size,
            completed = weekTasks.count { it.status == TaskStatus.COMPLETED },
            plannedMin = weekTasks.sumOf { it.estimateMin.toLong() },
            completedMin = weekTasks.filter { it.status == TaskStatus.COMPLETED }.sumOf { it.estimateMin.toLong() },
            breakCount = breaks.size,
            breakTotal = breaks.sumOf { Stats.minutes(it, now) },
            distractionTotal = days.sumOf { it.distraction },
            answered = weekCheckIns.count { it.respondedAt != null },
            unanswered = weekCheckIns.count { it.respondedAt == null },
            missedWakes = sessions.count { it.wakeAt == null && it.wakeCheckSentAt != null && it.endedAt != null },
            history = reports,
            hasData = logs.isNotEmpty() || tasks.isNotEmpty() || reports.isNotEmpty()
        )
    }
}

data class ReportUi(
    val loaded: Boolean = false,
    val session: DaySession? = null,
    val stats: DayStats? = null,
    val logs: List<ActivityLog> = emptyList(),
    val taskTitles: Map<Long, String> = emptyMap(),
    val completed: List<TaskEntity> = emptyList(),
    val unfinished: List<TaskEntity> = emptyList(),
    val answered: Int = 0,
    val unanswered: Int = 0
)

class ReportViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    private val repo = (app as NoctraApp).repo
    val sessionId: Long = handle.get<Long>("sessionId") ?: 0L

    val ui: StateFlow<ReportUi> = combine(
        repo.session(sessionId), repo.logs(sessionId), repo.checkIns(sessionId), repo.tasks(), ticker(30_000)
    ) { session, logs, checkIns, tasks, now ->
        if (session == null) ReportUi(loaded = true) else {
            val wake = session.wakeAt
            val end = session.endedAt ?: now
            ReportUi(
                loaded = true,
                session = session,
                stats = Stats.day(wake, session.endedAt, now, logs),
                logs = logs,
                taskTitles = tasks.associate { it.id to it.title },
                completed = if (wake == null) emptyList() else tasks.filter { t -> t.completedAt?.let { it in wake..end } == true },
                unfinished = tasks.filter { it.status != TaskStatus.COMPLETED && it.planDate <= session.date },
                answered = checkIns.count { it.respondedAt != null },
                unanswered = checkIns.count { it.respondedAt == null }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReportUi())

    fun finalize(done: () -> Unit) {
        viewModelScope.launch {
            repo.finalizeDay(sessionId)
            done()
        }
    }

    fun updateLog(id: Long, minutes: Int) = viewModelScope.launch { repo.updateLogMinutes(id, minutes) }
    fun deleteLog(id: Long) = viewModelScope.launch { repo.deleteLog(id) }
    fun addLog(kind: ActivityKind, minutes: Int) = viewModelScope.launch { repo.addManualLog(sessionId, kind, minutes, null) }
}

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as NoctraApp).repo
    private val nocStore = (app as NoctraApp).nocStore
    val settings: StateFlow<AppSettings> = repo.settings.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())
    val noc: StateFlow<NocConfig> = nocStore.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NocConfig())

    fun setNocKey(key: String) = viewModelScope.launch { nocStore.setKey(key) }
    fun clearNocKey() = viewModelScope.launch { nocStore.clearKey() }
    fun setNocModel(model: String) = viewModelScope.launch { nocStore.setModel(model) }

    fun setWakeTime(h: Int, m: Int) = viewModelScope.launch { repo.setWakeTime(h, m) }
    fun setWakeEnabled(v: Boolean) = viewModelScope.launch { repo.setWakeEnabled(v) }
    fun setInterval(min: Int) = viewModelScope.launch { repo.setInterval(min) }
    fun setTheme(t: String) = viewModelScope.launch { repo.settings.setTheme(t) }
    fun setTone(t: String) = viewModelScope.launch { repo.settings.setTone(t) }
    fun addSubject(name: String) = viewModelScope.launch { repo.settings.addSubject(name.trim()) }
    fun removeSubject(name: String) = viewModelScope.launch { repo.settings.removeSubject(name) }
    fun finishOnboarding(done: () -> Unit) {
        viewModelScope.launch {
            repo.finishOnboarding()
            done()
        }
    }

    fun export(onReady: (String) -> Unit) {
        viewModelScope.launch { onReady(repo.exportCsv()) }
    }

    fun backupTo(uri: Uri, done: (String) -> Unit) {
        viewModelScope.launch {
            val msg = try {
                val json = repo.exportBackup()
                withContext(Dispatchers.IO) {
                    val out = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                        ?: throw IOException("Couldn't open that file.")
                    out.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                }
                "Backup saved."
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                "Backup failed: ${e.message ?: e.javaClass.simpleName}"
            }
            done(msg)
        }
    }

    fun restoreFrom(uri: Uri, done: (String) -> Unit) {
        viewModelScope.launch {
            val msg = try {
                val text = withContext(Dispatchers.IO) {
                    val input = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?: throw IOException("Couldn't open that file.")
                    input.use { it.readBytes().toString(Charsets.UTF_8) }
                }
                repo.restoreBackup(text)
                "Backup restored."
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                e.message ?: "That file isn't a Noctra backup."
            } catch (e: Exception) {
                "Restore failed: ${e.message ?: e.javaClass.simpleName}"
            }
            done(msg)
        }
    }
}

enum class NocRole { USER, NOC, ACTION, ERROR }

data class NocMessage(val id: Long, val role: NocRole, val text: String)

class NocViewModel(app: Application) : AndroidViewModel(app) {
    private val nocApp = app as NoctraApp
    private val repo = nocApp.repo
    private val agent = NocAgent(repo)
    private var nextId = 1L
    private var lastUserText: String? = null

    // Chat lines are saved in order through one queue ("" role = clear everything).
    private val saveQueue = Channel<Pair<String, String>>(Channel.UNLIMITED)

    val config: StateFlow<NocConfig> = nocApp.nocStore.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NocConfig())

    var messages by mutableStateOf<List<NocMessage>>(emptyList())
        private set
    var sending by mutableStateOf(false)
        private set

    /** True while the last send failed before anything was changed, so sending it again is safe. */
    var canRetry by mutableStateOf(false)
        private set
    var loaded by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            for ((role, text) in saveQueue) {
                try {
                    if (role.isEmpty()) repo.clearNocMessages() else repo.saveNocMessage(role, text)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // History is a convenience: a failed save must never break the chat.
                }
            }
        }
        viewModelScope.launch {
            val saved = try {
                repo.nocMessages()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            messages = saved.mapNotNull { m ->
                NocRole.entries.firstOrNull { it.name == m.role }?.takeIf { it != NocRole.ERROR }
                    ?.let { NocMessage(nextId++, it, m.text) }
            }
            agent.restore(
                saved.filter { it.role == NocRole.USER.name || it.role == NocRole.NOC.name }
                    .map { (it.role == NocRole.USER.name) to it.text }
            )
            loaded = true
        }
    }

    private fun add(role: NocRole, text: String) {
        messages = messages + NocMessage(nextId++, role, text)
        if (role != NocRole.ERROR) saveQueue.trySend(role.name to text)
    }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || sending || !loaded) return
        add(NocRole.USER, t)
        run(t)
    }

    fun retry() {
        val t = lastUserText ?: return
        if (sending || !canRetry) return
        if (messages.lastOrNull()?.role == NocRole.ERROR) messages = messages.dropLast(1)
        run(t)
    }

    private fun run(t: String) {
        lastUserText = t
        canRetry = false
        sending = true
        viewModelScope.launch {
            var changes = 0
            try {
                val cfg = nocApp.nocStore.current()
                val tone = repo.settings.current().tone
                val reply = agent.chat(t, cfg, tone) {
                    changes++
                    add(NocRole.ACTION, it)
                }
                add(NocRole.NOC, reply)
            } catch (e: Gemini.ApiException) {
                fail(e.message ?: "Something went wrong.", changes)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                fail("Something went wrong: ${e.message ?: e.javaClass.simpleName}", changes)
            } finally {
                sending = false
            }
        }
    }

    private fun fail(message: String, changes: Int) {
        if (changes > 0) {
            add(NocRole.ERROR, "$message The changes above were saved, so don't resend the same request. Ask what's left.")
        } else {
            add(NocRole.ERROR, message)
            canRetry = true
        }
    }

    fun clear() {
        if (sending) return
        agent.reset()
        messages = emptyList()
        canRetry = false
        lastUserText = null
        saveQueue.trySend("" to "")
    }

    fun saveKey(key: String) = viewModelScope.launch { nocApp.nocStore.setKey(key) }
}

data class TimeUpUi(val loaded: Boolean = false, val task: TaskEntity? = null, val workedMin: Long = 0)

class TimeUpViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    private val repo = (app as NoctraApp).repo
    private val taskId: Long = handle.get<Long>("taskId") ?: 0L
    var ui by mutableStateOf(TimeUpUi())
        private set

    init {
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch {
            val t = repo.getTask(taskId)
            ui = TimeUpUi(true, t, (t?.workedAt(System.currentTimeMillis()) ?: 0L) / 60_000L)
        }
    }

    fun complete(done: () -> Unit) {
        viewModelScope.launch {
            repo.completeTask(taskId)
            done()
        }
    }

    fun extend(minutes: Int, done: () -> Unit) {
        viewModelScope.launch {
            repo.extendTask(taskId, minutes)
            done()
        }
    }

    fun pause(reason: PauseReason, done: () -> Unit) {
        viewModelScope.launch {
            repo.pauseTask(taskId, reason)
            done()
        }
    }
}
