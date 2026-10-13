package com.noctra.app.ui.screens

import android.content.Context
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noctra.app.notify.Perms
import com.noctra.app.notify.rememberNotificationAsk
import com.noctra.app.ui.components.DarkBackdrop
import com.noctra.app.ui.components.Wordmark
import com.noctra.app.ui.theme.NC
import com.noctra.app.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(onFinished: () -> Unit, vm: SettingsViewModel = viewModel()) {
    val pager = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var perms by remember { mutableStateOf(Perms.read(ctx)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { perms = Perms.read(ctx) }
    val askNotifications = rememberNotificationAsk { perms = Perms.read(ctx) }
    val last = pager.currentPage == 2

    DarkBackdrop {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp)) {
            Wordmark()
            HorizontalPager(pager, Modifier.weight(1f)) { page ->
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    when (page) {
                        0 -> {
                            Text(
                                buildAnnotatedString {
                                    append("Your NEET year.\n")
                                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("Under control.") }
                                },
                                style = MaterialTheme.typography.headlineLarge
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Less planning. More honest progress.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        1 -> {
                            Text("Every hour,\ntell it straight.", style = MaterialTheme.typography.headlineLarge)
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Completed, still working, distracted, stuck or on a break. You report it, the app records it. No timers, no guessing.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Allow the reminders", style = MaterialTheme.typography.headlineMedium)
                            Text(
                                "Check-ins only work if Android lets them through.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            PermCard("Notifications", "Wake-up and accountability prompts", perms.notifications) { askNotifications() }
                            PermCard("Exact alarms", "On-time delivery of hourly checks", perms.exactAlarms) {
                                ctx.startActivity(Perms.exactAlarmSettings(ctx))
                            }
                            PermCard("Battery unrestricted", "Fewer delayed reminders on aggressive phones", perms.batteryUnrestricted) {
                                ctx.startActivity(Perms.batterySettings())
                            }
                            Text(
                                "You can skip these and change them later in Settings. Reminders may then be late or missing.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) { i ->
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(if (i == pager.currentPage) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.25f), CircleShape)
                        )
                    }
                }
                Surface(
                    onClick = { scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) } },
                    enabled = pager.currentPage > 0,
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous") }
                }
                Spacer(Modifier.size(12.dp))
                Surface(
                    onClick = {
                        if (last) vm.finishOnboarding(onFinished)
                        else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    },
                    shape = RoundedCornerShape(18.dp),
                    color = NC.Violet,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (last) Icons.Filled.Check else Icons.AutoMirrored.Filled.ArrowForward,
                            if (last) "Get started" else "Next",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermCard(title: String, body: String, granted: Boolean, onAllow: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (granted) Text("ON", color = NC.Green, style = MaterialTheme.typography.labelLarge)
            else TextButton(onClick = onAllow) { Text("ALLOW") }
        }
    }
}
