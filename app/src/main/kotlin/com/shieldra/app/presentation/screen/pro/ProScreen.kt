package com.shieldra.app.presentation.screen.pro

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shieldra.app.R
import com.shieldra.app.design.components.foundation.GlassCard
import com.shieldra.app.design.components.foundation.ShieldraButton
import com.shieldra.app.design.theme.ShieldraTheme
import com.shieldra.billing.ProAvailability
import com.shieldra.billing.ProPeriod
import com.shieldra.billing.ProPlan
import com.shieldra.billing.ProState
import com.shieldra.billing.ProUnavailable

/**
 * The Pro screen (amendment A-40). Every price is the text Google Play returns for this user; the
 * screen sells nothing it cannot buy: when Play cannot sell (a sideloaded build, no Play, no product
 * yet) it says exactly why instead of showing a button that would do nothing.
 */
@Composable
fun ProScreen(
    state: ProState,
    onBack: () -> Unit,
    onPurchase: (basePlanId: String) -> Unit,
    onRefresh: () -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gold = ShieldraTheme.semantic.suspicious
    var selected by remember(state.plans) { mutableStateOf(state.plans.firstOrNull()?.basePlanId) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .background(Brush.verticalGradient(listOf(gold.copy(alpha = 0.20f), Color.Transparent), endY = 900f))
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
        }

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Brush.linearGradient(listOf(gold, gold.copy(alpha = 0.65f)))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.WorkspacePremium,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.background,
                    modifier = Modifier.size(46.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.pro_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.pro_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        GlassCard {
            listOf(
                R.string.pro_feature_photos,
                R.string.pro_feature_audio,
                R.string.pro_feature_telegram,
                R.string.pro_feature_remote,
                R.string.pro_feature_tracking,
                R.string.pro_feature_countdown,
            ).forEachIndexed { index, res ->
                if (index > 0) Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(22.dp).clip(CircleShape).background(gold.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = gold, modifier = Modifier.size(14.dp))
                    }
                    Spacer(Modifier.size(12.dp))
                    Text(stringResource(res), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.pro_free_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when {
            state.entitled -> {
                GlassCard {
                    Text(
                        text = stringResource(R.string.pro_active),
                        style = MaterialTheme.typography.titleMedium,
                        color = ShieldraTheme.semantic.protection,
                    )
                    Spacer(Modifier.height(10.dp))
                    ShieldraButton(
                        text = stringResource(R.string.pro_manage),
                        onClick = onManage,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            state.availability is ProAvailability.Ready -> {
                state.plans.forEach { plan ->
                    PlanCard(
                        plan = plan,
                        selected = plan.basePlanId == selected,
                        gold = gold,
                        onClick = { selected = plan.basePlanId },
                    )
                }
                val chosen = state.plans.firstOrNull { it.basePlanId == selected }
                ShieldraButton(
                    text = stringResource(
                        if (chosen?.trialDays != null) R.string.pro_start_trial else R.string.pro_subscribe,
                    ),
                    onClick = { chosen?.let { onPurchase(it.basePlanId) } },
                    enabled = chosen != null && !state.purchasePending,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.pro_cancel_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            state.availability is ProAvailability.Connecting -> {
                Text(
                    text = stringResource(R.string.pro_connecting),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            state.availability is ProAvailability.Unavailable -> {
                GlassCard {
                    Text(
                        text = stringResource(unavailableText(state.availability.reason)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onRefresh) { Text(stringResource(R.string.pro_try_again)) }
                }
            }
        }

        if (state.purchasePending) {
            Text(
                text = stringResource(R.string.pro_pending),
                style = MaterialTheme.typography.bodyMedium,
                color = gold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        TextButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.pro_restore))
        }
    }
}

@Composable
private fun PlanCard(plan: ProPlan, selected: Boolean, gold: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) gold.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface)
            .border(
                BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) gold else MaterialTheme.colorScheme.outline),
                shape,
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    when (plan.period) {
                        ProPeriod.YEARLY -> R.string.pro_plan_yearly
                        ProPeriod.MONTHLY -> R.string.pro_plan_monthly
                        ProPeriod.OTHER -> R.string.pro_title
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            plan.trialDays?.let { days ->
                Text(
                    text = stringResource(R.string.pro_trial_days, days),
                    style = MaterialTheme.typography.bodySmall,
                    color = gold,
                )
            }
        }
        Text(
            text = stringResource(
                when (plan.period) {
                    ProPeriod.YEARLY -> R.string.pro_price_per_year
                    ProPeriod.MONTHLY -> R.string.pro_price_per_month
                    ProPeriod.OTHER -> R.string.pro_price_plain
                },
                plan.recurringPrice,
            ),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun unavailableText(reason: ProUnavailable): Int = when (reason) {
    ProUnavailable.NOT_FROM_PLAY -> R.string.pro_unavailable_not_play
    ProUnavailable.BILLING_UNSUPPORTED -> R.string.pro_unavailable_billing
    ProUnavailable.SERVICE_UNAVAILABLE -> R.string.pro_unavailable_service
    ProUnavailable.PRODUCT_NOT_FOUND -> R.string.pro_unavailable_product
}
