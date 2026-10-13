package com.noctra.app.ui.screens

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noctra.app.data.TimeUtil
import com.noctra.app.data.UpdateChecker
import com.noctra.app.data.UpdateInfo
import kotlinx.coroutines.launch
import com.noctra.app.notify.Perms
import com.noctra.app.notify.rememberNotificationAsk
import com.noctra.app.ui.components.LightBackdrop
import com.noctra.app.ui.components.NcCard
import com.noctra.app.ui.components.PrimaryButton
import com.noctra.app.ui.components.SecondaryButton
import com.noctra.app.viewmodel.SettingsViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val noc by vm.noc.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var perms by remember { mutableStateOf(Perms.read(ctx)) }
    var newSubject by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val version = remember {
        try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0"
        } catch (e: Exception) {
            "0"
        }
    }
    var upd by remember { mutableStateOf<UpdateInfo?>(null) }
    var updMessage by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        UpdateChecker.check(version).onSuccess { upd = it }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { perms = Perms.read(ctx) }
    val askNotifications = rememberNotificationAsk { perms = Perms.read(ctx) }

    LightBackdrop {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineLarge)

            NcCard {
                Text("Appearance", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("SYSTEM" to "System default", "LIGHT" to "Light", "DARK" to "Dark").forEach { (key, label) ->
                        FilterChip(selected = s.theme == key, onClick = { vm.setTheme(key) }, label = { Text(label) })
                    }
                }
                Text(
                    "Dark mode applies to every screen right away.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            NcCard {
                Text("Wake-up reminder", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ask me every day", Modifier.weight(1f))
                    Switch(checked = s.wakeEnabled, onCheckedChange = { vm.setWakeEnabled(it) })
                }
                TextButton(
                    onClick = {
                        TimePickerDialog(ctx, { _, h, m -> vm.setWakeTime(h, m) }, s.wakeHour, s.wakeMinute, false).show()
                    }
                ) { Text("TIME: ${TimeUtil.clockOf(s.wakeHour, s.wakeMinute)}") }
            }

            NcCard {
                Text("Accountability interval", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30, 45, 60, 90, 120).forEach { m ->
                        FilterChip(selected = s.intervalMin == m, onClick = { vm.setInterval(m) }, label = { Text("$m min") })
                    }
                }
                Text(
                    "A change applies to the next check-in. Checks are held during breaks and while paused.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            NcCard {
                Text("Noc tone", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("BRUTAL", "DIRECT", "CALM").forEach { t ->
                        FilterChip(selected = s.tone == t, onClick = { vm.setTone(t) }, label = { Text(t.lowercase().replaceFirstChar { it.uppercase() }) })
                    }
                }
                Text(
                    "How Noc, your AI coach, talks to you. Applies to the next message.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            NocKeyCard(
                config = noc,
                onSaveKey = vm::setNocKey,
                onClearKey = vm::clearNocKey,
                onSaveModel = vm::setNocModel
            )

            NcCard {
                Text("App updates", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Installed version: $version",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val info = upd
                if (info != null && info.newer) {
                    Text("Update available: version ${info.latest}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Tap download, then open the downloaded file and tap Install. Your tasks and history are kept.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    PrimaryButton("DOWNLOAD UPDATE", {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.url)))
                    }, Modifier.fillMaxWidth())
                } else if (info != null) {
                    Text("You are on the latest version.", style = MaterialTheme.typography.bodyMedium)
                }
                updMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(8.dp))
                SecondaryButton(
                    if (checking) "CHECKING..." else "CHECK FOR UPDATES",
                    {
                        scope.launch {
                            checking = true
                            updMessage = null
                            UpdateChecker.check(version)
                                .onSuccess { upd = it }
                                .onFailure { updMessage = "Could not check for updates. ${it.message ?: ""}" }
                            checking = false
                        }
                    },
                    Modifier.fillMaxWidth(),
                    enabled = !checking
                )
            }

            NcCard {
                Text("Notifications and reliability", style = MaterialTheme.typography.titleMedium)
                PermRow("Notifications", perms.notifications, "Allow") { askNotifications() }
                PermRow("Exact alarms", perms.exactAlarms, "Allow") { ctx.startActivity(Perms.exactAlarmSettings(ctx)) }
                PermRow("Battery unrestricted", perms.batteryUnrestricted, "Open") { ctx.startActivity(Perms.batterySettings()) }
                Text(
                    "Without exact alarms or with aggressive battery saving, Android may delay or drop reminders. Delivery cannot be guaranteed on every device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            NcCard {
                Text("Custom subjects", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                if (s.customSubjects.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        s.customSubjects.forEach { c ->
                            FilterChip(selected = true, onClick = { vm.removeSubject(c) }, label = { Text("$c  ✕") })
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        newSubject, { newSubject = it }, label = { Text("New subject") }, singleLine = true,
                        shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        enabled = newSubject.isNotBlank(),
                        onClick = { vm.addSubject(newSubject); newSubject = "" }
                    ) { Text("ADD") }
                }
            }

            NcCard {
                Text("Data export and backup", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Export tasks, activity and check-ins as CSV text you can share or save. Android also backs up app data to your Google account when device backup is on.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                SecondaryButton("EXPORT CSV", {
                    vm.export { csv ->
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_SUBJECT, "Noctra export")
                            .putExtra(Intent.EXTRA_TEXT, csv)
                        ctx.startActivity(Intent.createChooser(send, "Export data"))
                    }
                }, Modifier.fillMaxWidth())
            }

            NcCard {
                Text("End-of-day behavior", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Your day ends only when you tap END MY DAY. There is no fixed bedtime and no automatic midnight shutdown.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PermRow(label: String, ok: Boolean, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label)
            Text(
                if (ok) "OK" else "Not granted",
                style = MaterialTheme.typography.bodySmall,
                color = if (ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
            )
        }
        if (!ok) TextButton(onClick = onClick) { Text(action.uppercase()) }
    }
}
