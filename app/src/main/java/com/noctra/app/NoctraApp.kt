package com.noctra.app

import android.app.Application
import com.noctra.app.data.AppDatabase
import com.noctra.app.data.NoctraRepository
import com.noctra.app.data.SettingsStore
import com.noctra.app.notify.Notifier
import com.noctra.app.notify.Scheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NoctraApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var repo: NoctraRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repo = NoctraRepository(this, AppDatabase.build(this), SettingsStore(this), Scheduler(this))
        Notifier.createChannels(this)
        appScope.launch { repo.rescheduleAll() }
    }
}
