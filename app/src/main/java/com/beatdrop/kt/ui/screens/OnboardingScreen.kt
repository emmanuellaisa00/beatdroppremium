package com.beatdrop.kt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beatdrop.kt.ui.components.pressableScale
import com.beatdrop.kt.ui.theme.LocalAppColors

@Composable
fun OnboardingScreen(onGetStarted: () -> Unit) {
    val C = LocalAppColors.current
    Box(
        Modifier.fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF1A0A2E),  // Deep purple at top
                        Color(0xFF0D0618),  // Darker middle
                        Color(0xFF05030A),  // Near black at bottom
                    )
                )
            )
    ) {
        Column(
            Modifier.fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(28.dp))

            // Logo with glow effect
            Box(
                Modifier
                    .size(112.dp)
                    .shadow(
                        elevation = 24.dp,
                        shape = CircleShape,
                        ambientColor = Color(0xFF7B2CBF).copy(alpha = 0.5f),
                        spotColor = Color(0xFFC77DFF).copy(alpha = 0.6f)
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                Color(0xFFC77DFF),
                                Color(0xFF7B2CBF),
                                Color(0xFF3D1259),
                            )
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.MusicNote,
                    null,
                    tint = Color.White,
                    modifier = Modifier.size(56.dp)
                )
            }

            Spacer(Modifier.height(24.dp))

            // Title
            Text(
                "BeatDrop",
                fontSize = 38.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                textAlign = TextAlign.Center,
                letterSpacing = (-1).sp,
            )

            Text(
                "Your music, beautifully played.",
                color = Color(0xFFD4B0FF),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(Modifier.height(32.dp))

            // Feature list with cards
            FeatureCard(Icons.Filled.MusicNote, "Your Local Library", "Instantly plays every song already on your phone.", Color(0xFFC77DFF))
            Spacer(Modifier.height(10.dp))
            FeatureCard(Icons.Filled.Lyrics, "Synced Lyrics", "Drop a .lrc file next to a track and sing along in real-time.", Color(0xFF0A84FF))
            Spacer(Modifier.height(10.dp))
            FeatureCard(Icons.Filled.QueueMusic, "Playlists & Queue", "Create playlists, reorder your queue, and manage your music.", Color(0xFF30D158))
            Spacer(Modifier.height(10.dp))
            FeatureCard(Icons.Filled.GraphicEq, "DJ Mode", "Two decks with a working crossfader — mix tracks live.", Color(0xFFFF9F0A))

            Spacer(Modifier.weight(1f))

            // CTA Button
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .shadow(
                        elevation = 12.dp,
                        shape = RoundedCornerShape(16.dp),
                        ambientColor = Color(0xFF7B2CBF).copy(alpha = 0.4f),
                        spotColor = Color(0xFFC77DFF).copy(alpha = 0.3f)
                    )
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFFC77DFF), Color(0xFF9D4EDD))
                        )
                    )
                    .pressableScale(onClick = onGetStarted, haptic = true)
                    .padding(vertical = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Get Started",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "BeatDrop reads audio on your device.\nNo account, no uploads, no tracking.",
                color = Color(0xFF8A8A9A),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun FeatureCard(icon: ImageVector, title: String, body: String, accent: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF120B20))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(3.dp))
            Text(body, color = Color(0xFFA9A9BC), fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}
