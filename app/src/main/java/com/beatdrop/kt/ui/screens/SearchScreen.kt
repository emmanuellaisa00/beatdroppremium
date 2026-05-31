package com.beatdrop.kt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
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

/**
 * Online search — fully wired against a pluggable backend (OnlineSearch.provider)
 * and the YoutubeExtractor hook. Until you configure those, it shows clear
 * "not configured" hints instead of fake data.
 */
@Composable
fun SearchScreen(vm: PlayerViewModel) {
    val C = LocalAppColors.current
    val ctx = LocalContext.current
    val q by vm.onlineQuery.collectAsState()
    val results by vm.onlineResults.collectAsState()
    val loading by vm.onlineLoading.collectAsState()
    val message by vm.onlineMessage.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.clearOnlineMessage() }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
            Text("Search", color = C.text, fontSize = 26.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(vertical = 10.dp))
            OutlinedTextField(
                value = q, onValueChange = vm::setOnlineQuery,
                placeholder = { Text("Search online") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.runOnlineSearch() }),
            )

            if (!vm.searchConfigured) {
                Spacer(Modifier.height(20.dp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.accentSoft).padding(16.dp)) {
                    Text(
                        "🔌 Online search is wired but no backend is configured.\n\n" +
                            "• Set OnlineSearch.provider to your own SearchProvider for results.\n" +
                            "• Implement YoutubeExtractor.extractStreamUrl(...) for play & download.\n\n" +
                            "Both live in the youtube/ package. The UI and ViewModel are ready.",
                        color = C.text, fontSize = 13.sp, lineHeight = 19.sp,
                    )
                }
            }

            when {
                loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = C.accent) }
                results.isNotEmpty() -> LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 160.dp)) {
                    items(results, key = { it.videoId }) { r ->
                        Row(
                            Modifier.fillMaxWidth().pressableScale(onClick = { vm.playOnline(r) }).padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(56.dp).clip(RoundedCornerShape(Radius.sm)).background(C.bg3), Alignment.Center) {
                                if (r.thumbnailUrl != null) {
                                    AsyncImage(model = ImageRequest.Builder(ctx).data(r.thumbnailUrl).crossfade(true).size(128).build(),
                                        contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                } else Icon(Icons.Filled.MusicNote, null, tint = C.textTertiary)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.title, color = C.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${r.author} · ${r.durationText}", color = C.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { vm.downloadOnline(r) { /* hand off to your download code */ } }) {
                                Icon(Icons.Filled.Download, "Download", tint = C.accent)
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 90.dp))
    }
}
