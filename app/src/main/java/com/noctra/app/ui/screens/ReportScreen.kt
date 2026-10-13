package com.noctra.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noctra.app.data.ActivityKind
import com.noctra.app.data.ActivityLog
import com.noctra.app.data.Stats
import com.noctra.app.data.TimeUtil
import com.noctra.app.ui.components.ConfirmDialog
import com.noctra.app.ui.components.EmptyState
import com.noctra.app.ui.components.LightBackdrop
import com.noctra.app.ui.components.NcCard
import com.noctra.app.ui.components.NumberField
import com.noctra.app.ui.components.PrimaryButton
import com.noctra.app.ui.components.StatTile
import com.noctra.app.viewmodel.ReportViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportScreen(onBack: () -> Unit, vm: ReportViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var editLog by remember { mutableStateOf<ActivityLog?>(null) }
    var deleteLog by remember { mutableStateOf<ActivityLog?>(null) }
    var addOpen by remember { mutableStateOf(false) }
    var confirmFinalize by remember { mutableStateOf(false) }
    val session = ui.session
    val stats = ui.stats
    val finalized = session?.endedAt != null

    LightBackdrop {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text(if (finalized) "Daily report" else "Review your day", style = MaterialTheme.typography.titleLarge)
            }
            if (session == null || stats == null) {
                if (ui.loaded) EmptyState("Nothing to show", "This day has no confirmed wake-up, so there is no report.")
            } else {
                Text(
                    "${session.date}  ·  awake ${session.wakeAt?.let { TimeUtil.clock(it) } ?: "-"}" +
                        (session.endedAt?.let { " to ${TimeUtil.clock(it)}" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!finalized) {
                    Text(
                        "Correct anything below, then finalize. Nothing is saved to history until you do.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Elapsed since wake", TimeUtil.duration(stats.elapsedMin), Modifier.weight(1f))
                    StatTile("Study", TimeUtil.duration(stats.studyMin), Modifier.weight(1f), "user-entered")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Breaks", TimeUtil.duration(stats.breakMin), Modifier.weight(1f), "recorded")
                    StatTile("Distracted", TimeUtil.duration(stats.distractionMin), Modifier.weight(1f), "user-entered")
                }
                StatTile("Unclassified", TimeUtil.duration(stats.unclassifiedMin), Modifier.fillMaxWidth(), "elapsed minus everything above")
                Text("Check-ins: ${ui.answered} answered, ${ui.unanswered} unanswered", style = MaterialTheme.typography.bodyMedium)

                NcCard {
                    Text("Completed (${ui.completed.size})", style = MaterialTheme.typography.titleMedium)
                    if (ui.completed.isEmpty()) Text("None yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ui.completed.forEach { Text("• ${it.title}") }
                }
                NcCard {
                    Text("Unfinished (${ui.unfinished.size})", style = MaterialTheme.typography.titleMedium)
                    if (ui.unfinished.isEmpty()) Text("Nothing left open.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ui.unfinished.forEach { Text("• ${it.title} (${it.progress}%)") }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Day log", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { addOpen = true }) { Text("ADD ENTRY") }
                }
                if (ui.logs.isEmpty()) {
                    Text("No entries yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ui.logs.forEach { log ->
                    NcCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${log.kind.name.lowercase().replaceFirstChar { it.uppercase() }} · ${TimeUtil.duration(Stats.minutes(log, session.endedAt ?: System.currentTimeMillis()))}",
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    TimeUtil.clock(log.startAt) + (log.endAt?.let { " - ${TimeUtil.clock(it)}" } ?: " - ongoing") +
                                        (log.taskId?.let { ui.taskTitles[it] }?.let { " · $it" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (log.endAt != null) {
                                IconButton(onClick = { editLog = log }) { Icon(Icons.Filled.Edit, "Edit entry") }
                            }
                            IconButton(onClick = { deleteLog = log }) { Icon(Icons.Filled.Delete, "Delete entry") }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                if (!finalized) {
                    PrimaryButton("FINALIZE DAY", { confirmFinalize = true }, Modifier.fillMaxWidth())
                } else {
                    Text(
                        "This report is saved in your history. Corrections above update it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PrimaryButton("DONE", onBack, Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    editLog?.let { log ->
        var m by remember(log.id) { mutableStateOf(Stats.minutes(log, System.currentTimeMillis()).toString()) }
        AlertDialog(
            onDismissRequest = { editLog = null },
            title = { Text("Edit minutes") },
            text = { NumberField(m, { m = it }, "Minutes", Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(onClick = { vm.updateLog(log.id, m.toIntOrNull() ?: 0); editLog = null }) { Text("SAVE") }
            },
            dismissButton = { TextButton(onClick = { editLog = null }) { Text("CANCEL") } }
        )
    }
    deleteLog?.let { log ->
        ConfirmDialog("Delete this entry?", "The time returns to unclassified.", "DELETE",
            { vm.deleteLog(log.id); deleteLog = null }, { deleteLog = null })
    }
    if (addOpen) {
        var kind by remember { mutableStateOf(ActivityKind.STUDY) }
        var minutes by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addOpen = false },
            title = { Text("Add entry") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActivityKind.entries.forEach { k ->
                            FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.name.lowercase().replaceFirstChar { it.uppercase() }) })
                        }
                    }
                    NumberField(minutes, { minutes = it }, "Minutes", Modifier.fillMaxWidth())
                    Text("Placed at the end of the day record. Limited to the time elapsed since wake.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = (minutes.toIntOrNull() ?: 0) > 0,
                    onClick = { vm.addLog(kind, minutes.toInt()); addOpen = false }
                ) { Text("ADD") }
            },
            dismissButton = { TextButton(onClick = { addOpen = false }) { Text("CANCEL") } }
        )
    }
    if (confirmFinalize) {
        ConfirmDialog(
            "Finalize this day?",
            "This ends the day, stops reminders, and saves the report. You can start a new day by confirming wake-up later.",
            "FINALIZE",
            { confirmFinalize = false; vm.finalize { } },
            { confirmFinalize = false }
        )
    }
}
