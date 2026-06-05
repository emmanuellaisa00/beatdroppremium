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
    val baseTintAlpha = 0.78f + tintBoost.coerceIn(0f, 0.20f)
    val noiseOpacity = 0.015f       // spec: 1.5%
    val rimAlpha = if (C.isDark) 0.10f else 0.16f
    val borderAlpha = if (C.isDark) 0.06f else 0.10f
    val blueRefractionAlpha = 0.03f  // 'Slight Blue Refraction' per spec

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
        // ── Surface clip (Layer 6) ────────────────────────────────────
        .clip(shape)
        // ── Backdrop sampling + blur (Layers 1, 2) ────────────────────
        .hazeGlass(
            shape = shape,
            tintColor = C.bg3.copy(alpha = baseTintAlpha),
            blurRadius = level.blurPx.dp,
        )
        // ── Darkening filter (Layer 3) ────────────────────────────────
        // Belt-and-braces over the hazeGlass tint so on devices without
        // RenderEffect support the glass still reads as smoked.
        .background(C.bg3.copy(alpha = baseTintAlpha), shape)
        // ── Blue refraction tint (per spec 'Slight Blue Refraction') ──
        // Adds the just-noticeable cool cast that distinguishes our
        // smoked glass from generic dark plastic. Subtle — 3% blue.
        .background(Color(0x66102030).copy(alpha = blueRefractionAlpha), shape)
        // ── Inner reflection (Layer 7) — top horizontal highlight ─────
        .drawWithContent {
            drawContent()
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
            // ── Bottom diffusion reflection ───────────────────────────
            // Subtle white wash at the bottom edge — adds optical
            // 'depth' as if light pools at the base of the glass slab.
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
) {
    Z1_List       (Blur.z1,  2f,  0f),
    Z2_Card       (Blur.z2,  8f,  0f),
    Z3_MiniPlayer (Blur.z3, 16f, 22f),
    Z4_TabBar     (Blur.z4, 18f, 26f),
    Z5_ActiveLens (Blur.z5,  8f, 14f),
    Z6_Floating   (Blur.z6, 22f, 32f);

    val ambientShadowDp: Dp get() = ambientShadowDpValue.dp
    val volumeShadowDp:  Dp get() = volumeShadowDpValue.dp
}
