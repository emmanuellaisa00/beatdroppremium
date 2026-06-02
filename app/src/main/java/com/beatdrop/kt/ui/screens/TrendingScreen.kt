package com.beatdrop.kt.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import kotlinx.coroutines.async
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.beatdrop.kt.PlayerViewModel
import com.beatdrop.kt.ui.components.pressableScale
import com.beatdrop.kt.ui.theme.LocalAppColors
import com.beatdrop.kt.youtube.OnlineResult
import com.beatdrop.kt.youtube.YouTubeTrending

/**
 * Online trending / discovery screen.
 * Shows YouTube Music trending, new releases, and curated playlists.
 */
@Composable
fun TrendingScreen(
    vm: PlayerViewModel,
    onExpandPlayer: () -> Unit,
    onBack: () -> Unit,
) {
    val C = LocalAppColors.current
    var trending by remember { mutableStateOf<List<OnlineResult>>(emptyList()) }
    var newReleases by remember { mutableStateOf<List<OnlineResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        isLoading = true
        val t = async { YouTubeTrending.fetchTrending() }
        val n = async { YouTubeTrending.fetchNewReleases() }
        trending = t.await()
        newReleases = n.await()
        isLoading = false
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = C.text) }
            Text("Discover", color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Black)
        }

        // Tabs
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            FilterChip(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                label = { Text("🔥 Trending") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = C.accent),
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                label = { Text("✨ New Releases") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = C.accent),
            )
        }

        Spacer(Modifier.height(12.dp))

        when {
            isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = C.accent)
            }
            else -> {
                val items = if (selectedTab == 0) trending else newReleases
                if (items.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text("Nothing available right now", color = C.textSecondary, fontSize = 15.sp)
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(bottom = 160.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(items, key = { it.videoId }) { result ->
                            TrendingRow(
                                result = result,
                                onPlay = {
                                    vm.playOnline(result)
                                    onExpandPlayer()
                                },
                                onDownload = { vm.downloadOnline(result) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendingRow(result: OnlineResult, onPlay: () -> Unit, onDownload: () -> Unit) {
    val C = LocalAppColors.current
    val ctx = LocalContext.current

    Row(
        Modifier.fillMaxWidth()
            .pressableScale(onClick = onPlay)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Thumbnail
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(8.dp))) {
            if (result.thumbnailUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(ctx).data(result.thumbnailUrl).crossfade(true).size(128).build(),
                    contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                Modifier.fillMaxSize(),
                Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(result.title, color = C.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(result.author, color = C.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(result.durationText, color = C.textTertiary, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp))
        IconButton(onClick = onDownload, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Download, "Download", tint = C.textTertiary, modifier = Modifier.size(20.dp))
        }
    }
}
