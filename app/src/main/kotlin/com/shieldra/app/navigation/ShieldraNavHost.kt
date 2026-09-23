package com.shieldra.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shieldra.app.presentation.demo.DemoData
import com.shieldra.app.design.components.domain.ErrorStateFullScreen
import com.shieldra.app.presentation.screen.dashboard.DashboardCallbacks
import com.shieldra.app.presentation.screen.dashboard.DashboardScreen
import com.shieldra.app.presentation.screen.eventdetail.EventDetailCallbacks
import com.shieldra.app.presentation.screen.eventdetail.EventDetailScreen
import com.shieldra.app.presentation.screen.history.HistoryCallbacks
import com.shieldra.app.presentation.screen.history.HistoryScreen
import com.shieldra.app.presentation.screen.onboarding.ProtectionSetupScreen
import com.shieldra.app.presentation.screen.onboarding.WelcomeScreen
import com.shieldra.app.presentation.screen.premium.PremiumCallbacks
import com.shieldra.app.presentation.screen.premium.PremiumScreen
import com.shieldra.app.presentation.screen.settings.SettingsCallbacks
import com.shieldra.app.presentation.screen.settings.SettingsScreen
import com.shieldra.app.presentation.model.HistoryFilterState
import com.shieldra.app.R
import kotlinx.coroutines.launch

@Composable
fun ShieldraNavHost(
    startDestination: String = ShieldraRoutes.ONBOARDING_WELCOME,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in BottomTab.values().map { it.route }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deferredMessage = stringResource(R.string.phase3_deferred)
    val defer = { scope.launch { snackbarHostState.showSnackbar(deferredMessage) } }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                ShieldraBottomBar(navController, currentRoute)
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding),
        ) {
            // ===== Onboarding =====
            composable(ShieldraRoutes.ONBOARDING_WELCOME) {
                WelcomeScreen(
                    onGetStarted = {
                        navController.navigate(ShieldraRoutes.ONBOARDING_PERMISSIONS)
                    },
                    onSkip = {
                        navController.navigate(ShieldraRoutes.DASHBOARD) {
                            popUpTo(ShieldraRoutes.ONBOARDING_WELCOME) { inclusive = true }
                        }
                    },
                )
            }
            composable(ShieldraRoutes.ONBOARDING_PERMISSIONS) {
                // Presentation-only Phase 3 screen; Android permission acquisition is not implemented.
                com.shieldra.app.presentation.screen.onboarding.PermissionsScreen(
                    step = 1,
                    totalSteps = 1,
                    icon = Icons.Filled.Settings,
                    title = stringResource(R.string.notifications),
                    reason = stringResource(R.string.permission_notification_reason),
                    onAllow = { defer() },
                    onNotNow = {
                        navController.navigate(ShieldraRoutes.ONBOARDING_PROTECTION)
                    },
                )
            }
            composable(ShieldraRoutes.ONBOARDING_PROTECTION) {
                ProtectionSetupScreen(
                    onContinue = {
                        navController.navigate(ShieldraRoutes.DASHBOARD) {
                            popUpTo(ShieldraRoutes.ONBOARDING_WELCOME) { inclusive = true }
                        }
                    },
                )
            }

            // ===== Main =====
            composable(ShieldraRoutes.DASHBOARD) {
                DashboardScreen(
                    model = DemoData.dashboard(),
                    callbacks = DashboardCallbacks(
                        onEventClick = { id ->
                            navController.navigate(ShieldraRoutes.eventDetail(id))
                        },
                        onGuardClick = { defer() },
                        onPanic = { defer() },
                        onViewAllEvents = {
                            navController.navigate(ShieldraRoutes.HISTORY)
                        },
                    ),
                )
            }
            composable(ShieldraRoutes.HISTORY) {
                var historyFilterState by remember { mutableStateOf(HistoryFilterState()) }
                HistoryScreen(
                    events = DemoData.recentEvents(),
                    filterState = historyFilterState,
                    callbacks = HistoryCallbacks(
                        onEventClick = { id ->
                            navController.navigate(ShieldraRoutes.eventDetail(id))
                        },
                        onFilterChange = { change ->
                            historyFilterState = HistoryFilterState(
                                type = change.type,
                                status = change.status,
                            )
                        },
                    ),
                )
            }
            composable(ShieldraRoutes.PREMIUM) {
                PremiumScreen(
                    model = DemoData.premium(),
                    callbacks = PremiumCallbacks(
                        onBuy = { defer() },
                        onRestore = { defer() },
                    ),
                )
            }
            composable(ShieldraRoutes.SETTINGS) {
                SettingsScreen(
                    rows = DemoData.settingsRows(),
                    callbacks = SettingsCallbacks { _, _ -> defer() },
                )
            }

            // ===== Event detail =====
            composable(
                route = ShieldraRoutes.EVENT_DETAIL,
                arguments = listOf(
                    navArgument(ShieldraRoutes.EVENT_DETAIL_ARG_ID) { type = NavType.StringType },
                ),
            ) { entry ->
                val id = entry.arguments?.getString(ShieldraRoutes.EVENT_DETAIL_ARG_ID).orEmpty()
                val detail = DemoData.eventDetail(id)
                if (detail == null) {
                    ErrorStateFullScreen(
                        title = stringResource(R.string.event_unknown_title),
                        message = stringResource(R.string.event_unknown_message),
                    )
                } else {
                    EventDetailScreen(
                        model = detail,
                        callbacks = EventDetailCallbacks(
                            onBack = { navController.popBackStack() },
                            onOpenInMaps = { _, _ -> defer() },
                            onDelete = { defer() },
                            onExport = { defer() },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ShieldraBottomBar(
    navController: NavHostController,
    currentRoute: String?,
) {
    NavigationBar {
        BottomTab.values().forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = {
                    if (currentRoute != tab.route) {
                        navController.navigate(tab.route) {
                            popUpTo(ShieldraRoutes.DASHBOARD) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = {
                    Icon(
                        imageVector = tab.icon(),
                        contentDescription = tab.labelRes().let { stringResource(it) },
                    )
                },
                label = { Text(stringResource(tab.labelRes())) },
            )
        }
    }
}

private fun BottomTab.icon(): ImageVector = when (this) {
    BottomTab.Dashboard -> Icons.Filled.Home
    BottomTab.History -> Icons.Filled.History
    BottomTab.Premium -> Icons.Filled.Star
    BottomTab.Settings -> Icons.Filled.Settings
}

private fun BottomTab.labelRes(): Int = when (this) {
    BottomTab.Dashboard -> R.string.tab_dashboard
    BottomTab.History -> R.string.tab_history
    BottomTab.Premium -> R.string.tab_premium
    BottomTab.Settings -> R.string.tab_settings
}
