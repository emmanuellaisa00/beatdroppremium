package com.beatdrop.kt.ui.screens

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
import com.beatdrop.kt.data.Subscriptions
import com.beatdrop.kt.ui.components.pressableScale
import com.beatdrop.kt.ui.theme.LocalAppColors
import com.beatdrop.kt.youtube.OnlineResult
import com.beatdrop.kt.youtube.YouTubeTrending

/**
 * Channel screen — shows a YouTube channel's latest videos with subscribe/unsubscribe.
 */
@Composable
fun ChannelScreen(
    vm: PlayerViewModel,
    channelId: String,
    channelName: String,
    channelThumb: String?,
    onExpandPlayer: () -> Unit,
    onBack: () -> Unit,
) {
    val C = LocalAppColors.current
    val context = LocalContext.current
    var isSubscribed by remember { mutableStateOf(Subscriptions.isSubscribed(channelId)) }
    var videos by remember { mutableStateOf<List<OnlineResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(channelId) {
        isLoading = true
        videos = YouTubeTrending.fetchChannelVideos(channelId)
        isLoading = false
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = C.text) }
        }

        // Channel info card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = C.bg2),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Row(
                Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Avatar
                Box(Modifier.size(56.dp).clip(CircleShape)) {
                    channelThumb?.let {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(it).crossfade(true).size(128).build(),
                            contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } ?: Icon(Icons.Filled.Person, null, tint = C.textTertiary, modifier = Modifier.size(56.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(channelName, color = C.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("${videos.size} videos", color = C.textSecondary, fontSize = 13.sp)
                }
                // Subscribe button
                Button(
                    onClick = {
                        if (isSubscribed) {
                            Subscriptions.unsubscribe(channelId)
                        } else {
                            Subscriptions.subscribe(Subscriptions.Channel(
                                channelId = channelId,
                                name = channelName,
                                thumbnailUrl = channelThumb,
                            ))
                        }
                        isSubscribed = !isSubscribed
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSubscribed) C.bg3 else C.accent,
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    Text(
                        if (isSubscribed) "Subscribed" else "Subscribe",
                        color = if (isSubscribed) C.text else Color.White,
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Videos
        when {
            isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = C.accent)
            }
            videos.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("No videos found", color = C.textSecondary)
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 160.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(videos, key = { it.videoId }) { result ->
                    Row(
                        Modifier.fillMaxWidth().pressableScale(onClick = {
                            vm.playOnline(result)
                            onExpandPlayer()
                        }).padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(result.title, color = C.text, fontSize = 14.sp,
                                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(result.author + " · " + result.durationText,
                                color = C.textSecondary, fontSize = 12.sp, maxLines = 1)
                        }
                        IconButton(onClick = { vm.downloadOnline(result) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Download, "Download", tint = C.textTertiary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}
