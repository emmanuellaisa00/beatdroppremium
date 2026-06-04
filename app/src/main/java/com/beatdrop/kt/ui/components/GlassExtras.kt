package com.beatdrop.kt.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beatdrop.kt.ui.components.Ic
import com.beatdrop.kt.ui.theme.LocalAppColors
import com.beatdrop.kt.ui.theme.Radius
import com.beatdrop.kt.ui.theme.Spacing
import com.beatdrop.kt.ui.theme.Type
import com.beatdrop.kt.ui.theme.Blur
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import kotlin.random.Random

// ═══════════════════════════════════════════════════════════════════════════════
// Haze backdrop blur — real CSS-style backdrop-filter for Compose.
//
//   How this works (chrisbanes/haze 0.7.3):
//
//     1. ScreenScaffold creates a HazeState and provides it via LocalHazeState.
//     2. ScreenScaffold applies `Modifier.haze(state, style)` to its root Box.
//        That registers the Box as the *source* — everything painted into
//        the Box is captured into a render node that haze can sample.
//     3. Any glass surface (card / row / header / mini player / tab bar / sheet)
//        calls `Modifier.hazeGlass(shape, ...)` which routes to `hazeChild`.
//        The child reads back the source pixels under its bounds, blurs and
//        tints them, then draws THAT as the surface background.
//
//   Net effect: real backdrop blur. The surface's own children (text, icons)
//   are NOT blurred — they are painted on top of the blurred-source layer
//   like normal.
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Page-level [HazeState], set by [ScreenScaffold]. Null on screens that don't
 * use the scaffold (Splash, Onboarding, VideoPlayer, NowPlaying transport).
 * [Modifier.hazeGlass] is a no-op when this is null.
 */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * Real backdrop blur on a glass surface. Resolves to a no-op when no
 * [LocalHazeState] is in scope (so it's always safe to chain on).
 *
 * Call this BEFORE `.background(...)` in the modifier chain so the tinted
 * surface paints on top of the blurred backdrop, not under it.
 */
@Composable
fun Modifier.hazeGlass(
    shape: Shape,
    tintColor: Color,
    blurRadius: Dp = 28.dp,
    noiseFactor: Float = HazeDefaults.noiseFactor,
): Modifier {
    val state = LocalHazeState.current ?: return this
    val style = HazeStyle(
        tint        = tintColor,
        blurRadius  = blurRadius,
        noiseFactor = noiseFactor,
    )
    return this.hazeChild(state = state, shape = shape, style = style)
}

// ═══════════════════════════════════════════════════════════════════════════════
// Z-Layer System  (spec §15)
//   0 Background │ 10 Artwork │ 20 Cards │ 30 Tabs │ 40 Navigation
//   50 Mini Player │ 60 Floating Actions │ 70 Modal │ 80 Sheet │ 90 Overlay
// ═══════════════════════════════════════════════════════════════════════════════
object Z {
    const val background     = 0f
    const val artwork        = 10f
    const val card           = 20f
    const val tabs           = 30f
    const val navigation     = 40f
    const val miniPlayer     = 50f
    const val floatingAction = 60f
    const val modal          = 70f
    const val sheet          = 80f
    const val overlay        = 90f
}

// ═══════════════════════════════════════════════════════════════════════════════
// Noise Overlay  (spec §5)
//
//   Required. Without noise glass looks fake / digital / cheap.
//   With noise it reads as physical / organic / premium.
//
//   Implementation: deterministic sparse dither rendered with BlendMode.Overlay
//   via a single drawPoints() call. Cached per element size (drawWithCache) so
//   the random points list is generated once and replayed.
// ═══════════════════════════════════════════════════════════════════════════════
fun Modifier.noiseOverlay(
    opacity: Float = 0.03f,
    seed: Long = 0x21FF6BL,
    densityDivisor: Int = 120,
): Modifier = this.drawWithCache {
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val n = ((w * h) / densityDivisor).toInt().coerceIn(64, 6000)
    val rng = Random(seed xor (w.toLong() * 31L + h.toLong()))
    val points = List(n) { Offset(rng.nextFloat() * w, rng.nextFloat() * h) }
    // Pre-multiply: drawPoints alpha is uniform so we scale opacity up a touch
    // because BlendMode.Overlay tones down whites that are already bright.
    val color = Color.White.copy(alpha = (opacity * 4f).coerceAtMost(1f))
    onDrawWithContent {
        drawContent()
        drawPoints(
            points     = points,
            pointMode  = PointMode.Points,
            color      = color,
            strokeWidth = 1f,
            blendMode  = BlendMode.Overlay,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Ambient Glow  (spec §13)
//
//   Radial background glow — placed under content at Z=0. Use accent green for
//   "Player glow" surfaces and the cool blue (purple/blue 30..80,200) for the
//   general "Background glow".
// ═══════════════════════════════════════════════════════════════════════════════
fun Modifier.ambientGlow(
    color: Color,
    intensity: Float = 0.18f,
    centerX: Float = 0.5f,
    centerY: Float = 0.30f,
    radiusFactor: Float = 0.85f,
): Modifier = this.drawBehind {
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = intensity), Color.Transparent),
            center = Offset(size.width * centerX, size.height * centerY),
            radius = size.maxDimension * radiusFactor,
        ),
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
// Glass Shadow  (spec §3 — 0 10px 30px rgba(0,0,0,.25) + 0 30px 80px rgba(0,0,0,.55))
//
//   Compose's `shadow` modifier accepts ambient + spot color (API 28+). We
//   approximate the two stacked shadows with elevation+softer spotColor.
// ═══════════════════════════════════════════════════════════════════════════════
fun Modifier.glassShadow(
    elevation: Dp = 18.dp,
    shape: Shape,
    isDark: Boolean = true,
): Modifier = this.shadow(
    elevation     = elevation,
    shape         = shape,
    clip          = false,
    ambientColor  = if (isDark) Color.Black.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.18f),
    spotColor     = if (isDark) Color.Black.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.22f),
)

// ═══════════════════════════════════════════════════════════════════════════════
// Icon Puck  (spec §9 — 64x64, blur(30px), white .08, rim, inner shadow)
//
//   Used for active-state icons (e.g. play/pause inline, subscribed avatar
//   indicator, settings group glyphs).
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun IconPuck(
    icon: ImageVector,
    contentDescription: String? = null,
    size: Dp = 64.dp,
    tint: Color = LocalAppColors.current.text,
    modifier: Modifier = Modifier,
) {
    val C = LocalAppColors.current
    Box(
        modifier
            .size(size)
            .glassShadow(elevation = 12.dp, shape = CircleShape, isDark = C.isDark)
            .clip(CircleShape)
            .hazeGlass(shape = CircleShape, tintColor = C.glassFloating, blurRadius = 30.dp)
            .background(C.glassFloating)
            .drawWithContent {
                drawContent()
                // Top rim — inset 0 1px 0 rgba(255,255,255,.15)
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (C.isDark) 0.18f else 0.30f),
                            Color.Transparent,
                        ),
                        startY = 0f,
                        endY = this.size.height * 0.4f,
                    ),
                )
            }
            .noiseOverlay(opacity = 0.025f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = contentDescription,
            tint               = tint,
            modifier           = Modifier.size(size * 0.42f),
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Glass Header  (floating top app bar — blur 28-40px, status-bar padded)
//
//   Replaces the ad-hoc IconButton/Text rows scattered across secondary
//   screens. Pulls the full rendering stack via masterGlass().
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun GlassHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    leadingIcon: ImageVector? = null,
    leadingTint: Color? = null,
) {
    val C = LocalAppColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        val headerShape = RoundedCornerShape(Radius.xl)
        Row(
            Modifier
                .fillMaxWidth()
                .height(58.dp)
                .glassShadow(elevation = 14.dp, shape = headerShape, isDark = C.isDark)
                .clip(headerShape)
                .hazeGlass(shape = headerShape, tintColor = C.glassFloating, blurRadius = Blur.heavy.dp)
                .background(C.glassFloating)
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                C.glassHighlight.copy(alpha = if (C.isDark) 0.14f else 0.26f),
                                Color.Transparent,
                            ),
                            startY = 0f,
                            endY = size.height * 0.45f,
                        ),
                    )
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                C.glassInnerShadow.copy(alpha = 0.06f),
                            ),
                            startY = size.height * 0.6f,
                            endY = size.height,
                        ),
                    )
                }
                .noiseOverlay(opacity = 0.03f)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Ic.Back, "Back", tint = C.text)
                }
            } else {
                Spacer(Modifier.width(Spacing.md))
            }
            if (leadingIcon != null) {
                Icon(leadingIcon, null, tint = leadingTint ?: C.accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Spacing.sm))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = Type.title2,
                    color = C.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = Type.caption,
                        color = C.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                trailing()
                Spacer(Modifier.width(4.dp))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Screen Scaffold  (spec §17 — App ─ BackgroundGlow ─ AmbientNoise ─ Page)
//
//   Every page consuming the design system should wrap its content in this.
//   It provides:
//     • base bg color (Z=0)
//     • optional radial ambient glow
//     • app-wide noise overlay
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun ScreenScaffold(
    modifier: Modifier = Modifier,
    ambientColor: Color? = null,
    ambientIntensity: Float = 0.18f,
    showNoise: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val C = LocalAppColors.current
    val glow = ambientColor ?: C.glassAmbient
    val hazeState = remember { HazeState() }
    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Box(
            modifier
                .fillMaxSize()
                .background(C.bg0)
                .ambientGlow(glow, intensity = ambientIntensity)
                .then(if (showNoise) Modifier.noiseOverlay(opacity = 0.025f) else Modifier)
                // Register as the haze source — anything painted into this Box
                // (page content, scrollable bodies, artwork, gradients) becomes
                // available as the backdrop for any descendant glass surface
                // that calls Modifier.hazeGlass(...).
                .haze(state = hazeState),
            content = content,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Section Header  (typography spec §7 — 11sp bold, 1.2sp tracking)
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun SectionHeader(label: String, modifier: Modifier = Modifier) {
    val C = LocalAppColors.current
    Text(
        label.uppercase(),
        style = Type.overline,
        color = C.textTertiary,
        modifier = modifier.padding(start = Spacing.lg, top = Spacing.xl, bottom = Spacing.xs),
    )
}
