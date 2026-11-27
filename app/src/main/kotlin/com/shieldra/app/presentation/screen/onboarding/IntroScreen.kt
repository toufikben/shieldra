package com.shieldra.app.presentation.screen.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhonelinkErase
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shieldra.app.R
import com.shieldra.app.design.components.foundation.GlassCard
import com.shieldra.app.design.components.foundation.ShieldraButtonVariant
import com.shieldra.app.design.components.foundation.ShieldraFullWidthButton
import com.shieldra.app.design.theme.ShieldraTheme
import com.shieldra.app.navigation.IntroCards

/**
 * P4-22 (owner order 2026-11-26): the short tour that says what this app does, shown once, after setup.
 *
 * It is a single screen with a pager of cards rather than a chain of routes, because the tour is not a
 * decision the owner has to make — it is something to read or skip. One route means the back button
 * cannot strand him inside it, and finishing or skipping both land on the same place: the dashboard.
 *
 * Every card is one title and one paragraph. The paragraph names the number that the code enforces
 * (two minutes of siren, ten minutes of wipe window, thirty days of retention), because a tour that
 * overstates the product is a lie printed on the first screen the owner sees.
 *
 * [onDone] is called by the last card and by Skip, and it is the only exit: the caller records the
 * completion and moves on.
 */
@Composable
fun IntroScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = ShieldraTheme.spacing
    val cards = IntroCards.cards
    // The current card, held across recompositions but not across process death: an interrupted tour
    // starts again from the first card rather than resuming at an arbitrary place.
    var index by remember { mutableIntStateOf(0) }
    val last = index == cards.lastIndex
    val card = cards[index]

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(spacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.step_of, index + 1, cards.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(spacing.xxl))
        Icon(
            imageVector = iconFor(card.key),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(spacing.xl))
        Text(
            text = stringResource(titleFor(card.key)),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(spacing.m))
        // The body sits on the app's own surface so a long Arabic paragraph stays readable against the
        // gradient instead of floating on it.
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(bodyFor(card.key)),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(spacing.xxl))
        Dots(count = cards.size, current = index)
        Spacer(Modifier.height(spacing.xxl))
        ShieldraFullWidthButton(
            text = stringResource(if (last) R.string.intro_action_done else R.string.intro_action_next),
            onClick = { if (last) onDone() else index += 1 },
        )
        Spacer(Modifier.height(spacing.s))
        ShieldraFullWidthButton(
            text = stringResource(R.string.action_skip),
            onClick = onDone,
            variant = ShieldraButtonVariant.Ghost,
        )
    }
}

/** One dot per card, the current one filled: the owner can see how much is left before he commits. */
@Composable
private fun Dots(count: Int, current: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics {
            contentDescription = "$current of $count"
        },
    ) {
        repeat(count) { dot ->
            val filled = dot == current
            val alpha by animateFloatAsState(if (filled) 1f else 0.30f, label = "intro-dot")
            Box(
                Modifier
                    .size(if (filled) 8.dp else 6.dp)
                    .alpha(alpha)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}

/**
 * The card's icon, by key. Kept beside the screen rather than in the model because an `ImageVector` is
 * a Compose type and the model is plain Kotlin that the truth test reads.
 */
private fun iconFor(key: String): ImageVector = when (key) {
    "lock" -> Icons.Filled.Lock
    "motion" -> Icons.Filled.DirectionsRun
    "sim" -> Icons.Filled.SimCard
    "battery" -> Icons.Filled.BatteryChargingFull
    "snatch" -> Icons.Filled.Security
    "safe_zones" -> Icons.Filled.Place
    "evidence" -> Icons.Filled.CameraAlt
    "delivery" -> Icons.Filled.CloudUpload
    "remote" -> Icons.Filled.NotificationsActive
    "siren" -> Icons.Filled.VolumeUp
    "tracking" -> Icons.Filled.GpsFixed
    "wipe" -> Icons.Filled.DeleteForever
    else -> Icons.Filled.PhonelinkErase
}

/**
 * The resource for each key. Written out, not computed with `getIdentifier`, so a missing string is a
 * compile error rather than a blank card at runtime — and so the truth test can read the join.
 */
private fun titleFor(key: String): Int = when (key) {
    "lock" -> R.string.intro_lock_title
    "motion" -> R.string.intro_motion_title
    "sim" -> R.string.intro_sim_title
    "battery" -> R.string.intro_battery_title
    "snatch" -> R.string.intro_snatch_title
    "safe_zones" -> R.string.intro_safe_zones_title
    "evidence" -> R.string.intro_evidence_title
    "delivery" -> R.string.intro_delivery_title
    "remote" -> R.string.intro_remote_title
    "siren" -> R.string.intro_siren_title
    "tracking" -> R.string.intro_tracking_title
    "wipe" -> R.string.intro_wipe_title
    else -> R.string.intro_lock_title
}

private fun bodyFor(key: String): Int = when (key) {
    "lock" -> R.string.intro_lock_body
    "motion" -> R.string.intro_motion_body
    "sim" -> R.string.intro_sim_body
    "battery" -> R.string.intro_battery_body
    "snatch" -> R.string.intro_snatch_body
    "safe_zones" -> R.string.intro_safe_zones_body
    "evidence" -> R.string.intro_evidence_body
    "delivery" -> R.string.intro_delivery_body
    "remote" -> R.string.intro_remote_body
    "siren" -> R.string.intro_siren_body
    "tracking" -> R.string.intro_tracking_body
    "wipe" -> R.string.intro_wipe_body
    else -> R.string.intro_lock_body
}

@Preview(showBackground = true)
@Composable
private fun IntroPreview() {
    ShieldraTheme(darkTheme = true) {
        IntroScreen(onDone = {})
    }
}
