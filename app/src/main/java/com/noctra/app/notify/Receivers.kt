package com.noctra.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.noctra.app.NoctraApp
import com.noctra.app.data.CheckInInput
import com.noctra.app.data.CheckInResponse
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as NoctraApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                when (intent.action) {
                    Actions.WAKE_CHECK -> if (app.repo.onWakeCheckFired()) Notifier.showWake(app)
                    Actions.CHECKIN -> app.repo.onCheckInFired()?.let { ci ->
                        val interval = app.repo.settings.current().intervalMin
                        val title = ci.taskId?.let { app.repo.getTask(it)?.title }
                        Notifier.showCheckIn(app, ci.id, title, interval)
                    }
                    Actions.TASK_TIMER -> app.repo.onTaskTimerFired()?.let { t ->
                        Notifier.showTaskTimeUp(app, t.id, t.title, t.estimateMin, t.workedAt(System.currentTimeMillis()) / 60_000L)
                    }
                    else -> Unit
                }
            } finally {
                pending.finish()
            }
        }
    }
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as NoctraApp
        val pending = goAsync()
        val checkInId = intent.getLongExtra(Actions.EXTRA_CHECKIN_ID, 0)
        val taskId = intent.getLongExtra(Actions.EXTRA_TASK_ID, 0)
        app.appScope.launch {
            try {
                when (intent.action) {
                    Actions.WAKE_YES -> app.repo.confirmWake()
                    Actions.WAKE_LATER -> app.repo.notWakeYet()
                    Actions.CHECKIN_STILL ->
                        app.repo.respond(checkInId, CheckInInput(CheckInResponse.STILL_WORKING))
                    Actions.CHECKIN_BREAK ->
                        app.repo.respond(checkInId, CheckInInput(CheckInResponse.BREAK))
                    Actions.END_BREAK -> app.repo.endBreak()
                    Actions.EXTEND_15 -> if (taskId > 0) app.repo.extendTask(taskId, 15)
                    else -> Unit
                }
            } finally {
                pending.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as NoctraApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                app.repo.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}
