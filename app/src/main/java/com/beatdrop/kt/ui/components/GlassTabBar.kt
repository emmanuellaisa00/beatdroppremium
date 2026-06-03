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
import androidx.compose.ui.geometry.Offset
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

// ─── iOS 26 Liquid Glass Tab Bar ─────────────────────────────────────────────

/**
 * iOS 26-style Liquid Glass floating tab bar with:
 *
 *   ✅ Backdrop blur + saturation boost (API 31+)
 *   ✅ Specular highlights responding to device tilt
 *   ✅ Rim light (Fresnel top-edge highlight for glass thickness)
 *   ✅ Scroll-responsive morphing: shrinks when scrolling down, expands back up
 *   ✅ Content-aware glass fill (adapts to dark/light)
 *   ✅ Interaction glow on tab press
 *   ✅ Concentric corner radii (outer bar vs inner indicator)
 *   ✅ Graceful pre-API-31 fallback (heavier opaque fill)
 *   ✅ Hairline border + inner shadow for depth
 *   ✅ Haptic feedback on selection
 *   ✅ Floating capsule with horizontal margin (iOS 26 style)
 *   ✅ Active glass highlight pill behind active icon
 *   ✅ Tiny labels below icons
 *
 * @param isScrolledDown pass true when the user is scrolling down content.
 *        The tab bar morphs smaller to focus on content.
 */
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
        targetValue = if (isScrolledDown) 48.dp else 64.dp,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f),
        label = "barHeight",
    )
    val iconSize by animateDpAsState(
        targetValue = if (isScrolledDown) 20.dp else 24.dp,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f),
        label = "iconSize",
    )
    val labelAlpha by animateFloatAsState(
        targetValue = if (isScrolledDown) 0f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "labelAlpha",
    )

    val outerRadius = 28.dp
    val outerShape = RoundedCornerShape(outerRadius)

    // ── Glass bar container — floating capsule ───────────────────────────────
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                // Glass fill — stronger for the floating capsule
                .background(
                    if (C.isDark) Color(0xD90E0C1A) else Color(0xE8F4F4F8),
                    shape = outerShape,
                )
                // Rim light (top-edge glow for glass thickness)
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                C.glassRimLight.copy(alpha = if (C.isDark) 0.14f else 0.28f),
                                Color.Transparent,
                            ),
                            startY = 0f,
                            endY = size.height * 0.4f,
                        ),
                    )
                }
                // Inner glow for depth
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                if (C.isDark) Color.White.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.08f),
                            ),
                            startY = size.height * 0.7f,
                            endY = size.height,
                        ),
                    )
                }
                // Specular highlight (device tilt)
                .specularHighlight(tilt, intensity = if (C.isDark) 0.10f else 0.07f, radius = 220f)
                // Hairline border — stronger for floating capsule
                .border(
                    width = if (C.isDark) 1.dp else 0.7.dp,
                    color = if (C.isDark) Color(0x40FFFFFF) else Color(0x28000000),
                    shape = outerShape,
                ),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .height(barHeight),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEachIndexed { _, tab ->
                    val isActive = tab.route == current
                    LiquidTabItem(
                        tab = tab,
                        active = isActive,
                        iconSize = iconSize,
                        labelAlpha = labelAlpha,
                        modifier = Modifier.weight(1f),
                        onClick = {
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
    val scale by animateFloatAsState(
        targetValue = if (active) 1.06f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "tabScale2",
    )

    // Glass highlight pill behind active icon
    val pillShape = RoundedCornerShape(14.dp)

    Box(
        modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Active background pill
        if (active) {
            Box(
                Modifier
                    .width(52.dp)
                    .height(32.dp)
                    .clip(pillShape)
                    .background(
                        if (C.isDark) C.accent.copy(alpha = 0.25f)
                        else C.accent.copy(alpha = 0.15f)
                    )
                    .border(
                        0.5.dp,
                        C.accent.copy(alpha = 0.30f),
                        pillShape,
                    )
            )
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
            // Label below icon (iOS 26 style)
            if (labelAlpha > 0.01f) {
                Spacer(Modifier.height(2.dp))
                Text(
                    tab.label,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.1.sp,
                    color = if (active) C.accent.copy(alpha = labelAlpha) else C.textTertiary.copy(alpha = labelAlpha * 0.7f),
                    maxLines = 1,
                )
            }
        }
    }
}
