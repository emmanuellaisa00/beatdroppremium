package com.beatdrop.kt.ui.screens

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.beatdrop.kt.PlayerViewModel
import com.beatdrop.kt.ui.components.Ic
import com.beatdrop.kt.ui.components.ScreenScaffold
import com.beatdrop.kt.ui.components.pressableScale
import com.beatdrop.kt.ui.theme.LocalAppColors
import com.beatdrop.kt.ui.theme.Radius
import com.beatdrop.kt.ui.theme.Spacing
import com.beatdrop.kt.ui.theme.Type
import com.beatdrop.kt.youtube.OnlineAlbum
import com.beatdrop.kt.youtube.OnlineResult
import com.beatdrop.kt.youtube.YouTubePlaylist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * OnlineAlbumScreen — Spotify/iOS-style album-detail screen for YT Music
 * search results.
 *
 *   ┌─────────────────────────────────┐
 *   │   ⟨ back               …  share │
 *   │                                 │
 *   │        ┌──────────┐             │
 *   │        │  cover   │   (blurred  │
 *   │        │   art    │    cover as │
 *   │        └──────────┘    backdrop)│
 *   │   Album Title (Bold 28sp)       │
 *   │   Artist · 2024                 │
 *   │                                 │
 *   │   [ ▶ Play ]   ↻ Shuffle        │
 *   │                                 │
 *   │   ── Tracks ────                │
 *   │   1  Track one          3:42    │
 *   │   2  Track two          4:21    │
 *   │   …                              │
 *   └─────────────────────────────────┘
 *
 * Tracks are fetched on first composition via YouTubePlaylist.fetchPlaylist
 * (albums are backed by an OLAK5uy_… playlist). The cover is shown
 * immediately from the album's thumbnailUrl + drawn behind everything
 * with a 60-px blur as a backdrop (matches iOS).
 */
@Composable
fun OnlineAlbumScreen(
    vm: PlayerViewModel,
    album: OnlineAlbum,
    onBack: () -> Unit,
    onExpandPlayer: () -> Unit,
) {
    val C   = LocalAppColors.current
    val ctx = LocalContext.current
    var tracks by remember { mutableStateOf<List<OnlineResult>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(album.audioPlaylistId) {
        loading = true
        val fetched = runCatching {
            withContext(Dispatchers.IO) {
                YouTubePlaylist.fetchPlaylist(album.audioPlaylistId, maxItems = 100).videos
            }
        }.getOrDefault(emptyList())
        tracks = fetched
        loading = false
    }

    ScreenScaffold {
        Box(Modifier.fillMaxSize()) {
            // ── Blurred-cover backdrop ────────────────────────────────────
            if (album.thumbnailUrl != null) {
                val blurMod =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                        Modifier.graphicsLayer {
                            renderEffect = RenderEffect
                                .createBlurEffect(60f, 60f, Shader.TileMode.CLAMP)
                                .asComposeRenderEffect()
                        }
                    else Modifier
                AsyncImage(
                    model = ImageRequest.Builder(ctx).data(album.thumbnailUrl)
                        .crossfade(true).size(720).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.55f)
                        .then(blurMod),
                )
                // Vertical dim gradient so text on top stays legible.
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0.0f  to Color.Transparent,
                            0.45f to C.bg0.copy(alpha = 0.72f),
                            1.0f  to C.bg0,
                        ),
                    ),
                )
            } else {
                Box(Modifier.fillMaxSize().background(C.bg0))
            }

            // ── Foreground content ───────────────────────────────────────
            LazyColumn(
                Modifier.fillMaxSize().statusBarsPadding(),
                contentPadding = PaddingValues(bottom = 180.dp),
            ) {
                // Top bar
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Ic.Back, "Back", tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.weight(1f))
                    }
                }
                // Cover
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(240.dp)
                                .clip(RoundedCornerShape(Radius.lg))
                                .background(C.bg3),
                        ) {
                            if (album.thumbnailUrl != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(ctx).data(album.thumbnailUrl)
                                        .crossfade(true).size(720).build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
                // Title + artist + year
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Text(
                            album.title,
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            buildString {
                                append(album.artist)
                                if (album.year.isNotBlank()) append(" · ").append(album.year)
                            },
                            color = Color.White.copy(alpha = 0.78f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(18.dp))
                        // Play + Shuffle buttons.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                Modifier
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(C.accent)
                                    .pressableScale(
                                        onClick = {
                                            vm.playAlbum(album)
                                            onExpandPlayer()
                                        },
                                        scaleTo = 0.96f,
                                    )
                                    .padding(horizontal = 22.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Ic.TransportPlay, null,
                                        tint = Color.Black, modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Play", color = Color.Black,
                                        fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            IconButton(
                                onClick = {
                                    // Shuffle: pick a random starting track
                                    val pick = tracks.randomOrNull() ?: return@IconButton
                                    vm.playAlbum(album, startVideoId = pick.videoId)
                                    onExpandPlayer()
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.12f)),
                            ) {
                                Icon(Ic.Shuffle, "Shuffle", tint = Color.White,
                                    modifier = Modifier.size(22.dp))
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
                // Loading silhouettes / track list
                if (loading && tracks.isEmpty()) {
                    item {
                        com.beatdrop.kt.ui.components.SearchResultSilhouettes(
                            rowCount = 8,
                            modifier = Modifier.padding(horizontal = Spacing.lg),
                        )
                    }
                } else {
                    itemsIndexed(tracks, key = { _, t -> t.videoId }) { idx, track ->
                        AlbumTrackRow(
                            index = idx + 1,
                            title = track.title,
                            artist = if (track.author != album.artist) track.author else null,
                            duration = track.durationText.ifBlank {
                                if (track.durationSecs > 0)
                                    "%d:%02d".format(track.durationSecs / 60, track.durationSecs % 60)
                                else ""
                            },
                            onPlay = {
                                vm.playAlbum(album, startVideoId = track.videoId)
                                onExpandPlayer()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumTrackRow(
    index: Int,
    title: String,
    artist: String?,
    duration: String,
    onPlay: () -> Unit,
) {
    val C = LocalAppColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .pressableScale(onClick = onPlay, scaleTo = 0.98f)
            .padding(horizontal = Spacing.lg, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            index.toString(),
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 14.sp,
            modifier = Modifier.width(28.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!artist.isNullOrBlank()) {
                Text(
                    artist,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (duration.isNotBlank()) {
            Text(
                duration,
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 13.sp,
            )
        }
    }
}
