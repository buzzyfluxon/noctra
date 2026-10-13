package com.noctra.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.noctra.app.data.ActivityLog
import com.noctra.app.data.Stats
import com.noctra.app.data.Subject
import com.noctra.app.ui.components.ConfirmDialog
import com.noctra.app.ui.components.NumberField
import com.noctra.app.ui.components.PrimaryButton
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noctra.app.data.TimeUtil
import com.noctra.app.ui.components.EmptyState
import com.noctra.app.ui.components.LightBackdrop
import com.noctra.app.ui.components.MiniBars
import com.noctra.app.ui.components.NcCard
import com.noctra.app.ui.components.NcProgress
import com.noctra.app.ui.components.StatTile
import com.noctra.app.ui.theme.NC
import com.noctra.app.viewmodel.InsightsViewModel

@Composable
fun InsightsScreen(onOpenReport: (Long) -> Unit, vm: InsightsViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val manual by vm.manualStudy.collectAsStateWithLifecycle()
    val customSubjects by vm.customSubjects.collectAsStateWithLifecycle()
    var logOpen by remember { mutableStateOf(false) }
    var deleteEntry by remember { mutableStateOf<ActivityLog?>(null) }
    LightBackdrop {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Insights", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Last 7 days. Study, break and distraction minutes are what you entered. Anything marked estimate is derived from task progress.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PrimaryButton("LOG PAST STUDY", { logOpen = true }, Modifier.fillMaxWidth())
            if (ui.loaded && !ui.hasData && manual.isEmpty()) {
                EmptyState("No data yet", "Add tasks and answer check-ins. Your trends appear here.")
            } else {
                NcCard {
                    Text("Study time per day (user-entered)", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    MiniBars(ui.days.map { it.study }, ui.days.map { it.label })
                }
                NcCard {
                    Text("Breaks per day", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    MiniBars(ui.days.map { it.brk }, ui.days.map { it.label }, color = NC.Blue)
                }
                NcCard {
                    Text("Reported distraction per day", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    MiniBars(ui.days.map { it.distraction }, ui.days.map { it.label }, color = NC.Pink)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        "Task completion",
                        if (ui.planned == 0) "-" else "${ui.completed * 100 / ui.planned}%",
                        Modifier.weight(1f),
                        "${ui.completed} of ${ui.planned} tasks"
                    )
                    StatTile(
                        "Workload done",
                        "${TimeUtil.duration(ui.completedMin)} / ${TimeUtil.duration(ui.plannedMin)}",
                        Modifier.weight(1f),
                        "estimates of completed vs planned tasks"
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        "Breaks",
                        "${ui.breakCount}",
                        Modifier.weight(1f),
                        if (ui.breakCount == 0) "none recorded" else "${TimeUtil.duration(ui.breakTotal)} total, avg ${TimeUtil.duration(ui.breakTotal / ui.breakCount)}"
                    )
                    StatTile("Distracted", TimeUtil.duration(ui.distractionTotal), Modifier.weight(1f), "reported by you")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Check-ins", "${ui.answered} answered", Modifier.weight(1f), "${ui.unanswered} unanswered")
                    StatTile("Missed wake checks", "${ui.missedWakes}", Modifier.weight(1f), "all time")
                }
                NcCard {
                    Text("Subject-wise study (user-entered)", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    SubjectBars(ui.subjectStudy, MaterialTheme.colorScheme.primary)
                }
                NcCard {
                    Text("Subject-wise progress (ESTIMATE)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Task progress percentage multiplied by its estimated duration.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    SubjectBars(ui.subjectEstimated, NC.Amber)
                }
                NcCard {
                    Text("Study you logged by hand", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    if (manual.isEmpty()) {
                        Text(
                            "Missed tracking a day? Use LOG PAST STUDY above. Entries show up here and count in the charts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    manual.take(15).forEach { e ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${TimeUtil.dateOf(e.startAt)} · ${e.subject.ifBlank { "General" }} · ${TimeUtil.duration(Stats.minutes(e, e.startAt))}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                val extra = e.note.removePrefix("manual study").removePrefix(":").trim()
                                if (extra.isNotEmpty()) {
                                    Text(extra, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            IconButton(onClick = { deleteEntry = e }) { Icon(Icons.Filled.Delete, "Delete entry") }
                        }
                    }
                }
                Text("History", style = MaterialTheme.typography.titleMedium)
                if (ui.history.isEmpty()) {
                    Text("Finalized days appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ui.history.forEach { r ->
                    NcCard(onClick = { onOpenReport(r.sessionId) }) {
                        Text(r.date, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Study ${TimeUtil.duration(r.studyMin)} · Breaks ${TimeUtil.duration(r.breakMin)} · Distracted ${TimeUtil.duration(r.distractionMin)} · Tasks ${r.tasksCompleted}/${r.tasksPlanned}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (logOpen) {
        LogStudyDialog(
            subjects = customSubjects,
            onDismiss = { logOpen = false },
            onSave = { d, m, subj, n -> vm.logStudy(d, m, subj, n); logOpen = false }
        )
    }
    deleteEntry?.let { e ->
        ConfirmDialog(
            "Delete this entry?",
            "It will no longer count toward your study time.",
            "DELETE",
            { vm.deleteManualStudy(e.id); deleteEntry = null },
            { deleteEntry = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun LogStudyDialog(
    subjects: List<String>,
    onDismiss: () -> Unit,
    onSave: (LocalDate, Int, String, String) -> Unit
) {
    var date by remember { mutableStateOf(LocalDate.now().minusDays(1)) }
    var pickDate by remember { mutableStateOf(false) }
    var hours by remember { mutableStateOf("") }
    var mins by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf(Subject.PHYSICS.label) }
    var note by remember { mutableStateOf("") }
    val total = (hours.toIntOrNull() ?: 0) * 60 + (mins.toIntOrNull() ?: 0)
    val options = listOf(Subject.PHYSICS.label, Subject.CHEMISTRY.label, Subject.BOTANY.label, Subject.ZOOLOGY.label) + subjects

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log past study") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(onClick = { pickDate = true }, Modifier.fillMaxWidth()) {
                    Text("Date: $date")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(hours, { hours = it.take(2) }, "Hours", Modifier.weight(1f))
                    NumberField(mins, { mins = it.take(2) }, "Minutes", Modifier.weight(1f))
                }
                Text("Subject", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { o ->
                        FilterChip(selected = subject == o, onClick = { subject = o }, label = { Text(o) })
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(120) },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Up to 16 hours per entry. Past days are added to that day's study time without changing its other numbers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(enabled = total > 0, onClick = { onSave(date, total, subject, note) }) { Text("SAVE") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } }
    )

    if (pickDate) {
        val todayUtc = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayUtc
            }
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("CANCEL") } }
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun SubjectBars(items: List<Pair<String, Long>>, color: Color) {
    if (items.isEmpty()) {
        Text("Nothing recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val max = items.maxOf { it.second }.coerceAtLeast(1L)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { (name, min) ->
            Column {
                Row {
                    Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(TimeUtil.duration(min), style = MaterialTheme.typography.labelMedium)
                }
                NcProgress(min.toFloat() / max, fill = color)
            }
        }
    }
}
