package com.noctra.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noctra.app.data.TimeUtil
import com.noctra.app.ui.components.DarkBackdrop
import com.noctra.app.ui.components.PauseReasonDialog
import com.noctra.app.ui.components.PrimaryButton
import com.noctra.app.ui.components.SecondaryButton
import com.noctra.app.ui.theme.NC
import com.noctra.app.viewmodel.TimeUpViewModel

/** Opens when a running task reaches its planned time: done, need more time, or pause with a reason. */
@Composable
fun TimeUpScreen(onClose: () -> Unit, vm: TimeUpViewModel = viewModel()) {
    val ui = vm.ui
    val task = ui.task
    var showPause by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }

    DarkBackdrop {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "TIME'S UP",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!ui.loaded) return@Column
            if (task == null) {
                Text("This task no longer exists.", style = MaterialTheme.typography.titleLarge)
                PrimaryButton("CLOSE", onClose, Modifier.fillMaxWidth())
                return@Column
            }
            Text(task.title, style = MaterialTheme.typography.headlineMedium)
            Text(
                "You planned ${TimeUtil.duration(task.estimateMin.toLong())} and have worked ${TimeUtil.duration(ui.workedMin)}.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("What's going on?", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))

            PrimaryButton(
                "I'M DONE",
                { vm.complete(onClose) },
                Modifier.fillMaxWidth(),
                container = NC.Green,
                content = NC.Slate
            )
            PrimaryButton("I NEED MORE TIME", { showMore = !showMore }, Modifier.fillMaxWidth())
            if (showMore) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 60).forEach { m ->
                        SecondaryButton("+$m MIN", { vm.extend(m, onClose) }, Modifier.weight(1f))
                    }
                }
            }
            SecondaryButton("PAUSE", { showPause = true }, Modifier.fillMaxWidth())
        }
    }

    if (showPause && task != null) {
        PauseReasonDialog(
            task.title,
            { reason -> showPause = false; vm.pause(reason, onClose) },
            { showPause = false }
        )
    }
}
