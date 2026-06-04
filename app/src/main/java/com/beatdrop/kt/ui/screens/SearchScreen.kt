package com.beatdrop.kt.ui.screens

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.beatdrop.kt.ui.components.BeatDropSearchField
import com.beatdrop.kt.ui.components.Ic
import com.beatdrop.kt.ui.components.ScreenScaffold
import com.beatdrop.kt.ui.components.glassRow
import com.beatdrop.kt.PlayerViewModel
import com.beatdrop.kt.ui.components.pressableScale
import com.beatdrop.kt.ui.theme.LocalAppColors
import com.beatdrop.kt.ui.theme.Radius
import com.beatdrop.kt.ui.theme.Type
import com.beatdrop.kt.youtube.DownloadStatus
import com.beatdrop.kt.youtube.OnlineResult

// ═══════════════════════════════════════════════════════════════════════════════
// Spotify Glassmorphism Search Screen
// Accent: #21FF6B (Spotify Green)
// Glass search bar with blur 28px
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun SearchScreen(
    vm: PlayerViewModel,
    onExpandPlayer: () -> Unit = {},
    onOpenOnlineAlbum: (com.beatdrop.kt.youtube.OnlineAlbum) -> Unit = {},
) {
    val C = LocalAppColors.current
    val q          by vm.onlineQuery.collectAsState()
    val results    by vm.onlineResults.collectAsState()
    val albums     by vm.albumResults.collectAsState()
    val playlists  by vm.playlistResults.collectAsState()
    val searching  by vm.isSearching.collectAsState()
    val message    by vm.onlineMessage.collectAsState()
    val suggestions by vm.suggestions.collectAsState()
    val history    by vm.searchHistory.collectAsState()
    val jobs       by vm.downloadJobs.collectAsState()

    var snackbarMessage   by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            snackbarMessage = null
        }
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    val listState          = rememberLazyListState()

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) keyboardController?.hide()
    }

    val lastFailed by vm.lastFailedOnline.collectAsState()

    LaunchedEffect(message) {
        message?.let { msg ->
            val result = if (lastFailed != null) {
                snackbarHostState.showSnackbar(msg, actionLabel = "Retry")
            } else {
                snackbarHostState.showSnackbar(msg)
            }
            if (result == SnackbarResult.ActionPerformed) vm.retryOnlinePlay()
            vm.clearOnlineMessage()
        }
    }

    ScreenScaffold {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            // ── Header ──────────────────────────────────────────────────────
            Text(
                "Browse",
                color     = C.text,
                fontSize  = 28.sp,
                fontWeight = FontWeight.Black,
                modifier  = Modifier.padding(vertical = 10.dp),
            )

            // ── Offline banner ──────────────────────────────────────────────
            val isOnline = com.beatdrop.kt.util.NetworkMonitor.isOnline.value
            if (!isOnline) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFFF3CD))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Ic.WifiOff, null, tint = Color(0xFF856404), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("You're offline. Search results won't load.", color = Color(0xFF856404), fontSize = 12.sp)
                }
            }

            // ── Unified search field ─────────────────────────────────────────
            // Replaces the legacy OutlinedTextField inside glassRow which
            // (a) used C.textTertiary (38% white) for the icon and placeholder
            // — invisible on the glass surface — and (b) had no focus state.
            // The shared BeatDropSearchField is opaque, has an accent focus
            // ring, an explicit clear button, and a green "Search" submit pill.
            BeatDropSearchField(
                value = q,
                onChange = {
                    vm.setOnlineQuery(it)
                    if (it.length >= 2) vm.loadSuggestions()
                },
                placeholder = "Search songs, artists, albums…",
                onSubmit = { vm.runOnlineSearch() },
                submitting = searching,
            )

            // ── Search history or autocomplete suggestions ──────────────────
            val showHistory    = q.isEmpty() && history.isNotEmpty() && results.isEmpty()
            val showSuggestions = q.isNotEmpty() && suggestions.isNotEmpty() && results.isEmpty()

            AnimatedVisibility(visible = showHistory || showSuggestions) {
                LazyColumn(
                    contentPadding = PaddingValues(top = 8.dp),
                    modifier       = Modifier.heightIn(max = 260.dp),
                ) {
                    if (showHistory) {
                        item {
                            Text(
                                "Recent Searches",
                                color     = C.textSecondary,
                                fontSize  = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier  = Modifier.padding(start = 6.dp, top = 8.dp, bottom = 6.dp),
                            )
                        }
                        items(history) { query ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .pressableScale(onClick = {
                                        vm.setOnlineQuery(query)
                                        vm.runOnlineSearch()
                                    })
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Ic.History, null, tint = C.textTertiary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    query, color = C.text, fontSize = 15.sp, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick  = { vm.deleteHistoryQuery(query) },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(Ic.Close, "Delete", tint = C.textTertiary, modifier = Modifier.size(16.dp))
                                }
                            }
                            HorizontalDivider(color = C.separator, thickness = 0.5.dp)
                        }
                    } else if (showSuggestions) {
                        items(suggestions) { suggestion ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .pressableScale(onClick = {
                                        vm.setOnlineQuery(suggestion)
                                        vm.runOnlineSearch()
                                    })
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Ic.Search, null, tint = C.textTertiary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(14.dp))
                                Text(suggestion, color = C.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            HorizontalDivider(color = C.separator, thickness = 0.5.dp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            when {
                // No spinner — content-shaped silhouettes with accent shimmer
                // sweep instead. The shape matches CatalogRow so there's no
                // layout shift when real results arrive.
                searching -> com.beatdrop.kt.ui.components.SearchResultSilhouettes(
                    rowCount = 6,
                    modifier = Modifier.padding(top = 4.dp),
                )
                results.isNotEmpty() || albums.isNotEmpty() || playlists.isNotEmpty() -> {
                    LazyColumn(
                        state          = listState,
                        contentPadding = PaddingValues(bottom = 160.dp),
                    ) {
                        // ── Albums section (Spotify-style horizontal carousel) ──
                        if (albums.isNotEmpty()) {
                            item {
                                SectionEyebrow("Albums", count = albums.size)
                            }
                            item {
                                AlbumCarousel(
                                    albums = albums,
                                    onOpen = onOpenOnlineAlbum,
                                )
                                Spacer(Modifier.height(16.dp))
                            }
                        }
                        // ── Playlists section ──────────────────────────────────
                        if (playlists.isNotEmpty()) {
                            item {
                                SectionEyebrow("Playlists", count = playlists.size)
                            }
                            item {
                                PlaylistCarousel(
                                    playlists = playlists,
                                    onOpen = { pl ->
                                        // Reuse playFeaturedPlaylist plumbing — it
                                        // starts playback and sets the onlineContext.
                                        vm.playFeaturedPlaylist(pl.playlistId)
                                        onExpandPlayer()
                                    },
                                )
                                Spacer(Modifier.height(16.dp))
                            }
                        }
                        // ── Songs header ───────────────────────────────────────
                        if (results.isNotEmpty()) {
                            item {
                                SectionEyebrow("Songs", count = results.size)
                            }
                        }
                        itemsIndexed(results, key = { _, r -> r.videoId }) { idx, r ->
                            val job = jobs[r.videoId]
                            // Predictive prefetch — if the row stays on
                            // screen >400 ms (i.e. user isn't fly-scrolling
                            // past it), kick off a background stream
                            // resolution so the next tap is ~instant.
                            LaunchedEffect(r.videoId) {
                                kotlinx.coroutines.delay(400)
                                vm.prefetchOnlineUrl(r.videoId)
                            }
                            CatalogRow(
                                result  = r,
                                isSaved = job?.status == DownloadStatus.COMPLETED,
                                onPlay  = {
                                    // Smart behavior: if this exact song is already playing, just open Now Playing
                                    // instead of restarting it. This matches Spotify behavior.
                                    val current = vm.current.value
                                    if (current?.sourceVideoId == r.videoId) {
                                        onExpandPlayer()
                                    } else {
                                        vm.prepareAndPlayOnline(r, results, idx)
                                        onExpandPlayer()
                                    }
                                },
                                onSave  = {
                                    when (job?.status) {
                                        DownloadStatus.FAILED        -> vm.retryDownload(r)
                                        DownloadStatus.QUEUED,
                                        DownloadStatus.DOWNLOADING   -> vm.cancelDownload(r.videoId)
                                        else                         -> vm.downloadOnline(r)
                                    }
                                },
                            )
                        }
                    }
                }
                !searching && q.isNotEmpty() && results.isEmpty() -> {
                    // Explicit "no results" state
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Ic.Search, null,
                                tint     = C.textTertiary.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "No results for \"$q\"",
                                color     = C.textSecondary,
                                fontSize  = 15.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Try different keywords or check your connection",
                                color     = C.textTertiary,
                                fontSize  = 13.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier  = Modifier.padding(horizontal = 32.dp),
                            )
                        }
                    }
                }
                else -> {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Ic.MusicNote, null,
                                tint     = C.textTertiary.copy(alpha = 0.6f),
                                modifier = Modifier.size(64.dp),
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Search millions of songs",
                                color     = C.textSecondary,
                                fontSize  = 15.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Find any song to stream or save to your library",
                                color     = C.textTertiary,
                                fontSize  = 13.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }

        SnackbarHost(
            snackbarHostState,
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 90.dp),
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Catalog-style result row — glass play overlay, Spotify Green save icon
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun CatalogRow(
    result: OnlineResult,
    isSaved: Boolean,
    onPlay: () -> Unit,
    onSave: () -> Unit,
) {
    val C  = LocalAppColors.current
    val ctx = LocalContext.current

    Row(
        Modifier
            .fillMaxWidth()
            .pressableScale(onClick = onPlay)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Artwork with glass play overlay
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(Radius.md))
                .background(C.bg3),
            Alignment.Center,
        ) {
            if (result.thumbnailUrl != null) {
                AsyncImage(
                    model  = ImageRequest.Builder(ctx).data(result.thumbnailUrl).crossfade(true).size(128).build(),
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize(),
                )
            }
            // Glass play overlay
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Ic.TransportPlay, "Play", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }

        Spacer(Modifier.width(14.dp))

        // Title + artist
        Column(Modifier.weight(1f)) {
            Text(
                result.title,
                color     = C.text,
                fontWeight = FontWeight.SemiBold,
                fontSize  = 15.sp,
                maxLines  = 1,
                overflow  = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                result.author,
                color     = C.textSecondary,
                fontSize  = 13.sp,
                maxLines  = 1,
                overflow  = TextOverflow.Ellipsis,
            )
        }

        // Duration
        Text(
            result.durationText,
            color     = C.textTertiary,
            fontSize  = 12.sp,
            modifier  = Modifier.padding(horizontal = 8.dp),
        )

        // Save action — Spotify Green when saved
        IconButton(
            onClick   = onSave,
            modifier  = Modifier.size(36.dp),
        ) {
            Icon(
                Ic.Bookmark,
                if (isSaved) "Saved to library" else "Save to library",
                tint     = if (isSaved) C.accent else C.textTertiary,   // Green when saved
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
// ─── Section headers + typed result carousels ─────────────────────────────────

@Composable
private fun SectionEyebrow(label: String, count: Int) {
    val C = LocalAppColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 8.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color      = C.text,
            fontSize   = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier   = Modifier.weight(1f),
        )
        Text(
            count.toString(),
            color    = C.textTertiary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun AlbumCarousel(
    albums: List<com.beatdrop.kt.youtube.OnlineAlbum>,
    onOpen: (com.beatdrop.kt.youtube.OnlineAlbum) -> Unit,
) {
    val C = LocalAppColors.current
    val ctx = LocalContext.current
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        items(albums, key = { it.browseId }) { album ->
            Column(
                Modifier
                    .width(150.dp)
                    .pressableScale(onClick = { onOpen(album) }, scaleTo = 0.96f),
            ) {
                Box(
                    Modifier
                        .size(150.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(C.bg3),
                ) {
                    if (album.thumbnailUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(ctx)
                                .data(album.thumbnailUrl).crossfade(true).size(512).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    album.title,
                    color    = C.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append("Album")
                        if (album.artist.isNotBlank()) append(" · ").append(album.artist)
                        if (album.year.isNotBlank())   append(" · ").append(album.year)
                    },
                    color    = C.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PlaylistCarousel(
    playlists: List<com.beatdrop.kt.youtube.OnlinePlaylist>,
    onOpen: (com.beatdrop.kt.youtube.OnlinePlaylist) -> Unit,
) {
    val C = LocalAppColors.current
    val ctx = LocalContext.current
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        items(playlists, key = { it.playlistId }) { pl ->
            Column(
                Modifier
                    .width(150.dp)
                    .pressableScale(onClick = { onOpen(pl) }, scaleTo = 0.96f),
            ) {
                Box(
                    Modifier
                        .size(150.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(C.bg3),
                ) {
                    if (pl.thumbnailUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(ctx)
                                .data(pl.thumbnailUrl).crossfade(true).size(512).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    pl.title,
                    color    = C.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append("Playlist")
                        if (pl.author.isNotBlank())   append(" · ").append(pl.author)
                        if (pl.trackCount > 0)        append(" · ").append("${pl.trackCount} tracks")
                    },
                    color    = C.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
