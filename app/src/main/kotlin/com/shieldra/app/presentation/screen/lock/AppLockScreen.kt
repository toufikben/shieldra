package com.shieldra.app.presentation.screen.lock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.shieldra.app.R
import com.shieldra.app.design.components.foundation.ShieldraFullWidthButton
import com.shieldra.app.design.theme.ShieldraTheme
import com.shieldra.app.presentation.auth.OwnerCheck
import com.shieldra.app.presentation.auth.OwnerCheckResult

/**
 * The app lock (amendment A-39): an opaque screen over Shieldra that asks for the phone's own screen
 * lock through [OwnerCheck]. It opens the platform prompt on its own, and again on tap if the owner
 * dismissed it. A phone with no screen lock has nothing to ask, so the screen lets go at once.
 */
@Composable
fun AppLockScreen(
    activity: FragmentActivity,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.app_lock_prompt_title)
    val subtitle = stringResource(R.string.app_lock_prompt_subtitle)
    val ask = {
        OwnerCheck.request(activity, title, subtitle) { result ->
            if (result != OwnerCheckResult.Refused) onUnlocked()
        }
    }
    // Opens the prompt as soon as the lock appears, so the owner does not have to find a button.
    LaunchedEffect(Unit) { ask() }
    // While locked, Back must not walk through the screens hidden behind this one.
    BackHandler(enabled = true) {}

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .background(
                Brush.verticalGradient(
                    colors = listOf(ShieldraTheme.semantic.protection.copy(alpha = 0.16f), Color.Transparent),
                    endY = 900f,
                ),
            )
            .clickable(enabled = false) {}
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(ShieldraTheme.semantic.protection.copy(alpha = 0.14f))
                .border(1.dp, ShieldraTheme.semantic.protection.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = ShieldraTheme.semantic.protection,
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.app_lock_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.app_lock_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        ShieldraFullWidthButton(
            text = stringResource(R.string.app_lock_unlock),
            onClick = { ask() },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
    }
}
