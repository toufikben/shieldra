package com.shieldra.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
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
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.shieldra.storage.FactoryResetResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.shieldra.app.presentation.settings.ChoiceDialog
import com.shieldra.app.settings.AppLanguage
import com.shieldra.app.settings.AppLockSession
import com.shieldra.app.settings.AppSettings
import com.shieldra.app.settings.ThemeMode
import com.shieldra.app.presentation.model.NetworkStatus
import kotlinx.coroutines.delay
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
import com.shieldra.app.presentation.screen.pro.ProScreen
import com.shieldra.app.presentation.screen.eventdetail.EventDetailCallbacks
import com.shieldra.app.presentation.screen.eventdetail.EventDetailScreen
import com.shieldra.app.presentation.screen.history.HistoryCallbacks
import com.shieldra.app.presentation.screen.history.HistoryScreen
import com.shieldra.app.presentation.screen.map.MapScreen
import com.shieldra.app.presentation.screen.health.HealthCheckScreen
import com.shieldra.app.presentation.screen.health.HealthChecks
import com.shieldra.app.presentation.guard.ProtectionScore
import com.shieldra.app.presentation.screen.onboarding.ProtectionSetupScreen
import com.shieldra.app.presentation.screen.onboarding.WelcomeScreen
import com.shieldra.app.presentation.settings.SettingsContent
import com.shieldra.app.presentation.settings.SettingsRowTarget
import com.shieldra.app.presentation.settings.honestSettingsRows
import com.shieldra.app.presentation.screen.settings.SettingsCallbacks
import com.shieldra.app.presentation.screen.settings.SettingsScreen
import com.shieldra.app.presentation.screen.settings.NotificationSetupScreen
import com.shieldra.app.presentation.screen.settings.AboutScreen
import com.shieldra.app.presentation.screen.settings.HelpScreen
import com.shieldra.app.presentation.screen.settings.PrivacyScreen
import com.shieldra.app.presentation.screen.settings.PanicGuideScreen
import com.shieldra.app.presentation.screen.settings.BatterySurvivalScreen
import com.shieldra.app.presentation.screen.settings.PlatformComparisonScreen
import com.shieldra.app.presentation.screen.settings.RetentionScreen
import com.shieldra.app.presentation.screen.settings.StorageScreen
import com.shieldra.storage.LocalShieldraStorage
import com.shieldra.app.presentation.screen.settings.ProtectionSetupScreen as ProtectionGuardsScreen
import com.shieldra.app.presentation.screen.settings.ProtectionPermissions
import com.shieldra.protection.ProtectionRuntime
import com.shieldra.protection.ProtectionChange
import com.shieldra.protection.ProtectionSettings
import com.shieldra.protection.ProtectionSettingsStore
import com.shieldra.protection.LockGuardAdmin
import com.shieldra.protection.CountdownDeadline
import com.shieldra.protection.CountdownScheduler
import com.shieldra.protection.RemainingTime
import com.shieldra.billing.TierGate
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
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
import com.shieldra.app.presentation.model.HistoryStatusFilter
import com.shieldra.app.presentation.model.HistoryTypeFilter
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
    // §5.5 / A-37: the one real evidence exporter. Every export is owner-confirmed, passphrase-sealed
    // and audited; the flow itself names the policy (see presentation/export).
    val exportAudit = remember(context) {
        com.shieldra.storage.ExportAuditLog(java.io.File(context.filesDir, "export_audit.log"))
    }
    val exportFlow = com.shieldra.app.presentation.export.rememberEvidenceExportFlow(
        readEvidence = { id -> runCatching { installedStorage?.evidenceFiles?.read(id) }.getOrNull() },
        auditLog = exportAudit,
        activity = context.findFragmentActivity(),
        onMessage = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
    )
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
    val ownerUnguardedMessage = stringResource(R.string.owner_check_unguarded)
    // Anything that weakens protection or redirects alerts proves it is the owner first (screen lock).
    val ownerChangeSubtitle = stringResource(R.string.owner_check_change_subtitle)
    val requireOwner: (() -> Unit) -> Unit = { action ->
        val activity = context.findFragmentActivity()
        if (activity == null) {
            // No prompt can be built without an activity to attach it to. The change still goes
            // ahead — a guard the owner cannot turn off is not a guard — but it is never silent.
            action()
            scope.launch { snackbarHostState.showSnackbar(ownerUnguardedMessage) }
        } else {
            OwnerCheck.request(activity, ownerCheckTitle, ownerChangeSubtitle) { result ->
                when (result) {
                    OwnerCheckResult.Confirmed -> action()
                    // B-1: the pass-through is allowed, the silence is not. The owner is told at the
                    // moment of the change, and permanently on the Health screen.
                    OwnerCheckResult.NoScreenLock -> {
                        action()
                        scope.launch { snackbarHostState.showSnackbar(ownerUnguardedMessage) }
                    }
                    OwnerCheckResult.Refused -> scope.launch { snackbarHostState.showSnackbar(ownerCheckRefusedMessage) }
                }
            }
        }
    }
    // The system settings page can change the permission behind the app's back, so the state is
    // re-read whenever the app comes back to the front.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissionRequests += 1 }
    val openNotificationSettings = {
        val intent = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
        runCatching { context.startActivity(intent) }
        Unit
    }
    // The owner's own choices (theme, language, app lock) and the dialog that edits them.
    val themeMode by AppSettings.theme.collectAsState()
    var language by remember { mutableStateOf(AppSettings.language(context)) }
    var appLockOn by remember { mutableStateOf(AppSettings.appLockEnabled(context)) }
    var settingsDialog by remember { mutableStateOf<SettingsDialog?>(null) }
    when (settingsDialog) {
        SettingsDialog.Theme -> ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = listOf(
                ThemeMode.SYSTEM to stringResource(R.string.settings_match_system),
                ThemeMode.LIGHT to stringResource(R.string.theme_light),
                ThemeMode.DARK to stringResource(R.string.theme_dark),
            ),
            selected = themeMode,
            onSelect = {
                AppSettings.setTheme(context, it)
                settingsDialog = null
            },
            onDismiss = { settingsDialog = null },
        )
        SettingsDialog.Language -> ChoiceDialog(
            title = stringResource(R.string.settings_language),
            options = listOf(
                AppLanguage.SYSTEM to stringResource(R.string.settings_match_system),
                AppLanguage.ENGLISH to stringResource(R.string.language_english),
                AppLanguage.ARABIC to stringResource(R.string.language_arabic),
            ),
            selected = language,
            onSelect = {
                AppSettings.setLanguage(context, it)
                language = it
                settingsDialog = null
                // The activity's resources are rebuilt in the chosen language.
                context.findFragmentActivity()?.recreate()
            },
            onDismiss = { settingsDialog = null },
        )
        SettingsDialog.AppLock -> {
            val authTitle = stringResource(R.string.app_lock_prompt_title)
            val authSubtitle = stringResource(R.string.app_lock_prompt_subtitle)
            val noScreenLock = stringResource(R.string.app_lock_needs_screen_lock)
            val refused = stringResource(R.string.app_lock_refused)
            ChoiceDialog(
                title = stringResource(R.string.settings_app_lock),
                options = listOf(
                    true to stringResource(R.string.app_lock_option_on),
                    false to stringResource(R.string.app_lock_option_off),
                ),
                selected = appLockOn,
                note = stringResource(R.string.settings_app_lock_scope),
                onSelect = { enable ->
                    settingsDialog = null
                    if (enable != appLockOn) {
                        val activity = context.findFragmentActivity()
                        // Changing the lock needs the owner's own screen lock first, in both directions.
                        if (activity != null) {
                            OwnerCheck.request(activity, authTitle, authSubtitle) { result ->
                                when (result) {
                                    OwnerCheckResult.Confirmed -> {
                                        AppSettings.setAppLockEnabled(context, enable)
                                        appLockOn = enable
                                        // Turning it on here must not lock the owner out of this very session.
                                        if (enable) AppLockSession.unlocked = true
                                    }
                                    // No screen lock exists: turning the lock off is fine, on has nothing to ask.
                                    OwnerCheckResult.NoScreenLock ->
                                        if (enable) {
                                            scope.launch { snackbarHostState.showSnackbar(noScreenLock) }
                                        } else {
                                            AppSettings.setAppLockEnabled(context, false)
                                            appLockOn = false
                                        }
                                    OwnerCheckResult.Refused ->
                                        scope.launch { snackbarHostState.showSnackbar(refused) }
                                }
                            }
                        }
                    }
                },
                onDismiss = { settingsDialog = null },
            )
        }
        SettingsDialog.Erase -> {
            val authTitle = stringResource(R.string.erase_prompt_title)
            val authSubtitle = stringResource(R.string.erase_prompt_subtitle)
            val noScreenLock = stringResource(R.string.erase_needs_screen_lock)
            val refused = stringResource(R.string.erase_refused)
            val failed = stringResource(R.string.erase_failed)
            AlertDialog(
                onDismissRequest = { settingsDialog = null },
                title = { Text(stringResource(R.string.erase_title)) },
                text = { Text(stringResource(R.string.erase_body)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            settingsDialog = null
                            val activity = context.findFragmentActivity() ?: return@TextButton
                            OwnerCheck.request(activity, authTitle, authSubtitle) { result ->
                                when (result) {
                                    OwnerCheckResult.Confirmed -> scope.launch {
                                        ProtectionRuntime.disarm(context)
                                        val outcome = withContext(Dispatchers.IO) {
                                            LocalShieldraStorage.eraseAll(context, installedStorage)
                                        }
                                        if (outcome is FactoryResetResult.Completed) {
                                            // The database is closed and the keys are gone: start clean.
                                            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
                                            if (launch != null) context.startActivity(android.content.Intent.makeRestartActivityTask(launch.component))
                                            android.os.Process.killProcess(android.os.Process.myPid())
                                        } else {
                                            snackbarHostState.showSnackbar(failed)
                                        }
                                    }
                                    OwnerCheckResult.NoScreenLock ->
                                        scope.launch { snackbarHostState.showSnackbar(noScreenLock) }
                                    OwnerCheckResult.Refused ->
                                        scope.launch { snackbarHostState.showSnackbar(refused) }
                                }
                            }
                        },
                    ) { Text(stringResource(R.string.erase_confirm), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { settingsDialog = null }) { Text(stringResource(R.string.erase_cancel)) }
                },
            )
        }
        null -> Unit
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
    // A-33/A-34: the protection guards. Armed state is read from the stored flag the service and
    // boot recovery share; the settings draft and the live permission states drive the setup screen.
    val protectionArmed by ProtectionRuntime.armed.collectAsState()
    val protectionStore = remember(context) { ProtectionSettingsStore(context) }
    var protectionSettings by remember { mutableStateOf(protectionStore.load()) }
    // A-44: the live arm state is the only writer of `armed`. The settings screen's snapshot was read
    // before an arm made from its own top button, so without this a Save could carry `armed=false`
    // back to storage and the service would stand down while the screen still said "Armed".
    LaunchedEffect(protectionArmed) {
        protectionSettings = protectionSettings.copy(armed = protectionArmed)
    }
    // The plan is written by the billing store when Google Play answers (after a purchase, a restore or
    // an app start). This copy of the settings was read once, so it would keep showing Free after a
    // purchase and send the owner back to the Pro screen; re-read the plan whenever Play's answer changes.
    val playProState by (context.applicationContext as ShieldraApp).proStore.state.collectAsState()
    LaunchedEffect(playProState.entitled) {
        protectionSettings = protectionSettings.copy(tier = protectionStore.load().tier)
    }
    var permissionRefresh by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { ProtectionRuntime.refresh(context) }
    val protectionPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissionRefresh += 1 }
    // The Lock Guard hears a wrong unlock attempt only while this app holds the device-admin role.
    // Android answers through its own dialog, so the grant is re-read on return, never assumed.
    val deviceAdminLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { permissionRefresh += 1 }
    val deviceAdminComponent = remember(context) { LockGuardAdmin.component(context) }
    val deviceAdminGranted = remember(permissionRefresh, permissionRequests) {
        LockGuardAdmin.isGranted(context)
    }
    val enableDeviceAdmin = {
        val intent = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdminComponent)
            .putExtra(
                android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                context.getString(R.string.device_admin_description),
            )
        runCatching { deviceAdminLauncher.launch(intent) }
        Unit
    }
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
            // The default is a 700 ms crossfade, which reads as lag on every tab change.
            enterTransition = { fadeIn(tween(140)) },
            exitTransition = { fadeOut(tween(90)) },
            popEnterTransition = { fadeIn(tween(140)) },
            popExitTransition = { fadeOut(tween(90)) },
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
                    onOpenSurvivalGuide = { navController.navigate(ShieldraRoutes.SURVIVAL_GUIDE) },
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
                // A-38: the phone's own readings, refreshed while the dashboard is on screen.
                val device by produceState(
                    initialValue = com.shieldra.protection.DeviceStatus.snapshot(context),
                    protectionArmed,
                ) {
                    while (true) {
                        value = withContext(Dispatchers.IO) { com.shieldra.protection.DeviceStatus.snapshot(context) }
                        delay(DEVICE_REFRESH_MS)
                    }
                }
                val wifiLabel = stringResource(R.string.status_network_wifi)
                val cellularLabel = stringResource(R.string.status_network_cellular)
                val otherLabel = stringResource(R.string.status_network_other)
                val (networkStatus, networkLabel) = when (device.link) {
                    com.shieldra.protection.LinkKind.WIFI -> NetworkStatus.Online to wifiLabel
                    com.shieldra.protection.LinkKind.CELLULAR -> NetworkStatus.Online to cellularLabel
                    com.shieldra.protection.LinkKind.OTHER -> NetworkStatus.Online to otherLabel
                    com.shieldra.protection.LinkKind.OFFLINE -> NetworkStatus.Offline to null
                    com.shieldra.protection.LinkKind.UNKNOWN -> NetworkStatus.Unknown to null
                }
                val proState by (context.applicationContext as ShieldraApp).proStore.state.collectAsState()
                val healthChecks = readHealthChecks(
                    context = context,
                    notifier = notifier,
                    armed = protectionArmed,
                    channel = deliverySettings?.activeChannels()?.isNotEmpty() == true,
                    motionMonitoring = motionClaim.claimsProtection,
                    deviceAdmin = deviceAdminGranted,
                )
                // A-33: the periodic signal's live cycle, read from the deadline the scheduler itself
                // stores. It is re-read while this screen is open because the scheduler re-arms a cycle
                // when one fires, and a card that only ever showed the deadline read at screen entry
                // would freeze a running countdown into an expired one. A press re-reads it at once.
                var periodicSignalRefresh by remember { mutableStateOf(0) }
                val periodicSignal by produceState(
                    initialValue = protectionStore.countdownDeadline(),
                    protectionArmed,
                    protectionSettings.countdownEnabled,
                    periodicSignalRefresh,
                ) {
                    while (true) {
                        value = withContext(Dispatchers.IO) { protectionStore.countdownDeadline() }
                        delay(DEVICE_REFRESH_MS)
                    }
                }
                // The card may only promise what a restart can actually deliver: CountdownScheduler.start
                // refuses silently for a switched-off signal or a Free plan, so neither shows the card.
                val periodicSignalCycle = periodicSignal?.takeIf {
                    protectionSettings.countdownEnabled && TierGate.allowsCountdown(protectionSettings.tier)
                }
                val periodicSignalResetMessage = stringResource(
                    R.string.countdown_warning_text,
                    RemainingTime.format(
                        protectionSettings.countdownIntervalMs.coerceAtLeast(CountdownDeadline.MIN_DURATION_MS),
                        stringResource(R.string.unit_day),
                        stringResource(R.string.unit_hour),
                        stringResource(R.string.unit_minute),
                        stringResource(R.string.unit_less_than_minute),
                    ),
                )
                DashboardScreen(
                    model = lockGuardDashboard(
                        state = guardState,
                        recentEvents = recentEvents.take(RECENT_EVENT_LIMIT),
                        motionClaim = motionClaim,
                        protectionArmed = protectionArmed,
                        simOn = protectionSettings.simEnabled,
                        batteryOn = protectionSettings.batteryEnabled,
                        lockOn = protectionSettings.lockEnabled,
                        deviceAdminGranted = deviceAdminGranted,
                        batteryPercent = device.batteryPercent,
                        networkStatus = networkStatus,
                        networkLabel = networkLabel,
                        measuredLastCheckSeconds = device.lastCheckSeconds,
                        protectionScore = scoreFor(healthChecks, protectionSettings),
                        proActive = proState.entitled,
                        totalEvents = recentEvents.size,
                        periodicSignal = periodicSignalCycle,
                        periodicSignalIntervalMs = protectionSettings.countdownIntervalMs,
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
                        onOpenPro = {
                            navController.navigate(ShieldraRoutes.PRO)
                        },
                        onPeriodicSignalReset = {
                            // A restart moves the next signal later, which is a weakening of the same
                            // kind as stopping a guard — so this screen's existing rule applies: the
                            // owner proves it is them first, and a refusal says so instead of failing
                            // silent. The card itself never starts a cycle; it re-arms the armed one.
                            val restartCycle = {
                                CountdownScheduler.restart(context)
                                periodicSignalRefresh += 1
                                scope.launch { snackbarHostState.showSnackbar(periodicSignalResetMessage) }
                            }
                            val activity = context.findFragmentActivity()
                            if (activity == null) {
                                restartCycle()
                            } else {
                                OwnerCheck.request(activity, ownerCheckTitle, ownerCheckSubtitle) { result ->
                                    when (result) {
                                        OwnerCheckResult.Confirmed, OwnerCheckResult.NoScreenLock -> restartCycle()
                                        OwnerCheckResult.Refused -> scope.launch {
                                            snackbarHostState.showSnackbar(ownerCheckRefusedMessage)
                                        }
                                    }
                                }
                            }
                        },
                        onMotionMonitoringToggle = {
                            if (motionClaim.claimsProtection) {
                                // Stopping weakens protection, so the owner proves it is them first
                                // (DISABLE_PROTECTION); otherwise one tap by whoever holds the phone
                                // switched monitoring off.
                                val activity = context.findFragmentActivity()
                                if (activity == null) {
                                    // A-43: the dashboard and the settings screen show the same
                                    // guard, so a live stop is also the stored choice.
                                    ProtectionRuntime.setMotionEnabled(context, false)
                                    protectionSettings = protectionSettings.copy(motionEnabled = false)
                                    stopWasRequested = true
                                    MotionRuntime.stopMonitoring(context)
                                } else {
                                    OwnerCheck.request(activity, ownerCheckTitle, ownerCheckSubtitle) { result ->
                                        when (result) {
                                            OwnerCheckResult.Confirmed, OwnerCheckResult.NoScreenLock -> {
                                                ProtectionRuntime.setMotionEnabled(context, false)
                                                protectionSettings = protectionSettings.copy(motionEnabled = false)
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
                                // and the sensor agree. A-43: this is the same guard the settings
                                // screen shows, so the stored choice follows the press.
                                ProtectionRuntime.setMotionEnabled(context, true)
                                protectionSettings = protectionSettings.copy(motionEnabled = true)
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
            composable(ShieldraRoutes.MAP) {
                val locationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                var geofenceRefresh by remember { mutableStateOf(0) }
                // G-11: the screen gets the places themselves, not name-keyed maps, so two rows can
                // never share an identity and one delete removes exactly one place.
                val zoneList = remember(geofenceRefresh) { ProtectionRuntime.zones(context) }
                val geofenceOn = remember(geofenceRefresh) { ProtectionRuntime.geofenceEnabled(context) }
                val zoneReadingFresh = remember(geofenceRefresh, protectionArmed) { ProtectionRuntime.zoneReadingFresh(context) }
                val safeZoneSaved = stringResource(R.string.safe_zones_added)
                val safeZoneNoFix = stringResource(R.string.safe_zones_no_fix)
                MapScreen(
                    armed = protectionArmed,
                    liveTrackingEnabled = protectionSettings.liveTrackingEnabled,
                    intervalMinutes = protectionSettings.trackingIntervalMinutes,
                    durationMinutes = protectionSettings.trackingDurationMinutes,
                    locationGranted = locationGranted,
                    lastLocatedEvent = recentEvents.firstOrNull { it.hasLocation },
                    onOpenEvent = { id -> navController.navigate(ShieldraRoutes.eventDetail(id)) },
                    onOpenProtectionSetup = { navController.navigate(ShieldraRoutes.PROTECTION_SETUP) },
                    onOpenHealthCheck = { navController.navigate(ShieldraRoutes.HEALTH_CHECK) },
                    geofenceEnabled = geofenceOn,
                    zoneReadingFresh = zoneReadingFresh,
                    zones = zoneList,
                    onToggleGeofence = { enabled ->
                        val apply: () -> Unit = {
                            ProtectionRuntime.setGeofenceEnabled(context, enabled)
                            geofenceRefresh++
                        }
                        if (enabled) apply() else requireOwner(apply)
                    },
                    onAddZone = { radius ->
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                ProtectionRuntime.addCurrentLocationAsZone(context, "", radius.toDouble())
                            }
                            geofenceRefresh++
                            snackbarHostState.showSnackbar(if (ok) safeZoneSaved else safeZoneNoFix)
                        }
                    },
                    onRemoveZone = { zone ->
                        requireOwner {
                            ProtectionRuntime.removeZone(context, zone)
                            geofenceRefresh++
                        }
                    },
                )
            }
            composable(ShieldraRoutes.PRO) {
                val proStore = (context.applicationContext as ShieldraApp).proStore
                val proState by proStore.state.collectAsState()
                // Re-ask Google Play whenever the screen opens, so a purchase made elsewhere shows up.
                LaunchedEffect(Unit) { proStore.refresh() }
                ProScreen(
                    state = proState,
                    onBack = { navController.popBackStack() },
                    onPurchase = { basePlanId ->
                        context.findFragmentActivity()?.let { proStore.purchase(it, basePlanId) }
                    },
                    onRefresh = proStore::refresh,
                    onManage = {
                        val uri = android.net.Uri.parse(
                            "https://play.google.com/store/account/subscriptions?sku=${com.shieldra.billing.ProCatalog.PRODUCT_ID}&package=${context.packageName}",
                        )
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                        Unit
                    },
                )
            }

            composable(ShieldraRoutes.HEALTH_CHECK) {
                val checks = readHealthChecks(
                    context = context,
                    notifier = notifier,
                    armed = protectionArmed,
                    channel = deliverySettings?.activeChannels()?.isNotEmpty() == true,
                    motionMonitoring = motionClaim.claimsProtection,
                    deviceAdmin = deviceAdminGranted,
                )
                HealthCheckScreen(
                    score = scoreFor(checks, protectionSettings),
                    checks = checks,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ShieldraRoutes.HISTORY) {
                // G-8/G-12: the filter used to live in a plain `remember` inside this destination,
                // so opening an event and coming back reset it to "All". A saveable value survives
                // that trip and the language switch that recreates the activity.
                var historyType by rememberSaveable { mutableStateOf(HistoryTypeFilter.All) }
                var historyStatus by rememberSaveable { mutableStateOf(HistoryStatusFilter.All) }
                HistoryScreen(
                    events = recentEvents,
                    filterState = HistoryFilterState(type = historyType, status = historyStatus),
                    callbacks = HistoryCallbacks(
                        onEventClick = { id ->
                            navController.navigate(ShieldraRoutes.eventDetail(id))
                        },
                        onFilterChange = { change ->
                            historyType = change.type
                            historyStatus = change.status
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
                        protectionArmed = protectionArmed,
                        themeMode = themeMode,
                        language = language,
                        appLockEnabled = appLockOn,
                    ),
                    callbacks = SettingsCallbacks { id, _ ->
                        when (SettingsContent.targetOf(id)) {
                            // The A-6 setup screen (first-run only now; Settings merges it into alerts).
                            SettingsRowTarget.NotificationSetup ->
                                navController.navigate(ShieldraRoutes.NOTIFICATION_SETUP)

                            // A-32: where alerts leave the phone (with the notification permission).
                            SettingsRowTarget.AlertChannels ->
                                navController.navigate(ShieldraRoutes.ALERT_CHANNELS)

                            // A-33/A-34: arm the guards and tune them.
                            SettingsRowTarget.ProtectionSetup ->
                                navController.navigate(ShieldraRoutes.PROTECTION_SETUP)

                            SettingsRowTarget.Health -> navController.navigate(ShieldraRoutes.HEALTH_CHECK)

                            // Safe zones live on the Map tab: go there like any bottom-bar destination.
                            SettingsRowTarget.SafeZones -> navController.navigate(ShieldraRoutes.MAP) {
                                popUpTo(ShieldraRoutes.DASHBOARD) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }

                            SettingsRowTarget.Theme -> settingsDialog = SettingsDialog.Theme
                            SettingsRowTarget.Language -> settingsDialog = SettingsDialog.Language

                            SettingsRowTarget.Storage -> navController.navigate(ShieldraRoutes.STORAGE_INFO)
                            SettingsRowTarget.Retention -> navController.navigate(ShieldraRoutes.RETENTION_INFO)
                            SettingsRowTarget.EraseData -> settingsDialog = SettingsDialog.Erase
                            SettingsRowTarget.About -> navController.navigate(ShieldraRoutes.ABOUT_INFO)
                            SettingsRowTarget.Help -> navController.navigate(ShieldraRoutes.HELP_INFO)
                            SettingsRowTarget.Privacy -> navController.navigate(ShieldraRoutes.PRIVACY_INFO)

                            // The stolen-phone steps: a screen of instructions, not a switch.
                            SettingsRowTarget.PanicGuide -> navController.navigate(ShieldraRoutes.PANIC_GUIDE)

                            // What Android already does against theft, named plainly (audit development د).
                            SettingsRowTarget.PlatformComparison -> navController.navigate(ShieldraRoutes.PLATFORM_COMPARISON)

                            SettingsRowTarget.AppLock -> settingsDialog = SettingsDialog.AppLock

                            SettingsRowTarget.NotAvailable -> defer()
                        }
                    },
                )
            }

            composable(ShieldraRoutes.STORAGE_INFO) {
                val figures by produceState<LocalShieldraStorage.Figures?>(initialValue = null, installedStorage) {
                    value = withContext(Dispatchers.IO) { runCatching { installedStorage?.figures() }.getOrNull() }
                }
                StorageScreen(
                    figures = figures,
                    storageInstalled = storageInstalled,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ShieldraRoutes.RETENTION_INFO) {
                var report by remember { mutableStateOf(installedStorage?.lastRetentionReport) }
                var running by remember { mutableStateOf(false) }
                RetentionScreen(
                    storageInstalled = storageInstalled,
                    lastReport = report,
                    running = running,
                    onRunNow = {
                        scope.launch {
                            running = true
                            val fresh = withContext(Dispatchers.IO) {
                                runCatching { installedStorage?.sweepExpiredEvidence() }.getOrNull()
                            }
                            if (fresh != null) report = fresh
                            running = false
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ShieldraRoutes.ABOUT_INFO) { AboutScreen(onBack = { navController.popBackStack() }) }
            composable(ShieldraRoutes.HELP_INFO) { HelpScreen(onBack = { navController.popBackStack() }) }
            composable(ShieldraRoutes.PRIVACY_INFO) { PrivacyScreen(onBack = { navController.popBackStack() }) }
            composable(ShieldraRoutes.PANIC_GUIDE) { PanicGuideScreen(onBack = { navController.popBackStack() }) }
            composable(ShieldraRoutes.SURVIVAL_GUIDE) {
                BatterySurvivalScreen(onBack = { navController.popBackStack() })
            }
            composable(ShieldraRoutes.PLATFORM_COMPARISON) {
                PlatformComparisonScreen(onBack = { navController.popBackStack() })
            }

            composable(ShieldraRoutes.ALERT_CHANNELS) {
                val saved = deliverySettings
                if (saved == null) {
                    LoadingStateList(rows = 3)
                } else {
                    AlertChannelsScreen(
                        saved = saved,
                        telegramNeedsPro = !com.shieldra.billing.TierGate.allowsTelegram(protectionSettings.tier),
                        onOpenPro = { navController.navigate(ShieldraRoutes.PRO) },
                        onSave = { updated ->
                            requireOwner {
                                scope.launch {
                                    deliverySettings = withContext(Dispatchers.IO) {
                                        deliveryStore.save(updated)
                                        deliveryStore.load()
                                    }
                                    navController.popBackStack()
                                    snackbarHostState.showSnackbar(channelsSavedMessage)
                                }
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
                        notificationState = remember(permissionRequests) { notifier.permissionState() },
                        onRequestNotifications = {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                        onOpenNotificationSettings = openNotificationSettings,
                    )
                }
            }

            // Protection setup (A-33/A-34): arm the guards, grant their permissions, tune them.
            composable(ShieldraRoutes.PROTECTION_SETUP) {
                val permissions = remember(permissionRefresh, permissionRequests) {
                    fun has(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
                    ProtectionPermissions(
                        camera = has(Manifest.permission.CAMERA),
                        location = has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION),
                        phone = has(Manifest.permission.READ_PHONE_STATE),
                        notifications = notifier.permissionState().let {
                            it == com.shieldra.notification.NotificationPermissionState.Granted ||
                                it == com.shieldra.notification.NotificationPermissionState.NotRequired
                        },
                        deviceAdmin = deviceAdminGranted,
                        batteryExemption = com.shieldra.protection.BackgroundSurvival.isExempt(context),
                        exactAlarms = com.shieldra.protection.ExactAlarmAccess.isGranted(context),
                    )
                }
                ProtectionGuardsScreen(
                    armed = protectionArmed,
                    planSwitchAllowed = !com.shieldra.billing.InstallSource.isGooglePlay(context),
                    onOpenPro = { navController.navigate(ShieldraRoutes.PRO) },
                    onTierChange = { tier ->
                        ProtectionSettingsStore(context).setTier(tier)
                        protectionSettings = protectionSettings.copy(tier = tier)
                    },
                    permissions = permissions,
                    settings = protectionSettings,
                    motionClaim = motionClaim,
                    onStartMotion = {
                        // The owner asked for this start from the guard's own screen, so the stored
                        // choice follows the press (A-43), exactly as the dashboard's Start does.
                        ProtectionRuntime.setMotionEnabled(context, true)
                        protectionSettings = protectionSettings.copy(motionEnabled = true)
                        when (MotionRuntime.startMonitoring(context, storageInstalled)) {
                            MotionMonitoringStartDecision.MayStartMonitoring -> Unit
                            MotionMonitoringStartDecision.AskForNotificationPermission ->
                                scope.launch { snackbarHostState.showSnackbar(monitoringNeedsPermissionMessage) }
                            is MotionMonitoringStartDecision.DoNotStartMonitoring ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        if (storageInstalled) monitoringRefusedMessage else monitoringNoStorageMessage,
                                    )
                                }
                        }
                    },
                    onGrantCamera = { protectionPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA)) },
                    onGrantLocation = {
                        protectionPermissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                        )
                    },
                    onGrantPhone = { protectionPermissionLauncher.launch(arrayOf(Manifest.permission.READ_PHONE_STATE)) },
                    onGrantNotifications = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    onEnableDeviceAdmin = enableDeviceAdmin,
                    onKeepAwake = {
                        // The protection package owns the platform API; the UI only asks it to open
                        // the system prompt so the guards survive battery saver.
                        com.shieldra.protection.BackgroundSurvival.requestExemption(context)
                        permissionRefresh += 1
                    },
                    onGrantExactAlarms = {
                        // Same split for exact scheduling: the countdown degrades to an inexact
                        // alarm until the owner flips this system toggle.
                        com.shieldra.protection.ExactAlarmAccess.request(context)
                        permissionRefresh += 1
                    },
                    onOpenSurvivalGuide = { navController.navigate(ShieldraRoutes.SURVIVAL_GUIDE) },
                    onArmToggle = { visible ->
                        if (protectionArmed) {
                            requireOwner { ProtectionRuntime.disarm(context) }
                        } else {
                            // A-43: arming starts the Motion guard too, from inside the runtime, so
                            // every arming path (this screen, the dashboard of a running app) agrees.
                            // A refusal is stated here, never left in the log: the owner must not
                            // believe a guard is watching when the platform kept it down.
                            // The screen's draft travels with Arm, so a guard switched on here — the
                            // Motion toggle above all — is stored before the guards start; pressing
                            // Save first is not a second, undocumented requirement (owner report
                            // 2026-11-08).
                            when (ProtectionRuntime.arm(context, visible)) {
                                MotionMonitoringStartDecision.AskForNotificationPermission ->
                                    scope.launch { snackbarHostState.showSnackbar(monitoringNeedsPermissionMessage) }
                                is MotionMonitoringStartDecision.DoNotStartMonitoring ->
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            if (storageInstalled) monitoringRefusedMessage else monitoringNoStorageMessage,
                                        )
                                    }
                                else -> Unit
                            }
                            // The screen's own snapshot follows the saved choices at once; the armed
                            // flag named here is the live one, not the draft this screen opened with.
                            protectionSettings = visible.copy(armed = true)
                        }
                    },
                    onSave = { incoming ->
                        // A-44: saving guards never decides whether protection runs. The screen's
                        // draft predates an arm made from its own button, so its `armed` is replaced
                        // by the live state before anything is stored or a Motion guard is started.
                        val updated = incoming.copy(armed = protectionArmed)
                        val apply: () -> Unit = {
                            protectionSettings = updated
                            ProtectionRuntime.applySettings(context, updated)
                            // A-43: a guard switched on while armed starts now, and one switched off
                            // stops now; the saved flag alone would only take effect on the next arm.
                            if (updated.armed) {
                                if (!updated.motionEnabled) {
                                    MotionRuntime.stopMonitoring(context)
                                } else if (!motionClaim.claimsProtection) {
                                    // Saving an enabled guard while armed starts it now; a refusal is
                                    // shown, because "on" in the list must not mean "watching" to the
                                    // owner when the platform declined (owner report 2026-11-08).
                                    when (ProtectionRuntime.startMotionIfEnabled(context)) {
                                        MotionMonitoringStartDecision.AskForNotificationPermission ->
                                            scope.launch { snackbarHostState.showSnackbar(monitoringNeedsPermissionMessage) }
                                        is MotionMonitoringStartDecision.DoNotStartMonitoring ->
                                            scope.launch {
                                                snackbarHostState.showSnackbar(
                                                    if (storageInstalled) monitoringRefusedMessage else monitoringNoStorageMessage,
                                                )
                                            }
                                        else -> Unit
                                    }
                                }
                            }
                        }
                        if (ProtectionChange.weakens(protectionSettings, updated)) requireOwner(apply) else apply()
                    },
                    onBack = { navController.popBackStack() },
                )
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
                            onOpenInMaps = { lat, lng ->
                                val uri = android.net.Uri.parse("geo:$lat,$lng?q=$lat,$lng")
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                                Unit
                            },
                            onDelete = { defer() },
                            onExport = { exportFlow.begin(detail) },
                        ),
                        exportAvailable = installedStorage != null && detail.evidenceItems.isNotEmpty(),
                        loadEvidence = { id ->
                            withContext(Dispatchers.IO) {
                                runCatching { installedStorage?.evidenceFiles?.read(id) }.getOrNull()
                            }
                        },
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
        SecurityEventType.LOCK_CONFIRMED_EVENT to stringResource(R.string.event_title_lock),
        SecurityEventType.PANIC_ACTIVATION to stringResource(R.string.event_title_panic),
        SecurityEventType.SIM_CONFIRMED_EVENT to stringResource(R.string.event_title_sim),
        SecurityEventType.COUNTDOWN_SIGNAL to stringResource(R.string.event_title_countdown),
        SecurityEventType.BATTERY_SIGNAL to stringResource(R.string.event_title_battery),
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
private const val DEVICE_REFRESH_MS = 5_000L

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
    BottomTab.Map -> Icons.Filled.Place
    BottomTab.History -> Icons.Filled.History
    BottomTab.Settings -> Icons.Filled.Settings
}

private fun BottomTab.labelRes(): Int = when (this) {
    BottomTab.Dashboard -> R.string.tab_dashboard
    BottomTab.Map -> R.string.tab_map
    BottomTab.History -> R.string.tab_history
    BottomTab.Settings -> R.string.tab_settings
}

/** Reads the real device state behind the health check and the dashboard's protection score. */
private fun readHealthChecks(
    context: Context,
    notifier: LocalEventNotifier,
    armed: Boolean,
    channel: Boolean,
    motionMonitoring: Boolean,
    deviceAdmin: Boolean,
): HealthChecks {
    fun has(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    return HealthChecks(
        armed = armed,
        camera = has(Manifest.permission.CAMERA),
        location = has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION),
        notifications = notifier.permissionState().let {
            it == com.shieldra.notification.NotificationPermissionState.Granted ||
                it == com.shieldra.notification.NotificationPermissionState.NotRequired
        },
        deviceAdmin = deviceAdmin,
        batteryExempt = com.shieldra.protection.BackgroundSurvival.isExempt(context),
        channel = channel,
        motionMonitoring = motionMonitoring,
        screenLock = OwnerCheck.canAskForTheOwner(context),
    )
}

private fun scoreFor(checks: HealthChecks, settings: ProtectionSettings): Int = ProtectionScore.score(
    ProtectionScore.Inputs(
        armed = checks.armed,
        lockEnabled = settings.lockEnabled,
        motionMonitoring = checks.motionMonitoring,
        simEnabled = settings.simEnabled,
        batteryEnabled = settings.batteryEnabled,
        cameraGranted = checks.camera,
        locationGranted = checks.location,
        notificationsReady = checks.notifications,
        deviceAdminActive = checks.deviceAdmin,
        batteryExempt = checks.batteryExempt,
        anyChannelActive = checks.channel,
    ),
)

/** Which small Settings editor is open. */
private enum class SettingsDialog { Theme, Language, AppLock, Erase }
