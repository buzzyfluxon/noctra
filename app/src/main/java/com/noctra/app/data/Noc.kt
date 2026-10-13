package com.noctra.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException
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

    /** A failure worth trying again (dropped connection, overloaded server). */
    private class TransientException(message: String) : Exception(message)

    private const val MAX_ATTEMPTS = 4
    private val RETRY_DELAYS_MS = longArrayOf(600L, 1_500L, 3_000L)

    /** Thinking makes Flash models slow and the chat feels stuck, so it is turned off. Dropped automatically if the model rejects it. */
    @Volatile
    private var thinkingOff = true

    suspend fun generate(
        apiKey: String,
        model: String,
        system: String,
        contents: List<JSONObject>,
        tools: JSONArray
    ): JSONObject {
        val m = model.trim().removePrefix("models/").ifBlank { DEFAULT_MODEL }
        if (!Regex("[A-Za-z0-9._-]+").matches(m)) throw ApiException("The model name looks invalid. Check it in Settings.")
        var last = "Couldn't reach Gemini. Check your internet connection."
        for (attempt in 0 until MAX_ATTEMPTS) {
            if (attempt > 0) delay(RETRY_DELAYS_MS[attempt - 1])
            val body = buildBody(system, contents, tools)
            try {
                return post(apiKey, m, body)
            } catch (e: TransientException) {
                last = e.message ?: last
            }
        }
        throw ApiException(last)
    }

    private fun buildBody(system: String, contents: List<JSONObject>, tools: JSONArray): ByteArray {
        val o = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", JSONArray(contents))
            .put("tools", tools)
        if (thinkingOff) {
            o.put("generationConfig", JSONObject().put("thinkingConfig", JSONObject().put("thinkingBudget", 0)))
        }
        return o.toString().toByteArray(Charsets.UTF_8)
    }

    /**
     * Streams the answer (server-sent events). Bytes keep arriving while Gemini works, so mobile networks and
     * proxies don't cut a long silent connection ("connection abort"). Nothing is returned until the stream is complete.
     */
    private suspend fun post(apiKey: String, m: String, body: ByteArray): JSONObject = withContext(Dispatchers.IO) {
        val conn = URL("https://generativelanguage.googleapis.com/v1beta/models/$m:streamGenerateContent?alt=sse")
            .openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 20_000
            conn.readTimeout = 90_000
            conn.useCaches = false
            conn.doOutput = true
            conn.setFixedLengthStreamingMode(body.size)
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "text/event-stream")
            // No gzip: compressed streams are buffered and arrive in one late lump.
            conn.setRequestProperty("Accept-Encoding", "identity")
            conn.setRequestProperty("x-goog-api-key", apiKey)
            conn.setRequestProperty("Connection", "close")
            conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val text = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                if (code == 400 && thinkingOff && text.contains("thinking", ignoreCase = true)) {
                    thinkingOff = false
                    throw TransientException("Retrying without the thinking setting.")
                }
                val msg = friendlyError(code, text, m)
                if (code == 429 || code == 500 || code == 502 || code == 503 || code == 504) throw TransientException(msg)
                throw ApiException(msg)
            }
            val parts = JSONArray()
            var reason = ""
            conn.inputStream.bufferedReader().use { r ->
                while (true) {
                    val line = r.readLine() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.substring(5).trim()
                    if (payload.isEmpty() || payload == "[DONE]") continue
                    val json = try {
                        JSONObject(payload)
                    } catch (_: JSONException) {
                        continue
                    }
                    val candidate = json.optJSONArray("candidates")?.optJSONObject(0)
                    val ps = candidate?.optJSONObject("content")?.optJSONArray("parts")
                    if (ps != null) for (i in 0 until ps.length()) parts.put(ps.get(i))
                    val fr = candidate?.optString("finishReason").orEmpty()
                    if (fr.isNotBlank()) reason = fr
                    val br = json.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
                    if (br.isNotBlank() && reason.isBlank()) reason = br
                }
            }
            if (parts.length() == 0) {
                if (reason.isBlank() || reason == "STOP") throw TransientException("Gemini sent back an empty answer. Try again.")
                throw ApiException("Gemini did not answer ($reason). Try rephrasing.")
            }
            JSONObject().put("role", "model").put("parts", parts)
        } catch (e: IOException) {
            throw TransientException(describeIo(e))
        } finally {
            conn.disconnect()
        }
    }

    private fun describeIo(e: IOException): String = when (e) {
        is UnknownHostException, is ConnectException, is NoRouteToHostException ->
            "Couldn't reach Gemini. Check your internet connection."
        is SocketTimeoutException -> "Gemini took too long to answer. Try again."
        is SSLException ->
            "Secure connection to Gemini failed (${e.message ?: "TLS error"}). Check the phone's date and time, or turn off any VPN."
        else -> "The connection to Gemini dropped (${e.javaClass.simpleName}${e.message?.let { ": $it" } ?: ""}). Try again."
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
                        "subject" to prop("STRING", "Physics, Chemistry, Botany, Zoology, or any other subject name."),
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
                    "edit_task",
                    "Change fields of an existing task. Only pass the fields that should change.",
                    obj(
                        "task_id" to prop("INTEGER", "Task id from list_tasks."),
                        "title" to prop("STRING", "New title."),
                        "subject" to prop("STRING", "Physics, Chemistry, Botany, Zoology, or any other subject name."),
                        "topic" to prop("STRING", "New chapter or topic."),
                        "estimate_minutes" to prop("INTEGER", "New planned minutes."),
                        "priority" to prop("STRING", "LOW, MEDIUM or HIGH."),
                        "plan_date" to prop("STRING", "today, tomorrow, or a date as YYYY-MM-DD."),
                        "deadline" to prop("STRING", "Deadline as YYYY-MM-DD, or none to remove it."),
                        "notes" to prop("STRING", "New notes (replaces the old notes)."),
                        "progress_percent" to prop("INTEGER", "Progress from 0 to 100."),
                        "add_subtasks" to obj(
                            "type" to "ARRAY",
                            "description" to "Checklist items to add.",
                            "items" to obj("type" to "STRING")
                        )
                    ),
                    listOf("task_id")
                )
            )
            .put(
                fn(
                    "delete_task",
                    "Permanently delete a task and its checklist. Only when the user clearly asks to delete or remove it.",
                    obj("task_id" to prop("INTEGER", "Task id from list_tasks.")),
                    listOf("task_id")
                )
            )
            .put(
                fn(
                    "log_study",
                    "Record study time the user already did, today or on a past day (for example 'I studied 3 hours yesterday').",
                    obj(
                        "minutes" to prop("INTEGER", "Minutes studied."),
                        "subject" to prop("STRING", "Physics, Chemistry, Botany, Zoology, or any other subject name. Default General."),
                        "date" to prop("STRING", "today, yesterday, or YYYY-MM-DD. Default today."),
                        "note" to prop("STRING", "Optional note.")
                    ),
                    listOf("minutes")
                )
            )
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
     * Rebuilds the model's memory from saved chat lines (user text and Noc replies only).
     * Consecutive lines from the same side are merged so the turns alternate.
     */
    fun restore(lines: List<Pair<Boolean, String>>) {
        history.clear()
        var lastUser: Boolean? = null
        for ((isUser, text) in lines) {
            if (text.isBlank()) continue
            if (history.isEmpty() && !isUser) continue
            if (lastUser == isUser) {
                val parts = history.last().getJSONArray("parts")
                val prev = parts.getJSONObject(0)
                prev.put("text", prev.optString("text") + "\n" + text)
            } else {
                history.add(
                    JSONObject().put("role", if (isUser) "user" else "model")
                        .put("parts", JSONArray().put(JSONObject().put("text", text)))
                )
                lastUser = isUser
            }
        }
        // A trailing user turn is a message that never got an answer; the next message starts fresh.
        if (lastUser == true && history.isNotEmpty()) history.removeAt(history.size - 1)
        trimHistory()
    }

    /**
     * Sends one user message, runs any tool calls Gemini asks for, and returns Noc's final text.
     * [onAction] is called with a short line for each change made to the app.
     */
    suspend fun chat(userText: String, cfg: NocConfig, tone: String, onAction: (String) -> Unit): String {
        trimHistory()
        val mark = history.size
        val done = mutableListOf<String>()
        val track: (String) -> Unit = { line ->
            done.add(line)
            onAction(line)
        }
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
                    val result = runTool(name, call.optJSONObject("args") ?: JSONObject(), track)
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
            if (done.isNotEmpty() && e !is CancellationException) {
                // Changes were already made: remember them so the next message doesn't redo them.
                history.add(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", userText))))
                history.add(
                    JSONObject().put("role", "model").put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", "Done in the app: ${done.joinToString("; ")}. (My reply was lost.)"))
                    )
                )
            }
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
            "edit_task" -> editTask(args, onAction)
            "delete_task" -> withTask(args) { t ->
                repo.deleteTask(t.id)
                onAction("Deleted: ${t.title}")
                ok("task_id" to t.id)
            }
            "log_study" -> logStudy(args, onAction)
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

    private fun resolveSubject(raw: String): Pair<Subject, String> {
        val builtIn = Subject.entries.firstOrNull { it != Subject.CUSTOM && it.label.equals(raw, ignoreCase = true) }
        return if (builtIn != null) builtIn to "" else Subject.CUSTOM to raw.ifBlank { "General" }
    }

    /** today / tomorrow / yesterday / YYYY-MM-DD (blank means today). Null when it can't be read. */
    private fun resolvePlanDate(raw: String): String? = when {
        raw.isBlank() || raw.equals("today", true) -> TimeUtil.todayStr()
        raw.equals("tomorrow", true) -> TimeUtil.tomorrowStr()
        raw.equals("yesterday", true) -> LocalDate.now().minusDays(1).toString()
        else -> runCatching { LocalDate.parse(raw).toString() }.getOrNull()
    }

    private suspend fun editTask(args: JSONObject, onAction: (String) -> Unit): JSONObject = withTask(args) { t ->
        var n = t
        if (args.has("title")) {
            val v = args.optString("title").trim()
            if (v.isBlank()) return@withTask err("title can't be empty")
            n = n.copy(title = v)
        }
        if (args.has("subject") && args.optString("subject").isNotBlank()) {
            val (subject, custom) = resolveSubject(args.optString("subject").trim())
            n = n.copy(subject = subject, customSubject = custom)
        }
        if (args.has("topic")) n = n.copy(topic = args.optString("topic").trim())
        if (args.has("notes")) n = n.copy(notes = args.optString("notes").trim())
        if (args.has("estimate_minutes")) n = n.copy(estimateMin = args.optInt("estimate_minutes", n.estimateMin).coerceIn(5, 600))
        if (args.has("priority") && args.optString("priority").isNotBlank()) {
            val p = Priority.entries.firstOrNull { it.name.equals(args.optString("priority").trim(), ignoreCase = true) }
                ?: return@withTask err("priority must be LOW, MEDIUM or HIGH")
            n = n.copy(priority = p)
        }
        if (args.has("plan_date") && args.optString("plan_date").isNotBlank()) {
            val d = resolvePlanDate(args.optString("plan_date").trim())
                ?: return@withTask err("plan_date must be today, tomorrow or YYYY-MM-DD")
            n = n.copy(planDate = d)
        }
        if (args.has("deadline")) {
            val raw = args.optString("deadline").trim()
            n = if (raw.isBlank() || raw.equals("none", true)) n.copy(deadlineEpochDay = null) else {
                val day = runCatching { LocalDate.parse(raw).toEpochDay() }.getOrNull()
                    ?: return@withTask err("deadline must be YYYY-MM-DD or none")
                n.copy(deadlineEpochDay = day)
            }
        }
        if (args.has("progress_percent") && n.status != TaskStatus.COMPLETED) {
            val p = args.optInt("progress_percent", n.progress).coerceIn(0, 100)
            val status = if (p > 0 && n.status != TaskStatus.IN_PROGRESS) TaskStatus.IN_PROGRESS else n.status
            n = n.copy(progress = p, status = status)
        }
        val existing = repo.getSubtasks(t.id)
        val extra = args.optJSONArray("add_subtasks")?.let { arr ->
            (0 until arr.length()).map { arr.optString(it).trim() }.filter { it.isNotBlank() }
        }.orEmpty().mapIndexed { i, title -> SubtaskEntity(title = title, sortOrder = existing.size + i) }
        if (n == t && extra.isEmpty()) return@withTask err("Nothing to change. Pass at least one field to edit.")
        repo.saveTask(n, existing + extra)
        onAction("Edited: ${n.title}")
        ok("task_id" to t.id)
    }

    private suspend fun logStudy(args: JSONObject, onAction: (String) -> Unit): JSONObject {
        val minutes = args.optInt("minutes", 0)
        if (minutes <= 0) return err("minutes must be greater than 0")
        val date = resolvePlanDate(args.optString("date").trim())
            ?: return err("date must be today, yesterday or YYYY-MM-DD")
        val subject = args.optString("subject").trim().ifBlank { "General" }
        val done = repo.logStudy(LocalDate.parse(date), minutes, subject, args.optString("note"))
        if (!done) return err("That date is in the future, so study can't be logged for it.")
        onAction("Logged ${TimeUtil.duration(minutes.toLong())} of $subject study ($date)")
        return ok("minutes" to minutes, "date" to date)
    }

    private suspend fun addTask(args: JSONObject, onAction: (String) -> Unit): JSONObject {
        val title = args.optString("title").trim()
        if (title.isBlank()) return err("title is required")
        val (subject, custom) = resolveSubject(args.optString("subject").trim())
        val priority = Priority.entries.firstOrNull { it.name.equals(args.optString("priority").trim(), ignoreCase = true) }
            ?: Priority.MEDIUM
        val planDate = resolvePlanDate(args.optString("plan_date").trim())
            ?: return err("plan_date must be today, tomorrow or YYYY-MM-DD")
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
            - You can change the app with tools: add_task, list_tasks, edit_task, delete_task, complete_task, postpone_task, start_task,
              log_study, log_distraction.
              Use them when the user asks for something that matches. Call list_tasks first if you need a task id you don't have.
              Only say something was done after the tool result says ok.
            - Delete a task only when the user clearly asks to delete or remove it. If it is unclear which task they mean, ask.
            - When the user says they studied for some time (today or on a past day), record it with log_study.
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
