package com.beatdrop.kt.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beatdrop.kt.PlayerViewModel
import com.beatdrop.kt.ui.theme.LocalAppColors
import com.beatdrop.kt.util.ClipboardWatcher

/**
 * Screen shown when a URL is detected (from clipboard, share menu, or deep link).
 * Shows video info and offers Play/Download options.
 */
@Composable
fun ClipUrlScreen(
    vm: PlayerViewModel,
    url: String,
    onExpandPlayer: () -> Unit,
    onBack: () -> Unit,
) {
    val C = LocalAppColors.current
    var isLoading by remember { mutableStateOf(false) }
    val detected = remember { ClipboardWatcher.parseUrl(url) }

    LaunchedEffect(url) {
        if (detected != null && !detected.isPlaylist) {
            isLoading = true
            vm.playOnlineByUrl(url)
            isLoading = false
        }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = C.text) }
            Text("Link Detected", color = C.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(32.dp))
        Icon(Icons.Filled.Link, null, tint = C.accent, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(url, color = C.textSecondary, fontSize = 14.sp)
        if (detected != null) {
            Spacer(Modifier.height(8.dp))
            Text("Platform: ${detected.platform}", color = C.textTertiary, fontSize = 12.sp)
            if (detected.isPlaylist) {
                Text("Playlist detected", color = C.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(24.dp))
        if (isLoading) {
            CircularProgressIndicator(color = C.accent)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { vm.playOnlineByUrl(url); onExpandPlayer() },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Play")
                }
                OutlinedButton(
                    onClick = { vm.downloadOnlineByUrl(url) },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Filled.Download, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Download")
                }
            }
        }
    }
}
