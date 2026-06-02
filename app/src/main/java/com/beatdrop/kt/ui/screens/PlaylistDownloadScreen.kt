package com.beatdrop.kt.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beatdrop.kt.PlayerViewModel
import com.beatdrop.kt.ui.components.pressableScale
import com.beatdrop.kt.ui.theme.LocalAppColors
import com.beatdrop.kt.youtube.DownloadManagerV2
import com.beatdrop.kt.youtube.OnlineResult
import com.beatdrop.kt.youtube.YouTubePlaylist

/**
 * Playlist download screen — fetches and shows all videos in a YouTube playlist
 * with batch download support.
 */
@Composable
fun PlaylistDownloadScreen(
    vm: PlayerViewModel,
    playlistId: String,
    onBack: () -> Unit,
) {
    val C = LocalAppColors.current
    var playlistInfo by remember { mutableStateOf<com.beatdrop.kt.youtube.PlaylistInfo?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var isDownloading by remember { mutableStateOf(false) }

    LaunchedEffect(playlistId) {
        isLoading = true
        playlistInfo = YouTubePlaylist.fetchPlaylist(playlistId)
        isLoading = false
    }

    val info = playlistInfo

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = C.text) }
            Column(Modifier.weight(1f)) {
                Text("Playlist", color = C.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                info?.let {
                    Text(it.title.ifBlank { playlistId }, color = C.textSecondary, fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // Batch controls
        info?.let { playlist ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${playlist.videos.size} videos", color = C.textSecondary, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    selectedIds = if (selectedIds.size == playlist.videos.size) emptySet()
                    else playlist.videos.map { it.videoId }.toSet()
                }) {
                    Text(if (selectedIds.size == playlist.videos.size) "Deselect All" else "Select All",
                        color = C.accent, fontSize = 12.sp)
                }
                if (selectedIds.isNotEmpty()) {
                    Button(
                        onClick = {
                            val app = vm.getApplication<android.app.Application>()
                            val selected = playlist.videos.filter { it.videoId in selectedIds }
                            DownloadManagerV2.enqueueBatch(selected, app)
                            isDownloading = true
                        },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Icon(Icons.Filled.Download, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Download ${selectedIds.size}", fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        when {
            isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = C.accent)
            }
            info == null || info.videos.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Could not load playlist", color = C.textSecondary)
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 160.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(info.videos, key = { it.videoId }) { result ->
                    val isSelected = result.videoId in selectedIds
                    Row(
                        Modifier.fillMaxWidth()
                            .pressableScale(onClick = {
                                selectedIds = if (isSelected) selectedIds - result.videoId
                                else selectedIds + result.videoId
                            })
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = {
                                selectedIds = if (isSelected) selectedIds - result.videoId
                                else selectedIds + result.videoId
                            },
                            colors = CheckboxDefaults.colors(checkedColor = C.accent),
                        )
                        Spacer(Modifier.width(4.dp))
                        Column(Modifier.weight(1f)) {
                            Text(result.title, color = C.text, fontSize = 13.sp,
                                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${result.author} · ${result.durationText}",
                                color = C.textSecondary, fontSize = 11.sp)
                        }
                        IconButton(onClick = { vm.playOnline(result) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.PlayArrow, "Play", tint = C.textTertiary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}
