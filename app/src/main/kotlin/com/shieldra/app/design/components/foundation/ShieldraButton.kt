package com.shieldra.app.design.components.foundation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material3.Icon
import androidx.compose.ui.unit.dp
import com.shieldra.app.R
import com.shieldra.app.design.tokens.ShieldraCardShape

enum class ShieldraButtonVariant { Primary, Secondary, Ghost, Destructive }

@Composable
fun ShieldraButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ShieldraButtonVariant = ShieldraButtonVariant.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    val height = 52.dp
    val contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
    val effectiveEnabled = enabled && !loading
    val loadingDescription = if (loading) stringResource(R.string.loading) else null
    val accessibilityModifier = if (loading) {
        Modifier.semantics {
            contentDescription = text
            stateDescription = loadingDescription.orEmpty()
        }
    } else Modifier

    when (variant) {
        ShieldraButtonVariant.Primary -> Button(
            onClick = onClick,
            enabled = effectiveEnabled,
            modifier = modifier.then(accessibilityModifier).height(height),
            shape = ShieldraCardShape,
            contentPadding = contentPadding,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) { ButtonContent(text, leadingIcon, loading) }

        ShieldraButtonVariant.Secondary -> OutlinedButton(
            onClick = onClick,
            enabled = effectiveEnabled,
            modifier = modifier.then(accessibilityModifier).height(height),
            shape = ShieldraCardShape,
            contentPadding = contentPadding,
        ) { ButtonContent(text, leadingIcon, loading) }

        ShieldraButtonVariant.Ghost -> TextButton(
            onClick = onClick,
            enabled = effectiveEnabled,
            modifier = modifier.then(accessibilityModifier).height(height),
            shape = ShieldraCardShape,
            contentPadding = contentPadding,
        ) { ButtonContent(text, leadingIcon, loading) }

        ShieldraButtonVariant.Destructive -> Button(
            onClick = onClick,
            enabled = effectiveEnabled,
            modifier = modifier.then(accessibilityModifier).height(height),
            shape = ShieldraCardShape,
            contentPadding = contentPadding,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) { ButtonContent(text, leadingIcon, loading) }
    }
}

@Composable
private fun ButtonContent(
    text: String,
    leadingIcon: ImageVector?,
    loading: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        when {
            loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = androidx.compose.material3.LocalContentColor.current,
                )
            }
            leadingIcon != null -> {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
        }
        if (!loading) {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Convenience: full-width variant. */
@Composable
fun ShieldraFullWidthButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ShieldraButtonVariant = ShieldraButtonVariant.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    ShieldraButton(
        text = text,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        variant = variant,
        enabled = enabled,
        loading = loading,
    )
}
