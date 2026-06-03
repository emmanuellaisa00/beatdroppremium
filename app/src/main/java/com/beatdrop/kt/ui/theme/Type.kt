package com.beatdrop.kt.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * iOS 26-inspired type scale — SF Pro-like tight tracking with a premium feel.
 * Larger sizes with tighter letter-spacing for that Apple Music / Spotify
 * "cinematic" readability on glass surfaces.
 */
object Type {
    val largeTitle = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.8).sp, lineHeight = 40.sp)
    val title1     = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, lineHeight = 34.sp)
    val title2     = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, lineHeight = 28.sp)
    val title3     = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp, lineHeight = 24.sp)
    val headline   = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp, lineHeight = 22.sp)
    val body       = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.1).sp, lineHeight = 21.sp)
    val callout    = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.05).sp, lineHeight = 19.sp)
    val subhead    = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp, lineHeight = 18.sp)
    val footnote   = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp, lineHeight = 16.sp)
    val caption    = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp, lineHeight = 14.sp)
    val overline   = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, lineHeight = 14.sp)
}
