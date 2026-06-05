package com.beatdrop.kt.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.beatdrop.kt.ui.components.Ic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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

// ═══════════════════════════════════════════════════════════════════════════════
// Spotify Glassmorphism Mini Player
// Spec: blur(50px) — higher than nav for elevation, outer radius=44dp
// accent=#21FF6B (Spotify Green)
// ═══════════════════════════════════════════════════════════════════════════════

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
    val tilt = rememberDeviceTilt()

    var dragX by remember { mutableStateOf(0f) }
    var dragY by remember { mutableStateOf(0f) }
    val animX by animateFloatAsState(dragX, label = "miniX")
    val animY by animateFloatAsState(dragY.coerceAtMost(0f), label = "miniY")

    // Concept target: floating iOS-style glass capsule sitting above the dock.
    // Larger art, calmer controls, no handle, stronger rim, and real backdrop
    // blur from the global HazeState provided by MainScaffold.
    val outerRadius = 42.dp
    val outerShape  = RoundedCornerShape(outerRadius)
    val artShape    = CircleShape

    Box(
        Modifier
            .fillMaxWidth()
            .height(84.dp)
            .padding(horizontal = 16.dp)
            .graphicsLayer {
                translationX = animX
                translationY = animY
                shape = outerShape
                clip = false
            }
            // Background-only glass. Do NOT use haze/premiumGlass on the
            // content container here: on some devices Haze draws above child
            // composables, turning the MiniPlayer into an empty black pill.
            .shadow(
                elevation = 24.dp,
                shape = outerShape,
                ambientColor = Color.Black.copy(alpha = 0.55f),
                spotColor = Color.Black.copy(alpha = 0.45f),
            )
            .clip(outerShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF232832).copy(alpha = if (C.isDark) 0.78f else 0.82f),
                        Color(0xFF11141A).copy(alpha = if (C.isDark) 0.70f else 0.78f),
                    ),
                ),
            )
            .border(0.8.dp, Color.White.copy(alpha = if (C.isDark) 0.18f else 0.28f), outerShape)
            // Blue refraction on the left edge, matching the uploaded concept's
            // cool smoked-glass MiniPlayer.
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF5B8CFF).copy(alpha = if (C.isDark) 0.08f else 0.10f),
                            Color.Transparent,
                        ),
                        startX = 0f,
                        endX = size.width * 0.46f,
                    ),
                )
            }
            .specularHighlight(tilt, intensity = if (C.isDark) 0.10f else 0.07f, radius = 240f)
            .pointerInput(track.id) {
                detectDragGestures(
                    onDragEnd = {
                        when {
                            dragX < -120f -> onNext()
                            dragX >  120f -> onPrev()
                            dragY < -100f -> onExpand()
                        }
                        dragX = 0f; dragY = 0f
                    },
                    onDragCancel = { dragX = 0f; dragY = 0f },
                ) { change, amount ->
                    change.consume()
                    if (abs(amount.x) > abs(amount.y)) dragX += amount.x
                    else dragY += amount.y
                }
            }
            .pointerInput(Unit) { detectTapGestures(onTap = { onExpand() }) },
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Artwork inset: rounded jewel under the glass surface.
            Box(
                Modifier
                    .size(60.dp)
                    .border(1.dp, Color.Black.copy(alpha = 0.42f), artShape)
                    .clip(artShape)
                    .background(C.bg3),
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(ctx)
                        .data(track.artworkUri)
                        .crossfade(true)
                        .size(coil.size.Size(140, 140))
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .matchParentSize()
                        .border(0.6.dp, Color.White.copy(alpha = 0.18f), artShape)
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.08f),
                                        Color.Transparent,
                                    ),
                                    startY = 0f,
                                    endY = size.height * 0.42f,
                                ),
                            )
                        },
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = C.text,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 15.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = track.artist,
                    color = C.textSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            IconButton(onClick = onExpand) {
                Icon(
                    imageVector = Ic.Airplay,
                    contentDescription = null,
                    tint = C.textSecondary,
                    modifier = Modifier.size(26.dp),
                )
            }

            IconButton(onClick = onToggle, modifier = Modifier.size(58.dp)) {
                Box(
                    Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(27.dp))
                        .background(Color.White.copy(alpha = if (C.isDark) 0.92f else 0.96f))
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.55f),
                                        Color.Transparent,
                                    ),
                                    startY = 0f,
                                    endY = size.height * 0.5f,
                                ),
                            )
                        }
                        .border(0.7.dp, Color.White.copy(alpha = 0.36f), RoundedCornerShape(27.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isPlaying) Ic.TransportPause else Ic.TransportPlay,
                        contentDescription = null,
                        tint = Color.Black.copy(alpha = 0.88f),
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }

        // Ultra-subtle progress: visible enough to be useful, but not a hard
        // Material line through the glass capsule.
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 32.dp)
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(1.5.dp)
                .background(C.accent.copy(alpha = 0.70f))
        )
    }
}