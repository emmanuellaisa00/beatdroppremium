package com.beatdrop.kt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.beatdrop.kt.ui.components.Ic
import com.beatdrop.kt.PlayerViewModel
import com.beatdrop.kt.ui.components.ScreenScaffold
import com.beatdrop.kt.ui.components.glassCard
import com.beatdrop.kt.ui.components.glassShadow
import com.beatdrop.kt.ui.components.noiseOverlay
import com.beatdrop.kt.ui.components.pressableScale
import com.beatdrop.kt.ui.theme.Blur
import com.beatdrop.kt.ui.theme.LocalAppColors
import com.beatdrop.kt.ui.theme.Radius
import com.beatdrop.kt.ui.theme.Spacing
import com.beatdrop.kt.ui.theme.Type

private data class RadioMix(val title: String, val desc: String, val gradient: List<Color>)

private val MIXES = listOf(
    RadioMix("Chill Vibes",   "Mellow tracks to relax",     listOf(Color(0xFF667eea), Color(0xFF764ba2))),
    RadioMix("Energy Boost",  "High-tempo hits",            listOf(Color(0xFFf857a6), Color(0xFFff5858))),
    RadioMix("Deep Focus",    "Ambient & instrumental",     listOf(Color(0xFF00c6fb), Color(0xFF005bea))),
    RadioMix("Throwbacks",    "Classics from your library", listOf(Color(0xFFf7971e), Color(0xFFffd200))),
    RadioMix("Night Drive",   "Smooth evening tracks",      listOf(Color(0xFF654ea3), Color(0xFFeaafc8))),
    RadioMix("Fresh Mix",     "Random picks for you",       listOf(Color(0xFF11998e), Color(0xFF38ef7d))),
)

@Composable
fun RadioScreen(vm: PlayerViewModel) {
    val C = LocalAppColors.current
    val tracks by vm.tracks.collectAsState()

    ScreenScaffold(ambientColor = C.glassAmbient) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Text(
                "Radio", style = Type.largeTitle, color = C.text,
                modifier = Modifier.padding(start = Spacing.lg, top = 14.dp, bottom = 12.dp),
            )

            if (tracks.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("Add music to unlock radio mixes", color = C.textSecondary, style = Type.body)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(Spacing.lg, 4.dp, Spacing.lg, 170.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(MIXES) { mix ->
                        val shape = RoundedCornerShape(Radius.xl)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.1f)
                                // Outer shadow (spec §3)
                                .glassShadow(elevation = 18.dp, shape = shape, isDark = C.isDark)
                                .clip(shape)
                                // Colored gradient as the "wallpaper" behind the glass
                                .background(Brush.linearGradient(mix.gradient))
                                // Full glass treatment over the gradient
                                .glassCard(radius = Radius.xl, blur = Blur.medium)
                                .noiseOverlay(opacity = 0.04f)
                                .pressableScale(onClick = {
                                    val shuffled = tracks.shuffled().take(20)
                                    if (shuffled.isNotEmpty()) vm.playList(shuffled, shuffled.first().id)
                                })
                                .padding(16.dp),
                        ) {
                            Column(Modifier.align(Alignment.BottomStart)) {
                                Icon(
                                    Ic.Radio, null,
                                    tint = Color.White.copy(alpha = 0.92f),
                                    modifier = Modifier.size(22.dp),
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(mix.title, style = Type.title3, color = Color.White, fontWeight = FontWeight.Bold)
                                Text(
                                    mix.desc,
                                    style = Type.caption,
                                    color = Color.White.copy(alpha = 0.85f),
                                    maxLines = 2,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
