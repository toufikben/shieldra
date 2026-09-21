package com.shieldra.app.design.components.foundation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.shieldra.app.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.shieldra.app.design.tokens.ShieldraCardShape

enum class ShieldraBannerKind { Info, Warning, Error, Ad }

@Composable
fun ShieldraBanner(
    title: String,
    message: String? = null,
    modifier: Modifier = Modifier,
    kind: ShieldraBannerKind = ShieldraBannerKind.Info,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val bg: Color = when (kind) {
        ShieldraBannerKind.Info    -> MaterialTheme.colorScheme.surfaceVariant
        ShieldraBannerKind.Warning -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
        ShieldraBannerKind.Error   -> MaterialTheme.colorScheme.errorContainer
        ShieldraBannerKind.Ad      -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg: Color = when (kind) {
        ShieldraBannerKind.Info    -> MaterialTheme.colorScheme.onSurfaceVariant
        ShieldraBannerKind.Warning -> MaterialTheme.colorScheme.tertiary
        ShieldraBannerKind.Error   -> MaterialTheme.colorScheme.onErrorContainer
        ShieldraBannerKind.Ad      -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShieldraCardShape)
            .background(bg)
            .border(1.dp, MaterialTheme.colorScheme.outline, ShieldraCardShape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = fg,
            )
            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = fg.copy(alpha = 0.85f),
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onAction) {
                Text(actionLabel, color = fg, style = MaterialTheme.typography.labelLarge)
            }
        }
        if (onDismiss != null) {
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.dismiss),
                    tint = fg,
                )
            }
        }
    }
}

/** Ad slot placeholder — no SDK. */
@Composable
fun ShieldraAdSlot(modifier: Modifier = Modifier, label: String? = null) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(ShieldraCardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, ShieldraCardShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label ?: stringResource(R.string.ad),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
