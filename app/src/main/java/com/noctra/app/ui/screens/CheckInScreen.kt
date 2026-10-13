package com.noctra.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noctra.app.data.CheckInInput
import com.noctra.app.data.CheckInOutcome
import com.noctra.app.data.CheckInResponse
import com.noctra.app.data.TimeUtil
import com.noctra.app.ui.components.DarkBackdrop
import com.noctra.app.ui.components.NcProgress
import com.noctra.app.ui.components.NumberField
import com.noctra.app.ui.components.PrimaryButton
import com.noctra.app.ui.theme.NC
import com.noctra.app.viewmodel.CheckInUi
import com.noctra.app.viewmodel.CheckInViewModel

private data class RespStyle(val response: CheckInResponse, val label: String, val color: Color, val icon: ImageVector)

private val responses = listOf(
    RespStyle(CheckInResponse.COMPLETED, "COMPLETED", NC.Green, Icons.Filled.CheckCircle),
    RespStyle(CheckInResponse.STILL_WORKING, "STILL WORKING", Color(0xFF9D8CFF), Icons.Filled.RadioButtonChecked),
    RespStyle(CheckInResponse.DISTRACTED, "DISTRACTED", NC.Pink, Icons.Filled.Warning),
    RespStyle(CheckInResponse.STUCK, "STUCK", NC.Amber, Icons.AutoMirrored.Filled.HelpOutline),
    RespStyle(CheckInResponse.BREAK, "TAKING A BREAK", NC.Blue, Icons.Filled.Coffee)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckInScreen(
    onClose: () -> Unit,
    onSplitTask: (Long) -> Unit,
    vm: CheckInViewModel = viewModel()
) {
    val ui = vm.ui
    var selected by remember { mutableStateOf<RespStyle?>(null) }
    var outcome by remember { mutableStateOf<CheckInOutcome?>(null) }

    DarkBackdrop {
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
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text(
                    "HOURLY CHECK-IN",
                    Modifier.weight(1f).padding(start = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close") }
            }
            Text(
                buildAnnotatedString {
                    append("What have you actually ")
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("accomplished?") }
                },
                style = MaterialTheme.typography.headlineMedium
            )
            val ctx = ui.context
            when {
                !ui.loaded -> Text("Loading…")
                ctx == null -> {
                    Text(
                        "Check-ins are available only after you confirm you're awake.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PrimaryButton("BACK", onClose, Modifier.fillMaxWidth())
                }
                else -> {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Color.White.copy(alpha = 0.06f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Person, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Text("  Current Task", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(ctx.task?.title ?: "No task selected", style = MaterialTheme.typography.titleMedium)
                            if (ctx.task != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    NcProgress(
                                        ctx.task.progress / 100f,
                                        Modifier.weight(1f),
                                        track = Color.White.copy(alpha = 0.12f)
                                    )
                                    Text("  ${ctx.task.progress}%", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                                Column(Modifier.padding(start = 10.dp)) {
                                    Text(
                                        if (ctx.scheduledAt != null) "Scheduled Check-in" else "Manual check-in",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(TimeUtil.clock(ctx.scheduledAt ?: System.currentTimeMillis()), style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                    responses.forEach { r ->
                        Surface(
                            onClick = { selected = r },
                            shape = RoundedCornerShape(20.dp),
                            color = r.color.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, r.color.copy(alpha = 0.6f))
                        ) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(r.icon, null, tint = r.color, modifier = Modifier.size(28.dp))
                                Text(
                                    r.label,
                                    Modifier.weight(1f).padding(start = 14.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = r.color
                                )
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = r.color)
                            }
                        }
                    }
                    Text(
                        "Study, distraction and break minutes are recorded exactly as you enter them. Nothing is guessed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        val ctx = ui.context
        val sel = selected
        if (sel != null && ctx != null) {
            ModalBottomSheet(onDismissRequest = { selected = null }, containerColor = NC.InkCard) {
                ResponseForm(sel, ui) { input ->
                    vm.submit(input) {
                        outcome = it
                        selected = null
                    }
                }
            }
        }
        outcome?.let { o ->
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Logged") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(o.message)
                        o.nextTask?.let { Text("Next planned task: ${it.title}", fontWeight = FontWeight.Bold) }
                    }
                },
                confirmButton = {
                    val next = o.nextTask
                    val split = o.splitTaskId
                    when {
                        next != null -> TextButton(onClick = { vm.startTask(next.id); onClose() }) { Text("START NEXT TASK") }
                        split != null -> TextButton(onClick = { onSplitTask(split) }) { Text("SPLIT INTO SUBTASKS") }
                        else -> TextButton(onClick = onClose) { Text("DONE") }
                    }
                },
                dismissButton = {
                    if (o.nextTask != null || o.splitTaskId != null) TextButton(onClick = onClose) { Text("LATER") }
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResponseForm(style: RespStyle, ui: CheckInUi, onSubmit: (CheckInInput) -> Unit) {
    val ctx = ui.context ?: return
    val task = ctx.task
    val avail = ctx.availableMin
    var studied by remember { mutableStateOf("") }
    var wasted by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var progress by remember { mutableFloatStateOf((task?.progress ?: 0).toFloat()) }
    var estimate by remember { mutableStateOf((task?.estimateMin ?: 60).toString()) }
    val s = studied.toIntOrNull() ?: 0
    val w = wasted.toIntOrNull() ?: 0
    val isDistracted = style.response == CheckInResponse.DISTRACTED
    val total = s + (if (isDistracted) w else 0)
    val overLimit = total > avail
    val valid = !overLimit && (!isDistracted || w > 0)

    Column(
        Modifier.fillMaxWidth().imePadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(style.label, style = MaterialTheme.typography.titleLarge, color = style.color)
        when (style.response) {
            CheckInResponse.COMPLETED ->
                Text(
                    if (task != null) "Confirm \"${task.title}\" is finished. It is only marked completed if you confirm below."
                    else "No task is selected, so only your study minutes will be logged."
                )
            CheckInResponse.STILL_WORKING -> {
                Text("Keep the task active. Update its progress or estimate if they changed.")
                if (task != null) {
                    Text("Progress ${progress.toInt()}%", style = MaterialTheme.typography.labelLarge)
                    Slider(progress, { progress = it }, valueRange = 0f..100f, steps = 19)
                    NumberField(estimate, { estimate = it }, "Estimated minutes for this task", Modifier.fillMaxWidth())
                }
            }
            CheckInResponse.DISTRACTED -> {
                Text("How much time did the distraction cost? Be honest.")
                NumberField(wasted, { wasted = it }, "Minutes wasted", Modifier.fillMaxWidth())
            }
            CheckInResponse.STUCK ->
                OutlinedTextField(
                    note, { note = it }, label = { Text("What exactly are you stuck on?") }, minLines = 3,
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
                )
            CheckInResponse.BREAK ->
                Text("Your break starts now. No timer runs. End it when you are ready.")
        }
        NumberField(
            studied,
            { studied = it },
            "Minutes you actually studied (optional)",
            Modifier.fillMaxWidth(),
            supporting = if (overLimit) "Only $avail min have passed since your last logged time." else "Up to $avail min since your last logged time."
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 30, 45, 60).filter { it <= avail }.forEach { m ->
                FilterChip(selected = studied == m.toString(), onClick = { studied = m.toString() }, label = { Text("$m min") })
            }
        }
        when (style.response) {
            CheckInResponse.COMPLETED -> {
                PrimaryButton(
                    "YES, MARK COMPLETED",
                    { onSubmit(CheckInInput(style.response, s, 0, note, confirmComplete = true)) },
                    Modifier.fillMaxWidth(),
                    enabled = valid && task != null,
                    container = style.color,
                    content = NC.Ink
                )
                TextButton(
                    onClick = { onSubmit(CheckInInput(style.response, s, 0, note)) },
                    enabled = valid,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("NOT FULLY DONE, KEEP IT OPEN") }
            }
            else -> PrimaryButton(
                when (style.response) {
                    CheckInResponse.BREAK -> "START BREAK"
                    else -> "SAVE"
                },
                {
                    onSubmit(
                        CheckInInput(
                            style.response, s, w, note,
                            progress = if (style.response == CheckInResponse.STILL_WORKING && task != null) progress.toInt() else null,
                            estimateMin = if (style.response == CheckInResponse.STILL_WORKING) estimate.toIntOrNull()?.coerceAtLeast(5) else null
                        )
                    )
                },
                Modifier.fillMaxWidth(),
                enabled = valid,
                container = style.color,
                content = NC.Ink
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}
