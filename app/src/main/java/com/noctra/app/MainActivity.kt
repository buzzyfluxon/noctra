package com.noctra.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noctra.app.data.AppSettings
import com.noctra.app.notify.Notifier
import com.noctra.app.ui.AppNav
import com.noctra.app.ui.theme.NoctraTheme

class MainActivity : ComponentActivity() {
    private val route = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) route.value = intent?.getStringExtra(Notifier.EXTRA_ROUTE)
        val app = application as NoctraApp
        setContent {
            val st by app.repo.settings.flow.collectAsStateWithLifecycle(initialValue = AppSettings())
            val dark = when (st.theme) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }
            NoctraTheme(dark = dark) {
                AppNav(route.value) { route.value = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        route.value = intent.getStringExtra(Notifier.EXTRA_ROUTE)
    }
}
