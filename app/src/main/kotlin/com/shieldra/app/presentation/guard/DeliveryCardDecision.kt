package com.shieldra.app.presentation.guard

import com.shieldra.app.presentation.model.ChannelReceiptUiModel

/**
 * Whether Event Detail may draw its Delivery card (F-K13).
 *
 * The card opens with a "Delivery" heading, which is itself a claim that delivery was undertaken, so
 * it is drawn only from receipts the delivery pipeline actually recorded. Nothing here manufactures a
 * receipt: an event no channel was offered to shows no delivery section at all, rather than a heading
 * over an empty body or a per-channel "not attempted" line naming channels it never reached.
 */
fun deliveryCardVisible(receipts: List<ChannelReceiptUiModel>): Boolean = receipts.isNotEmpty()
