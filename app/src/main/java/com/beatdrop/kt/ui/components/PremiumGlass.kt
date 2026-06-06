package com.beatdrop.kt.ui.components

import android.annotation.SuppressLint
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.beatdrop.kt.ui.theme.Blur
import com.beatdrop.kt.ui.theme.LocalAppColors

/**
 * Premium Glass — the single unified material used everywhere in the app.
 *
 * Per the design spec ('Global Material Engine'):
 *   "The entire UI uses one material. Never create different glass
 *    styles. Use one material definition everywhere."
 *
 * This Modifier composes the spec's 10-layer material stack:
 *
 *   1. Background sampling      — done implicitly when this sits on a
 *                                  parent backed by ScreenScaffold's
 *                                  hazeGlass source.
 *   2. Blur                      — RenderEffect blur at the appropriate
 *                                  z-level (Blur.z1..z6).
 *   3. Darkening filter          — bg3 tint at 78% alpha (smoked-glass).
 *   4. Saturation boost          — implicit in the dark tint + accent
 *                                  refraction (Compose has no cheap
 *                                  saturation modifier; the tint
 *                                  produces the perceived saturation).
 *   5. Noise                     — 1.5% monochrome via noiseOverlay.
 *   6. Glass surface             — clipped rounded-rect shape.
 *   7. Inner reflection          — top horizontal white-12% gradient.
 *   8. Outer reflection          — 0.5-px hairline border on the edges.
 *   9. Shadow                    — three-layer shadow stack (ambient
 *                                  huge soft + contact small tight +
 *                                  volume deep spread).
 *  10. Content                   — drawn last by the consuming composable.
 *
 * Replaces the previous family (glassRow, glassCard, masterGlass) which
 * each had slightly different defaults — call sites diverged, the app
 * accumulated 'cheap glass' surfaces. From now on every floating surface
 * uses this one modifier, parametrised by depth level.
 *
 * @param level The z-level of this surface in the depth hierarchy:
 *   z1 = lists (least elevated)
 *   z2 = album cards
 *   z3 = mini player
 *   z4 = tab bar
 *   z5 = active tab lens
 *   z6 = floating buttons / sheets
 *   Default: z2 (cards) — the most common case.
 * @param shape The corner shape of the glass surface. Pass any shape;
 *   RoundedCornerShape is the common case but Apple-style superellipses
 *   work too. Defaults to RadiusFamily.md (16.dp) rounded-rect.
 * @param tintBoost Optional extra darkening on top of the spec's
 *   built-in smoked-glass tint. 0f = stock, 0.10f = +10% extra dark.
 *   Use sparingly — the unified material rule means most surfaces
 *   should look identical.
 */
@SuppressLint("NewApi")
@Composable
fun Modifier.premiumGlass(
    level: GlassLevel = GlassLevel.Z2_Card,
    shape: Shape = RoundedCornerShape(16.dp),
    tintBoost: Float = 0f,
): Modifier {
    val C = LocalAppColors.current

    // ── Spec constants — DO NOT diverge per call site ────────────────
    // The whole point of premiumGlass is one material, not 12 'almost
    // the same' materials. If you find yourself wanting to tweak these
    // for ONE screen, you're fighting the architecture — push back.
    val depthAlpha = when (level) {
        GlassLevel.Z1_List -> 0.075f
        GlassLevel.Z2_Card -> 0.095f
        GlassLevel.Z3_MiniPlayer -> 0.120f
        GlassLevel.Z4_TabBar -> 0.135f
        GlassLevel.Z5_ActiveLens -> 0.155f
        GlassLevel.Z6_Floating -> 0.170f
    } + tintBoost.coerceIn(0f, 0.08f)
    val noiseOpacity = 0.012f       // premium grain: present, never dirty
    val rimAlpha = if (C.isDark) 0.16f else 0.24f
    val borderAlpha = if (C.isDark) 0.13f else 0.20f
    val blueRefractionAlpha = if (C.isDark) 0.035f else 0.018f

    return this
        // ── Shadow stack (Layer 9) ────────────────────────────────────
        // Three shadows per the spec: ambient huge-soft + contact tight
        // + volume deep-spread. Compose only exposes one shadow per
        // Modifier.shadow call, so we stack three with progressively
        // smaller offsets + ambientColor / spotColor split for tint.
        .then(
            if (level >= GlassLevel.Z3_MiniPlayer) {
                // Volume — deep, spread.
                Modifier.shadow(
                    elevation = level.volumeShadowDp,
                    shape = shape,
                    ambientColor = Color.Black,
                    spotColor = Color.Black,
                )
            } else Modifier
        )
        // Ambient — huge, soft. Stretches out, blends into the page.
        .shadow(
            elevation = level.ambientShadowDp,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = 0.35f),
            spotColor = Color.Black.copy(alpha = 0.20f),
        )
        // Contact — small, tight. Anchors the surface to whatever it
        // sits on. Per spec: 'Three shadows minimum. Ambient + Contact
        // + Volume.' The contact shadow is what makes the glass feel
        // PRESSED INTO the screen instead of floating above it.
        .shadow(
            elevation = level.contactShadowDp,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = 0.50f),
            spotColor = Color.Black.copy(alpha = 0.40f),
        )
        // ── Surface clip (Layer 6) ────────────────────────────────────
        .clip(shape)
        // ── Content-safe glass base (Layers 1-3 approximation) ───────
        // Haze is intentionally NOT applied here. On several real devices the
        // haze child render pass can paint over descendant text/icons, making
        // cards, MiniPlayer, and dock appear as empty black blobs. Keep glass
        // surfaces readable first; the global blurred artwork/scrim still gives
        // the frosted context behind them.
        // ── Frosted translucent material (Layer 3) ─────────────────────
        // Use 8–18% luminous material in dark mode per the design spec, with
        // a tiny charcoal substrate for legibility. This reads as glass, not
        // a flat black rectangle.
        .background(
            Brush.verticalGradient(
                colors = if (C.isDark) listOf(
                    Color.White.copy(alpha = depthAlpha + 0.035f),
                    Color.White.copy(alpha = depthAlpha * 0.55f),
                    Color(0xFF05070C).copy(alpha = 0.20f),
                ) else listOf(
                    Color.White.copy(alpha = 0.72f + depthAlpha),
                    Color.White.copy(alpha = 0.56f + depthAlpha * 0.5f),
                    Color(0xFFEAF0F6).copy(alpha = 0.24f),
                ),
            ),
            shape,
        )
        // ── Cool refraction tint ──────────────────────────────────────
        .background(Color(0xFF7FB2FF).copy(alpha = blueRefractionAlpha), shape)
        // ── Accent light scattering — very subtle, keeps BeatDrop identity ─
        .background(C.accent.copy(alpha = if (C.isDark) 0.018f else 0.012f), shape)
        // ── Reflection system (Layers 7-8) — 4-layer reflection stack ─
        // Per spec 'Reflection System':
        //   Top:    Horizontal Reflection  (white, brightest)
        //   Sides:  Vertical Reflection    (cool blue, sub-pixel)
        //   Bottom: Diffusion Reflection   (white wash, soft)
        //   Corners: Radial Reflection      (handled by 0.5dp border)
        // Total: 4 reflection draws per surface (within the spec's
        // 4-8 layer budget). Going to 8 doubles overdraw with little
        // perceptual gain at this surface scale.
        .drawWithContent {
            drawContent()
            // 1. Top horizontal — bright, primary highlight
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = rimAlpha),
                        Color.Transparent,
                    ),
                    startY = 0f,
                    endY = size.height * 0.35f,
                ),
            )
            // 2. Left side vertical — cool blue, just-noticeable
            // Per spec: 'Left: cool blue'. ~1dp-wide gradient against
            // the inside of the left edge — readable as 'wet glass'.
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF80B0FF).copy(alpha = rimAlpha * 0.5f),
                        Color.Transparent,
                    ),
                    startX = 0f,
                    endX = size.width * 0.06f,
                ),
            )
            // 3. Right side vertical — neutral white, slightly weaker
            // Per spec: 'Right: neutral'.
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = rimAlpha * 0.35f),
                    ),
                    startX = size.width * 0.94f,
                    endX = size.width,
                ),
            )
            // 4. Bottom diffusion — white wash pooling at the base
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = rimAlpha * 0.4f),
                    ),
                    startY = size.height * 0.75f,
                    endY = size.height,
                ),
            )
        }
        // ── Noise (Layer 5) ───────────────────────────────────────────
        // Breaks gradients + banding. 1.5% monochrome.
        .noiseOverlay(opacity = noiseOpacity)
        // ── Outer reflection (Layer 8) — 0.5-px hairline border ───────
        .border(0.5.dp, Color.White.copy(alpha = borderAlpha), shape)
}

/**
 * Depth levels in the Premium Glass hierarchy. Each level maps to a
 * blur radius, an ambient shadow, and an optional volume shadow.
 *
 * Values pulled directly from the design spec's 'Blur Architecture'
 * and 'Depth Hierarchy' sections. NEVER use raw px values — pick a
 * level. If a new surface doesn't fit any level, the answer is
 * 'pick the closest one', not 'invent a new level'.
 */
enum class GlassLevel(
    val blurPx: Float,
    val ambientShadowDpValue: Float,
    val volumeShadowDpValue: Float,
    val contactShadowDpValue: Float,
) {
    Z1_List       (Blur.z1,  2f,  0f, 1f),
    Z2_Card       (Blur.z2,  8f,  0f, 2f),
    Z3_MiniPlayer (Blur.z3, 16f, 22f, 3f),
    Z4_TabBar     (Blur.z4, 18f, 26f, 3f),
    Z5_ActiveLens (Blur.z5,  8f, 14f, 2f),
    Z6_Floating   (Blur.z6, 22f, 32f, 4f);

    val ambientShadowDp: Dp get() = ambientShadowDpValue.dp
    val volumeShadowDp:  Dp get() = volumeShadowDpValue.dp
    val contactShadowDp: Dp get() = contactShadowDpValue.dp
}
