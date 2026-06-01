package com.beatdrop.kt.ui.screens

import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.beatdrop.kt.PlayerViewModel
import com.beatdrop.kt.data.Track
import com.beatdrop.kt.ui.components.TintedGlassButton
import com.beatdrop.kt.ui.components.pressableScale
import com.beatdrop.kt.ui.theme.LocalAppColors
import com.beatdrop.kt.ui.theme.Radius

/**
 * DJ Mode — dual decks with working crossfader.
 * Liquid Glass deck panels, spinning vinyl animation when playing,
 * tinted play buttons, glass crossfader track, and glass track picker.
 */
@Composable
fun DJScreen(vm: PlayerViewModel, onBack: () -> Unit = {}) {
    val C = LocalAppColors.current
    val tracks by vm.tracks.collectAsState()
    val deckA by vm.deckATrack.collectAsState()
    val deckB by vm.deckBTrack.collectAsState()
    val aPlaying by vm.deckAPlaying.collectAsState()
    val bPlaying by vm.deckBPlaying.collectAsState()
    val xfade by vm.crossfade.collectAsState()

    var picking by remember { mutableStateOf<Char?>(null) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(16.dp),
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = C.text)
            }
            Spacer(Modifier.width(4.dp))
            Text("DJ Mode", color = C.text, fontWeight = FontWeight.Black, fontSize = 26.sp)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Filled.Album, null, tint = C.accent, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(16.dp))

        // ── Dual Decks ──────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassDeck(
                label = "DECK A",
                track = deckA,
                playing = aPlaying,
                accent = C.accent,
                modifier = Modifier.weight(1f),
                onLoad = { picking = 'A' },
                onToggle = { vm.toggleDeckA() },
            )
            GlassDeck(
                label = "DECK B",
                track = deckB,
                playing = bPlaying,
                accent = C.blue,
                modifier = Modifier.weight(1f),
                onLoad = { picking = 'B' },
                onToggle = { vm.toggleDeckB() },
            )
        }

        Spacer(Modifier.height(20.dp))

        // ── Crossfader ──────────────────────────────────────────────────────
        GlassCrossfader(xfade, C.accent, C.blue) { vm.setCrossfade(it) }

        Spacer(Modifier.height(16.dp))
    }

    // ── Track Picker Bottom Sheet ────────────────────────────────────────────
    picking?.let { deck ->
        TrackPickerSheet(tracks, onPick = {
            if (deck == 'A') vm.loadDeckA(it) else vm.loadDeckB(it)
            picking = null
        }, onDismiss = { picking = null })
    }
}

// ─── Liquid Glass Deck Panel ─────────────────────────────────────────────────

@Composable
private fun GlassDeck(
    label: String,
    track: Track?,
    playing: Boolean,
    accent: Color,
    modifier: Modifier,
    onLoad: () -> Unit,
    onToggle: () -> Unit,
) {
    val C = LocalAppColors.current
    val ctx = LocalContext.current
    val shape = RoundedCornerShape(Radius.lg)

    // Spinning vinyl animation
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl$label")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
        ),
        label = "rotation$label",
    )

    Column(
        modifier
            .clip(shape)
            .background(if (C.isDark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.5f))
            // Rim light
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            if (C.isDark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.15f),
                            Color.Transparent,
                        ),
                        startY = 0f, endY = size.height * 0.25f,
                    )
                )
            }
            .border(0.8.dp, C.liquidGlassBorder, shape)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Label
        Text(label, color = accent, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(10.dp))

        // Vinyl disc
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f)
                .clip(CircleShape)
                .background(if (C.isDark) Color(0xFF1A1A2E) else Color(0xFFE8E8F0)),
            Alignment.Center,
        ) {
            if (track != null) {
                AsyncImage(
                    model = ImageRequest.Builder(ctx).data(track.artworkUri).crossfade(true).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                        .rotate(if (playing) rotation else 0f),
                )
            }
            // Center hole
            Box(
                Modifier.size(20.dp).clip(CircleShape)
                    .background(if (C.isDark) Color(0xFF0A0910) else Color(0xFFF2F2F7))
                    .border(1.dp, accent.copy(alpha = 0.3f), CircleShape)
            )
            // Playing ring
            if (playing) {
                Box(
                    Modifier.fillMaxSize()
                        .border(2.dp, accent.copy(alpha = 0.4f), CircleShape)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Track info
        Text(
            track?.title ?: "Empty",
            color = C.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold, fontSize = 13.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            track?.artist ?: "Tap Load",
            color = C.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontSize = 11.sp, textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(10.dp))

        // Controls
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Load button — glass chip
            Box(
                Modifier.clip(RoundedCornerShape(20.dp))
                    .background(if (C.isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                    .border(0.5.dp, C.liquidGlassBorder, RoundedCornerShape(20.dp))
                    .pressableScale(onClick = onLoad)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text("Load", color = C.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            // Play/Pause — tinted glass button
            TintedGlassButton(
                modifier = Modifier.size(36.dp),
                tintColor = accent,
                cornerRadius = 18.dp,
            ) {
                Box(Modifier.fillMaxSize().pressableScale(onClick = onToggle), Alignment.Center) {
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        null, tint = Color.White, modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

// ─── Glass Crossfader ────────────────────────────────────────────────────────

@Composable
private fun GlassCrossfader(value: Float, colorA: Color, colorB: Color, onChange: (Float) -> Unit) {
    val C = LocalAppColors.current
    val shape = RoundedCornerShape(Radius.md)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (C.isDark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.4f))
            .border(0.5.dp, C.liquidGlassBorder, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Tune, null, tint = C.textSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("CROSSFADER", color = C.textTertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("A", color = colorA, fontWeight = FontWeight.Black, fontSize = 16.sp)
            Slider(
                value = value, onValueChange = onChange, valueRange = 0f..1f,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = C.text,
                    activeTrackColor = colorA,
                    inactiveTrackColor = colorB.copy(alpha = 0.5f),
                ),
            )
            Text("B", color = colorB, fontWeight = FontWeight.Black, fontSize = 16.sp)
        }
        Text(
            "${((1 - value) * 100).toInt()}%  ·  ${(value * 100).toInt()}%",
            color = C.textSecondary, fontSize = 12.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

// ─── Track Picker Sheet ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackPickerSheet(tracks: List<Track>, onPick: (Track) -> Unit, onDismiss: () -> Unit) {
    val C = LocalAppColors.current
    val ctx = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (C.isDark) Color(0xFF141020) else Color(0xFFF8F8FC),
    ) {
        Text(
            "Load a track", color = C.text, fontWeight = FontWeight.Bold, fontSize = 18.sp,
            modifier = Modifier.padding(20.dp, 4.dp, 20.dp, 12.dp),
        )
        Divider(color = C.separator)
        LazyColumn(Modifier.fillMaxHeight(0.7f)) {
            items(tracks) { t ->
                Row(
                    Modifier.fillMaxWidth()
                        .pressableScale(onClick = { onPick(t) })
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(Radius.sm)).background(C.bg3)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(ctx).data(t.artworkUri).crossfade(true).size(96).build(),
                            contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.title, color = C.text, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text(t.artist, color = C.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(fmt(t.durationMs), color = C.textTertiary, fontSize = 12.sp)
                }
            }
        }
    }
}
