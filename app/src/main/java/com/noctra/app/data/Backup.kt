package com.noctra.app.data

import org.json.JSONArray
import org.json.JSONObject

/** Everything stored in the database, as plain lists. */
data class BackupData(
    val tasks: List<TaskEntity>,
    val subtasks: List<SubtaskEntity>,
    val sessions: List<DaySession>,
    val logs: List<ActivityLog>,
    val checkIns: List<CheckIn>,
    val reports: List<DailyReport>,
    val chat: List<NocMessageEntity>
)

/** JSON backup of the whole database. The Gemini API key and app settings are not part of it. */
object Backup {
    const val FORMAT = 1

    private fun JSONObject.long(k: String): Long? = if (has(k) && !isNull(k)) getLong(k) else null
    private fun JSONObject.str(k: String): String = if (has(k) && !isNull(k)) getString(k) else ""
    private inline fun <reified E : Enum<E>> JSONObject.enumOf(k: String): E? =
        enumValues<E>().firstOrNull { it.name == str(k) }

    fun toJson(d: BackupData): String {
        val root = JSONObject()
        root.put("app", "noctra")
        root.put("format", FORMAT)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("tasks", JSONArray().also { a ->
            d.tasks.forEach { t ->
                a.put(
                    JSONObject().put("id", t.id).put("title", t.title).put("subject", t.subject.name)
                        .put("customSubject", t.customSubject).put("topic", t.topic).put("estimateMin", t.estimateMin)
                        .put("priority", t.priority.name).putOpt("deadlineEpochDay", t.deadlineEpochDay)
                        .put("notes", t.notes).put("status", t.status.name).put("progress", t.progress)
                        .put("sortOrder", t.sortOrder).put("planDate", t.planDate).put("postponeCount", t.postponeCount)
                        .putOpt("completedAt", t.completedAt).put("createdAt", t.createdAt)
                        .putOpt("runningSince", t.runningSince).put("workedMs", t.workedMs)
                        .putOpt("pausedAt", t.pausedAt).putOpt("pauseReason", t.pauseReason?.name)
                )
            }
        })
        root.put("subtasks", JSONArray().also { a ->
            d.subtasks.forEach {
                a.put(JSONObject().put("id", it.id).put("taskId", it.taskId).put("title", it.title).put("done", it.done).put("sortOrder", it.sortOrder))
            }
        })
        root.put("sessions", JSONArray().also { a ->
            d.sessions.forEach { s ->
                a.put(
                    JSONObject().put("id", s.id).put("date", s.date).putOpt("wakeCheckSentAt", s.wakeCheckSentAt)
                        .putOpt("wakeRetryAt", s.wakeRetryAt).putOpt("wakeAt", s.wakeAt).putOpt("endedAt", s.endedAt)
                        .putOpt("currentTaskId", s.currentTaskId).put("paused", s.paused)
                        .putOpt("nextCheckInAt", s.nextCheckInAt).putOpt("accountedUntil", s.accountedUntil)
                )
            }
        })
        root.put("logs", JSONArray().also { a ->
            d.logs.forEach { l ->
                a.put(
                    JSONObject().put("id", l.id).put("sessionId", l.sessionId).put("kind", l.kind.name)
                        .putOpt("taskId", l.taskId).put("startAt", l.startAt).putOpt("endAt", l.endAt)
                        .put("verified", l.verified).put("note", l.note).put("subject", l.subject)
                )
            }
        })
        root.put("checkIns", JSONArray().also { a ->
            d.checkIns.forEach { c ->
                a.put(
                    JSONObject().put("id", c.id).put("sessionId", c.sessionId).putOpt("taskId", c.taskId)
                        .put("scheduledAt", c.scheduledAt).putOpt("respondedAt", c.respondedAt)
                        .putOpt("response", c.response?.name).put("studiedMin", c.studiedMin)
                        .put("wastedMin", c.wastedMin).put("note", c.note).put("manual", c.manual)
                )
            }
        })
        root.put("reports", JSONArray().also { a ->
            d.reports.forEach { r ->
                a.put(
                    JSONObject().put("id", r.id).put("sessionId", r.sessionId).put("date", r.date).put("wakeAt", r.wakeAt)
                        .put("endedAt", r.endedAt).put("elapsedMin", r.elapsedMin).put("studyMin", r.studyMin)
                        .put("breakMin", r.breakMin).put("distractionMin", r.distractionMin)
                        .put("unclassifiedMin", r.unclassifiedMin).put("tasksPlanned", r.tasksPlanned)
                        .put("tasksCompleted", r.tasksCompleted).put("checkInsAnswered", r.checkInsAnswered)
                        .put("checkInsUnanswered", r.checkInsUnanswered).put("generatedAt", r.generatedAt)
                )
            }
        })
        root.put("chat", JSONArray().also { a ->
            d.chat.forEach { a.put(JSONObject().put("id", it.id).put("role", it.role).put("text", it.text).put("createdAt", it.createdAt)) }
        })
        return root.toString()
    }

    private fun <T> JSONObject.list(key: String, map: (JSONObject) -> T): List<T> {
        val a = optJSONArray(key) ?: return emptyList()
        return (0 until a.length()).map { map(a.getJSONObject(it)) }
    }

    /** Throws IllegalArgumentException when the text is not a Noctra backup. */
    fun parse(text: String): BackupData {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("That file isn't a Noctra backup.")
        }
        if (root.optString("app") != "noctra") throw IllegalArgumentException("That file isn't a Noctra backup.")
        if (root.optInt("format", 0) != FORMAT) throw IllegalArgumentException("This backup was made by a different version of the app.")
        try {
            return BackupData(
                tasks = root.list("tasks") { o ->
                    TaskEntity(
                        id = o.getLong("id"), title = o.str("title"),
                        subject = o.enumOf<Subject>("subject") ?: Subject.CUSTOM,
                        customSubject = o.str("customSubject").ifBlank { if (o.str("subject") == "BIOLOGY") "Biology" else "" },
                        topic = o.str("topic"),
                        estimateMin = o.getInt("estimateMin"),
                        priority = o.enumOf<Priority>("priority") ?: Priority.MEDIUM,
                        deadlineEpochDay = o.long("deadlineEpochDay"), notes = o.str("notes"),
                        status = o.enumOf<TaskStatus>("status") ?: TaskStatus.PENDING,
                        progress = o.getInt("progress"), sortOrder = o.getInt("sortOrder"),
                        planDate = o.str("planDate"), postponeCount = o.getInt("postponeCount"),
                        completedAt = o.long("completedAt"), createdAt = o.getLong("createdAt"),
                        // A timer that was running when the backup was made must not resume by itself.
                        runningSince = null, workedMs = o.getLong("workedMs"),
                        pausedAt = o.long("pausedAt"), pauseReason = o.enumOf<PauseReason>("pauseReason")
                    )
                },
                subtasks = root.list("subtasks") { o ->
                    SubtaskEntity(o.getLong("id"), o.getLong("taskId"), o.str("title"), o.getBoolean("done"), o.getInt("sortOrder"))
                },
                sessions = root.list("sessions") { o ->
                    DaySession(
                        id = o.getLong("id"), date = o.str("date"), wakeCheckSentAt = o.long("wakeCheckSentAt"),
                        wakeRetryAt = o.long("wakeRetryAt"), wakeAt = o.long("wakeAt"), endedAt = o.long("endedAt"),
                        currentTaskId = o.long("currentTaskId"), paused = o.getBoolean("paused"),
                        nextCheckInAt = o.long("nextCheckInAt"), accountedUntil = o.long("accountedUntil")
                    )
                },
                logs = root.list("logs") { o ->
                    ActivityLog(
                        id = o.getLong("id"), sessionId = o.getLong("sessionId"),
                        kind = o.enumOf<ActivityKind>("kind") ?: ActivityKind.STUDY,
                        taskId = o.long("taskId"), startAt = o.getLong("startAt"), endAt = o.long("endAt"),
                        verified = o.getBoolean("verified"), note = o.str("note"), subject = o.str("subject")
                    )
                },
                checkIns = root.list("checkIns") { o ->
                    CheckIn(
                        id = o.getLong("id"), sessionId = o.getLong("sessionId"), taskId = o.long("taskId"),
                        scheduledAt = o.getLong("scheduledAt"), respondedAt = o.long("respondedAt"),
                        response = o.enumOf<CheckInResponse>("response"), studiedMin = o.getInt("studiedMin"),
                        wastedMin = o.getInt("wastedMin"), note = o.str("note"), manual = o.getBoolean("manual")
                    )
                },
                reports = root.list("reports") { o ->
                    DailyReport(
                        id = o.getLong("id"), sessionId = o.getLong("sessionId"), date = o.str("date"),
                        wakeAt = o.getLong("wakeAt"), endedAt = o.getLong("endedAt"), elapsedMin = o.getLong("elapsedMin"),
                        studyMin = o.getLong("studyMin"), breakMin = o.getLong("breakMin"),
                        distractionMin = o.getLong("distractionMin"), unclassifiedMin = o.getLong("unclassifiedMin"),
                        tasksPlanned = o.getInt("tasksPlanned"), tasksCompleted = o.getInt("tasksCompleted"),
                        checkInsAnswered = o.getInt("checkInsAnswered"), checkInsUnanswered = o.getInt("checkInsUnanswered"),
                        generatedAt = o.getLong("generatedAt")
                    )
                },
                chat = root.list("chat") { o ->
                    NocMessageEntity(o.getLong("id"), o.str("role"), o.str("text"), o.getLong("createdAt"))
                }
            )
        } catch (e: org.json.JSONException) {
            throw IllegalArgumentException("The backup file is damaged or incomplete.")
        }
    }
}
