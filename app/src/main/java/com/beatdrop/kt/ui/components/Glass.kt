package com.beatdrop.kt.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.RenderEffect
import android.graphics.Shader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.beatdrop.kt.ui.theme.LocalAppColors
import com.beatdrop.kt.ui.theme.Radius

// ─── Device Tilt Sensor for Specular Highlights ──────────────────────────────

/**
 * Reads accelerometer to provide tilt-based specular highlight offset.
 * Returns (x, y) in range -1..1 representing device tilt from flat.
 */
@Composable
fun rememberDeviceTilt(): State<Offset> {
    val context = LocalContext.current
    val tilt = remember { mutableStateOf(Offset.Zero) }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensorManager == null || accelerometer == null) return@DisposableEffect onDispose {}

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = (event.values[0] / 9.81f).coerceIn(-1f, 1f)
                val y = (event.values[1] / 9.81f).coerceIn(-1f, 1f)
                tilt.value = Offset(x, y)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        onDispose { sensorManager.unregisterListener(listener) }
    }
    return tilt
}

// ─── Backdrop Blur + Saturation Boost ────────────────────────────────────────

/**
 * Real backdrop-blur + saturation boost (Liquid Glass material base).
 * API 31+: RenderEffect chain (blur + saturation). Pre-31: no-op fallback.
 */
@SuppressLint("NewApi")
fun Modifier.glassBlur(radiusPx: Float = 40f): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        this.graphicsLayer {
            renderEffect = RenderEffect.createChainEffect(
                RenderEffect.createColorFilterEffect(
                    android.graphics.ColorMatrixColorFilter(
                        android.graphics.ColorMatrix().apply { setSaturation(1.8f) }
                    )
                ),
                RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP),
            ).asComposeRenderEffect()
        }
    } else this

// ─── Specular Highlight (device-tilt-responsive) ─────────────────────────────

/**
 * Draws a moving specular highlight responding to device tilt.
 * Simulates light traveling across the glass surface.
 */
@Composable
fun Modifier.specularHighlight(
    tilt: State<Offset>,
    intensity: Float = 0.15f,
    radius: Float = 300f,
): Modifier {
    val animX by animateFloatAsState(tilt.value.x, animationSpec = tween(150), label = "specX")
    val animY by animateFloatAsState(tilt.value.y, animationSpec = tween(150), label = "specY")

    return this.drawWithContent {
        drawContent()
        val cx = size.width * (0.5f + animX * 0.35f)
        val cy = size.height * (0.5f - animY * 0.35f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = intensity),
                    Color.White.copy(alpha = intensity * 0.3f),
                    Color.Transparent,
                ),
                center = Offset(cx, cy),
                radius = radius,
            ),
            center = Offset(cx, cy),
            radius = radius,
        )
    }
}

// ─── Rim Light (Fresnel top-edge highlight) ──────────────────────────────────

/**
 * Draws a subtle top-edge rim light — simulates light catching the glass edge.
 * Without this, glass looks paper-thin.
 */
@Composable
fun Modifier.rimLight(
    @Suppress("UNUSED_PARAMETER") cornerRadius: Dp = Radius.xxl,
): Modifier {
    val C = LocalAppColors.current
    return this.drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    C.glassRimLight.copy(alpha = if (C.isDark) 0.16f else 0.30f),
                    Color.Transparent,
                ),
                startY = 0f,
                endY = size.height * 0.35f,
            ),
        )
    }
}

// ─── Inner Glow (bottom-edge soft glow for depth) ────────────────────────────

/**
 * Draws a subtle bottom-edge inner glow for depth perception.
 * Gives glass a sense of thickness and dimensionality.
 */
@Composable
fun Modifier.innerGlow(): Modifier {
    val C = LocalAppColors.current
    return this.drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    C.glassInnerShadow.copy(alpha = if (C.isDark) 0.06f else 0.04f),
                ),
                startY = size.height * 0.7f,
                endY = size.height,
            ),
        )
    }
}

// ─── Tinted Glass Button ─────────────────────────────────────────────────────

/**
 * A tinted glass button — accent color rendered as translucent glass material.
 * Use selectively for primary actions/CTAs only.
 */
@Composable
fun TintedGlassButton(
    modifier: Modifier = Modifier,
    tintColor: Color = LocalAppColors.current.accent,
    cornerRadius: Dp = Radius.xl,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier
            .clip(shape)
            .background(tintColor.copy(alpha = 0.55f))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.22f),
                        Color.Transparent,
                    )
                )
            )
            .border(0.6.dp, tintColor.copy(alpha = 0.40f), shape),
        contentAlignment = Alignment.Center,
    ) { content() }
}
