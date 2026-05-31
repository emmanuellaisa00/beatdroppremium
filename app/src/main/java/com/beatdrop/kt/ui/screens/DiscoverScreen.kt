package com.beatdrop.kt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.beatdrop.kt.PlayerViewModel
import com.beatdrop.kt.data.Track
import com.beatdrop.kt.ui.components.SectionHeader
import com.beatdrop.kt.ui.components.pressableScale
import com.beatdrop.kt.ui.theme.LocalAppColors
import com.beatdrop.kt.ui.theme.Radius
import com.beatdrop.kt.ui.theme.Spacing
import com.beatdrop.kt.ui.theme.Type

/** Apple-Music / Spotify-style home built entirely from the local library. */
@Composable
fun DiscoverScreen(vm: PlayerViewModel, onOpenSearch: () -> Unit = {}) {
    val C = LocalAppColors.current
    val tracks by vm.tracks.collectAsState()
    val counts by vm.playCounts.collectAsState()

    val featured = remember(tracks, counts) {
        val byId = tracks.associateBy { it.id }
        counts.entries.maxByOrNull { it.value }?.let { byId[it.key] } ?: tracks.firstOrNull()
    }
    val recent = remember(tracks) { tracks.sortedByDescending { it.dateAdded }.take(12) }
    val mostPlayed = remember(tracks, counts) {
        val byId = tracks.associateBy { it.id }
        counts.entries.sortedByDescending { it.value }.mapNotNull { byId[it.key] }.take(12)
    }
    val jumpBackIn = remember(tracks) { tracks.shuffled().take(12) }
    val quickGrid = remember(tracks) { tracks.shuffled().take(6) }

    LazyColumn(contentPadding = PaddingValues(bottom = 170.dp)) {
        // ── Header ──────────────────────────────────────────────────────────
        item {
            Row(Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.lg, top = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("Discover", style = Type.largeTitle, color = C.text, modifier = Modifier.weight(1f))
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).background(C.bg2)
                    .pressableScale(onClick = onOpenSearch, scaleTo = 0.85f), Alignment.Center) {
                    Icon(Icons.Filled.Search, "Search online", tint = C.text, modifier = Modifier.size(20.dp))
                }
            }
        }

        if (tracks.isEmpty()) {
            item { Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) { Text("Your library is empty.", style = Type.body, color = C.textSecondary) } }
            return@LazyColumn
        }

        // ── Featured hero ───────────────────────────────────────────────────
        featured?.let { f ->
            item { FeaturedHero(f) { vm.play(f) } }
        }

        // ── Quick-pick grid (2 cols of compact rows) ────────────────────────
        if (quickGrid.isNotEmpty()) {
            item { Eyebrow("QUICK PICKS") }
            item { QuickGrid(quickGrid) { vm.play(it) } }
        }

        // ── Carousels ───────────────────────────────────────────────────────
        if (mostPlayed.isNotEmpty()) item { Carousel("Most Played", mostPlayed, vm) }
        item { Carousel("Recently Added", recent, vm) }
        item { Carousel("Jump Back In", jumpBackIn, vm) }
    }
}

@Composable
private fun Eyebrow(text: String) {
    val C = LocalAppColors.current
    Text(text, style = Type.overline, color = C.textTertiary, modifier = Modifier.padding(start = Spacing.lg, top = 18.dp, bottom = 8.dp))
}

@Composable
private fun FeaturedHero(track: Track, onPlay: () -> Unit) {
    val C = LocalAppColors.current
    val ctx = LocalContext.current
    Box(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = 4.dp)
            .aspectRatio(1.6f).clip(RoundedCornerShape(Radius.lg)).background(C.bg3)
            .pressableScale(onClick = onPlay, scaleTo = 0.98f),
    ) {
        AsyncImage(model = ImageRequest.Builder(ctx).data(track.artworkUri).crossfade(true).size(512).build(),
            contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text("FEATURED", style = Type.overline, color = Color.White.copy(alpha = 0.8f))
            Spacer(Modifier.height(4.dp))
            Text(track.title, style = Type.title2, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, style = Type.callout, color = Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(16.dp).size(48.dp).clip(RoundedCornerShape(24.dp)).background(C.accent), Alignment.Center) {
            Icon(Icons.Filled.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun QuickGrid(list: List<Track>, onPlay: (Track) -> Unit) {
    val C = LocalAppColors.current
    val ctx = LocalContext.current
    Column(Modifier.padding(horizontal = Spacing.lg)) {
        list.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { t ->
                    Row(
                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(C.bg2)
                            .pressableScale(onClick = { onPlay(t) }, scaleTo = 0.97f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp)).background(C.bg3)) {
                            AsyncImage(model = ImageRequest.Builder(ctx).data(t.artworkUri).crossfade(true).build(),
                                contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                        Text(t.title, style = Type.caption, color = C.text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Carousel(title: String, list: List<Track>, vm: PlayerViewModel) {
    val C = LocalAppColors.current
    val ctx = LocalContext.current
    Column(Modifier.padding(top = 18.dp)) {
        SectionHeader(title)
        Spacer(Modifier.height(10.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = Spacing.lg), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(list, key = { it.id }) { t ->
                Column(Modifier.width(150.dp).pressableScale(onClick = { vm.playList(list, t.id) }, scaleTo = 0.96f)) {
                    Box(Modifier.size(150.dp).clip(RoundedCornerShape(Radius.md)).background(C.bg3)) {
                        AsyncImage(model = ImageRequest.Builder(ctx).data(t.artworkUri).crossfade(true).build(),
                            contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        Box(Modifier.align(Alignment.BottomEnd).padding(8.dp).size(36.dp).clip(RoundedCornerShape(18.dp)).background(C.accent.copy(alpha = 0.95f)), Alignment.Center) {
                            Icon(Icons.Filled.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(t.title, style = Type.callout, color = C.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.artist, style = Type.footnote, color = C.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
