package com.seagori.hgr.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.seagori.hgr.domain.SessionRestoreRoute
import com.seagori.hgr.i18n.LocalStrings
import com.seagori.hgr.ui.AppViewModelFactory
import com.seagori.hgr.ui.goals.GoalsScreen
import com.seagori.hgr.ui.goals.GoalsViewModel
import com.seagori.hgr.ui.archive.ArchiveScreen
import com.seagori.hgr.ui.archive.ArchiveViewModel
import com.seagori.hgr.ui.daily.DailyTasksScreen
import com.seagori.hgr.ui.daily.DailyTasksViewModel
import com.seagori.hgr.ui.home.HomeScreen
import com.seagori.hgr.ui.home.HomeViewModel
import com.seagori.hgr.ui.records.RecordsScreen
import com.seagori.hgr.ui.records.RecordsViewModel
import com.seagori.hgr.ui.session.CheckScreen
import com.seagori.hgr.ui.session.SessionViewModel
import com.seagori.hgr.ui.session.SuggestionScreen
import com.seagori.hgr.ui.settings.SettingsScreen
import com.seagori.hgr.ui.settings.SettingsViewModel
import com.seagori.hgr.ui.theme.AccentDeep
import com.seagori.hgr.ui.theme.AccentLight
import com.seagori.hgr.ui.theme.Bg
import com.seagori.hgr.ui.theme.Card
import com.seagori.hgr.ui.theme.Muted

object Routes {
    const val Home = "home"
    const val Daily = "daily"
    const val Records = "records"
    const val Goals = "goals"
    const val Settings = "settings"
    const val Archive = "archive"
    const val Suggestion = "suggestion"
    const val Check = "check"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

@Composable
fun AppNav(
    factory: AppViewModelFactory,
    bootstrap: suspend () -> SessionRestoreRoute?,
    triggerGoalId: Long? = null,
    onTriggerHandled: () -> Unit = {},
    onOpenGoalFromTrigger: suspend (Long) -> SessionRestoreRoute? = { SessionRestoreRoute.SUGGESTION }
) {
    val strings = LocalStrings.current
    val tabs = listOf(
        Tab(Routes.Home, strings.navHome, Icons.Outlined.Home),
        Tab(Routes.Daily, strings.navDaily, Icons.Outlined.CheckCircle),
        Tab(Routes.Records, strings.navRecords, Icons.AutoMirrored.Outlined.MenuBook),
        Tab(Routes.Goals, strings.navGoals, Icons.Outlined.Flag),
        Tab(Routes.Settings, strings.navSettings, Icons.Outlined.Settings)
    )
    val navController = rememberNavController()
    val sessionViewModel: SessionViewModel = viewModel(factory = factory)

    LaunchedEffect(triggerGoalId) {
        if (triggerGoalId != null) {
            val result = onOpenGoalFromTrigger(triggerGoalId)
            onTriggerHandled()
            val route = when (result) {
                SessionRestoreRoute.SUGGESTION -> Routes.Suggestion
                SessionRestoreRoute.CHECK -> Routes.Check
                null -> return@LaunchedEffect
            }
            navController.navigate(route) {
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(Unit) {
        if (triggerGoalId != null) return@LaunchedEffect
        val route = when (bootstrap()) {
            SessionRestoreRoute.SUGGESTION -> Routes.Suggestion
            SessionRestoreRoute.CHECK -> Routes.Check
            else -> return@LaunchedEffect
        }
        navController.navigate(route) {
            launchSingleTop = true
        }
    }
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val showBar = tabs.any { it.route == current }

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = Card, tonalElevation = 0.dp) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = current == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AccentDeep,
                                selectedTextColor = AccentDeep,
                                indicatorColor = AccentLight,
                                unselectedIconColor = Muted,
                                unselectedTextColor = Muted
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.Home,
            modifier = Modifier.padding(padding),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            composable(Routes.Home) {
                val vm: HomeViewModel = viewModel(factory = factory)
                HomeScreen(
                    viewModel = vm,
                    onStart = { navController.navigate(Routes.Suggestion) },
                    onQuickCheck = { navController.navigate(Routes.Check) }
                )
            }
            composable(Routes.Daily) {
                val vm: DailyTasksViewModel = viewModel(factory = factory)
                DailyTasksScreen(vm)
            }
            composable(Routes.Records) {
                val vm: RecordsViewModel = viewModel(factory = factory)
                RecordsScreen(vm, onOpenArchive = { navController.navigate(Routes.Archive) })
            }
            composable(Routes.Goals) {
                val vm: GoalsViewModel = viewModel(factory = factory)
                GoalsScreen(vm, onOpenArchive = { navController.navigate(Routes.Archive) })
            }
            composable(Routes.Archive) {
                val vm: ArchiveViewModel = viewModel(factory = factory)
                ArchiveScreen(vm, onBack = { navController.popBackStack() })
            }
            composable(Routes.Settings) {
                val vm: SettingsViewModel = viewModel(factory = factory)
                SettingsScreen(vm)
            }
            composable(Routes.Suggestion) {
                SuggestionScreen(
                    viewModel = sessionViewModel,
                    onBack = { navController.popBackStack() },
                    onCheck = { navController.navigate(Routes.Check) },
                    onHome = {
                        navController.popBackStack(Routes.Home, inclusive = false)
                    },
                    onOpenGoals = {
                        navController.navigate(Routes.Goals) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(Routes.Check) {
                CheckScreen(
                    viewModel = sessionViewModel,
                    onBack = { navController.popBackStack() },
                    onHome = { navController.popBackStack(Routes.Home, inclusive = false) },
                    onNext = {
                        navController.popBackStack(Routes.Suggestion, inclusive = false)
                    }
                )
            }
        }
    }
}
