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
import com.beatdrop.kt.ui.components.pressableScale
import com.beatdrop.kt.ui.theme.LocalAppColors
import com.beatdrop.kt.ui.theme.Radius

@Composable
fun AlbumScreen(vm: PlayerViewModel, albumName: String, artistName: String, onBack: () -> Unit) {
    val C = LocalAppColors.current
    var sheetTrack by remember { mutableStateOf<com.beatdrop.kt.data.Track?>(null) }
    val ctx = LocalContext.current
    val group = remember(albumName, artistName) {
        vm.albums().firstOrNull { it.album == albumName && it.artist == artistName }
    }
    val tracks = group?.tracks ?: emptyList()
    val current by vm.current.collectAsState()

  Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 160.dp)) {
        item {
            Box {
                IconButton(onClick = onBack, modifier = Modifier.padding(8.dp)) {
                    Icon(Icons.Filled.ArrowBack, "Back", tint = C.text)
                }
            }
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(200.dp).clip(RoundedCornerShape(Radius.xl)).background(C.bg3)) {
                    AsyncImage(
                        model = ImageRequest.Builder(ctx).data(group?.artworkUri).crossfade(true).size(512).build(),
                        contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(albumName, color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(artistName, color = C.textSecondary, fontSize = 14.sp)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { if (tracks.isNotEmpty()) vm.playList(tracks, tracks.first().id) }) {
                        Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Play")
                    }
                    OutlinedButton(onClick = { if (tracks.isNotEmpty()) vm.playList(tracks.shuffled(), tracks.first().id) }) {
                        Icon(Icons.Filled.Shuffle, null); Spacer(Modifier.width(6.dp)); Text("Shuffle")
                    }
                }
            }
        }
        itemsIndexed(tracks, key = { _, t -> t.id }) { index, t ->
            Row(
                Modifier.fillMaxWidth().pressableScale(onClick = { vm.playList(tracks, t.id) }, onLongClick = { sheetTrack = t })
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${index + 1}", color = C.textTertiary, modifier = Modifier.width(28.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, color = if (current?.id == t.id) C.accent else C.text,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
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
