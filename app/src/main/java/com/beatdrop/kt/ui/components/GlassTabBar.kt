package com.beatdrop.kt.ui.components

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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
        targetValue = if (isScrolledDown) 54.dp else 78.dp,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f),
        label = "barHeight",
    )
    val iconSize by animateDpAsState(
        targetValue = if (isScrolledDown) 22.dp else 26.dp,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f),
        label = "iconSize",
    )
    val labelAlpha by animateFloatAsState(
        targetValue = 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "labelAlpha",
    )

    // ── Spec: outer radius=44px (pill), inner indicator uses concentric rule ─
    val outerRadius = 39.dp
    val outerShape  = RoundedCornerShape(outerRadius)

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 6.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(barHeight)
                // Background-only dock glass. Keeping the blur material off the
                // parent content layer prevents icons from being swallowed by
                // the glass shader on real devices.
                .shadow(
                    elevation = 26.dp,
                    shape = outerShape,
                    ambientColor = Color.Black.copy(alpha = 0.58f),
                    spotColor = Color.Black.copy(alpha = 0.42f),
                )
                .clip(outerShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF25272B).copy(alpha = if (C.isDark) 0.74f else 0.84f),
                            Color(0xFF111214).copy(alpha = if (C.isDark) 0.68f else 0.76f),
                        ),
                    ),
                )
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
                            startY = 0f,
                            endY = size.height * 0.38f,
                        ),
                    )
                }
                .border(0.8.dp, Color.White.copy(alpha = if (C.isDark) 0.16f else 0.26f), outerShape)
                .specularHighlight(tilt, intensity = if (C.isDark) 0.08f else 0.06f, radius = 320f),
        ) {
            // ── Floating Active Lens (Premium Glass spec) ────────────
            // The lens is a SINGLE glass puck that lives in the parent
            // Box and slides between tab slots with spring physics —
            // NOT a per-item background that fades. Per the spec:
            //
            //   'Active Lens Motion
            //    Never fade. Never cross dissolve.
            //    Instead: Spring Position moves.'
            //
            // Implemented via BoxWithConstraints so we know the exact
            // bar width, then a Box(.offset(animated x)) overlay carries
            // the puck. The Row of tab items renders icon+label only;
            // the puck-shaped background lives one level up.
            androidx.compose.foundation.layout.BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .height(barHeight),
            ) {
                val activeIdx = tabs.indexOfFirst { it.route == current }
                    .coerceAtLeast(0)
                val barWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) {
                    this@BoxWithConstraints.maxWidth.toPx()
                }
                val itemWidthPx = barWidthPx / tabs.size
                val puckSizePx = with(androidx.compose.ui.platform.LocalDensity.current) { 58.dp.toPx() }
                val targetXpx = (activeIdx * itemWidthPx) + (itemWidthPx / 2f) - (puckSizePx / 2f)
                val animatedX by animateFloatAsState(
                    targetValue = targetXpx,
                    animationSpec = androidx.compose.animation.core.spring(
                        stiffness = 400f,
                        dampingRatio = 0.75f,
                    ),
                    label = "puckX",
                )

                // The floating puck — single instance, slides between slots.
                // Concept target: a raised circular lens like the uploaded iOS dock.
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset { androidx.compose.ui.unit.IntOffset(animatedX.toInt(), 0) }
                        .size(58.dp)
                        .premiumGlass(
                            level = GlassLevel.Z5_ActiveLens,
                            shape = CircleShape,
                            tintBoost = if (C.isDark) 0.05f else 0f,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.foundation.layout.Box(
                        Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(C.accent.copy(alpha = if (C.isDark) 0.18f else 0.14f))
                            .border(0.5.dp, C.accent.copy(alpha = 0.32f), CircleShape),
                    )
                }

                // ── Tab items row — icon + label only, NO background ───
                Row(
                    Modifier
                        .fillMaxSize(),
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

    // ── Per-item background removed ───────────────────────────────────
    // The 'active puck' is now a single FLOATING lens in the parent
    // BoxWithConstraints that slides between slots with spring physics
    // (per Premium Glass spec: 'Never fade. Never cross dissolve.
    // Instead: Spring Position moves.'). Each LiquidTabItem now renders
    // ONLY icon + label.

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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.scale(scale),
        ) {
            Icon(
                imageVector = if (active) tab.iconFilled else tab.iconOutlined,
                contentDescription = tab.label,
                tint = if (active) C.text else C.textSecondary.copy(alpha = 0.72f),
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