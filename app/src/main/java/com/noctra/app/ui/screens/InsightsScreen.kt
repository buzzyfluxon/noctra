package com.noctra.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
            if (ui.loaded && !ui.hasData) {
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
