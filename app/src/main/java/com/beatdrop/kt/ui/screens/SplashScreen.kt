package com.beatdrop.kt.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beatdrop.kt.R
import com.beatdrop.kt.ui.theme.LocalAppColors
import com.beatdrop.kt.ui.theme.Type
import com.beatdrop.kt.ui.components.pressableScale
import kotlinx.coroutines.delay

/**
 * Branded splash: BeatDrop logo + name + "From Laisacorp".
 * Shows for ~1.8s with a gentle scale/fade-in, then calls [onDone].
 */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val C = LocalAppColors.current
    var visible by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.7f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "logoScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(700),
        label = "logoAlpha",
    )

    LaunchedEffect(Unit) {
        visible = true
        delay(1400)
        onDone()
    }

    Box(
        Modifier
            .fillMaxSize()
            .pressableScale(onClick = { onDone() })
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1A1020), Color(0xFF151025), Color(0xFF12121A))
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.beatdrop_logo),
                contentDescription = "BeatDrop",
                modifier = Modifier
                    .size(128.dp)
                    .scale(scale)
                    .clip(RoundedCornerShape(28.dp)),
            )
            Spacer(Modifier.height(22.dp))
            Row(Modifier.scale(scale)) {
                Text("Beat", color = C.accent, style = Type.largeTitle, fontWeight = FontWeight.Black, fontSize = 34.sp)
                Text("Drop", color = Color.White, style = Type.largeTitle, fontWeight = FontWeight.Black, fontSize = 34.sp)
            }
        }

        // "From Laisacorp" pinned near the bottom
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "FROM",
                color = C.textTertiary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Laisacorp",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
            )
        }
    }
}
