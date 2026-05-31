package com.beatdrop.kt.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.beatdrop.kt.PlayerViewModel
import com.beatdrop.kt.ui.components.pressableScale
import com.beatdrop.kt.ui.theme.LocalAppColors
import com.beatdrop.kt.ui.theme.Radius
import com.beatdrop.kt.youtube.DownloadStatus
import com.beatdrop.kt.youtube.OnlineResult

@Composable
fun SearchScreen(vm: PlayerViewModel) {
    val C        = LocalAppColors.current
    val ctx      = LocalContext.current
    val q        by vm.onlineQuery.collectAsState()
    val results  by vm.onlineResults.collectAsState()
    val loading  by vm.onlineLoading.collectAsState()
    val message  by vm.onlineMessage.collectAsState()
    val suggestions by vm.suggestions.collectAsState()
    val jobs     by vm.downloadJobs.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.clearOnlineMessage() }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)
        ) {
            Text(
                "Search", color = C.text, fontSize = 26.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.padding(vertical = 10.dp),
            )

            // ── Search field ──────────────────────────────────────────────────
            OutlinedTextField(
                value = q,
                onValueChange = { vm.setOnlineQuery(it); if (it.length >= 2) vm.loadSuggestions() else Unit },
                placeholder = { Text("Search songs, artists…") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (q.isNotEmpty()) IconButton(onClick = { vm.setOnlineQuery("") }) {
                        Icon(Icons.Filled.Close, "Clear")
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.runOnlineSearch() }),
            )

            // ── Suggestions chips ─────────────────────────────────────────────
            AnimatedVisibility(visible = suggestions.isNotEmpty() && results.isEmpty()) {
                LazyColumn(
                    contentPadding = PaddingValues(top = 8.dp),
                    modifier = Modifier.heightIn(max = 200.dp),
                ) {
                    items(suggestions) { suggestion ->
                        Row(
                            Modifier.fillMaxWidth()
                                .pressableScale(onClick = {
                                    vm.setOnlineQuery(suggestion)
                                    vm.runOnlineSearch()
                                })
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.History, null, tint = C.textTertiary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(suggestion, color = C.text, fontSize = 14.sp)
                        }
                        Divider(color = C.bg3.copy(alpha = 0.5f), thickness = 0.5.dp)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            when {
                loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = C.accent)
                }
                results.isNotEmpty() -> {
                    Text(
                        "${results.size} results",
                        color = C.textSecondary, fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    LazyColumn(contentPadding = PaddingValues(bottom = 160.dp)) {
                        items(results, key = { it.videoId }) { r ->
                            val job = jobs[r.videoId]
                            OnlineTrackRow(
                                result  = r,
                                job     = job,
                                onPlay  = { vm.playOnline(r) },
                                onDownload = {
                                    when (job?.status) {
                                        DownloadStatus.FAILED -> vm.retryDownload(r)
                                        DownloadStatus.QUEUED,
                                        DownloadStatus.DOWNLOADING -> vm.cancelDownload(r.videoId)
                                        else -> vm.downloadOnline(r)
                                    }
                                },
                            )
                        }
                    }
                }
                else -> {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.MusicNote, null,
                                tint = C.textTertiary,
                                modifier = Modifier.size(56.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text("Search YouTube for songs to stream or download",
                                color = C.textSecondary, fontSize = 14.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }

        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 90.dp),
        )
    }
}

// ─── Single search result row ─────────────────────────────────────────────────
@Composable
private fun OnlineTrackRow(
    result: OnlineResult,
    job: com.beatdrop.kt.youtube.DownloadJob?,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
) {
    val C   = LocalAppColors.current
    val ctx = LocalContext.current

    Row(
        Modifier.fillMaxWidth()
            .pressableScale(onClick = onPlay)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Thumbnail
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(Radius.sm)).background(C.bg3),
            Alignment.Center,
        ) {
            if (result.thumbnailUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(ctx).data(result.thumbnailUrl).crossfade(true).size(128).build(),
                    contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else Icon(Icons.Filled.MusicNote, null, tint = C.textTertiary)

            // Live badge
            if (result.isLive) {
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(2.dp)
                        .background(Color.Red, RoundedCornerShape(3.dp)).padding(horizontal = 3.dp),
                ) {
                    Text("LIVE", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        // Title + meta
        Column(Modifier.weight(1f)) {
            Text(result.title, color = C.text, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${result.author} · ${result.durationText}",
                color = C.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        // Download button with state
        DownloadStatusButton(
            job      = job,
            onClick  = onDownload,
            accentColor = C.accent,
        )
    }
}

// ─── Download button: idle / queued / progress ring / done / failed ───────────
@Composable
private fun DownloadStatusButton(
    job: com.beatdrop.kt.youtube.DownloadJob?,
    onClick: () -> Unit,
    accentColor: Color,
) {
    val status   = job?.status ?: DownloadStatus.IDLE
    val progress = job?.progress ?: 0

    // Pulse animation for QUEUED state
    val alpha by animateFloatAsState(
        targetValue = if (status == DownloadStatus.QUEUED) 0.5f else 1f,
        animationSpec = if (status == DownloadStatus.QUEUED)
            infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse)
        else tween(200),
        label = "pulse",
    )

    IconButton(onClick = onClick) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            when (status) {
                DownloadStatus.DOWNLOADING -> {
                    CircularProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxSize(),
                        color = accentColor,
                        trackColor = accentColor.copy(alpha = 0.2f),
                        strokeWidth = 2.5.dp,
                        strokeCap = StrokeCap.Round,
                    )
                    Text("$progress", color = accentColor, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
                DownloadStatus.COMPLETED -> Icon(
                    Icons.Filled.CheckCircle, "Downloaded",
                    tint = Color(0xFF34C759), modifier = Modifier.fillMaxSize(),
                )
                DownloadStatus.FAILED -> Icon(
                    Icons.Filled.ErrorOutline, "Retry download",
                    tint = Color(0xFFFF453A), modifier = Modifier.fillMaxSize(),
                )
                DownloadStatus.QUEUED -> Icon(
                    Icons.Filled.CloudDownload, "Queued",
                    tint = accentColor.copy(alpha = alpha), modifier = Modifier.fillMaxSize(),
                )
                DownloadStatus.IDLE -> Icon(
                    Icons.Filled.Download, "Download",
                    tint = accentColor.copy(alpha = 0.6f), modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
