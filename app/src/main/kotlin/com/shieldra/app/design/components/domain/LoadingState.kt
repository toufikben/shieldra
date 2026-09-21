package com.shieldra.app.design.components.domain

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shieldra.app.R
import com.shieldra.app.design.theme.ShieldraTheme
import com.shieldra.app.design.tokens.ShieldraCardShape

@Composable
fun SkeletonBlock(
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeleton_progress",
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val brush = Brush.horizontalGradient(
        colors = listOf(
            base.copy(alpha = 0.4f + 0.3f * progress),
            base.copy(alpha = 0.2f + 0.15f * progress),
        ),
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(ShieldraCardShape)
            .background(brush),
    )
}

@Composable
fun LoadingStateList(
    modifier: Modifier = Modifier,
    rows: Int = 4,
) {
    val loadingDescription = stringResource(R.string.loading)
    Column(
        modifier = modifier
            .padding(16.dp)
            .semantics { stateDescription = loadingDescription },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(rows) { SkeletonBlock(88.dp) }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoadingStatePreview() {
    ShieldraTheme(darkTheme = true) {
        LoadingStateList()
    }
}
