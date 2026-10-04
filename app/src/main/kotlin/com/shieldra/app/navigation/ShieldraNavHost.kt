package com.shieldra.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.shieldra.app.design.components.domain.LoadingStateList
import com.shieldra.app.presentation.auth.OwnerCheck
import com.shieldra.app.presentation.auth.OwnerCheckResult
import com.shieldra.domain.SecurityEventType
import com.shieldra.delivery.AlertKind
import com.shieldra.delivery.AlertRuntime
import com.shieldra.delivery.DeliverySettings
import com.shieldra.delivery.DeliverySettingsStore
import com.shieldra.delivery.EmailChannel
import com.shieldra.delivery.TelegramChannel
import com.shieldra.app.presentation.screen.settings.AlertChannelsScreen
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.shieldra.app.ShieldraApp
import com.shieldra.app.presentation.guard.LockGuardDecision
import com.shieldra.app.presentation.guard.PersistedEventLabels
import com.shieldra.app.presentation.guard.StorageEventReader
import com.shieldra.app.presentation.guard.ageParts
import com.shieldra.app.presentation.guard.ageQuantityRes
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
import com.shieldra.app.presentation.settings.SettingsContent
import com.shieldra.app.presentation.settings.SettingsRowTarget
import com.shieldra.app.presentation.settings.honestSettingsRows
import com.shieldra.app.presentation.screen.settings.SettingsCallbacks
import com.shieldra.app.presentation.screen.settings.SettingsScreen
import com.shieldra.app.presentation.screen.settings.NotificationSetupScreen
import com.shieldra.domain.PanicActivation
import com.shieldra.notification.LocalEventNotifier
import com.shieldra.notification.LocalPostResult
import com.shieldra.runtime.MotionEventWrites
import com.shieldra.runtime.MotionMonitoringStartDecision
import com.shieldra.runtime.MotionRuntime
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
    // Leaving onboarding is a recorded fact, not a navigation detail: without it every cold start
    // after a process kill would put the welcome page in front of a returning owner (Q-K-7, A-27).
    onOnboardingCompleted: () -> Unit = {},
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in BottomTab.entries.map { it.route }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deferredMessage = stringResource(R.string.phase3_deferred)
    val defer = { scope.launch { snackbarHostState.showSnackbar(deferredMessage) } }
    // Motion monitoring is projected from the claim the service publishes about itself, never from
    // the fact that Start was pressed (A-18): while no service has reported a live registration the
    // guard renders as not monitoring.
    val motionClaim by MotionRuntime.state.collectAsState()
    // Honest projection: the guards without a detector are projected from whether the storage gate
    // installed anything, never from fabricated protection numbers.
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
    // UI renders empty until the real rows arrive. The keys are the writes that change what the
    // query can return: a Panic press, and an event row the running monitor just persisted.
    val motionEventWrites by MotionEventWrites.state.collectAsState()
    // One load serves both lists: History shows the whole page, the dashboard the newest few. A page
    // is three queries whatever its size, so History no longer stops at the dashboard's twenty.
    val recentEvents by produceState(initialValue = emptyList<EventUiModel>(), installedStorage, panicActivations, motionEventWrites) {
        value = withContext(Dispatchers.IO) { eventReader.readRecent(HISTORY_EVENT_LIMIT, Instant.now()) }
    }
    var panicInFlight by remember { mutableStateOf(false) }
    val panicRejectedMessage = stringResource(R.string.panic_not_recorded)
    val panicHoldMessage = stringResource(R.string.panic_hold_hint)
    val ownerCheckTitle = stringResource(R.string.owner_check_stop_title)
    val ownerCheckSubtitle = stringResource(R.string.owner_check_stop_subtitle)
    val ownerCheckRefusedMessage = stringResource(R.string.owner_check_refused)
    // The system settings page can change the permission behind the app's back, so the state is
    // re-read whenever the app comes back to the front.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissionRequests += 1 }
    val openNotificationSettings = {
        val intent = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
        runCatching { context.startActivity(intent) }
        Unit
    }
    val monitoringStoppedMessage = stringResource(R.string.motion_monitoring_stopped)
    val monitoringNeedsPermissionMessage = stringResource(R.string.motion_monitoring_needs_permission)
    val monitoringRefusedMessage = stringResource(R.string.motion_monitoring_refused)
    val monitoringNoStorageMessage = stringResource(R.string.motion_monitoring_no_storage)
    var stopWasRequested by remember { mutableStateOf(false) }
    // A-32: the owner's alert channels. Loading opens Keystore-sealed secrets, so it runs off the
    // main thread; null means "not read yet", never "nothing set up".
    val deliveryStore = remember(context) { DeliverySettingsStore(context) }
    var deliverySettings by remember { mutableStateOf<DeliverySettings?>(null) }
    LaunchedEffect(deliveryStore) {
        deliverySettings = withContext(Dispatchers.IO) { deliveryStore.load() }
    }
    val channelsSavedMessage = stringResource(R.string.alert_channels_saved)
    // "Stopped" reports an observed state, not a press: it appears once the service has retracted
    // itself, and never while it still claims to be monitoring.
    LaunchedEffect(motionClaim) {
        if (stopWasRequested && !motionClaim.claimsProtection) {
            stopWasRequested = false
            snackbarHostState.showSnackbar(monitoringStoppedMessage)
        }
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
                        onOnboardingCompleted()
                        navController.navigate(ShieldraRoutes.DASHBOARD) {
                            popUpTo(ShieldraRoutes.ONBOARDING_WELCOME) { inclusive = true }
                        }
                    },
                )
            }
            composable(ShieldraRoutes.ONBOARDING_PERMISSIONS) {
                // Continue opens the notification setup screen, which stays the one place the
                // permission is requested (A-6); this step used to answer Continue with a deferral.
                com.shieldra.app.presentation.screen.onboarding.PermissionsScreen(
                    step = 1,
                    totalSteps = 2,
                    icon = Icons.Filled.Notifications,
                    title = stringResource(R.string.notifications),
                    reason = stringResource(R.string.onboarding_notification_reason),
                    onAllow = {
                        navController.navigate(ShieldraRoutes.ONBOARDING_NOTIFICATIONS)
                    },
                    onNotNow = {
                        navController.navigate(ShieldraRoutes.ONBOARDING_PROTECTION)
                    },
                )
            }
            composable(ShieldraRoutes.ONBOARDING_NOTIFICATIONS) {
                NotificationSetupScreen(
                    permissionState = remember(permissionRequests) { notifier.permissionState() },
                    lastResult = null,
                    onRequest = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    onBack = { navController.navigate(ShieldraRoutes.ONBOARDING_PROTECTION) },
                    onOpenSystemSettings = openNotificationSettings,
                    doneLabel = R.string.action_continue,
                )
            }
            composable(ShieldraRoutes.ONBOARDING_PROTECTION) {
                ProtectionSetupScreen(
                    onContinue = {
                        onOnboardingCompleted()
                        navController.navigate(ShieldraRoutes.DASHBOARD) {
                            popUpTo(ShieldraRoutes.ONBOARDING_WELCOME) { inclusive = true }
                        }
                    },
                )
            }

            // ===== Main =====
            composable(ShieldraRoutes.DASHBOARD) {
                DashboardScreen(
                    model = lockGuardDashboard(
                        state = guardState,
                        recentEvents = recentEvents.take(RECENT_EVENT_LIMIT),
                        motionClaim = motionClaim,
                    ),
                    callbacks = DashboardCallbacks(
                        onEventClick = { id ->
                            navController.navigate(ShieldraRoutes.eventDetail(id))
                        },
                        onGuardClick = { defer() },
                        onPanicTapHint = {
                            scope.launch { snackbarHostState.showSnackbar(panicHoldMessage) }
                        },
                        onPanic = {
                            val coordinator = panicCoordinator
                            if (coordinator == null) {
                                defer()
                            } else if (!panicInFlight) {
                                // One activation at a time: a second hold while the first is still
                                // being stored would only queue a duplicate write behind it.
                                panicInFlight = true
                                scope.launch {
                                    try {
                                        val activation = withContext(Dispatchers.IO) { coordinator.activate() }
                                        // The event is already persisted at this point; a missing
                                        // notification never undoes that (A-6). The post goes to the
                                        // silent channel, so nobody holding the phone is told.
                                        lastPostResult = when (activation) {
                                            is PanicActivation.Recorded -> notifier.postSecurityEvent(
                                                eventId = activation.event.id.value.toString(),
                                                statusLabel = eventLabels.stateLabel(activation.event.state),
                                                title = eventLabels.titleFor(activation.event.type),
                                                silent = true,
                                            )
                                            is PanicActivation.BoundToActive -> notifier.postSecurityEvent(
                                                eventId = activation.event.id.value.toString(),
                                                statusLabel = eventLabels.stateLabel(activation.event.state),
                                                title = eventLabels.titleFor(activation.event.type),
                                                silent = true,
                                            )
                                            is PanicActivation.Rejected -> {
                                                snackbarHostState.showSnackbar(panicRejectedMessage)
                                                LocalPostResult.NotAttempted
                                            }
                                        }
                                        if (activation !is PanicActivation.Rejected) {
                                            panicActivations += 1
                                            // A-32: every press reaches the owner's channels, in the
                                            // background, with nothing shown on this screen.
                                            val pressedEvent = when (activation) {
                                                is PanicActivation.Recorded -> activation.event
                                                is PanicActivation.BoundToActive -> activation.event
                                                is PanicActivation.Rejected -> null
                                            }
                                            pressedEvent?.let {
                                                AlertRuntime.alert(context, AlertKind.PANIC, it.id.value.toString())
                                            }
                                        }
                                    } finally {
                                        panicInFlight = false
                                    }
                                }
                            }
                        },
                        onViewAllEvents = {
                            navController.navigate(ShieldraRoutes.HISTORY)
                        },
                        onMotionMonitoringToggle = {
                            if (motionClaim.claimsProtection) {
                                // Stopping weakens protection, so the owner proves it is them first
                                // (DISABLE_PROTECTION); otherwise one tap by whoever holds the phone
                                // switched monitoring off.
                                val activity = context.findFragmentActivity()
                                if (activity == null) {
                                    stopWasRequested = true
                                    MotionRuntime.stopMonitoring(context)
                                } else {
                                    OwnerCheck.request(activity, ownerCheckTitle, ownerCheckSubtitle) { result ->
                                        when (result) {
                                            OwnerCheckResult.Confirmed, OwnerCheckResult.NoScreenLock -> {
                                                stopWasRequested = true
                                                MotionRuntime.stopMonitoring(context)
                                            }
                                            OwnerCheckResult.Refused -> scope.launch {
                                                snackbarHostState.showSnackbar(ownerCheckRefusedMessage)
                                            }
                                        }
                                    }
                                }
                            } else {
                                // The service publishes its own claim once it is really up, so a start
                                // that this press asked for renders as monitoring only when Android
                                // and the sensor agree.
                                when (MotionRuntime.startMonitoring(context, storageInstalled)) {
                                    MotionMonitoringStartDecision.MayStartMonitoring -> Unit
                                    // Not asked yet: take the owner to the one screen that asks (A-6)
                                    // instead of leaving them with a message and nowhere to go.
                                    MotionMonitoringStartDecision.AskForNotificationPermission -> {
                                        navController.navigate(ShieldraRoutes.NOTIFICATION_SETUP)
                                        scope.launch {
                                            snackbarHostState.showSnackbar(monitoringNeedsPermissionMessage)
                                        }
                                    }

                                    is MotionMonitoringStartDecision.DoNotStartMonitoring ->
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                if (storageInstalled) {
                                                    monitoringRefusedMessage
                                                } else {
                                                    monitoringNoStorageMessage
                                                },
                                            )
                                        }
                                }
                            }
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
            composable(ShieldraRoutes.SETTINGS) {
                SettingsScreen(
                    rows = honestSettingsRows(
                        storageInstalled = storageInstalled,
                        notificationState = remember(permissionRequests) { notifier.permissionState() },
                        alertChannelsActive = deliverySettings?.activeChannels()?.isNotEmpty() == true,
                    ),
                    callbacks = SettingsCallbacks { id, _ ->
                        when (SettingsContent.targetOf(id)) {
                            // The A-6 setup screen.
                            SettingsRowTarget.NotificationSetup ->
                                navController.navigate(ShieldraRoutes.NOTIFICATION_SETUP)

                            // A-32: where alerts leave the phone.
                            SettingsRowTarget.AlertChannels ->
                                navController.navigate(ShieldraRoutes.ALERT_CHANNELS)

                            SettingsRowTarget.NotAvailable -> defer()
                        }
                    },
                )
            }

            composable(ShieldraRoutes.ALERT_CHANNELS) {
                val saved = deliverySettings
                if (saved == null) {
                    LoadingStateList(rows = 3)
                } else {
                    AlertChannelsScreen(
                        saved = saved,
                        onSave = { updated ->
                            scope.launch {
                                deliverySettings = withContext(Dispatchers.IO) {
                                    deliveryStore.save(updated)
                                    deliveryStore.load()
                                }
                                navController.popBackStack()
                                snackbarHostState.showSnackbar(channelsSavedMessage)
                            }
                        },
                        onTest = { candidate, telegram ->
                            withContext(Dispatchers.IO) {
                                val channel = if (telegram) {
                                    TelegramChannel(candidate.telegram)
                                } else {
                                    EmailChannel(candidate.email)
                                }
                                AlertRuntime.dispatcher(context).sendNow(
                                    channel,
                                    AlertRuntime.snapshot(context, AlertKind.TEST, eventId = null),
                                )
                            }
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
            }

            // Notification setup (A-6): the only permission request in the app.
            composable(ShieldraRoutes.NOTIFICATION_SETUP) {
                NotificationSetupScreen(
                    permissionState = remember(permissionRequests) { notifier.permissionState() },
                    lastResult = lastPostResult,
                    onRequest = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    onBack = { navController.popBackStack() },
                    onOpenSystemSettings = openNotificationSettings,
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
                if (lookup == DetailLookup.Loading) {
                    // Still reading: "Event unavailable" used to flash here before every detail.
                    LoadingStateList(rows = 3)
                } else if (detail == null) {
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
    val typeTitles = mapOf(
        SecurityEventType.MOTION_CONFIRMED_EVENT to stringResource(R.string.event_title_motion),
        SecurityEventType.PANIC_ACTIVATION to stringResource(R.string.event_title_panic),
    )
    return PersistedEventLabels(
        title = stringResource(R.string.event_security_event),
        stateLabels = stateLabels,
        typeTitles = typeTitles,
        ageLabel = ageLabel,
    )
}

/** BiometricPrompt needs the hosting FragmentActivity; the composition only holds a Context. */
private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

/**
 * Resolves the localized "x ago" wording. The Android resources handle is captured
 * here because [stringResource] is composable while the returned lambda is called
 * later from a plain projection function; the quantity itself has to reach
 * `getQuantityString`, because a fixed-form template was what made this read
 * "1 hours ago" (F-K11).
 */
@Composable
private fun ageLabelFormatter(): (Instant, Instant) -> String {
    val resources = LocalContext.current.resources
    return { now, createdAt ->
        val parts = ageParts(now, createdAt)
        val count = parts.value.toInt()
        resources.getQuantityString(ageQuantityRes(parts), count, count)
    }
}

private const val RECENT_EVENT_LIMIT = 20
private const val HISTORY_EVENT_LIMIT = 200

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
        BottomTab.entries.forEach { tab ->
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
    BottomTab.Settings -> Icons.Filled.Settings
}

private fun BottomTab.labelRes(): Int = when (this) {
    BottomTab.Dashboard -> R.string.tab_dashboard
    BottomTab.History -> R.string.tab_history
    BottomTab.Settings -> R.string.tab_settings
}
