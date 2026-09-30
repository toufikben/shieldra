package com.shieldra.app.presentation.demo

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.shieldra.app.R
import com.shieldra.app.presentation.model.*

/** Localized static data for the intentionally demo-only Phase 3 shell. */
object DemoData {
    @Composable
    fun guards(): List<GuardUiModel> = listOf(
        GuardUiModel(GuardKind.Lock, stringResource(R.string.demo_lock_guard), pluralStringResource(R.plurals.demo_active_attempts, 2, 2), GuardStatus.Active),
        GuardUiModel(GuardKind.Motion, stringResource(R.string.demo_motion_guard), stringResource(R.string.demo_monitoring), GuardStatus.Active),
        GuardUiModel(GuardKind.Sim, stringResource(R.string.demo_sim_guard), stringResource(R.string.demo_monitored), GuardStatus.Active),
        GuardUiModel(GuardKind.Panic, stringResource(R.string.demo_panic), stringResource(R.string.demo_ready), GuardStatus.Active),
    )

    @Composable
    fun recentEvents(): List<EventUiModel> = listOf(
        EventUiModel("evt_1", EventType.FailedUnlock, stringResource(R.string.demo_failed_unlock), stringResource(R.string.demo_attempt, 2), stringResource(R.string.demo_time_failed_unlock), DeliveryStatus.Delivered, true, true),
        EventUiModel("evt_2", EventType.Motion, stringResource(R.string.demo_motion_detected), stringResource(R.string.demo_movement_after_stillness), stringResource(R.string.demo_time_motion), DeliveryStatus.Deferred, true, false),
    )

    @Composable
    fun dashboard(): DashboardUiModel = DashboardUiModel(
        ProtectionVisualState.Protected,
        12L,
        guards(),
        recentEvents(),
        84,
        stringResource(R.string.demo_network_wifi),
        true,
        true,
    )

    @Composable
    fun dashboardEmpty(): DashboardUiModel = dashboard().copy(recentEvents = emptyList())

    @Composable
    fun dashboardSuspicious(): DashboardUiModel = dashboard().copy(
        protectionState = ProtectionVisualState.Suspicious,
        lastCheckSeconds = 4L,
    )

    @Composable
    fun dashboardDisabled(): DashboardUiModel {
        val model = dashboard()
        return model.copy(
            protectionState = ProtectionVisualState.Disabled,
            guards = model.guards.map { it.copy(status = GuardStatus.Disabled) },
        )
    }

    @Composable
    fun eventDetail(): EventDetailUiModel = EventDetailUiModel(
        id = "evt_8a3f...d92",
        type = EventType.FailedUnlock,
        title = stringResource(R.string.demo_failed_unlock),
        attemptNumber = 2,
        timestampLabel = stringResource(R.string.demo_time_detail),
        batteryPercent = 84,
        hasPhoto = true,
        location = LocationUiModel(LocationKind.Current, 36.7538, 3.0588, 8, 4, stringResource(R.string.location_source_gps)),
        delivery = listOf(
            ChannelReceiptUiModel(ChannelId.Email, DeliveryStatus.Delivered, stringResource(R.string.demo_receipt_time)),
            ChannelReceiptUiModel(ChannelId.WhatsApp, DeliveryStatus.Failed, stringResource(R.string.demo_invalid_recipient)),
            ChannelReceiptUiModel(ChannelId.Telegram, DeliveryStatus.Delivered, stringResource(R.string.demo_receipt_time)),
        ),
    )

    @Composable
    fun eventDetailLastKnown(): EventDetailUiModel = eventDetail().copy(
        location = LocationUiModel(
            LocationKind.LastKnown,
            36.7538,
            3.0588,
            45,
            7200,
            stringResource(R.string.location_source_fused),
        ),
    )

    @Composable
    fun eventDetail(id: String): EventDetailUiModel? = when (id) {
        "evt_1" -> eventDetail().copy(id = id)
        "evt_2" -> eventDetail().copy(
            id = id,
            type = EventType.Motion,
            title = stringResource(R.string.demo_motion_detected),
            attemptNumber = null,
            location = LocationUiModel(LocationKind.Unavailable),
            delivery = listOf(
                ChannelReceiptUiModel(ChannelId.Email, DeliveryStatus.Deferred),
            ),
        )
        else -> null
    }

    @Composable
    fun premium(): PremiumUiModel = PremiumUiModel(
        priceLabel = stringResource(R.string.demo_premium_price),
        features = listOf(
            PremiumFeatureUiModel(stringResource(R.string.premium_feature_channels), stringResource(R.string.premium_feature_channels_detail)),
            PremiumFeatureUiModel(stringResource(R.string.premium_feature_history), stringResource(R.string.premium_feature_history_detail)),
            PremiumFeatureUiModel(stringResource(R.string.premium_feature_receipts)),
            PremiumFeatureUiModel(stringResource(R.string.premium_feature_no_ads)),
        ),
        freeTierSummary = stringResource(R.string.premium_free_summary),
    )
}
