package com.beatdrop.kt.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.beatdrop.kt.ui.theme.LocalAppColors
import com.beatdrop.kt.ui.theme.Type

/**
 * BeatDropSearchField — the single, consistent search input used across the app.
 *
 * Replaces the four legacy inline search bars (LibraryScreen, SearchScreen,
 * DiscoverScreen, LocalDiscoverScreen) that each had subtly different colors,
 * states and accessibility behaviour.
 *
 * Visual contract:
 *   • Pill (default 50.dp radius), 52.dp tall, full width.
 *   • Resting:   solid elevated-glass fill, 1.dp border, icon + placeholder
 *                in textSecondary (always readable, never washed out).
 *   • Focused:   accent border (1.5.dp) + accent leading icon + soft accent
 *                glow shadow. Placeholder steps down to textTertiary so the
 *                user knows they can type.
 *   • Typing:    text in C.text, accent caret, trailing × clear button, and
 *                — when [submitting] is true — the accent border pulses
 *                (no spinner, no "Loading…" text).
 *
 * The same 52.dp height is preserved across every state so there is zero
 * layout shift between resting / focused / loading.
 */
@Composable
fun BeatDropSearchField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSubmit: (() -> Unit)? = null,
    submitting: Boolean = false,
    showClear: Boolean = true,
    radius: Dp = 50.dp,
) {
    val C = LocalAppColors.current
    val keyboard = LocalSoftwareKeyboardController.current
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()

    // ── Border colour: accent on focus, glassCardElevatedBorder at rest. ─
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) C.accent else Color.White.copy(alpha = if (C.isDark) 0.14f else 0.32f),
        animationSpec = tween(220),
        label = "border",
    )
    val borderWidth = if (isFocused) 1.2.dp else 0.7.dp

    // ── Leading icon colour follows focus, plus a calm pulse during submit.
    val pulseTransition = rememberInfiniteTransition(label = "submit-pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.45f,
        targetValue  = 1.0f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse-alpha",
    )
    val iconBase = if (isFocused || value.isNotEmpty()) C.accent else C.textSecondary
    val iconColor = if (submitting) iconBase.copy(alpha = pulseAlpha) else iconBase

    // Glow halo when focused (accent at 18% alpha, 6.dp soft shadow).
    val glowElevation by animateFloatAsState(
        targetValue = if (isFocused || submitting) 8f else 0f,
        animationSpec = tween(240),
        label = "glow",
    )

    val shape = RoundedCornerShape(radius)

    // Selection colours that match the accent (avoids the Material default blue).
    val selectionColors = TextSelectionColors(
        handleColor = C.accent,
        backgroundColor = C.accent.copy(alpha = 0.30f),
    )

    CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(48.dp)
                .shadow(
                    elevation = glowElevation.dp,
                    shape = shape,
                    ambientColor = C.accent,
                    spotColor = C.accent,
                )
                .clip(shape)
                // Solid elevated fill — *not* glassRow. The previous design
                // sat on a translucent surface which let the page backdrop
                // bleed through and produced the "white on white" look on
                // light mode and the smeary icon in dark mode.
                .background(
                    Brush.verticalGradient(
                        listOf(
                            if (C.isDark) Color(0xFF1A202A).copy(alpha = 0.72f) else Color.White.copy(alpha = 0.78f),
                            if (C.isDark) Color(0xFF111721).copy(alpha = 0.66f) else Color.White.copy(alpha = 0.68f),
                        ),
                    ),
                )
                .border(borderWidth, borderColor, shape)
                .padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ── Leading search glyph ─────────────────────────────────────
            Icon(
                Ic.Search,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(19.dp),
            )
            Spacer(Modifier.width(12.dp))

            // ── Text field + placeholder ─────────────────────────────────
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    androidx.compose.material3.Text(
                        text = placeholder,
                        style = Type.body,
                        color = if (isFocused) C.textTertiary else C.textSecondary,
                        fontWeight = FontWeight.Medium,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onChange,
                    singleLine = true,
                    textStyle = Type.body.copy(
                        color = C.text,
                        fontWeight = FontWeight.Medium,
                    ),
                    cursorBrush = SolidColor(C.accent),
                    interactionSource = interaction,
                    keyboardOptions = KeyboardOptions(
                        imeAction = if (onSubmit != null) ImeAction.Search else ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            onSubmit?.invoke()
                            keyboard?.hide()
                        },
                        onDone = { keyboard?.hide() },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ── Trailing: clear (×) and optional submit (→) ──────────────
            if (showClear && value.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(C.bg2.copy(alpha = 0.45f))
                        .pressableScale(onClick = { onChange("") }, scaleTo = 0.85f),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Ic.Close,
                        contentDescription = "Clear",
                        tint = C.text,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            // For online search, render a green "submit" pill once the user
            // has typed something — gives the IME-Search action a visible
            // counterpart for one-handed thumbs.
            if (onSubmit != null && value.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(C.accent)
                        .pressableScale(onClick = {
                            onSubmit()
                            keyboard?.hide()
                        }, scaleTo = 0.90f)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Text(
                            "Search",
                            style = Type.caption,
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

/**
 * BeatDropSearchButton — the round 44.dp icon button used in the Discover and
 * Local Discover headers as a shortcut into the full search screen.
 *
 * The previous version used `glassRow` directly which, on light mode, rendered
 * the icon against a near-solid white surface (alpha 0.95 of glassCardElevated
 * = ~0xDDFFFFFF) → invisible icon. This version stacks a solid bg2 underlay
 * (always opaque) under the haze so the icon is guaranteed contrast in every
 * theme.
 */
@Composable
fun BeatDropSearchButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "Search",
) {
    val C = LocalAppColors.current
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(shape)
            // Opaque underlay → icon always has contrast even in light theme.
            .background(C.bg2.copy(alpha = if (C.isDark) 0.55f else 0.85f))
            .border(1.dp, C.glassCardElevatedBorder, shape)
            .pressableScale(onClick = onClick, scaleTo = 0.86f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Ic.Search,
            contentDescription = contentDescription,
            tint = C.text,
            modifier = Modifier.size(22.dp),
        )
    }
}
