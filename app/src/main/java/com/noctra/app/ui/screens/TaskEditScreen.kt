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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noctra.app.data.Priority
import com.noctra.app.data.Subject
import com.noctra.app.data.SubtaskEntity
import com.noctra.app.data.TaskStatus
import com.noctra.app.data.TimeUtil
import com.noctra.app.ui.components.ConfirmDialog
import com.noctra.app.ui.components.LightBackdrop
import com.noctra.app.ui.components.NumberField
import com.noctra.app.ui.components.PrimaryButton
import com.noctra.app.ui.components.SecondaryButton
import com.noctra.app.viewmodel.TaskEditViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private enum class DateTarget { DEADLINE, PLAN }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditScreen(onBack: () -> Unit, vm: TaskEditViewModel = viewModel()) {
    val loaded = vm.task
    val customSubjects by vm.customSubjects.collectAsStateWithLifecycle()

    LightBackdrop {
        if (loaded == null) {
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text(if (vm.missing) "This task no longer exists." else "Loading…")
            }
            return@LightBackdrop
        }
        var title by remember { mutableStateOf(loaded.title) }
        var subject by remember { mutableStateOf(loaded.subject) }
        var custom by remember { mutableStateOf(loaded.customSubject) }
        var topic by remember { mutableStateOf(loaded.topic) }
        var estimate by remember { mutableStateOf(loaded.estimateMin.toString()) }
        var priority by remember { mutableStateOf(loaded.priority) }
        var deadline by remember { mutableStateOf(loaded.deadlineEpochDay) }
        var planDate by remember { mutableStateOf(loaded.planDate) }
        var notes by remember { mutableStateOf(loaded.notes) }
        var status by remember { mutableStateOf(loaded.status) }
        var progress by remember { mutableStateOf(loaded.progress.toFloat()) }
        val subs = remember { mutableStateListOf<SubtaskEntity>().also { it.addAll(vm.subtasks) } }
        var newSub by remember { mutableStateOf("") }
        var pickTarget by remember { mutableStateOf<DateTarget?>(null) }
        var confirmDelete by remember { mutableStateOf(false) }
        val isNew = vm.taskId == 0L
        val valid = title.isNotBlank() && (subject != Subject.CUSTOM || custom.isNotBlank())

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text(if (isNew) "New task" else "Edit task", style = MaterialTheme.typography.titleLarge)
            }
            OutlinedTextField(
                title, { title = it }, label = { Text("Title") }, singleLine = true,
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
            )
            Text("Subject", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Subject.entries.forEach { s ->
                    FilterChip(selected = subject == s, onClick = { subject = s }, label = { Text(s.label) })
                }
            }
            if (subject == Subject.CUSTOM) {
                OutlinedTextField(
                    custom, { custom = it }, label = { Text("Custom subject") }, singleLine = true,
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
                )
                if (customSubjects.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        customSubjects.forEach { c ->
                            FilterChip(selected = custom == c, onClick = { custom = c }, label = { Text(c) })
                        }
                    }
                }
            }
            OutlinedTextField(
                topic, { topic = it }, label = { Text("Chapter or topic") }, singleLine = true,
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
            )
            NumberField(estimate, { estimate = it }, "Estimated duration (minutes)", Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30, 60, 90, 120, 180).forEach { m ->
                    FilterChip(selected = estimate == m.toString(), onClick = { estimate = m.toString() }, label = { Text(TimeUtil.duration(m.toLong())) })
                }
            }
            Text("Priority", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Priority.entries.forEach { p ->
                    FilterChip(selected = priority == p, onClick = { priority = p }, label = { Text(p.label) })
                }
            }
            Text("Plan for", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(selected = planDate == TimeUtil.todayStr(), onClick = { planDate = TimeUtil.todayStr() }, label = { Text("Today") })
                FilterChip(selected = planDate == TimeUtil.tomorrowStr(), onClick = { planDate = TimeUtil.tomorrowStr() }, label = { Text("Tomorrow") })
                TextButton(onClick = { pickTarget = DateTarget.PLAN }) { Text("PICK DATE ($planDate)") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { pickTarget = DateTarget.DEADLINE }) {
                    Text(deadline?.let { "DEADLINE ${LocalDate.ofEpochDay(it)}" } ?: "SET DEADLINE (OPTIONAL)")
                }
                if (deadline != null) TextButton(onClick = { deadline = null }) { Text("CLEAR") }
            }
            OutlinedTextField(
                notes, { notes = it }, label = { Text("Notes") }, minLines = 3,
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
            )
            if (!isNew) {
                Text("Status", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskStatus.entries.forEach { s ->
                        FilterChip(selected = status == s, onClick = { status = s }, label = { Text(s.label) })
                    }
                }
                Text("Progress ${progress.toInt()}%", style = MaterialTheme.typography.labelLarge)
                Slider(progress, { progress = it }, valueRange = 0f..100f, steps = 19)
            }
            Text("Subtasks", style = MaterialTheme.typography.labelLarge)
            subs.forEachIndexed { i, s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = s.done, onCheckedChange = { subs[i] = s.copy(done = it) })
                    Text(s.title, Modifier.weight(1f))
                    IconButton(onClick = { subs.removeAt(i) }) { Icon(Icons.Filled.Close, "Remove subtask") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    newSub, { newSub = it }, label = { Text("Add a subtask") }, singleLine = true,
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        if (newSub.isNotBlank()) {
                            subs.add(SubtaskEntity(title = newSub.trim()))
                            newSub = ""
                        }
                    }
                ) { Icon(Icons.Filled.Add, "Add subtask") }
            }
            Spacer(Modifier.height(4.dp))
            PrimaryButton(
                if (isNew) "ADD TASK" else "SAVE CHANGES",
                {
                    val base = loaded.copy(
                        title = title.trim(),
                        subject = subject,
                        customSubject = if (subject == Subject.CUSTOM) custom.trim() else "",
                        topic = topic.trim(),
                        estimateMin = (estimate.toIntOrNull() ?: 60).coerceAtLeast(5),
                        priority = priority,
                        deadlineEpochDay = deadline,
                        notes = notes.trim(),
                        planDate = planDate,
                        status = if (isNew) TaskStatus.PENDING else status,
                        progress = if (isNew) 0 else progress.toInt()
                    )
                    vm.save(base, subs.toList(), onBack)
                },
                Modifier.fillMaxWidth(),
                enabled = valid
            )
            if (!isNew) SecondaryButton("DELETE TASK", { confirmDelete = true }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
        }

        pickTarget?.let { target ->
            val state = rememberDatePickerState()
            DatePickerDialog(
                onDismissRequest = { pickTarget = null },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { ms ->
                            val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                            if (target == DateTarget.DEADLINE) deadline = d.toEpochDay() else planDate = d.toString()
                        }
                        pickTarget = null
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { pickTarget = null }) { Text("CANCEL") } }
            ) { DatePicker(state) }
        }
        if (confirmDelete) {
            ConfirmDialog("Delete this task?", "The task and its subtasks are removed. Logged study time stays in your history.", "DELETE",
                { confirmDelete = false; vm.delete(onBack) }, { confirmDelete = false })
        }
    }
}
