package com.noctra.app.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noctra.app.data.TaskEntity
import com.noctra.app.data.TaskStatus
import com.noctra.app.data.TimeUtil
import com.noctra.app.ui.components.ConfirmDialog
import com.noctra.app.ui.components.EmptyState
import com.noctra.app.ui.components.LightBackdrop
import com.noctra.app.ui.components.NcCard
import com.noctra.app.ui.components.NcProgress
import com.noctra.app.ui.components.Pill
import com.noctra.app.ui.components.SubjectChip
import com.noctra.app.ui.components.subjectColors
import com.noctra.app.ui.components.subjectTint
import com.noctra.app.viewmodel.PlanFilter
import com.noctra.app.viewmodel.PlannerViewModel
import com.noctra.app.viewmodel.TaskRow
import java.time.LocalDate

@Composable
fun TasksScreen(onAdd: () -> Unit, onEdit: (Long) -> Unit, vm: PlannerViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var completeFor by remember { mutableStateOf<TaskEntity?>(null) }
    var postponeFor by remember { mutableStateOf<TaskEntity?>(null) }

    LightBackdrop {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Text(
                "Tasks",
                Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp),
                style = MaterialTheme.typography.headlineLarge
            )
            Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlanFilter.entries.forEach { f ->
                    FilterChip(selected = ui.filter == f, onClick = { vm.setFilter(f) }, label = { Text(f.label) })
                }
            }
            if (ui.loaded && ui.rows.isEmpty()) {
                EmptyState(
                    when (ui.filter) {
                        PlanFilter.TODAY -> "Nothing planned for today"
                        PlanFilter.LATER -> "Nothing scheduled later"
                        PlanFilter.DONE -> "No completed tasks yet"
                    },
                    "Tap ADD TASK to plan your work."
                )
            }
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(ui.rows, key = { _, r -> r.task.id }) { index, row ->
                    TaskCard(
                        row = row,
                        canUp = index > 0 && ui.filter != PlanFilter.DONE,
                        canDown = index < ui.rows.lastIndex && ui.filter != PlanFilter.DONE,
                        onClick = { onEdit(row.task.id) },
                        onUp = { vm.move(ui.rows, index, true) },
                        onDown = { vm.move(ui.rows, index, false) },
                        onStart = { vm.start(row.task.id) },
                        onDone = { completeFor = row.task },
                        onPostpone = { postponeFor = row.task }
                    )
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onAdd,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text("ADD TASK") }
        )
    }

    completeFor?.let { t ->
        ConfirmDialog("Mark as completed?", "\"${t.title}\" will be recorded as done now.", "YES, COMPLETED",
            { vm.complete(t.id); completeFor = null }, { completeFor = null })
    }
    postponeFor?.let { t ->
        ConfirmDialog("Postpone to tomorrow?", "\"${t.title}\" moves to tomorrow. Postponements are counted.", "POSTPONE",
            { vm.postpone(t.id); postponeFor = null }, { postponeFor = null })
    }
}

@Composable
private fun TaskCard(
    row: TaskRow,
    canUp: Boolean,
    canDown: Boolean,
    onClick: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onStart: () -> Unit,
    onDone: () -> Unit,
    onPostpone: () -> Unit
) {
    val t = row.task
    val (pbg, pfg) = priorityColors(t.priority)
    val finished = t.status == TaskStatus.COMPLETED
    NcCard(container = subjectTint(t.subject), onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SubjectChip(t)
                    Pill(t.priority.label, pbg, pfg)
                }
                Spacer(Modifier.height(8.dp))
                Text(t.title, style = MaterialTheme.typography.titleMedium)
                if (t.topic.isNotBlank()) {
                    Text(t.topic, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!finished) {
                Column {
                    IconButton(onClick = onUp, enabled = canUp, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.KeyboardArrowUp, "Move up")
                    }
                    IconButton(onClick = onDown, enabled = canDown, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.KeyboardArrowDown, "Move down")
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.Schedule, null, modifier = Modifier.size(16.dp))
            Text(TimeUtil.duration(t.estimateMin.toLong()), style = MaterialTheme.typography.bodySmall)
            Text("· ${t.status.label}", style = MaterialTheme.typography.bodySmall)
            if (t.postponeCount > 0) Text("· postponed ${t.postponeCount}x", style = MaterialTheme.typography.bodySmall)
            if (row.subTotal > 0) Text("· ${row.subDone}/${row.subTotal} subtasks", style = MaterialTheme.typography.bodySmall)
        }
        t.deadlineEpochDay?.let {
            Text(
                "Deadline ${LocalDate.ofEpochDay(it)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        NcProgress(t.progress / 100f, fill = subjectColors(t.subject).second)
        if (!finished) {
            Row {
                TextButton(onClick = onStart) { Text("START") }
                TextButton(onClick = onDone) { Text("DONE") }
                TextButton(onClick = onPostpone) { Text("POSTPONE") }
            }
        }
    }
}
