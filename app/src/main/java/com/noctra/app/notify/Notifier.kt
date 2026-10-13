package com.noctra.app.notify

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.noctra.app.MainActivity
import com.noctra.app.R
import com.noctra.app.data.TimeUtil

object Actions {
    const val WAKE_CHECK = "com.noctra.app.WAKE_CHECK"
    const val CHECKIN = "com.noctra.app.CHECKIN"
    const val WAKE_YES = "com.noctra.app.WAKE_YES"
    const val WAKE_LATER = "com.noctra.app.WAKE_LATER"
    const val CHECKIN_STILL = "com.noctra.app.CHECKIN_STILL"
    const val CHECKIN_BREAK = "com.noctra.app.CHECKIN_BREAK"
    const val END_BREAK = "com.noctra.app.END_BREAK"
    const val EXTRA_CHECKIN_ID = "checkin_id"
}

object Notifier {
    const val EXTRA_ROUTE = "route"
    const val CH_WAKE = "wake"
    const val CH_CHECKIN = "checkin"
    const val CH_BREAK = "break"
    const val WAKE_ID = 1
    const val CHECKIN_ID = 2
    const val BREAK_ID = 3

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_WAKE, "Wake-up check", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Asks whether you are awake"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_CHECKIN, "Accountability checks", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Periodic check-ins about your current task"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_BREAK, "Break in progress", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows while a break is running"
            }
        )
    }

    private fun activity(ctx: Context, code: Int, route: String): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ROUTE, route)
        }
        return PendingIntent.getActivity(
            ctx, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun broadcast(ctx: Context, code: Int, action: String, checkInId: Long = 0): PendingIntent {
        val intent = Intent(ctx, ActionReceiver::class.java).setAction(action)
            .putExtra(Actions.EXTRA_CHECKIN_ID, checkInId)
        return PendingIntent.getBroadcast(
            ctx, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    @SuppressLint("MissingPermission")
    private fun post(ctx: Context, id: Int, n: Notification) {
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return
        try {
            nm.notify(id, n)
        } catch (_: SecurityException) {
        }
    }

    fun cancel(ctx: Context, id: Int) {
        NotificationManagerCompat.from(ctx).cancel(id)
    }

    fun showWake(ctx: Context) {
        val n = NotificationCompat.Builder(ctx, CH_WAKE)
            .setSmallIcon(R.drawable.ic_stat_noctra)
            .setContentTitle("Wake-up check")
            .setContentText("You're supposed to be awake. Are you up?")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(activity(ctx, 10, "wake"))
            .addAction(0, "YES, I'M UP", broadcast(ctx, 11, Actions.WAKE_YES))
            .addAction(0, "NOT YET", broadcast(ctx, 12, Actions.WAKE_LATER))
            .build()
        post(ctx, WAKE_ID, n)
    }

    fun showCheckIn(ctx: Context, checkInId: Long, taskTitle: String?, intervalMin: Int) {
        val span = if (intervalMin == 60) "the last hour" else "the last $intervalMin minutes"
        val question = "What have you done in $span?"
        val open = activity(ctx, 20, "checkin/$checkInId")
        val n = NotificationCompat.Builder(ctx, CH_CHECKIN)
            .setSmallIcon(R.drawable.ic_stat_noctra)
            .setContentTitle("ACCOUNTABILITY CHECK")
            .setContentText(question)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(question + (taskTitle?.let { "\nCurrent task: $it" } ?: ""))
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, "STILL WORKING", broadcast(ctx, 21, Actions.CHECKIN_STILL, checkInId))
            .addAction(0, "TAKING A BREAK", broadcast(ctx, 22, Actions.CHECKIN_BREAK, checkInId))
            .addAction(0, "ANSWER IN APP", open)
            .build()
        post(ctx, CHECKIN_ID, n)
    }

    fun showBreak(ctx: Context, startedAt: Long) {
        val n = NotificationCompat.Builder(ctx, CH_BREAK)
            .setSmallIcon(R.drawable.ic_stat_noctra)
            .setContentTitle("On a break")
            .setContentText("Started at ${TimeUtil.clock(startedAt)}. Accountability checks are paused.")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(activity(ctx, 30, "plan"))
            .addAction(0, "END BREAK", broadcast(ctx, 31, Actions.END_BREAK))
            .build()
        post(ctx, BREAK_ID, n)
    }
}
