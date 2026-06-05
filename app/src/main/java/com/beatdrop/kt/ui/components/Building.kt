package com.beatdrop.kt.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.beatdrop.kt.ui.theme.LocalAppColors
import com.beatdrop.kt.ui.theme.Spacing
import com.beatdrop.kt.ui.theme.Radius
import com.beatdrop.kt.ui.theme.Type

/** Section header: bold title + optional trailing action, used across screens. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val C = LocalAppColors.current
    Row(
        modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = Type.title2, color = C.text, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                action, style = Type.callout, color = C.accent,
                modifier = Modifier.pressableScale(onClick = onAction, scaleTo = 0.92f, haptic = false),
            )
        }
    }
}

/** Small uppercase label above a group (Apple "BROWSE", Spotify section eyebrow). */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    val C = LocalAppColors.current
    Text(text, style = Type.overline, color = C.textTertiary,
        modifier = modifier.padding(horizontal = Spacing.lg, vertical = 4.dp))
}

/** Shimmer placeholder block for loading states (premium perceived speed). */
@Composable
fun Shimmer(modifier: Modifier = Modifier, corner: Int = 12) {
    val C = LocalAppColors.current
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -2f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "shimmerX",
    )
    val base = if (C.isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.06f)
    val hi = if (C.isDark) Color.White.copy(alpha = 0.13f) else Color.Black.copy(alpha = 0.12f)
    Box(
        modifier.clip(RoundedCornerShape(corner.dp)).background(
            Brush.linearGradient(
                colors = listOf(base, hi, base),
                start = androidx.compose.ui.geometry.Offset(x * 200f, 0f),
                end = androidx.compose.ui.geometry.Offset((x + 1f) * 200f, 200f),
            )
        )
    )
}

/** Vertical gradient scrim — for legible text over artwork (Apple/Spotify hero). */
@Composable
fun GradientScrim(modifier: Modifier = Modifier, color: Color = Color.Black) {
    Box(modifier.background(Brush.verticalGradient(listOf(Color.Transparent, color.copy(alpha = 0.85f)))))
}

/**
 * Shared Liquid Glass card — multi-layer translucent fill + rim light + inner glow + hairline border.
 * Matches the iOS 26 floating glass panels from the Spotify concept.
 */
@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val C = LocalAppColors.current
    val shape = RoundedCornerShape(Radius.xl)
    Column(
        modifier.fillMaxWidth().padding(vertical = 5.dp)
            .shadow(
                elevation = 10.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = if (C.isDark) 0.34f else 0.12f),
                spotColor = Color.Black.copy(alpha = if (C.isDark) 0.24f else 0.10f),
            )
            .clip(shape)
            // Content-safe premium glass fill: translucent but never so dark
            // that child text/icons disappear on real devices.
            .background(
                Brush.verticalGradient(
                    listOf(
                        if (C.isDark) Color(0xFF1B2028).copy(alpha = 0.72f) else Color.White.copy(alpha = 0.78f),
                        if (C.isDark) Color(0xFF0E1116).copy(alpha = 0.66f) else Color.White.copy(alpha = 0.66f),
                    ),
                ),
            )
            .drawWithContent {
                drawContent()
                drawRect(brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (C.isDark) 0.12f else 0.26f),
                        Color.Transparent
                    ),
                    startY = 0f, endY = size.height * 0.32f))
                drawRect(brush = Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        if (C.isDark) Color.White.copy(alpha = 0.035f) else Color.Black.copy(alpha = 0.025f),
                    ),
                    startY = size.height * 0.72f,
                    endY = size.height,
                ))
            }
            .border(0.75.dp, Color.White.copy(alpha = if (C.isDark) 0.13f else 0.36f), shape)
            .padding(16.dp),
        content = content,
    )
}

/**
 * Liquid Glass pill — stadium-shaped glass chip for tags, filters, action buttons.
 * Used throughout the iOS 26 Spotify concept for compact interactive elements.
 */
@Composable
fun GlassPill(
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit = {},
    content: @Composable RowScope.() -> Unit,
) {
    val C = LocalAppColors.current
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .clip(shape)
            .background(
                when {
                    active -> C.accent.copy(alpha = 0.42f)
                    C.isDark -> Color(0xFF1A1F27).copy(alpha = 0.70f)
                    else -> Color.White.copy(alpha = 0.70f)
                }
            )
            .drawWithContent {
                drawContent()
                // Top rim light
                drawRect(brush = Brush.verticalGradient(
                    listOf(
                        if (active) C.accent.copy(alpha = 0.18f) else Color.White.copy(alpha = if (C.isDark) 0.08f else 0.15f),
                        Color.Transparent
                    ),
                    startY = 0f, endY = size.height * 0.4f,
                ))
            }
            .border(
                0.5.dp,
                if (active) C.accent.copy(alpha = 0.35f) else Color.White.copy(alpha = if (C.isDark) 0.13f else 0.32f),
                shape,
            )
            .pressableScale(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * Liquid Glass search bar — frosted stadium pill with magnifying glass icon.
 * Matches the iOS 26 concept's translucent search fields.
 */
@Composable
fun GlassSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "Search",
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    val C = LocalAppColors.current
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (C.isDark) Color(0xFF171C24).copy(alpha = 0.76f) else Color.White.copy(alpha = 0.76f))
            .drawWithContent {
                drawContent()
                drawRect(brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (C.isDark) 0.06f else 0.12f),
                        Color.Transparent
                    ),
                    startY = 0f, endY = size.height * 0.4f,
                ))
            }
            .border(0.7.dp, Color.White.copy(alpha = if (C.isDark) 0.14f else 0.32f), shape)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(Modifier.width(10.dp))
        }
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(placeholder, style = Type.body, color = C.textTertiary)
            }
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = Type.body.copy(color = C.text),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(C.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (trailingIcon != null) {
            Spacer(Modifier.width(8.dp))
            trailingIcon()
        }
    }
}
