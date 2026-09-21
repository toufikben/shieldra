package com.shieldra.app.design.components.domain

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shieldra.app.R
import com.shieldra.app.design.theme.ShieldraTheme
import com.shieldra.app.design.tokens.ShieldraPillShape
import com.shieldra.app.presentation.model.LocationKind

@Composable
fun LocationBadge(
    kind: LocationKind,
    modifier: Modifier = Modifier,
) {
    val shape = ShieldraPillShape
    val color: Color = when (kind) {
        LocationKind.Current -> ShieldraTheme.semantic.protection
        LocationKind.LastKnown -> MaterialTheme.colorScheme.onSurfaceVariant
        LocationKind.Unavailable -> MaterialTheme.colorScheme.outline
    }
    val label = stringResource(when (kind) {
        LocationKind.Current -> R.string.location_current
        LocationKind.LastKnown -> R.string.location_last_known
        LocationKind.Unavailable -> R.string.location_unavailable
    })

    Row(
        modifier = modifier
            .clip(shape)
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color, shape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(8.dp)) {
            if (kind == LocationKind.Current) {
                drawCircle(color)
            } else {
                drawCircle(color, style = Stroke(width = 1.5.dp.toPx()))
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LocationBadgePreview() {
    ShieldraTheme(darkTheme = true) {
        Row(Modifier.padding(16.dp)) {
            LocationBadge(LocationKind.Current)
            Spacer(Modifier.width(8.dp))
            LocationBadge(LocationKind.LastKnown)
        }
    }
}
