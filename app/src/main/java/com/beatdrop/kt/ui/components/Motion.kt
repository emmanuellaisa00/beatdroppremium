package com.beatdrop.kt.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalView

/**
 * Spring press-scale + haptic. Optional long-press (combinedClickable) powers the
 * Spotify-style track action sheet.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.pressableScale(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    scaleTo: Float = 0.97f,
    haptic: Boolean = false,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) scaleTo else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    val view = LocalView.current
    return this
        .scale(scale)
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            onClick = {
                if (haptic) view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            },
            onLongClick = onLongClick?.let {
                {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    it()
                }
            },
        )
}
