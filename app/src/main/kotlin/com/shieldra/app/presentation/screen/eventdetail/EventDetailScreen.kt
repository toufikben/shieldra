package com.shieldra.app.presentation.screen.eventdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.shieldra.app.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shieldra.app.design.components.domain.DeliveryReceipt
import com.shieldra.app.design.components.domain.LocationEvidenceCard
import com.shieldra.app.design.components.domain.PhotoEvidenceCard
import com.shieldra.app.design.components.foundation.ShieldraButton
import com.shieldra.app.design.components.foundation.ShieldraButtonVariant
import com.shieldra.app.design.theme.ShieldraTheme
import com.shieldra.app.design.tokens.ShieldraCardShape
import com.shieldra.app.design.tokens.ShieldraTextStyles
import com.shieldra.app.presentation.model.EventDetailUiModel
import com.shieldra.app.presentation.model.LocationKind
import com.shieldra.app.presentation.preview.PreviewData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    model: EventDetailUiModel,
    callbacks: EventDetailCallbacks,
    modifier: Modifier = Modifier,
) {
    val spacing = ShieldraTheme.spacing
    val latitude = model.location.latitude
    val longitude = model.location.longitude

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = model.title,
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = callbacks.onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = spacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(spacing.l),
        ) {
            item {
                PhotoEvidenceCard(
                    available = model.hasPhoto,
                    modifier = Modifier.padding(horizontal = spacing.l),
                )
            }

            item {
                LocationEvidenceCard(
                    location = model.location,
                    onOpenInMaps = if (
                        model.location.kind != LocationKind.Unavailable &&
                        latitude != null &&
                        longitude != null
                    ) {
                        {
                            callbacks.onOpenInMaps(
                                latitude,
                                longitude,
                            )
                        }
                    } else null,
                    modifier = Modifier.padding(horizontal = spacing.l),
                )
            }

            item {
                DetailsBlock(model, modifier = Modifier.padding(horizontal = spacing.l))
            }

            item {
                DeliveryReceipt(
                    receipts = model.delivery,
                    modifier = Modifier.padding(horizontal = spacing.l),
                )
            }

            item {
                DestructiveActions(
                    onDelete = callbacks.onDelete,
                    onExport = callbacks.onExport,
                    modifier = Modifier.padding(horizontal = spacing.l),
                )
            }
        }
    }
}

@Composable
private fun DetailsBlock(
    model: EventDetailUiModel,
    modifier: Modifier = Modifier,
) {
    val spacing = ShieldraTheme.spacing
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShieldraCardShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(spacing.l),
        verticalArrangement = Arrangement.spacedBy(spacing.s),
    ) {
        Text(
            text = stringResource(R.string.details),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DetailRow(stringResource(R.string.detail_type), model.title)
        if (model.attemptNumber != null) {
            DetailRow(stringResource(R.string.detail_attempt), "${model.attemptNumber}")
        }
        DetailRow(stringResource(R.string.detail_timestamp), model.timestampLabel)
        if (model.batteryPercent != null) {
            DetailRow(
                stringResource(R.string.detail_battery),
                "${model.batteryPercent}% · " + stringResource(
                    if (model.charging) R.string.charging else R.string.not_charging,
                ),
            )
        }
        DetailRow(stringResource(R.string.detail_event_id), model.id)
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Text(
            text = value,
            style = ShieldraTextStyles.NumericData,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.6f),
        )
    }
}

@Composable
private fun DestructiveActions(
    onDelete: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ShieldraButton(
            text = stringResource(R.string.action_delete),
            onClick = onDelete,
            variant = ShieldraButtonVariant.Destructive,
            modifier = Modifier.weight(1f),
        )
        ShieldraButton(
            text = stringResource(R.string.action_export),
            onClick = onExport,
            variant = ShieldraButtonVariant.Secondary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EventDetailDarkPreview() {
    ShieldraTheme(darkTheme = true) {
        EventDetailScreen(
            model = PreviewData.eventDetail,
            callbacks = EventDetailCallbacks({}, { _, _ -> }, {}, {}),
        )
    }
}
