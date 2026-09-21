package com.shieldra.app.design.components.domain

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.shieldra.app.R
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shieldra.app.design.theme.ShieldraTheme
import com.shieldra.app.design.tokens.ShieldraTextStyles

@Composable
fun FreshnessIndicator(
    ageSeconds: Long,
    modifier: Modifier = Modifier,
) {
    val color: Color = when {
        ageSeconds < 60 -> ShieldraTheme.semantic.protection
        ageSeconds < 3600 -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> ShieldraTheme.semantic.suspicious
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.Schedule,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = formatAge(ageSeconds),
            style = ShieldraTextStyles.NumericData,
            color = color,
        )
    }
}

@Composable
private fun formatAge(seconds: Long): String = when {
    seconds < 60 -> stringResource(R.string.freshness_seconds, seconds)
    seconds < 3600 -> stringResource(R.string.freshness_minutes, seconds / 60)
    seconds < 86400 -> stringResource(R.string.freshness_hours, seconds / 3600)
    else -> stringResource(R.string.freshness_days, seconds / 86400)
}

@Preview(showBackground = true)
@Composable
private fun FreshnessIndicatorPreview() {
    ShieldraTheme(darkTheme = true) {
        FreshnessIndicator(4L)
    }
}
