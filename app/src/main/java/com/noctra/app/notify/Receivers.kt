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
