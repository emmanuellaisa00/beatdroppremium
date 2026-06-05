package com.beatdrop.kt.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
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

    // ── Premium Glass spec: outer radius 40dp, RadiusFamily.xl ───────
    // Spec: 'Mini Player Shape — Height 74-80, Radius 38-40. Very soft.
    // Feels like polished glass stone.' Concentric inner radius for the
    // album-art inset still derived mathematically.
    val outerRadius = 40.dp
    val innerRadius = Radius.inner(outerRadius, 8.dp)  // 40 - 8 = 32dp
    val outerShape  = RoundedCornerShape(outerRadius)

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .graphicsLayer {
                translationX = animX
                translationY = animY
                shape = outerShape
                clip = false
            }
            // ── Unified Premium Glass material (Z3 — mini player) ────
            // Replaces the previous bespoke hazeGlass + background +
            // gradients + rimLight + border stack with the SINGLE
            // material the rest of the app uses. Per spec rule:
            // 'never create different glass styles.'
            .premiumGlass(level = GlassLevel.Z3_MiniPlayer, shape = outerShape)
            // ── Spec lighting: Left cool blue, Right neutral ─────────
            // The premiumGlass material already paints top reflection +
            // bottom diffusion. The mini-player adds an additional
            // LATERAL lighting pass — cool-blue cast on the left edge,
            // neutral fade on the right — to give the slab a sense of
            // 'lit from upper-left' that vertical lighting alone can't
            // produce. Per spec: 'Left: cool blue / Right: neutral'.
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF4080FF).copy(alpha = if (C.isDark) 0.06f else 0.10f),
                            Color.Transparent,
                        ),
                        startX = 0f,
                        endX = size.width * 0.45f,
                    ),
                )
            }
            // ── Specular highlight (device tilt) ──────────────────────
            // Kept — the mini player is large enough + always on screen,
            // so the tilt-tracked highlight reads as 'real glass'.
            .specularHighlight(tilt, intensity = if (C.isDark) 0.10f else 0.08f, radius = 220f)
            // ── Gestures: tap → expand, swipe left/right → next/prev ─────────
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
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onExpand() })
            },
    ) {
        Column {
            // ── Drag handle hint ──────────────────────────────────────────
            Box(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(
                    Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.3f))
                )
            }
            Row(Modifier.padding(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 10.dp), 
                verticalAlignment = Alignment.CenterVertically) {
            // ── Artwork — concentric inner radius (36dp) ─────────────────────
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(innerRadius))
                    .background(C.bg3)
            ) {
                AsyncImage(
                    model  = ImageRequest.Builder(ctx)
                        .data(track.artworkUri)
                        .crossfade(true)
                        .size(coil.size.Size(96, 96))
                        .build(),
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize(),
                )
            }

            Spacer(Modifier.width(12.dp))

            // ── Metadata: track title + artist ───────────────────────────────
            Column(Modifier.weight(1f)) {
                Text(
                    text      = track.title,
                    color     = C.text,
                    fontWeight = FontWeight.SemiBold,
                    maxLines  = 1,
                    overflow  = TextOverflow.Ellipsis,
                    fontSize  = 14.sp,
                )
                Text(
                    text      = track.artist,
                    color     = C.textSecondary,
                    fontSize  = 12.sp,
                    maxLines  = 1,
                    overflow  = TextOverflow.Ellipsis,
                )
            }

            // ── Tinted glass play button (Spotify Green accent) ─────────────
            IconButton(onClick = onToggle) {
                TintedGlassButton(
                    modifier     = Modifier.size(40.dp),
                    tintColor    = C.accent,
                    cornerRadius = 20.dp,
                ) {
                    Icon(
                        imageVector = if (isPlaying) Ic.TransportPause else Ic.TransportPlay,
                        contentDescription = null,
                        tint         = Color.White,
                        modifier     = Modifier.size(20.dp),
                    )
                }
            }

            // ── Skip next ───────────────────────────────────────────────────
            IconButton(onClick = onNext) {
                Icon(
                    imageVector = Ic.SkipNext,
                    contentDescription = null,
                    tint         = C.text,
                    modifier     = Modifier.size(22.dp),
                )
            }
        }
        } // closes Column from drag handle

        // ── Progress bar — accent green, refined 2.5dp ───────────────────────
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.5.dp)
                .align(Alignment.BottomStart)
                .background(
                    if (C.isDark) Color(0x1AFFFFFF)
                    else Color(0x14000000)
                )
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(C.accent)
            )
        }
    }
}