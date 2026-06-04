package com.beatdrop.kt.ui.components

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beatdrop.kt.ui.theme.LocalAppColors
import com.beatdrop.kt.ui.theme.Radius
import com.beatdrop.kt.ui.theme.Type


data class TabSpec2(val route: String, val label: String, val iconFilled: ImageVector, val iconOutlined: ImageVector)

// ═══════════════════════════════════════════════════════════════════════════════
// iOS 26 / Spotify Glassmorphism Bottom Tab Bar
// Spec: height=88px, radius=44px, blur=40px, accent=#21FF6B
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun GlassTabBar2(
    tabs: List<TabSpec2>,
    current: String,
    isScrolledDown: Boolean = false,
    onSelect: (String) -> Unit,
) {
    val C = LocalAppColors.current
    val haptic = LocalHapticFeedback.current
    val tilt = rememberDeviceTilt()

    // ── Scroll-responsive morphing ───────────────────────────────────────────
    val barHeight by animateDpAsState(
        targetValue = if (isScrolledDown) 56.dp else 88.dp,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f),
        label = "barHeight",
    )
    val iconSize by animateDpAsState(
        targetValue = if (isScrolledDown) 22.dp else 26.dp,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f),
        label = "iconSize",
    )
    val labelAlpha by animateFloatAsState(
        targetValue = if (isScrolledDown) 0f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "labelAlpha",
    )

    // ── Spec: outer radius=44px (pill), inner indicator uses concentric rule ─
    val outerRadius = 44.dp
    val outerShape  = RoundedCornerShape(outerRadius)

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                // ── Glass fill (rgba(18,18,22,.45) + gradient top-light) ─────
                .background(C.glassNav)
                .drawWithContent {
                    drawContent()
                    // Top reflection gradient
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.08f),
                                Color.Transparent,
                            ),
                            startY = 0f,
                            endY = size.height * 0.35f,
                        ),
                    )
                    // Bottom inner glow for depth
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                if (C.isDark) Color.White.copy(alpha = 0.04f)
                                         else Color.White.copy(alpha = 0.06f),
                            ),
                            startY = size.height * 0.70f,
                            endY = size.height,
                        ),
                    )
                }
                // ── Backdrop blur (40px = heavy level) ────────────────────────
                .then(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Modifier.graphicsLayer {
                            renderEffect = RenderEffect.createChainEffect(
                                RenderEffect.createColorFilterEffect(
                                    android.graphics.ColorMatrixColorFilter(
                                        android.graphics.ColorMatrix().apply { setSaturation(1.8f) }
                                    )
                                ),
                                RenderEffect.createBlurEffect(40f, 40f, Shader.TileMode.CLAMP),
                            ).asComposeRenderEffect()
                        }
                    } else Modifier
                )
                // ── Specular highlight (device tilt) ──────────────────────────
                .specularHighlight(tilt, intensity = if (C.isDark) 0.08f else 0.06f, radius = 320f)
                // ── Rim light (Fresnel top-edge) ─────────────────────────────
                .rimLight(outerRadius)
                // ── Hairline border ──────────────────────────────────────────
                .border(
                    width  = if (C.isDark) 1.dp else 0.7.dp,
                    color  = C.glassNavBorder,
                    shape  = outerShape,
                ),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                tabs.forEachIndexed { _, tab ->
                    val isActive = tab.route == current
                    LiquidTabItem(
                        tab      = tab,
                        active   = isActive,
                        iconSize = iconSize,
                        labelAlpha = labelAlpha,
                        modifier = Modifier.weight(1f),
                        onClick  = {
                            if (isActive) return@LiquidTabItem
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(tab.route)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LiquidTabItem(
    tab: TabSpec2,
    active: Boolean,
    iconSize: Dp,
    labelAlpha: Float,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current

    // Spring animation for scale
    val scale by animateFloatAsState(
        targetValue = if (active) 1.06f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "tabScale2",
    )

    // ── Active icon puck (spec: 64x64px, blur(30px)) ─────────────────────
    val puckShape = RoundedCornerShape(32.dp)

    Box(
        modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Active background pill — glass puck with green accent glow
        if (active) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(puckShape)
                    .background(
                        if (C.isDark) Color.White.copy(alpha = 0.08f)
                                     else Color.White.copy(alpha = 0.15f),
                    )
                    .border(
                        width  = 1.dp,
                        color  = if (C.isDark) Color.White.copy(alpha = 0.15f)
                                 else Color.White.copy(alpha = 0.30f),
                        shape  = puckShape,
                    )
                    .then(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Modifier.graphicsLayer {
                                renderEffect = RenderEffect.createChainEffect(
                                    RenderEffect.createColorFilterEffect(
                                        android.graphics.ColorMatrixColorFilter(
                                            android.graphics.ColorMatrix().apply { setSaturation(1.8f) }
                                        )
                                    ),
                                    RenderEffect.createBlurEffect(30f, 30f, Shader.TileMode.CLAMP),
                                ).asComposeRenderEffect()
                            }
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // Green accent glow inside puck
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(puckShape)
                        .background(
                            C.accent.copy(alpha = 0.20f),
                        )
                        .border(0.5.dp, C.accent.copy(alpha = 0.30f), puckShape)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.scale(scale),
        ) {
            Icon(
                imageVector = if (active) tab.iconFilled else tab.iconOutlined,
                contentDescription = tab.label,
                tint = if (active) C.accent else C.textSecondary,
                modifier = Modifier.size(iconSize),
            )
            // Label below icon (spec: 10sp, letter-spacing 0.1sp)
            if (labelAlpha > 0.01f) {
                Spacer(Modifier.height(2.dp))
                Text(
                    tab.label,
                    fontSize       = 10.sp,
                    fontWeight     = FontWeight.Medium,
                    letterSpacing  = 0.1.sp,
                    color = if (active) C.accent.copy(alpha = labelAlpha)
                            else C.textTertiary.copy(alpha = labelAlpha * 0.7f),
                    maxLines = 1,
                )
            }
        }
    }
}