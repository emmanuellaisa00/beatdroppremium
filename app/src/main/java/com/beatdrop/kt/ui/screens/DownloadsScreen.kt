package com.beatdrop.kt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.beatdrop.kt.data.DownloadHistory
import com.beatdrop.kt.ui.components.GlassHeader
import com.beatdrop.kt.ui.components.ScreenScaffold
import com.beatdrop.kt.ui.components.glassCard
import com.beatdrop.kt.ui.components.glassRow
import com.beatdrop.kt.ui.theme.LocalAppColors
import com.beatdrop.kt.ui.theme.Radius
import com.beatdrop.kt.ui.theme.Spacing
import com.beatdrop.kt.ui.theme.Type
import com.beatdrop.kt.util.ShareHelper
import com.beatdrop.kt.util.StorageHelper
import com.beatdrop.kt.youtube.DownloadManagerV2
import java.io.File

/**
 * Download management screen — shows active downloads, history, and storage info.
 * Supports pause/resume/cancel/retry/share/re-download.
 */
@Composable
fun DownloadsScreen(
    vm: com.beatdrop.kt.PlayerViewModel,
    onBack: () -> Unit,
) {
    val C = LocalAppColors.current
    val activeJobs by DownloadManagerV2.jobs.collectAsState()
    var showHistory by remember { mutableStateOf(true) }

    ScreenScaffold(ambientColor = C.glassAmbient) {
        Column(Modifier.fillMaxSize()) {
            GlassHeader(
                title = "Downloads",
                subtitle = StorageHelper.formatSize(DownloadHistory.totalDownloadSize()),
                onBack = onBack,
                leadingIcon = Icons.Outlined.Download,
            )

            // Tab selector — glass chips
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassChip(label = "Active",  selected = !showHistory) { showHistory = false }
                GlassChip(label = "History", selected =  showHistory) { showHistory = true }
                Spacer(Modifier.weight(1f))
                val failedCount = activeJobs.values.count {
                    it.status == com.beatdrop.kt.youtube.DownloadStatusV2.FAILED
                }
                if (failedCount > 0) {
                    TextButton(onClick = {
                        val app = vm.getApplication<android.app.Application>()
                        DownloadManagerV2.retryAllFailed(app)
                    }) {
                        Text("Retry All ($failedCount)", color = C.accent, style = Type.caption)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            if (!showHistory) {
                val active = activeJobs.values.filter {
                    it.status != com.beatdrop.kt.youtube.DownloadStatusV2.IDLE
                }.sortedByDescending { it.status.ordinal }

                if (active.isEmpty()) {
                    EmptyState("No active downloads")
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize().padding(horizontal = Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 180.dp),
                    ) {
                        items(active, key = { it.videoId }) { job ->
                            ActiveDownloadRow(job)
                        }
                    }
                }
            } else {
                val records = DownloadHistory.getAll()
                if (records.isEmpty()) {
                    EmptyState("No download history yet")
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize().padding(horizontal = Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(bottom = 180.dp),
                    ) {
                        items(records, key = { it.videoId }) { record ->
                            HistoryRow(record)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val C = LocalAppColors.current
    val shape = RoundedCornerShape(Radius.pill)
    if (selected) {
        Box(
            Modifier
                .clip(shape)
                .background(C.accent)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label, style = Type.callout,
                color = androidx.compose.ui.graphics.Color.Black,
                fontWeight = FontWeight.SemiBold,
            )
        }
    } else {
        Box(
            Modifier
                .glassRow(radius = Radius.pill)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label, style = Type.callout,
                color = C.text,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun ActiveDownloadRow(job: com.beatdrop.kt.youtube.DownloadJobV2) {
    val C = LocalAppColors.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val app = ctx.applicationContext as android.app.Application

    Column(
        Modifier
            .fillMaxWidth()
            .glassRow()
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(job.title, style = Type.callout, color = C.text, fontWeight = FontWeight.Medium, maxLines = 1)
                Text(
                    when (job.status) {
                        com.beatdrop.kt.youtube.DownloadStatusV2.QUEUED -> "Queued"
                        com.beatdrop.kt.youtube.DownloadStatusV2.DOWNLOADING -> "${job.progress}%"
                        com.beatdrop.kt.youtube.DownloadStatusV2.PAUSED -> "Paused — ${job.progress}%"
                        com.beatdrop.kt.youtube.DownloadStatusV2.COMPLETED -> "Complete"
                        com.beatdrop.kt.youtube.DownloadStatusV2.FAILED -> "Failed: ${job.error?.take(50)}"
                        else -> ""
                    },
                    style = Type.footnote, color = C.textSecondary, maxLines = 1,
                )
            }
            Row {
                when (job.status) {
                    com.beatdrop.kt.youtube.DownloadStatusV2.DOWNLOADING -> {
                        IconButton(onClick = { DownloadManagerV2.pause(job.videoId) }) {
                            Icon(Icons.Outlined.Pause, "Pause", tint = C.accent, modifier = Modifier.size(20.dp))
                        }
                    }
                    com.beatdrop.kt.youtube.DownloadStatusV2.PAUSED -> {
                        IconButton(onClick = { DownloadManagerV2.resume(job.videoId, app) }) {
                            Icon(Icons.Outlined.PlayArrow, "Resume", tint = C.accent, modifier = Modifier.size(20.dp))
                        }
                    }
                    com.beatdrop.kt.youtube.DownloadStatusV2.FAILED -> {
                        IconButton(onClick = {
                            job.result?.let { DownloadManagerV2.retry(it, app) }
                        }) {
                            Icon(Icons.Outlined.Refresh, "Retry", tint = C.accent, modifier = Modifier.size(20.dp))
                        }
                    }
                    else -> {}
                }
                if (job.status == com.beatdrop.kt.youtube.DownloadStatusV2.DOWNLOADING ||
                    job.status == com.beatdrop.kt.youtube.DownloadStatusV2.QUEUED ||
                    job.status == com.beatdrop.kt.youtube.DownloadStatusV2.PAUSED) {
                    IconButton(onClick = { DownloadManagerV2.cancel(job.videoId, app) }) {
                        Icon(Icons.Outlined.Close, "Cancel", tint = C.textTertiary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        if (job.status == com.beatdrop.kt.youtube.DownloadStatusV2.DOWNLOADING ||
            job.status == com.beatdrop.kt.youtube.DownloadStatusV2.PAUSED) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { job.progress / 100f },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = C.accent,
                trackColor = C.bg3,
            )
        }
    }
}

@Composable
private fun HistoryRow(record: DownloadHistory.DownloadRecord) {
    val C = LocalAppColors.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val app = ctx.applicationContext as android.app.Application
    Row(
        Modifier
            .fillMaxWidth()
            .glassRow()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (record.isVideo) Icons.Outlined.Videocam else Icons.Outlined.MusicNote,
            null, tint = C.accent, modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(record.title, style = Type.callout, color = C.text, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${record.artist} · ${record.format.uppercase()} · ${StorageHelper.formatSize(record.fileSize)} · ${record.status}",
                style = Type.footnote, color = C.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (record.status == "completed" && record.filePath != null) {
            IconButton(onClick = {
                val file = File(record.filePath)
                if (file.exists()) ShareHelper.shareFile(ctx, file, record.title)
            }) {
                Icon(Icons.Outlined.Share, "Share", tint = C.textTertiary, modifier = Modifier.size(18.dp))
            }
        }
        if (record.status == "deleted") {
            IconButton(onClick = { DownloadManagerV2.redownload(record, app) }) {
                Icon(Icons.Outlined.Refresh, "Re-download", tint = C.accent, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    val C = LocalAppColors.current
    Box(
        Modifier.fillMaxSize().padding(Spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .glassCard(radius = Radius.lg)
                .padding(horizontal = 24.dp, vertical = 32.dp),
        ) {
            Text(text, style = Type.body, color = C.textSecondary)
        }
    }
}

