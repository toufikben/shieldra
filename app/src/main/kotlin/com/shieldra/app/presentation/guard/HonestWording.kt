package com.shieldra.app.presentation.guard

import androidx.annotation.StringRes
import com.shieldra.app.R
import com.shieldra.app.presentation.model.DeliveryStatus
import com.shieldra.app.presentation.model.NetworkStatus

/**
 * The wording a History delivery chip is allowed to print for a state (Q-K-4, F-K10).
 *
 * Kept outside Compose so the decision — which sentence a state may reach — is unit
 * testable, exactly like the Settings and dashboard wording beside it.
 */
@StringRes
internal fun deliveryChipLabelRes(status: DeliveryStatus): Int = when (status) {
    DeliveryStatus.Ready -> R.string.status_ready
    DeliveryStatus.Delivering -> R.string.status_sending
    DeliveryStatus.Delivered -> R.string.status_delivered
    DeliveryStatus.Deferred -> R.string.status_retrying
    DeliveryStatus.Failed -> R.string.status_failed
    DeliveryStatus.Expired -> R.string.status_expired
    DeliveryStatus.NotAttempted -> R.string.status_not_attempted
}

/**
 * The label the network slot must override with, or null to show the caller's own
 * label (Q-K-4, F-K7).
 *
 * Only a real offline reading may reach the offline wording. [NetworkStatus.Unknown]
 * and [NetworkStatus.Online] both show the label the caller measured or declared: the
 * dashboard's "not measured" sentence, or the network a connected reading named.
 */
@StringRes
internal fun networkSlotLabelRes(status: NetworkStatus): Int? = when (status) {
    NetworkStatus.Offline -> R.string.status_network_offline
    NetworkStatus.Unknown, NetworkStatus.Online -> null
}
