package com.shieldra.app.presentation.screen.settings

import com.shieldra.protection.ProtectionSettings

/**
 * The draft rule for the protection form (G-9). The screen receives live settings that change while
 * the owner is editing — arming from the top button, a permission grant, a language switch that
 * recreates the activity. A form the owner has not touched follows the new settings; a form with
 * unsaved edits keeps them, because losing an edit is worse than showing a value the Save replaces.
 *
 * [openedWith] is the settings the draft was last synced to. Pure, so the rule is tested without
 * Compose or a device.
 */
internal data class SetupDraft(
    val draft: ProtectionSettings,
    val openedWith: ProtectionSettings,
)

internal fun draftAfterSettingsChange(
    draft: ProtectionSettings,
    openedWith: ProtectionSettings,
    incoming: ProtectionSettings,
): SetupDraft =
    if (draft == openedWith) SetupDraft(incoming, incoming) else SetupDraft(draft, openedWith)
