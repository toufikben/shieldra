package com.shieldra.app.design.components.domain

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.shieldra.app.R
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shieldra.app.design.theme.ShieldraTheme
import com.shieldra.app.design.tokens.ShieldraCardShape
import com.shieldra.app.design.tokens.ShieldraTextStyles
import com.shieldra.app.presentation.model.LocationKind
import com.shieldra.app.presentation.model.LocationUiModel

@Composable
fun PhotoEvidenceCard(
    available: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val interactionModifier = if (onClick != null) {
        Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.role = Role.Button }
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .clip(ShieldraCardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(interactionModifier),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.CameraAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(if (available) R.string.evidence_photo else R.string.evidence_no_photo),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun LocationEvidenceCard(
    location: LocationUiModel,
    onOpenInMaps: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val spacing = ShieldraTheme.spacing
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShieldraCardShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(spacing.l),
    ) {
        Text(
            text = stringResource(R.string.location_section),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(spacing.s))

        when (location.kind) {
            LocationKind.Unavailable -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.location_unavailable_short),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                LocationBadge(location.kind)
                Spacer(Modifier.height(spacing.s))

                LocatorPanel(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracyMeters = location.accuracyMeters,
                )

                Spacer(Modifier.height(spacing.s))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (location.accuracyMeters != null) {
                        Text(
                            text = stringResource(R.string.accuracy_meters, location.accuracyMeters),
                            style = ShieldraTextStyles.NumericData,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(spacing.s))
                    }
                    if (location.ageSeconds != null) {
                        FreshnessIndicator(location.ageSeconds)
                        Spacer(Modifier.width(spacing.s))
                    }
                    if (location.source != null) {
                        Text(
                            text = location.source,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (onOpenInMaps != null) {
                    Spacer(Modifier.height(spacing.s))
                    com.shieldra.app.design.components.foundation.ShieldraButton(
                        text = stringResource(R.string.location_open_in_maps),
                        onClick = onOpenInMaps,
                        variant = com.shieldra.app.design.components.foundation.ShieldraButtonVariant.Secondary,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LocationEvidenceCardPreview() {
    ShieldraTheme(darkTheme = true) {
        LocationEvidenceCard(
            location = LocationUiModel(
                kind = LocationKind.Current,
                accuracyMeters = 8,
                ageSeconds = 4,
                source = "GPS",
            ),
            onOpenInMaps = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

/**
 * A map-less locator: the pin on a grid with the measured accuracy as a ring, and the exact
 * coordinates. It draws from the stored fix alone, so viewing an event never asks a map server
 * about where the phone was.
 */
@Composable
private fun LocatorPanel(latitude: Double?, longitude: Double?, accuracyMeters: Int?) {
    val line = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val ring = ShieldraTheme.semantic.protection
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
            val step = 32.dp.toPx()
            var x = size.width / 2f % step
            while (x < size.width) {
                drawLine(line, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height))
                x += step
            }
            var y = size.height / 2f % step
            while (y < size.height) {
                drawLine(line, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y))
                y += step
            }
            val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
            val radius = (accuracyMeters ?: 0).coerceIn(0, 400) / 400f * (size.height / 2f - 10.dp.toPx())
            drawCircle(ring.copy(alpha = 0.18f), radius = maxOf(radius, 14.dp.toPx()), center = center)
            drawCircle(ring, radius = maxOf(radius, 14.dp.toPx()), center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
            drawCircle(ring, radius = 6.dp.toPx(), center = center)
        }
        if (latitude != null && longitude != null) {
            Text(
                text = "%.5f, %.5f".format(java.util.Locale.US, latitude, longitude),
                style = ShieldraTextStyles.NumericData,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
            )
        }
    }
}
