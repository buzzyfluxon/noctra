package com.noctra.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// A separate DataStore file so the API key can be excluded from Android backups (see res/xml/backup_rules.xml).
private val Context.nocDs by preferencesDataStore("noc_ai")

data class NocConfig(
    val apiKey: String = "",
    val model: String = Gemini.DEFAULT_MODEL
) {
    val ready: Boolean get() = apiKey.isNotBlank()
}

class NocStore(private val context: Context) {
    private object K {
        val key = stringPreferencesKey("gemini_api_key")
        val model = stringPreferencesKey("gemini_model")
    }

    val flow: Flow<NocConfig> = context.nocDs.data.map { p ->
        NocConfig(
            apiKey = p[K.key] ?: "",
            model = (p[K.model] ?: "").ifBlank { Gemini.DEFAULT_MODEL }
        )
    }

    suspend fun current(): NocConfig = flow.first()

    suspend fun setKey(key: String) {
        context.nocDs.edit { it[K.key] = key.trim() }
    }

    suspend fun clearKey() {
        context.nocDs.edit { it.remove(K.key) }
    }

    suspend fun setModel(model: String) {
        context.nocDs.edit { it[K.model] = model.trim() }
    }
}

/** Minimal Gemini REST client (generateContent with function calling). */
object Gemini {
    // An alias that follows the current Flash model, so a retired model name does not break the chat.
    const val DEFAULT_MODEL = "gemini-flash-latest"

    class ApiException(message: String) : Exception(message)

    suspend fun generate(
        apiKey: String,
        model: String,
        system: String,
        contents: List<JSONObject>,
        tools: JSONArray
    ): JSONObject = withContext(Dispatchers.IO) {
        val m = model.trim().removePrefix("models/").ifBlank { DEFAULT_MODEL }
        if (!Regex("[A-Za-z0-9._-]+").matches(m)) throw ApiException("The model name looks invalid. Check it in Settings.")
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", JSONArray(contents))
            .put("tools", tools)
        val conn = URL("https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent")
            .openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-goog-api-key", apiKey)
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw ApiException(friendlyError(code, text, m))
            val json = JSONObject(text)
            val candidate = json.optJSONArray("candidates")?.optJSONObject(0)
            val content = candidate?.optJSONObject("content")
            if (content == null || content.optJSONArray("parts") == null) {
                val reason = candidate?.optString("finishReason").orEmpty()
                    .ifBlank { json.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty() }
                throw ApiException(
                    if (reason.isBlank()) "Gemini sent back an empty answer. Try again."
                    else "Gemini did not answer ($reason). Try rephrasing."
                )
            }
            if (!content.has("role")) content.put("role", "model")
            content
        } catch (e: IOException) {
            throw ApiException("Couldn't reach Gemini. Check your internet connection.")
        } finally {
            conn.disconnect()
        }
    }

    private fun friendlyError(code: Int, body: String, model: String): String {
        val message = try {
            JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
        } catch (_: Exception) {
            ""
        }
        return when {
            code == 400 && message.contains("API key", ignoreCase = true) ->
                "Gemini rejected your API key. Re-check it in Settings."
            code == 401 || code == 403 ->
                "Gemini refused this key (${message.ifBlank { "HTTP $code" }}). Check the key in Settings."
            code == 404 -> "Gemini doesn't know the model \"$model\". Change the model name in Settings."
            code == 429 -> "Gemini rate limit or free quota reached. Wait a bit and try again."
            code >= 500 -> "Gemini is having trouble right now. Try again in a moment."
            else -> "Gemini error $code: ${message.ifBlank { "unknown" }}"
        }
    }
}

private fun obj(vararg pairs: Pair<String, Any>): JSONObject = JSONObject().apply { pairs.forEach { (k, v) -> put(k, v) } }

private fun prop(type: String, description: String): JSONObject = obj("type" to type, "description" to description)

private fun fn(name: String, description: String, props: JSONObject, required: List<String> = emptyList()): JSONObject =
    obj(
        "name" to name,
        "description" to description,
        "parameters" to obj("type" to "OBJECT", "properties" to props, "required" to JSONArray(required))
    )

private val NOC_TOOLS: JSONArray = JSONArray().put(
    obj(
        "functionDeclarations" to JSONArray()
            .put(
                fn(
                    "add_task",
                    "Add a task to the user's plan in the app.",
                    obj(
                        "title" to prop("STRING", "Short task title."),
                        "subject" to prop("STRING", "Physics, Chemistry, Biology, or any other subject name."),
                        "topic" to prop("STRING", "Chapter or topic, optional."),
                        "estimate_minutes" to prop("INTEGER", "Planned minutes. Default 60."),
                        "priority" to prop("STRING", "LOW, MEDIUM or HIGH."),
                        "plan_date" to prop("STRING", "today, tomorrow, or a date as YYYY-MM-DD. Default today."),
                        "deadline" to prop("STRING", "Deadline as YYYY-MM-DD, optional."),
                        "notes" to prop("STRING", "Extra notes, optional."),
                        "subtasks" to obj(
                            "type" to "ARRAY",
                            "description" to "Optional checklist items.",
                            "items" to obj("type" to "STRING")
                        )
                    ),
                    listOf("title")
                )
            )
            .put(
                fn(
                    "list_tasks",
                    "List the user's tasks with their ids. Call this before changing an existing task.",
                    obj("include_completed" to prop("BOOLEAN", "Also include finished tasks. Default false."))
                )
            )
            .put(fn("complete_task", "Mark a task as completed.", obj("task_id" to prop("INTEGER", "Task id from list_tasks.")), listOf("task_id")))
            .put(fn("postpone_task", "Move a task to tomorrow.", obj("task_id" to prop("INTEGER", "Task id from list_tasks.")), listOf("task_id")))
            .put(fn("start_task", "Start the timer on a task.", obj("task_id" to prop("INTEGER", "Task id from list_tasks.")), listOf("task_id")))
            .put(
                fn(
                    "log_distraction",
                    "Record time the user wasted or got distracted. Use when they admit they messed up and give an amount of time.",
                    obj(
                        "minutes" to prop("INTEGER", "Minutes lost."),
                        "note" to prop("STRING", "What pulled them away, optional.")
                    ),
                    listOf("minutes")
                )
            )
    )
)

class NocAgent(private val repo: NoctraRepository) {
    private val history = mutableListOf<JSONObject>()

    fun reset() = history.clear()

    /**
     * Sends one user message, runs any tool calls Gemini asks for, and returns Noc's final text.
     * [onAction] is called with a short line for each change made to the app.
     */
    suspend fun chat(userText: String, cfg: NocConfig, tone: String, onAction: (String) -> Unit): String {
        trimHistory()
        val mark = history.size
        history.add(
            JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", userText)))
        )
        try {
            val system = systemPrompt(tone)
            repeat(MAX_STEPS) {
                val content = Gemini.generate(cfg.apiKey, cfg.model, system, history, NOC_TOOLS)
                history.add(content)
                val parts = content.optJSONArray("parts") ?: JSONArray()
                val calls = (0 until parts.length()).mapNotNull { parts.optJSONObject(it)?.optJSONObject("functionCall") }
                if (calls.isEmpty()) {
                    val text = (0 until parts.length())
                        .mapNotNull { parts.optJSONObject(it) }
                        .filter { !it.optBoolean("thought", false) }
                        .joinToString("") { it.optString("text", "") }
                        .trim()
                    return text.ifBlank { "..." }
                }
                val replies = JSONArray()
                for (call in calls) {
                    val name = call.optString("name")
                    val result = runTool(name, call.optJSONObject("args") ?: JSONObject(), onAction)
                    replies.put(
                        JSONObject().put(
                            "functionResponse",
                            JSONObject().put("name", name).put("response", result)
                        )
                    )
                }
                history.add(JSONObject().put("role", "user").put("parts", replies))
            }
            return "I got tangled up doing that. Say it again, shorter."
        } catch (e: Throwable) {
            // Never leave a half-finished turn behind: a dangling tool call makes every later request fail.
            if (history.size > mark) history.subList(mark, history.size).clear()
            throw e
        }
    }

    /** Drops the oldest turns, but only at a plain user message so tool call pairs stay together. */
    private fun trimHistory() {
        if (history.size <= MAX_HISTORY) return
        val from = history.size - MAX_HISTORY / 2
        val cut = (from until history.size).firstOrNull { i ->
            val c = history[i]
            c.optString("role") == "user" && c.optJSONArray("parts")?.optJSONObject(0)?.has("text") == true
        } ?: return
        history.subList(0, cut).clear()
    }

    // ---------------------------------------------------------------- tools

    private fun ok(vararg pairs: Pair<String, Any>) = obj("ok" to true, *pairs)

    private fun err(message: String) = obj("ok" to false, "error" to message)

    private suspend fun runTool(name: String, args: JSONObject, onAction: (String) -> Unit): JSONObject = try {
        when (name) {
            "add_task" -> addTask(args, onAction)
            "list_tasks" -> listTasks(args.optBoolean("include_completed", false))
            "complete_task" -> withTask(args) { t ->
                repo.completeTask(t.id)
                onAction("Completed: ${t.title}")
                ok("task_id" to t.id)
            }
            "postpone_task" -> withTask(args) { t ->
                repo.postpone(t.id)
                onAction("Moved to tomorrow: ${t.title}")
                ok("task_id" to t.id)
            }
            "start_task" -> withTask(args) { t ->
                repo.startTask(t.id)
                onAction("Started: ${t.title}")
                ok("task_id" to t.id)
            }
            "log_distraction" -> logDistraction(args, onAction)
            else -> err("Unknown tool: $name")
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        err(e.message ?: "Tool failed")
    }

    private suspend fun withTask(args: JSONObject, block: suspend (TaskEntity) -> JSONObject): JSONObject {
        val id = args.optLong("task_id", 0)
        val t = if (id > 0) repo.getTask(id) else null
        return if (t == null) err("No task with id $id. Call list_tasks to see valid ids.") else block(t)
    }

    private suspend fun addTask(args: JSONObject, onAction: (String) -> Unit): JSONObject {
        val title = args.optString("title").trim()
        if (title.isBlank()) return err("title is required")
        val subjectRaw = args.optString("subject").trim()
        val builtIn = Subject.entries.firstOrNull { it != Subject.CUSTOM && it.label.equals(subjectRaw, ignoreCase = true) }
        val subject = builtIn ?: Subject.CUSTOM
        val custom = if (builtIn != null) "" else subjectRaw.ifBlank { "General" }
        val priority = Priority.entries.firstOrNull { it.name.equals(args.optString("priority").trim(), ignoreCase = true) }
            ?: Priority.MEDIUM
        val planRaw = args.optString("plan_date").trim()
        val planDate = when {
            planRaw.isBlank() || planRaw.equals("today", true) -> TimeUtil.todayStr()
            planRaw.equals("tomorrow", true) -> TimeUtil.tomorrowStr()
            else -> runCatching { LocalDate.parse(planRaw).toString() }.getOrNull()
                ?: return err("plan_date must be today, tomorrow or YYYY-MM-DD")
        }
        val deadlineRaw = args.optString("deadline").trim()
        val deadline = if (deadlineRaw.isBlank()) null else
            runCatching { LocalDate.parse(deadlineRaw).toEpochDay() }.getOrNull()
                ?: return err("deadline must be YYYY-MM-DD")
        val subs = args.optJSONArray("subtasks")?.let { arr ->
            (0 until arr.length()).map { arr.optString(it).trim() }.filter { it.isNotBlank() }
        }.orEmpty().mapIndexed { i, s -> SubtaskEntity(title = s, sortOrder = i) }
        val id = repo.saveTask(
            TaskEntity(
                title = title,
                subject = subject,
                customSubject = custom,
                topic = args.optString("topic").trim(),
                estimateMin = args.optInt("estimate_minutes", 60).coerceIn(5, 600),
                priority = priority,
                deadlineEpochDay = deadline,
                notes = args.optString("notes").trim(),
                planDate = planDate
            ),
            subs
        )
        onAction("Added task: $title")
        return ok("task_id" to id, "plan_date" to planDate)
    }

    private suspend fun listTasks(includeDone: Boolean): JSONObject {
        val now = System.currentTimeMillis()
        val arr = JSONArray()
        repo.tasksSnapshot()
            .filter { includeDone || it.status != TaskStatus.COMPLETED }
            .take(60)
            .forEach { t ->
                arr.put(
                    obj(
                        "id" to t.id,
                        "title" to t.title,
                        "subject" to t.subjectLabel,
                        "estimate_minutes" to t.estimateMin,
                        "priority" to t.priority.name,
                        "status" to t.status.name,
                        "plan_date" to t.planDate,
                        "progress_percent" to t.progress,
                        "worked_minutes" to t.workedAt(now) / 60_000L
                    )
                )
            }
        return ok("tasks" to arr)
    }

    private suspend fun logDistraction(args: JSONObject, onAction: (String) -> Unit): JSONObject {
        val minutes = args.optInt("minutes", 0)
        if (minutes <= 0) return err("minutes must be greater than 0")
        val s = repo.openSessionOnce()
        if (s?.wakeAt == null) return err("The user hasn't confirmed they're awake today, so time can't be logged yet.")
        repo.respond(0L, CheckInInput(CheckInResponse.DISTRACTED, wastedMin = minutes, note = args.optString("note").trim()))
        onAction("Logged $minutes min as distracted")
        return ok("minutes" to minutes)
    }

    // ---------------------------------------------------------------- prompt

    private suspend fun systemPrompt(tone: String): String {
        val persona = when (tone) {
            "BRUTAL" ->
                "Tone: brutally honest. Short, hard sentences. Call out excuses and wasted time plainly and push the user to act now. " +
                    "Attack the behaviour, never the person. No profanity, no insults about who they are."
            "CALM" ->
                "Tone: calm, warm and steady. Acknowledge slips without drama, then make the next step small and doable."
            else ->
                "Tone: direct and practical. No fluff, no lectures. Say what happened and what to do next."
        }
        val header = """
            You are Noc, the AI coach inside Noctra, a study planner and accountability app. The user is a student.
            $persona

            Rules:
            - Keep replies short: this is a phone chat. Plain text only. No markdown, no asterisks, no headings.
            - Reply in the language the user writes in.
            - You can change the app with tools: add_task, list_tasks, complete_task, postpone_task, start_task, log_distraction.
              Use them when the user asks for something that matches. Call list_tasks first if you need a task id you don't have.
              Only say something was done after the tool result says ok.
            - When the user says they messed up (wasted time, skipped work, got distracted): react in your tone in one or two
              sentences, log the lost minutes with log_distraction if they tell you how long, then offer a realistic recovery
              plan for the rest of the day. Offer to postpone what no longer fits. Do not pile on.
            - Never push the user to skip sleep or meals. If they seem genuinely distressed, drop the act and be kind.
        """.trimIndent()
        return header + "\n\nCurrent state of the app:\n" + stateSummary()
    }

    private suspend fun stateSummary(): String {
        val now = System.currentTimeMillis()
        val today = TimeUtil.todayStr()
        val tasks = repo.tasksSnapshot()
        val s = repo.openSessionOnce()
        val wake = s?.wakeAt
        val sb = StringBuilder()
        sb.appendLine("Now: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEEE d MMM yyyy, h:mm a", Locale.ENGLISH)))
        if (s != null && wake != null) {
            val st = Stats.day(wake, s.endedAt, now, repo.sessionLogs(s.id))
            sb.appendLine(
                "Awake since ${TimeUtil.clock(wake)}. As the user reported it today: study ${TimeUtil.duration(st.studyMin)}, " +
                    "breaks ${TimeUtil.duration(st.breakMin)}, distracted ${TimeUtil.duration(st.distractionMin)}, " +
                    "unaccounted ${TimeUtil.duration(st.unclassifiedMin)}."
            )
        } else {
            sb.appendLine("The user has not confirmed they are awake today, so time logging is unavailable.")
        }
        fun line(t: TaskEntity) =
            "#${t.id} \"${t.title}\" [${t.subjectLabel}, ${t.estimateMin}m planned, ${t.workedAt(now) / 60_000L}m worked, " +
                "${t.priority.name}, ${t.status.label}, ${t.progress}%, plan ${t.planDate}${if (t.isRunning) ", RUNNING" else ""}]"
        val open = tasks.filter { it.status != TaskStatus.COMPLETED }
        val dueNow = open.filter { it.planDate <= today }
        val later = open.filter { it.planDate > today }
        sb.appendLine("Tasks for today (${dueNow.size}):")
        dueNow.take(30).forEach { sb.appendLine("- " + line(it)) }
        if (later.isNotEmpty()) {
            sb.appendLine("Planned for later (${later.size}):")
            later.take(15).forEach { sb.appendLine("- " + line(it)) }
        }
        val doneToday = tasks.count { t -> t.completedAt?.let { TimeUtil.dateOf(it).toString() == today } == true }
        sb.appendLine("Completed today: $doneToday")
        return sb.toString().trim()
    }

    private companion object {
        const val MAX_STEPS = 6
        const val MAX_HISTORY = 60
    }
}
