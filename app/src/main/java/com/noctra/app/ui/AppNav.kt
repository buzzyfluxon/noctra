package com.noctra.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.noctra.app.NoctraApp
import com.noctra.app.data.AppSettings
import com.noctra.app.ui.screens.CheckInScreen
import com.noctra.app.ui.screens.InsightsScreen
import com.noctra.app.ui.screens.OnboardingScreen
import com.noctra.app.ui.screens.PlanScreen
import com.noctra.app.ui.screens.ReportScreen
import com.noctra.app.ui.screens.SettingsScreen
import com.noctra.app.ui.screens.TaskEditScreen
import com.noctra.app.ui.screens.TasksScreen
import com.noctra.app.ui.screens.WakeScreen
import com.noctra.app.ui.theme.NC

object Routes {
    const val ONBOARD = "onboarding"
    const val PLAN = "plan"
    const val TASKS = "tasks"
    const val INSIGHTS = "insights"
    const val PROFILE = "profile"
    const val WAKE = "wake"
    const val TASK_EDIT = "task_edit/{taskId}"
    const val CHECKIN = "checkin/{checkInId}"
    const val REPORT = "report/{sessionId}"
    fun taskEdit(id: Long = 0) = "task_edit/$id"
    fun checkIn(id: Long = 0) = "checkin/$id"
    fun report(id: Long) = "report/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.PLAN, "Plan", Icons.Filled.Home),
    Tab(Routes.TASKS, "Tasks", Icons.Filled.TaskAlt),
    Tab(Routes.INSIGHTS, "Insights", Icons.Filled.BarChart),
    Tab(Routes.PROFILE, "Profile", Icons.Filled.Person)
)

@Composable
fun AppNav(externalRoute: String?, onRouteConsumed: () -> Unit) {
    val app = LocalContext.current.applicationContext as NoctraApp
    val settingsState = app.repo.settings.flow.collectAsStateWithLifecycle<AppSettings, AppSettings?>(initialValue = null)
    val settings = settingsState.value
    if (settings == null) {
        Box(Modifier.fillMaxSize())
        return
    }
    val start = remember { if (settings.onboarded) Routes.PLAN else Routes.ONBOARD }
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    LaunchedEffect(externalRoute) {
        val r = externalRoute
        if (r != null) {
            if (settings.onboarded && r != Routes.PLAN) nav.navigate(r) { launchSingleTop = true }
            onRouteConsumed()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (tabs.any { it.route == current }) {
                NavigationBar(containerColor = Color.White) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = current == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(Routes.PLAN) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, null) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = NC.Violet,
                                selectedTextColor = NC.Violet,
                                indicatorColor = NC.VioletTint,
                                unselectedIconColor = NC.Muted,
                                unselectedTextColor = NC.Muted
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(nav, startDestination = start, modifier = Modifier.padding(padding)) {
            composable(Routes.ONBOARD) {
                OnboardingScreen(onFinished = {
                    nav.navigate(Routes.PLAN) { popUpTo(Routes.ONBOARD) { inclusive = true } }
                })
            }
            composable(Routes.PLAN) {
                PlanScreen(
                    onWake = { nav.navigate(Routes.WAKE) },
                    onAddTask = { nav.navigate(Routes.taskEdit()) },
                    onEditTask = { nav.navigate(Routes.taskEdit(it)) },
                    onCheckIn = { nav.navigate(Routes.checkIn()) },
                    onEndDay = { nav.navigate(Routes.report(it)) },
                    onProfile = { nav.navigate(Routes.PROFILE) { launchSingleTop = true } }
                )
            }
            composable(Routes.TASKS) {
                TasksScreen(onAdd = { nav.navigate(Routes.taskEdit()) }, onEdit = { nav.navigate(Routes.taskEdit(it)) })
            }
            composable(Routes.INSIGHTS) {
                InsightsScreen(onOpenReport = { nav.navigate(Routes.report(it)) })
            }
            composable(Routes.PROFILE) { SettingsScreen() }
            composable(Routes.WAKE) {
                WakeScreen(onDone = { nav.popBackStack() })
            }
            composable(
                Routes.TASK_EDIT,
                arguments = listOf(navArgument("taskId") { type = NavType.LongType })
            ) {
                TaskEditScreen(onBack = { nav.popBackStack() })
            }
            composable(
                Routes.CHECKIN,
                arguments = listOf(navArgument("checkInId") { type = NavType.LongType })
            ) {
                CheckInScreen(
                    onClose = { nav.popBackStack() },
                    onSplitTask = { id ->
                        nav.popBackStack()
                        nav.navigate(Routes.taskEdit(id))
                    }
                )
            }
            composable(
                Routes.REPORT,
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
            ) {
                ReportScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}
