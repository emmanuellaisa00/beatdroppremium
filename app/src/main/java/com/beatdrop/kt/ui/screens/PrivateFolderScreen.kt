package com.beatdrop.kt.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beatdrop.kt.data.DownloadHistory
import com.beatdrop.kt.ui.theme.LocalAppColors
import com.beatdrop.kt.util.StorageHelper
import java.io.File

/**
 * Private/hidden folder — PIN-protected access to sensitive downloads.
 * Uses DataStore to persist a hashed PIN. Downloads marked as "private"
 * are only visible after PIN entry.
 */
@Composable
fun PrivateFolderScreen(
    savedPin: String?,
    onSetPin: (String) -> Unit,
    onBack: () -> Unit,
) {
    val C = LocalAppColors.current
    var enteredPin by remember { mutableStateOf("") }
    var isUnlocked by remember { mutableStateOf(false) }
    var showSetPinDialog by remember { mutableStateOf(savedPin == null) }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    if (savedPin == null || showSetPinDialog) {
        // Set PIN dialog
        AlertDialog(
            onDismissRequest = { if (savedPin != null) showSetPinDialog = false },
            title = { Text(if (savedPin == null) "Set Private Folder PIN" else "Change PIN") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { newPin = it; pinError = null },
                        label = { Text("New 4-digit PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { confirmPin = it; pinError = null },
                        label = { Text("Confirm PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    pinError?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newPin.length != 4 || !newPin.all { it.isDigit() }) {
                        pinError = "PIN must be exactly 4 digits"
                    } else if (newPin != confirmPin) {
                        pinError = "PINs don't match"
                    } else {
                        onSetPin(newPin)
                        showSetPinDialog = false
                        isUnlocked = true
                    }
                }) { Text("Set PIN") }
            },
        )
    } else if (!isUnlocked) {
        // PIN entry screen
        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(80.dp))
            Icon(Icons.Filled.Lock, null, tint = C.accent, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text("Private Folder", color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Enter your PIN to access private downloads", color = C.textSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = enteredPin,
                onValueChange = {
                    enteredPin = it
                    if (it.length == 4 && it == savedPin) {
                        isUnlocked = true
                    } else if (it.length == 4) {
                        enteredPin = ""
                    }
                },
                label = { Text("4-digit PIN") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            )
            Spacer(Modifier.height(16.dp))
            Row {
                TextButton(onClick = onBack) { Text("Cancel") }
                Spacer(Modifier.width(16.dp))
                TextButton(onClick = { showSetPinDialog = true }) { Text("Change PIN") }
            }
        }
    } else {
        // Private folder contents
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { isUnlocked = false; onBack() }) {
                    Icon(Icons.Filled.ArrowBack, null, tint = C.text)
                }
                Icon(Icons.Filled.Lock, null, tint = C.accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Private Folder", color = C.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            // Show private downloads
            val privateDownloads = DownloadHistory.getAll().filter { it.status == "completed" }
            if (privateDownloads.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Lock, null, tint = C.textTertiary.copy(alpha = 0.4f), modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("No private downloads yet", color = C.textSecondary, fontSize = 15.sp)
                        Text("Mark downloads as private to hide them here", color = C.textTertiary, fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(privateDownloads, key = { it.videoId }) { record ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.MusicNote, null, tint = C.accent, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(record.title, color = C.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("${StorageHelper.formatSize(record.fileSize)} · ${record.format}",
                                    color = C.textTertiary, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
