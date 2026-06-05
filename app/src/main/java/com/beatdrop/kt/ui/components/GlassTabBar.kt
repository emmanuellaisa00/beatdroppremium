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
                // ── Unified Premium Glass material (spec: 'one material
                //   everywhere'). Z4_TabBar gives blur 45 + 3-shadow stack
                //   + 4-layer reflections + 0.5dp border in one call.
                //   Bespoke hazeGlass/drawWithContent/rimLight/border stack
                //   removed — was diverging from the spec's unified surface.
                .premiumGlass(level = GlassLevel.Z4_TabBar, shape = outerShape)
                // ── Specular highlight (device tilt) — ADDITIVE, kept ───────
                //   because it's a runtime sensor effect orthogonal to the
                //   static material stack. Subtle.
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
                    .height(barHeight)
                    .padding(horizontal = 8.dp),
            ) {
                val activeIdx = tabs.indexOfFirst { it.route == current }
                    .coerceAtLeast(0)
                val barWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) {
                    (this@BoxWithConstraints.maxWidth - 16.dp).toPx()   // minus 8.dp padding × 2
                }
                val itemWidthPx = barWidthPx / tabs.size
                val puckSizePx = with(androidx.compose.ui.platform.LocalDensity.current) { 64.dp.toPx() }
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
                val puckShape = RoundedCornerShape(32.dp)
                val puckTint = if (C.isDark) Color.White.copy(alpha = 0.08f)
                               else Color.White.copy(alpha = 0.15f)
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset { androidx.compose.ui.unit.IntOffset(animatedX.toInt(), 0) }
                        .size(64.dp)
                        .clip(puckShape)
                        .hazeGlass(shape = puckShape, tintColor = puckTint, blurRadius = 30.dp)
                        .background(puckTint)
                        .border(
                            width = 1.dp,
                            color = if (C.isDark) Color.White.copy(alpha = 0.15f)
                                    else Color.White.copy(alpha = 0.30f),
                            shape = puckShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    // Accent glow inside the puck — the lens 'lit from
                    // within' look. Per spec: 'Inner Reflection'.
                    androidx.compose.foundation.layout.Box(
                        Modifier
                            .size(52.dp)
                            .clip(puckShape)
                            .background(C.accent.copy(alpha = 0.20f))
                            .border(0.5.dp, C.accent.copy(alpha = 0.30f), puckShape),
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