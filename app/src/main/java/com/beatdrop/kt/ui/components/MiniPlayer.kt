package com.beatdrop.kt.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.beatdrop.kt.data.Track
import com.beatdrop.kt.ui.theme.LocalAppColors
import com.beatdrop.kt.ui.theme.Radius
import kotlin.math.abs

/**
 * Glass mini-player with gestures (Spotify/Apple style):
 *  - tap → expand to Now Playing
 *  - swipe left → next, swipe right → previous
 *  - swipe up → expand
 * The card follows the finger slightly and springs back.
 */
@Composable
fun MiniPlayer(
    track: Track,
    isPlaying: Boolean,
    progress: Float,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onExpand: () -> Unit,
) {
    val C = LocalAppColors.current
    val ctx = LocalContext.current

    var dragX by remember { mutableStateOf(0f) }
    var dragY by remember { mutableStateOf(0f) }
    val animX by animateFloatAsState(dragX, label = "miniX")
    val animY by animateFloatAsState(dragY.coerceAtMost(0f), label = "miniY")

    Box(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)
            .graphicsLayer { translationX = animX; translationY = animY }
            .clip(RoundedCornerShape(Radius.xxl))
            // Opaque base layer prevents content bleeding through
            .background(if (C.isDark) Color(0xFF101018) else Color(0xFFF2F2F7))
            .background(if (C.isDark) C.liquidGlass else Color.White.copy(alpha = 0.85f))
            .border(1.dp, C.liquidGlassBorder, RoundedCornerShape(Radius.xxl))
            .pointerInput(track.id) {
                detectDragGestures(
                    onDragEnd = {
                        when {
                            dragX < -120f -> onNext()
                            dragX > 120f -> onPrev()
                            dragY < -100f -> onExpand()
                        }
                        dragX = 0f; dragY = 0f
                    },
                    onDragCancel = { dragX = 0f; dragY = 0f },
                ) { change, amount ->
                    change.consume()
                    if (abs(amount.x) > abs(amount.y)) dragX += amount.x else dragY += amount.y
                }
            }
            .pointerInput(Unit) {
                // tap to expand (separate so it doesn't fight the drag detector)
                detectTapGestures(onTap = { onExpand() })
            },
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(Radius.md)).background(C.bg3)) {
                AsyncImage(
                    model = ImageRequest.Builder(ctx).data(track.artworkUri).crossfade(true).size(96).build(),
                    contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, color = C.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                Text(track.artist, color = C.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onToggle) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(C.accent), contentAlignment = Alignment.Center) {
                    Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            IconButton(onClick = onNext) { Icon(Icons.Filled.SkipNext, null, tint = C.text) }
        }
        Box(Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomStart).background(C.bg5)) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(C.accent))
        }
    }
}
