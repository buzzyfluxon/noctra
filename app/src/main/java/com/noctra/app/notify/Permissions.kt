package com.noctra.app.notify

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat

data class PermStatus(
    val notifications: Boolean,
    val exactAlarms: Boolean,
    val batteryUnrestricted: Boolean
)

object Perms {
    fun read(ctx: Context): PermStatus {
        val pm = ctx.getSystemService(PowerManager::class.java)
        return PermStatus(
            notifications = NotificationManagerCompat.from(ctx).areNotificationsEnabled(),
            exactAlarms = Scheduler(ctx).canExact(),
            batteryUnrestricted = pm.isIgnoringBatteryOptimizations(ctx.packageName)
        )
    }

    fun notificationSettings(ctx: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)

    fun exactAlarmSettings(ctx: Context): Intent =
        if (Build.VERSION.SDK_INT >= 31) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
        }

    fun batterySettings(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
}

@Composable
fun rememberNotificationAsk(onDone: () -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onDone() }
    return {
        val needsRuntime = Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsRuntime) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            ctx.startActivity(Perms.notificationSettings(ctx))
        }
    }
}
