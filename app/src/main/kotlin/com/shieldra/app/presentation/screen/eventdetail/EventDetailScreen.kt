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
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import com.shieldra.app.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.shieldra.app.presentation.guard.deliveryCardVisible
import com.shieldra.app.presentation.model.EventDetailUiModel
import com.shieldra.app.presentation.model.LocationKind
import com.shieldra.app.presentation.model.LocationUiModel
import com.shieldra.app.presentation.demo.DemoData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    model: EventDetailUiModel,
    callbacks: EventDetailCallbacks,
    modifier: Modifier = Modifier,
    actionsAvailable: Boolean = false,
    /** Decrypts one evidence payload by id (off the main thread). Null in previews/pure contexts. */
    loadEvidence: (suspend (String) -> ByteArray?)? = null,
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
                .background(MaterialTheme.colorScheme.background)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(ShieldraTheme.semantic.protection.copy(alpha = 0.10f), Color.Transparent),
                        endY = 360f,
                    ),
                ),
            contentPadding = PaddingValues(bottom = spacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(spacing.l),
        ) {
            // Evidence cards appear only over evidence that exists; an empty 4:3 "no photo" frame and
            // a "location unavailable" card said nothing the details block does not already say.
            val photoId = model.photoEvidenceId
            when {
                photoId != null && loadEvidence != null -> item {
                    RealPhotoCard(photoId, loadEvidence, Modifier.padding(horizontal = spacing.l))
                }
                model.hasPhoto -> item {
                    PhotoEvidenceCard(available = true, modifier = Modifier.padding(horizontal = spacing.l))
                }
            }
            val audioId = model.audioEvidenceId
            if (audioId != null && loadEvidence != null) item {
                AudioPlayerCard(audioId, loadEvidence, Modifier.padding(horizontal = spacing.l))
            }

            val locationId = model.locationEvidenceId
            if (locationId != null && loadEvidence != null) {
                item {
                    RealLocationCard(locationId, loadEvidence, callbacks.onOpenInMaps, Modifier.padding(horizontal = spacing.l))
                }
            } else if (model.location.kind != LocationKind.Unavailable) item {
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

            if (deliveryCardVisible(model.delivery)) {
                item {
                    DeliveryReceipt(
                        receipts = model.delivery,
                        modifier = Modifier.padding(horizontal = spacing.l),
                    )
                }
            }

            // Delete and Export are shown only when the caller has a real action behind them.
            if (actionsAvailable) item {
                DestructiveActions(
                    onDelete = callbacks.onDelete,
                    onExport = callbacks.onExport,
                    modifier = Modifier.padding(horizontal = spacing.l),
                )
            }
        }
    }
}

/** The real front-camera photo, decrypted on demand from the encrypted store and shown in place. */
@Composable
private fun RealPhotoCard(
    photoId: String,
    loadEvidence: suspend (String) -> ByteArray?,
    modifier: Modifier = Modifier,
) {
    var image by remember(photoId) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(photoId) {
        image = withContext(Dispatchers.IO) {
            runCatching { loadEvidence(photoId)?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } }.getOrNull()
        }?.asImageBitmap()
    }
    val bmp = image
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = stringResource(R.string.evidence_photo),
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = 340.dp)
                .clip(ShieldraCardShape),
            contentScale = ContentScale.Crop,
        )
    } else {
        PhotoEvidenceCard(available = true, modifier = modifier)
    }
}

/** The real recorded position, decrypted on demand and shown with an Open-in-Maps action. */
@Composable
private fun RealLocationCard(
    locationId: String,
    loadEvidence: suspend (String) -> ByteArray?,
    onOpenInMaps: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    var loc by remember(locationId) { mutableStateOf<LocationUiModel?>(null) }
    LaunchedEffect(locationId) {
        loc = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = loadEvidence(locationId) ?: return@runCatching null
                val parts = String(bytes, Charsets.UTF_8).split("|")
                val lat = parts.getOrNull(0)?.toDoubleOrNull() ?: return@runCatching null
                val lng = parts.getOrNull(1)?.toDoubleOrNull() ?: return@runCatching null
                LocationUiModel(
                    kind = LocationKind.Current,
                    latitude = lat,
                    longitude = lng,
                    accuracyMeters = parts.getOrNull(2)?.toFloatOrNull()?.toInt(),
                    source = parts.getOrNull(3)?.ifBlank { null },
                )
            }.getOrNull()
        }
    }
    val l = loc
    if (l?.latitude != null && l.longitude != null) {
        LocationEvidenceCard(
            location = l,
            onOpenInMaps = { onOpenInMaps(l.latitude, l.longitude) },
            modifier = modifier,
        )
    }
}

/** Plays the real audio evidence: decrypts it to an app-private temp file, deleted on dispose. */
@Composable
private fun AudioPlayerCard(
    audioId: String,
    loadEvidence: suspend (String) -> ByteArray?,
    modifier: Modifier = Modifier,
) {
    val spacing = ShieldraTheme.spacing
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var playing by remember(audioId) { mutableStateOf(false) }
    var prepared by remember(audioId) { mutableStateOf(false) }
    val player = remember(audioId) { MediaPlayer() }
    val tempFile = remember(audioId) { java.io.File(context.cacheDir, "ev_audio_$audioId.m4a") }
    DisposableEffect(audioId) {
        onDispose {
            runCatching { player.reset(); player.release() }
            runCatching { tempFile.delete() }
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShieldraCardShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = {
            if (playing) {
                runCatching { player.pause() }
                playing = false
            } else {
                scope.launch {
                    if (!prepared) {
                        val ok = withContext(Dispatchers.IO) {
                            val bytes = runCatching { loadEvidence(audioId) }.getOrNull() ?: return@withContext false
                            runCatching {
                                tempFile.writeBytes(bytes)
                                player.reset()
                                player.setDataSource(tempFile.absolutePath)
                                player.prepare()
                            }.isSuccess
                        }
                        if (ok) {
                            player.setOnCompletionListener { playing = false }
                            prepared = true
                        }
                    }
                    if (prepared) {
                        runCatching { player.start() }
                        playing = true
                    }
                }
            }
        }) {
            Icon(
                imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(if (playing) R.string.evidence_pause else R.string.evidence_play),
                tint = ShieldraTheme.semantic.protection,
            )
        }
        Spacer(Modifier.width(spacing.s))
        Text(
            text = stringResource(R.string.evidence_audio),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
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
        if (model.statusLabel != null) {
            DetailRow(stringResource(R.string.detail_status), model.statusLabel)
        }
        if (model.activationCount != null) {
            DetailRow(stringResource(R.string.detail_presses), "${model.activationCount}")
        }
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
            model = DemoData.eventDetail(),
            callbacks = EventDetailCallbacks({}, { _, _ -> }, {}, {}),
        )
    }
}

@Preview(name = "Event Detail Last Known", showBackground = true)
@Composable
private fun EventDetailLastKnownPreview() {
    ShieldraTheme(darkTheme = true) {
        EventDetailScreen(
            model = DemoData.eventDetailLastKnown(),
            callbacks = EventDetailCallbacks({}, { _, _ -> }, {}, {}),
        )
    }
}
