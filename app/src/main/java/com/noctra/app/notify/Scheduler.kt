package com.noctra.app.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

class Scheduler(private val ctx: Context) {
    private val am: AlarmManager = ctx.getSystemService(AlarmManager::class.java)

    fun canExact(): Boolean = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()

    private fun pi(action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx,
            code,
            Intent(ctx, AlarmReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun set(at: Long, pending: PendingIntent) {
        if (canExact()) {
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                return
            } catch (_: SecurityException) {
            }
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }

    fun scheduleWake(at: Long) = set(at, pi(Actions.WAKE_CHECK, 1001))

    fun scheduleCheckIn(at: Long) = set(at, pi(Actions.CHECKIN, 1002))

    fun cancelWake() = am.cancel(pi(Actions.WAKE_CHECK, 1001))

    fun cancelCheckIn() = am.cancel(pi(Actions.CHECKIN, 1002))
}
