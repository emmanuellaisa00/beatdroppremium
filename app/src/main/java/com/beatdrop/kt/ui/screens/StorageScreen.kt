package com.beatdrop.kt.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beatdrop.kt.data.DownloadHistory
import com.beatdrop.kt.ui.theme.LocalAppColors
import com.beatdrop.kt.util.StorageHelper

/**
 * Storage management screen — shows storage usage, SD card options, and cleanup tools.
 */
@Composable
fun StorageScreen(onBack: () -> Unit) {
    val C = LocalAppColors.current
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var showClearDialog by remember { mutableStateOf(false) }

    val storageLocations = remember { StorageHelper.getStorageLocations(context) }
    val totalDownloadSize = remember { DownloadHistory.totalDownloadSize() }
    val completedCount = remember { DownloadHistory.countByStatus("completed") }
    val deletedCount = remember { DownloadHistory.countByStatus("deleted") }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(scrollState)
            .padding(horizontal = 16.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = C.text) }
            Text("Storage", color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Black)
        }

        Spacer(Modifier.height(16.dp))

        // Download size card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = C.bg2),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Download Storage", color = C.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    StorageHelper.formatSize(totalDownloadSize),
                    color = C.accent, fontSize = 28.sp, fontWeight = FontWeight.Black,
                )
                Text("$completedCount files", color = C.textSecondary, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Storage locations
        Text("Storage Locations", color = C.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))

        storageLocations.forEach { storage ->
            StorageLocationCard(storage)
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(16.dp))

        // Actions
        Text("Actions", color = C.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = { showClearDialog = true },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF453A)),
        ) {
            Icon(Icons.Filled.DeleteSweep, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Clear All Downloads")
        }

        Spacer(Modifier.height(8.dp))

        if (deletedCount > 0) {
            OutlinedButton(
                onClick = {
                    // Recover deleted — re-download
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Restore, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Recover $deletedCount Deleted Downloads")
            }
        }

        Spacer(Modifier.height(80.dp))
    }

    // Clear confirmation dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Downloads?") },
            text = { Text("This will delete all downloaded music files. This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    // Clear downloads handled by ViewModel
                }) { Text("Delete All", color = Color(0xFFFF453A)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun StorageLocationCard(storage: StorageHelper.StorageInfo) {
    val C = LocalAppColors.current
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = C.bg2),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (storage.isRemovable) Icons.Filled.SdCard else Icons.Filled.Storage,
                    null, tint = C.accent, modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(storage.label, color = C.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("${storage.freeGB.toInt()} / ${storage.totalGB.toInt()} GB",
                    color = C.textSecondary, fontSize = 12.sp)
            }
            Spacer(Modifier.height(6.dp))
            // Usage bar
            LinearProgressIndicator(
                progress = { storage.usedPercent / 100f },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = if (storage.usedPercent > 90) Color(0xFFFF453A) else C.accent,
                trackColor = C.bg3,
            )
            Spacer(Modifier.height(4.dp))
            Text("${storage.usedPercent}% used · ${String.format("%.1f", storage.freeGB)} GB free",
                color = C.textTertiary, fontSize = 11.sp)
        }
    }
}
