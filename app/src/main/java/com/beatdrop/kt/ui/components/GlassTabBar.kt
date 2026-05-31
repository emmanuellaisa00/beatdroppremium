package com.beatdrop.kt.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beatdrop.kt.ui.theme.LocalAppColors
import com.beatdrop.kt.ui.theme.Radius

data class TabSpec(val route: String, val label: String, val icon: ImageVector)

/** iOS-26 floating glass pill tab bar (port of the RN AppNavigator tab bar). */
@Composable
fun GlassTabBar(tabs: List<TabSpec>, current: String, onSelect: (String) -> Unit) {
    val C = LocalAppColors.current
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(Radius.xxl))
            // Opaque base layer prevents content from bleeding through
            .background(if (C.isDark) Color(0xFF101018) else Color(0xFFF2F2F7))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.xxl))
                .background(if (C.isDark) C.glassTint else Color.White.copy(alpha = 0.82f))
                .border(1.dp, C.liquidGlassBorder, RoundedCornerShape(Radius.xxl))
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                TabItem(tab, tab.route == current, Modifier.weight(1f)) { onSelect(tab.route) }
            }
        }
    }
}

@Composable
private fun TabItem(tab: TabSpec, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val C = LocalAppColors.current
    val scale by animateFloatAsState(
        targetValue = if (active) 1.06f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "tab",
    )
    Box(
        modifier
            .padding(horizontal = 4.dp)
            .pressableScale(onClick = onClick, scaleTo = 0.9f),
        contentAlignment = Alignment.Center,
    ) {
        if (active) {
            Box(
                Modifier.matchParentSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(C.accentSoft)
                    .border(1.dp, C.accentBorder, RoundedCornerShape(16.dp))
            )
        }
        Column(
            Modifier.padding(vertical = 8.dp).scale(scale),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(tab.icon, tab.label, tint = if (active) C.accent else C.textSecondary, modifier = Modifier.size(22.dp))
            Text(tab.label, color = if (active) C.accent else C.textSecondary, fontSize = 10.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.padding(top = 3.dp))
        }
    }
}
