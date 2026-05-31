package com.beatdrop.kt.ui.components

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beatdrop.kt.lyrics.LyricLine
import com.beatdrop.kt.ui.theme.LocalAppColors
import kotlin.math.abs

/**
 * Apple-Music-style synced lyrics:
 *  - Active line: largest, bold, full white, sharp focus
 *  - Adjacent lines: progressively dimmer, smaller, blurred (API 31+)
 *  - Fading gradient mask at top and bottom edges
 *  - Auto-scrolls to keep active line centred
 *  - Tap any line to seek to it
 *  - Pulsing dot placeholder for instrumental gaps
 */
@Composable
fun AppleLyrics(
    lines: List<LyricLine>,
    activeIndex: Int,
    modifier: Modifier = Modifier,
    onSeek: (Long) -> Unit = {},
) {
    val C = LocalAppColors.current
    val state = rememberLazyListState()

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            state.animateScrollToItem(
                index = activeIndex.coerceAtLeast(0),
                scrollOffset = -300,
            )
        }
    }

    // ── Fading-edge gradient mask (top + bottom fade out) ─────────────────
    LazyColumn(
        state = state,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val fadeH = 96.dp.toPx()
                // Single mask rect: transparent at both edges, opaque in centre
                drawRect(
                    brush = Brush.verticalGradient(
                        0.00f to Color.Transparent,
                        0.13f to Color.Black,
                        0.87f to Color.Black,
                        1.00f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
        contentPadding = PaddingValues(top = 80.dp, bottom = 220.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        itemsIndexed(lines, key = { i, _ -> i }) { i, line ->
            val isActive  = i == activeIndex
            val distance  = abs(i - activeIndex)

            // ── Per-line visual properties ────────────────────────────────
            val targetAlpha = when (distance) {
                0    -> 1.00f
                1    -> 0.65f
                2    -> 0.42f
                3    -> 0.28f
                else -> 0.18f
            }
            val scale by animateFloatAsState(
                targetValue = when (distance) {
                    0 -> 1.00f
                    1 -> 0.93f
                    else -> 0.88f
                },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness    = Spring.StiffnessLow,
                ),
                label = "lyricScale",
            )
            val color by animateColorAsState(
                targetValue = when {
                    isActive       -> Color.White
                    distance == 1  -> Color.White.copy(alpha = 0.65f)
                    distance == 2  -> Color.White.copy(alpha = 0.42f)
                    distance == 3  -> Color.White.copy(alpha = 0.28f)
                    else           -> Color.White.copy(alpha = 0.18f)
                },
                label = "lyricColor",
            )

            // Blur radius: real RenderEffect on API 31+; pure alpha fallback below
            val blurPx = when (distance) {
                0, 1 -> 0f
                2    -> 3f
                3    -> 6f
                else -> 9f
            }
            val blurMod = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurPx > 0f) {
                Modifier.graphicsLayer {
                    renderEffect = RenderEffect
                        .createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
                        .asComposeRenderEffect()
                }
            } else Modifier

            if (line.text.isBlank()) {
                GapDots(isActive)
            } else {
                Text(
                    text       = line.text,
                    color      = color,
                    fontSize   = if (isActive) 30.sp else if (distance == 1) 24.sp else 21.sp,
                    fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
                    textAlign  = TextAlign.Start,
                    lineHeight = if (isActive) 36.sp else 28.sp,
                    modifier   = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 9.dp)
                        .graphicsLayer {
                            scaleX          = scale
                            scaleY          = scale
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            alpha           = targetAlpha
                        }
                        .then(blurMod)
                        .pressableScale(onClick = { onSeek(line.timeMs) }, scaleTo = 0.97f, haptic = false),
                )
            }
        }
    }
}

@Composable
private fun GapDots(active: Boolean) {
    val alpha by animateFloatAsState(if (active) 1f else 0.3f, label = "gap")
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(3) {
            Text(
                "●",
                color    = Color.White.copy(alpha = alpha),
                fontSize = if (active) 14.sp else 10.sp,
            )
        }
    }
}
