package com.noctra.app.ui.screens

import android.app.TimePickerDialog
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noctra.app.data.TimeUtil
import com.noctra.app.ui.components.DarkBackdrop
import com.noctra.app.ui.components.PrimaryButton
import com.noctra.app.ui.components.SecondaryButton
import com.noctra.app.ui.components.Wordmark
import com.noctra.app.viewmodel.WakeViewModel
import kotlinx.coroutines.delay

@Composable
fun WakeScreen(onDone: () -> Unit, vm: WakeViewModel = viewModel()) {
    val session by vm.session.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var chosen by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(15_000)
        }
    }
    val wakeAt = session?.wakeAt

    fun pickTime(initial: Long, onPicked: (Long) -> Unit) {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = initial
        TimePickerDialog(
            ctx,
            { _, h, m -> onPicked(TimeUtil.lastOccurrence(h, m, System.currentTimeMillis())) },
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE),
            false
        ).show()
    }

    DarkBackdrop {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Wordmark()
            Spacer(Modifier.height(32.dp))
            Text(TimeUtil.clock(now), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            if (wakeAt != null) {
                Text("You confirmed you were up at ${TimeUtil.clock(wakeAt)}.", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Wrong time? Change it. Your day and unclassified time update to match.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(32.dp))
                SecondaryButton("CHANGE WAKE TIME", { pickTime(wakeAt) { vm.changeWake(it) } }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                PrimaryButton("BACK TO MY PLAN", onDone, Modifier.fillMaxWidth())
            } else {
                Text("You're supposed to be awake. Are you up?", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Confirming records the time you woke up and starts your accountability checks.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Text("When did you wake up?", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val picked = chosen
                    Text(
                        if (picked != null) "At ${TimeUtil.clock(picked)}" else "Right now",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(onClick = { pickTime(picked ?: now) { chosen = it } }) {
                        Text(if (picked != null) "CHANGE" else "I WOKE UP EARLIER")
                    }
                    if (picked != null) {
                        TextButton(onClick = { chosen = null }) { Text("RESET") }
                    }
                }
                Spacer(Modifier.height(20.dp))
                PrimaryButton("YES, I'M UP", { vm.confirm(chosen, onDone) }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                SecondaryButton("NOT YET (ASK AGAIN IN 15 MIN)", { vm.later(onDone) }, Modifier.fillMaxWidth())
            }
        }
    }
}
