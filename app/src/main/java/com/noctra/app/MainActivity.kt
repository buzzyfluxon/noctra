package com.noctra.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.noctra.app.notify.Notifier
import com.noctra.app.ui.AppNav
import com.noctra.app.ui.theme.NoctraTheme

class MainActivity : ComponentActivity() {
    private val route = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) route.value = intent?.getStringExtra(Notifier.EXTRA_ROUTE)
        setContent {
            NoctraTheme {
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
