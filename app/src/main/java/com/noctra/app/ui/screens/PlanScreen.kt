package com.noctra.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noctra.app.data.Priority
import com.noctra.app.data.TaskEntity
import com.noctra.app.data.TaskStatus
import com.noctra.app.data.TimeUtil
import com.noctra.app.ui.components.ConfirmDialog
import com.noctra.app.ui.components.LightBackdrop
import com.noctra.app.ui.components.NcCard
import com.noctra.app.ui.components.NcProgress
import com.noctra.app.ui.components.PauseReasonDialog
import com.noctra.app.ui.components.Pill
import com.noctra.app.ui.components.PrimaryButton
import com.noctra.app.ui.components.ProgressDialog
import com.noctra.app.ui.components.SecondaryButton
import com.noctra.app.ui.components.StatTile
import com.noctra.app.ui.components.softBlue
import com.noctra.app.ui.components.softViolet
import com.noctra.app.ui.components.SubjectChip
import com.noctra.app.ui.components.Wordmark
import com.noctra.app.ui.components.subjectColors
import com.noctra.app.ui.components.subjectTint
import com.noctra.app.ui.theme.NC
import com.noctra.app.viewmodel.HomeViewModel
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

fun priorityColors(p: Priority): Pair<Color, Color> = when (p) {
    Priority.HIGH -> Color(0xFFFFD1E3) to Color(0xFFB4235A)
    Priority.MEDIUM -> Color(0xFFCFF3E6) to Color(0xFF0B6B58)
    Priority.LOW -> Color(0xFFE5E3F2) to Color(0xFF5B5780)
}

@Composable
fun PlanScreen(
    onWake: () -> Unit,
    onAddTask: () -> Unit,
    onEditTask: (Long) -> Unit,
    onCheckIn: () -> Unit,
    onEndDay: (Long) -> Unit,
    onProfile: () -> Unit,
    vm: HomeViewModel = viewModel()
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var progressFor by remember { mutableStateOf<TaskEntity?>(null) }
    var completeFor by remember { mutableStateOf<TaskEntity?>(null) }
    var pauseFor by remember { mutableStateOf<TaskEntity?>(null) }
    val running = ui.current?.isRunning == true
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(running) {
        while (running) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val session = ui.session
    val wakeAt = session?.wakeAt

    LightBackdrop {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Wordmark()
                    Text(
                        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.getDefault())),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(onClick = onProfile, shape = CircleShape, color = MaterialTheme.colorScheme.surface, modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Text("My plan", style = MaterialTheme.typography.headlineLarge)

            if (wakeAt == null) {
                NcCard {
                    Text("You haven't confirmed you're awake", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Accountability checks start only after you confirm.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("YES, I'M UP", onWake, Modifier.fillMaxWidth())
                }
            } else {
                Text(
                    "Awake since ${TimeUtil.clock(wakeAt)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val current = ui.current
            if (current != null) {
                CurrentTaskCard(
                    current,
                    ui.tally.completed,
                    ui.tally.planned,
                    now = now,
                    onStart = { vm.startTask(current.id) },
                    onPause = { pauseFor = current },
                    onClick = { onEditTask(current.id) },
                    onProgress = { progressFor = current },
                    onDone = { completeFor = current }
                )
            } else if (ui.loaded) {
                NcCard {
                    Text("No active task", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (ui.upcoming.isEmpty()) "Add a task to plan your day." else "Start one of the tasks below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (ui.upcoming.isEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        PrimaryButton("ADD A TASK", onAddTask)
                    }
                }
            }

            if (ui.upcoming.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ui.upcoming.forEach { t ->
                        MiniTaskCard(t, Modifier.weight(1f), onClick = { onEditTask(t.id) }, onStart = { vm.startTask(t.id) })
                    }
                    if (ui.upcoming.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            when {
                wakeAt == null -> Unit
                ui.breakSince != null -> NcCard(container = softBlue()) {
                    Text("On a break since ${TimeUtil.clock(ui.breakSince!!)}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Accountability checks are held until you end the break.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("END BREAK", vm::endBreak, Modifier.fillMaxWidth())
                }
                session?.paused == true -> NcCard {
                    Text("Accountability paused", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("RESUME CHECK-INS", vm::resume, Modifier.fillMaxWidth())
                }
                else -> NcCard(container = softViolet(), onClick = onCheckIn) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Shield, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text("Next Accountability Check-in", style = MaterialTheme.typography.bodySmall)
                            Text(
                                session?.nextCheckInAt?.let { TimeUtil.clock(it) } ?: "Scheduling…",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                "Every ${ui.settings.intervalMin} minutes. Stay consistent. Stay on track.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Check in now")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = onCheckIn) { Text("CHECK IN NOW") }
                        TextButton(onClick = vm::startBreak) { Text("TAKE A BREAK") }
                        TextButton(onClick = vm::pause) { Text("PAUSE") }
                    }
                }
            }

            val stats = ui.stats
            if (stats != null) {
                Text("Today, as you reported it", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Study", TimeUtil.duration(stats.studyMin), Modifier.weight(1f))
                    StatTile("Breaks", TimeUtil.duration(stats.breakMin), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Distracted", TimeUtil.duration(stats.distractionMin), Modifier.weight(1f))
                    StatTile("Unclassified", TimeUtil.duration(stats.unclassifiedMin), Modifier.weight(1f))
                }
                Text(
                    "Elapsed since wake: ${TimeUtil.duration(stats.elapsedMin)}. Study, break and distraction come from your own entries. Unclassified is everything not accounted for.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (ui.loggedStudy.isNotEmpty()) {
                NcCard {
                    Text("Study you logged", style = MaterialTheme.typography.titleMedium)
                    if (ui.stats == null && ui.extraStudyToday > 0) {
                        Text(
                            "Today so far: ${TimeUtil.duration(ui.extraStudyToday)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    ui.loggedStudy.take(5).forEach { l ->
                        val d = java.time.Instant.ofEpochMilli(l.startAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                        val day = when (d) {
                            LocalDate.now() -> "Today"
                            LocalDate.now().minusDays(1) -> "Yesterday"
                            else -> d.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
                        }
                        val mins = com.noctra.app.data.Stats.minutes(l, l.endAt ?: l.startAt)
                        Text(
                            "$day: ${l.subject.ifBlank { "General" }}, ${TimeUtil.duration(mins)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton("ADD TASK", onAddTask, Modifier.weight(1f))
                if (session != null && wakeAt != null) {
                    PrimaryButton(
                        "END MY DAY",
                        { onEndDay(session.id) },
                        Modifier.weight(1f),
                        container = NC.Slate,
                        content = Color.White
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    progressFor?.let { t ->
        ProgressDialog(t, { progressFor = null }) { p, e ->
            vm.setProgress(t.id, p, e)
            progressFor = null
        }
    }
    pauseFor?.let { t ->
        PauseReasonDialog(t.title, { reason -> vm.pauseTask(t.id, reason); pauseFor = null }, { pauseFor = null })
    }
    completeFor?.let { t ->
        ConfirmDialog(
            "Mark as completed?",
            "\"${t.title}\" will be recorded as done now.",
            "YES, COMPLETED",
            { vm.completeTask(t.id); completeFor = null },
            { completeFor = null }
        )
    }
}

@Composable
private fun CurrentTaskCard(
    task: TaskEntity,
    done: Int,
    planned: Int,
    now: Long,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onClick: () -> Unit,
    onProgress: () -> Unit,
    onDone: () -> Unit
) {
    Surface(onClick = onClick, shape = RoundedCornerShape(28.dp), color = Color.Transparent) {
        Column(
            Modifier
                .background(Brush.linearGradient(listOf(Color(0xFFA791FF), Color(0xFF6C54F0))))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(task.subjectLabel, Color.White.copy(alpha = 0.25f), Color.White)
                Spacer(Modifier.weight(1f))
                Pill(task.status.label, Color.White.copy(alpha = 0.25f), Color.White)
            }
            Text(task.title, style = MaterialTheme.typography.titleLarge, color = Color.White)
            if (task.topic.isNotBlank()) {
                Text(task.topic, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Schedule, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(18.dp))
                Text(
                    "  ${TimeUtil.duration(task.estimateMin.toLong())} estimated",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                NcProgress(task.progress / 100f, Modifier.weight(1f), track = Color.White.copy(alpha = 0.3f), fill = Color.White)
                Text("  $done / $planned tasks", style = MaterialTheme.typography.labelMedium, color = Color.White)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        task.isRunning -> "Running ${TimeUtil.clockDuration(task.workedAt(now))}"
                        task.pausedAt != null -> "Paused · worked ${TimeUtil.duration(task.workedMs / 60_000L)}"
                        else -> "Timer not started"
                    },
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White
                )
                TextButton(onClick = if (task.isRunning) onPause else onStart) {
                    Text(
                        if (task.isRunning) "PAUSE" else if (task.pausedAt != null) "RESUME" else "START",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onProgress) { Text("UPDATE PROGRESS", color = Color.White, fontWeight = FontWeight.Bold) }
                TextButton(onClick = onDone) { Text("DONE", color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun MiniTaskCard(task: TaskEntity, modifier: Modifier, onClick: () -> Unit, onStart: () -> Unit) {
    val (pbg, pfg) = priorityColors(task.priority)
    NcCard(modifier, container = subjectTint(task.subject), onClick = onClick) {
        SubjectChip(task)
        Spacer(Modifier.height(8.dp))
        Text(task.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.Schedule, null, modifier = Modifier.size(16.dp))
            Text(TimeUtil.duration(task.estimateMin.toLong()), style = MaterialTheme.typography.bodySmall)
            Pill(task.priority.label, pbg, pfg)
        }
        Spacer(Modifier.height(10.dp))
        NcProgress(task.progress / 100f, fill = subjectColors(task.subject).second)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (task.status == TaskStatus.PENDING) "Not started" else task.status.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onStart) { Text(if (task.pausedAt != null) "RESUME" else "START") }
        }
    }
}
