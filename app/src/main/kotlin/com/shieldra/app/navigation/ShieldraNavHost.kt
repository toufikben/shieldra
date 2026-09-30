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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
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
import com.shieldra.app.ShieldraApp
import com.shieldra.app.presentation.guard.AgeParts
import com.shieldra.app.presentation.guard.LockGuardDecision
import com.shieldra.app.presentation.guard.PersistedEventLabels
import com.shieldra.app.presentation.guard.StorageEventReader
import com.shieldra.app.presentation.guard.ageParts
import com.shieldra.app.presentation.guard.eventStateLabelRes
import com.shieldra.app.presentation.guard.lockGuardDashboard
import com.shieldra.app.presentation.model.EventUiModel
import com.shieldra.app.presentation.model.EventDetailUiModel
import com.shieldra.domain.EventState
import com.shieldra.domain.PanicIncidentCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import androidx.compose.ui.platform.LocalContext
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
import com.shieldra.app.presentation.screen.settings.NotificationSetupScreen
import com.shieldra.domain.PanicActivation
import com.shieldra.notification.LocalEventNotifier
import com.shieldra.notification.LocalPostResult
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
    // Honest projection: there is no live detection/monitoring yet (phase 5/G3),
    // so the UI derives from whether the storage gate installed anything, never
    // from fabricated protection numbers.
    val installedStorage = (LocalContext.current.applicationContext as ShieldraApp).installedStorage
    val storageInstalled = installedStorage != null
    val guardState = LockGuardDecision.decide(storageInstalled)
    val eventLabels = persistedEventLabels()
    val eventReader = remember(installedStorage, eventLabels) {
        StorageEventReader.reader(installedStorage, eventLabels)
    }
    // Panic needs the installed repository; without installed storage there is
    // nowhere to persist the event, so the press stays an honest deferral.
    val panicCoordinator = remember(installedStorage) {
        installedStorage?.let { PanicIncidentCoordinator(repository = it.eventRepository) }
    }
    var panicActivations by remember { mutableStateOf(0) }
    // Real local notifications (A-6). The permission is requested only from the
    // notification setup screen; a Panic press never triggers a request.
    val context = LocalContext.current
    val notifier = remember(context) { LocalEventNotifier(context) }
    var lastPostResult by remember { mutableStateOf<LocalPostResult?>(null) }
    // Bumped when the system dialog has been answered, so the screen re-reads the
    // live state instead of showing "not requested" after we already asked.
    var permissionRequests by remember { mutableStateOf(0) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        // Both answers count as having been asked; the permission itself is read
        // from the platform afterwards, never assumed from this callback.
        notifier.recordPermissionRequest()
        permissionRequests += 1
    }
    // Room refuses main-thread queries, so the load is dispatched off it and the
    // UI renders empty until the real rows arrive.
    val recentEvents by produceState(initialValue = emptyList<EventUiModel>(), installedStorage, panicActivations) {
        value = withContext(Dispatchers.IO) { eventReader.readRecent(RECENT_EVENT_LIMIT, Instant.now()) }
    }

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
                    model = lockGuardDashboard(guardState, recentEvents.take(RECENT_EVENT_LIMIT)),
                    callbacks = DashboardCallbacks(
                        onEventClick = { id ->
                            navController.navigate(ShieldraRoutes.eventDetail(id))
                        },
                        onGuardClick = { defer() },
                        onPanic = {
                            val coordinator = panicCoordinator
                            if (coordinator == null) {
                                defer()
                            } else {
                                scope.launch {
                                    val activation = withContext(Dispatchers.IO) { coordinator.activate() }
                                    // The event is already persisted at this point; a
                                    // missing notification never undoes that (A-6).
                                    lastPostResult = when (activation) {
                                        is PanicActivation.Recorded ->
                                            notifier.postSecurityEvent(
                                                activation.event.id.value.toString(),
                                                eventLabels.stateLabel(activation.event.state),
                                            )
                                        is PanicActivation.BoundToActive ->
                                            notifier.postSecurityEvent(
                                                activation.event.id.value.toString(),
                                                eventLabels.stateLabel(activation.event.state),
                                            )
                                        is PanicActivation.Rejected -> LocalPostResult.NotAttempted
                                    }
                                    panicActivations += 1
                                }
                            }
                        },
                        onViewAllEvents = {
                            navController.navigate(ShieldraRoutes.HISTORY)
                        },
                    ),
                )
            }
            composable(ShieldraRoutes.HISTORY) {
                var historyFilterState by remember { mutableStateOf(HistoryFilterState()) }
                HistoryScreen(
                    events = recentEvents,
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
                    callbacks = SettingsCallbacks { id, _ ->
                        if (id == DELIVERY_ROW_ID) {
                            navController.navigate(ShieldraRoutes.NOTIFICATION_SETUP)
                        } else {
                            defer()
                        }
                    },
                )
            }

            // Notification setup (A-6): the only permission request in the app.
            composable(ShieldraRoutes.NOTIFICATION_SETUP) {
                NotificationSetupScreen(
                    permissionState = remember(permissionRequests) { notifier.permissionState() },
                    lastResult = lastPostResult,
                    onRequest = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    onBack = { navController.popBackStack() },
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
                var lookup by remember(id, installedStorage) { mutableStateOf<DetailLookup>(DetailLookup.Loading) }
                LaunchedEffect(id, installedStorage) {
                    lookup = DetailLookup.Resolved(
                        withContext(Dispatchers.IO) { eventReader.readOne(id, Instant.now()) },
                    )
                }
                val detail = (lookup as? DetailLookup.Resolved)?.detail
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
private fun persistedEventLabels(): PersistedEventLabels {
    // stringResource is composable, so the map is written out instead of built in a lambda.
    val stateLabels = mapOf(
        EventState.INITIATED to stringResource(eventStateLabelRes(EventState.INITIATED)),
        EventState.COLLECTING to stringResource(eventStateLabelRes(EventState.COLLECTING)),
        EventState.READY to stringResource(eventStateLabelRes(EventState.READY)),
        EventState.DELIVERING to stringResource(eventStateLabelRes(EventState.DELIVERING)),
        EventState.DELIVERED to stringResource(eventStateLabelRes(EventState.DELIVERED)),
        EventState.DEFERRED to stringResource(eventStateLabelRes(EventState.DEFERRED)),
        EventState.FAILED_FINAL to stringResource(eventStateLabelRes(EventState.FAILED_FINAL)),
        EventState.EXPIRED to stringResource(eventStateLabelRes(EventState.EXPIRED)),
    )
    val ageLabel = ageLabelFormatter()
    return PersistedEventLabels(
        title = stringResource(R.string.event_security_event),
        stateLabels = stateLabels,
        ageLabel = ageLabel,
    )
}

/**
 * Resolves the localized "x ago" wording. The template strings are captured
 * here because [stringResource] is composable, while the returned lambda is
 * called later from a plain projection function.
 */
@Composable
private fun ageLabelFormatter(): (Instant, Instant) -> String {
    val days = stringResource(R.string.event_age_days)
    val hours = stringResource(R.string.event_age_hours)
    val minutes = stringResource(R.string.event_age_minutes)
    val seconds = stringResource(R.string.event_age_seconds)
    return { now, createdAt ->
        val parts = ageParts(now, createdAt)
        when (parts) {
            is AgeParts.Days -> days.format(parts.value)
            is AgeParts.Hours -> hours.format(parts.value)
            is AgeParts.Minutes -> minutes.format(parts.value)
            is AgeParts.Seconds -> seconds.format(parts.value)
        }
    }
}

private const val RECENT_EVENT_LIMIT = 20

/** Settings row that opens notification setup (A-6); it is the only permission request. */
private const val DELIVERY_ROW_ID = "delivery"

/** Distinguishes "still loading" from "no such event", which are both null. */
private sealed interface DetailLookup {
    data object Loading : DetailLookup
    data class Resolved(val detail: EventDetailUiModel?) : DetailLookup
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
