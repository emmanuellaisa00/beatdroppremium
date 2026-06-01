package com.beatdrop.kt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
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
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.beatdrop.kt.PlayerViewModel
import com.beatdrop.kt.ui.components.TintedGlassButton
import com.beatdrop.kt.ui.components.pressableScale
import com.beatdrop.kt.ui.theme.LocalAppColors
import com.beatdrop.kt.ui.theme.Radius

@Composable
fun ArtistScreen(vm: PlayerViewModel, artistName: String, onBack: () -> Unit) {
    val C = LocalAppColors.current
    var sheetTrack by remember { mutableStateOf<com.beatdrop.kt.data.Track?>(null) }
    val ctx = LocalContext.current
    val group = remember(artistName) { vm.artists().firstOrNull { it.artist == artistName } }
    val tracks = group?.tracks ?: emptyList()
    val current by vm.current.collectAsState()

  Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 160.dp)) {
        item {
            IconButton(onClick = onBack, modifier = Modifier.padding(8.dp)) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = C.text)
            }
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(120.dp).clip(RoundedCornerShape(60.dp)).background(Brush.linearGradient(listOf(C.accent, C.purple))), Alignment.Center) {
                    Text(artistName.take(1).uppercase(), color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(16.dp))
                Text(artistName, color = C.text, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${tracks.size} songs", color = C.textSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TintedGlassButton(modifier = Modifier.height(44.dp).width(120.dp)) {
                        Row(
                            Modifier.fillMaxSize()
                                .pressableScale(onClick = { if (tracks.isNotEmpty()) vm.playList(tracks, tracks.first().id) }),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Play", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    Box(
                        Modifier.height(44.dp).width(120.dp)
                            .clip(RoundedCornerShape(Radius.xl))
                            .background(if (C.isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                            .pressableScale(onClick = { if (tracks.isNotEmpty()) vm.playList(tracks.shuffled(), tracks.first().id) }),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Shuffle, null, tint = C.text, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Shuffle", color = C.text, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Text("Songs", color = C.text, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                modifier = Modifier.padding(start = 20.dp, bottom = 6.dp))
        }
        itemsIndexed(tracks, key = { _, t -> t.id }) { _, t ->
            Row(
                Modifier.fillMaxWidth().pressableScale(onClick = { vm.playList(tracks, t.id) }, onLongClick = { sheetTrack = t })
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(Radius.sm)).background(C.bg3)) {
                    AsyncImage(model = ImageRequest.Builder(ctx).data(t.artworkUri).crossfade(true).size(96).build(),
                        contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, color = if (current?.id == t.id) C.accent else C.text,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(t.album, color = C.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(fmt(t.durationMs), color = C.textTertiary, fontSize = 12.sp)
            }
        }
    }
    sheetTrack?.let { tk ->
        com.beatdrop.kt.ui.components.TrackActionsSheet(vm, tk, onDismiss = { sheetTrack = null })
    }
  }
}
