// Copyright © 2026 Polar Electro Oy. All rights reserved.

package com.polar.bgsyncsample.ui

import android.app.Activity
import android.content.IntentSender
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.alpha
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.polar.bgsyncsample.BuildConfig
import com.polar.polarbgsync.api.ConnectionState
import com.polar.polarbgsync.api.SyncableDataType
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncDashboardScreen(
    onNavigateToDataBrowser: () -> Unit,
    viewModel: SyncViewModel,
    onNavigateToGraph: (deviceId: String, logLines: List<String>) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val companionConsentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) viewModel.cancelCompanionAssociation()
    }

    // Track which device is selected for graph viewing
    var selectedGraphDevice by remember { mutableStateOf(state.deviceIds.firstOrNull() ?: "") }

    // Reload persistent log entries and optionally trigger sync when returning from background
    LifecycleResumeEffect(Unit) {
        viewModel.onAppForegrounded()
        onPauseOrDispose {}
    }

    // Sync failure dialog for newly-added devices
    state.failedSyncDevice?.let { deviceId ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissSyncFailure() },
            title = { Text("Sync Failed") },
            text = { Text("Failed to sync with device $deviceId. Would you like to retry or remove the device?") },
            confirmButton = {
                TextButton(onClick = { viewModel.retrySyncForFailedDevice() }) {
                    Text("Retry")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.removeDevice(deviceId) }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Polar Background Sync") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val cardColors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )

            // ── Device connection card ──────────────────────────────
            Card(modifier = Modifier.fillMaxWidth(), colors = cardColors) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Devices", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = state.deviceIdInput,
                            onValueChange = { viewModel.updateDeviceIdInput(it) },
                            label = { Text("Device ID") },
                            placeholder = { Text("e.g. B5F8B5C3") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(
                            onClick = { viewModel.addDevice() },
                            enabled = state.deviceIdInput.isNotBlank() && state.companionAssociationDeviceId == null
                        ) {
                            Text("Add")
                        }
                    }

                    if (state.deviceIds.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        state.deviceIds.forEach { deviceId ->
                            val syncInfo = state.deviceSyncInfo[deviceId]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(deviceId, style = MaterialTheme.typography.bodyMedium,
                                             fontWeight = FontWeight.Medium)
                                        state.batteryLevels[deviceId]?.let { battery ->
                                            val isStale = ChronoUnit.MINUTES.between(battery.receivedAt, LocalDateTime.now()) > 5
                                            BatteryIndicator(
                                                level = battery.level,
                                                modifier = Modifier.alpha(if (isStale) 0.4f else 1f)
                                            )
                                        }
                                    }
                                    if (syncInfo?.lastSyncTime != null) {
                                        val now = LocalDateTime.now()
                                        val syncTime = syncInfo.lastSyncTime
                                        val formattedTime = if (syncTime.toLocalDate() == now.toLocalDate()) {
                                            syncTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
                                        } else {
                                            syncTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                                        }
                                        Text(
                                            "Last sync: $formattedTime",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (syncInfo.status == DeviceSyncStatus.ERROR) Color(0xFFF57C00) else Color(0xFF388E3C)
                                        )
                                    }
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    StatusChip(
                                        syncStatus = syncInfo?.status ?: DeviceSyncStatus.NONE,
                                        connectionState = state.connectionStates[deviceId] ?: ConnectionState.DISCONNECTED
                                    )
                                    IconButton(
                                        onClick = { viewModel.removeDevice(deviceId) },
                                        enabled = state.companionAssociationDeviceId == null
                                    ) {
                                        Icon(Icons.Filled.Close, "Remove device",
                                             tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            if (deviceId in state.companionAssociatedDevices) {
                                Text("Companion association enabled", style = MaterialTheme.typography.bodySmall)
                            } else {
                                TextButton(
                                    enabled = state.companionAssociationDeviceId == null &&
                                        state.connectionStates[deviceId] != ConnectionState.CONNECTING,
                                    onClick = {
                                        viewModel.associateCompanionDevice(deviceId) { sender ->
                                            try {
                                                companionConsentLauncher.launch(IntentSenderRequest.Builder(sender).build())
                                            } catch (e: ActivityNotFoundException) {
                                                android.util.Log.e("SyncDashboard", "Cannot launch companion consent", e)
                                                viewModel.cancelCompanionAssociation()
                                            } catch (e: IntentSender.SendIntentException) {
                                                android.util.Log.e("SyncDashboard", "Cannot launch companion consent", e)
                                                viewModel.cancelCompanionAssociation()
                                            }
                                        }
                                    }
                                ) {
                                    Text("Associate for background sync")
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!state.isSyncing) {
                            Button(
                                onClick = { viewModel.startSync() },
                                enabled = state.deviceIds.isNotEmpty() && state.companionAssociationDeviceId == null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Start Background Sync")
                            }
                        } else {
                            Button(
                                onClick = { viewModel.stopSync() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Stop Background Sync")
                            }
                            OutlinedButton(
                                onClick = { viewModel.triggerSyncNow() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Synchronize Now")
                            }
                        }

                        OutlinedButton(
                            onClick = { onNavigateToDataBrowser() },
                            enabled = state.deviceIds.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Browse Synced Data")
                        }

                        if (state.deviceIds.isNotEmpty()) {
                            TextButton(
                                onClick = { viewModel.disconnectAndForget() },
                                enabled = state.companionAssociationDeviceId == null,
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Disconnect and Forget All", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // ── Data types card ─────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth(), colors = cardColors) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Data Types", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))

                    SyncableDataType.entries.forEach { dataType ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                dataType.name.lowercase().replace('_', ' ')
                                    .replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Switch(
                                checked = state.enabledDataTypes.contains(dataType),
                                onCheckedChange = { viewModel.toggleDataType(dataType) }
                            )
                        }
                    }
                }
            }

            // ── Settings card ───────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth(), colors = cardColors) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Device Cleanup", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))

                    // Auto-delete from device
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Device Cleanup", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Auto-delete from device after sync",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.autoDeleteFromDevice,
                            onCheckedChange = { viewModel.setAutoDeleteFromDevice(it) }
                        )
                    }

                    if (state.autoDeleteFromDevice) {
                        // Activity data retention stepper
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Activity data:",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { viewModel.setDeviceRetentionDays(state.deviceRetentionDays - 1) },
                                    enabled = state.deviceRetentionDays > 1
                                ) { Text("−", style = MaterialTheme.typography.titleMedium) }
                                Text(
                                    "${state.deviceRetentionDays} days",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.widthIn(min = 48.dp),
                                    textAlign = TextAlign.Center
                                )
                                IconButton(
                                    onClick = { viewModel.setDeviceRetentionDays(state.deviceRetentionDays + 1) },
                                    enabled = state.deviceRetentionDays < 30
                                ) { Text("+", style = MaterialTheme.typography.titleMedium) }
                            }
                        }

                        // Offline recording retention stepper
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Offline recordings:",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { viewModel.setOfflineRecordingRetentionDays(state.offlineRecordingRetentionDays - 1) },
                                    enabled = state.offlineRecordingRetentionDays > 0
                                ) { Text("−", style = MaterialTheme.typography.titleMedium) }
                                Text(
                                    "${state.offlineRecordingRetentionDays} days",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.widthIn(min = 48.dp),
                                    textAlign = TextAlign.Center
                                )
                                IconButton(
                                    onClick = { viewModel.setOfflineRecordingRetentionDays(state.offlineRecordingRetentionDays + 1) },
                                    enabled = state.offlineRecordingRetentionDays < 14
                                ) { Text("+", style = MaterialTheme.typography.titleMedium) }
                            }
                        }
                    }
                }
            }

            // ── Sync triggers card ──────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth(), colors = cardColors) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Sync Triggers", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))

                    // Sync on foreground toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sync when foregrounded", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Trigger a sync cycle when app returns to foreground",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.syncOnForeground,
                            onCheckedChange = { viewModel.setSyncOnForeground(it) }
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // Timed sync interval
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Timed sync interval", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (state.syncIntervalMinutes == 0) "Off — sync only on device notification"
                            else "Sync every ${state.syncIntervalMinutes} min",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = state.syncIntervalMinutes.toFloat(),
                            onValueChange = { viewModel.setSyncIntervalMinutes(it.toInt()) },
                            valueRange = 0f..60f,
                            steps = 59
                        )
                    }
                }
            }

            // ── Local storage card ─────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth(), colors = cardColors) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Local Storage", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))

                    // Local data retention
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Local data auto-purge", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Delete local files older than ${state.localRetentionDays} days",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.localRetentionEnabled,
                            onCheckedChange = { viewModel.setLocalRetentionEnabled(it) }
                        )
                    }

                    if (state.localRetentionEnabled) {
                        Text(
                            "Local retention: ${state.localRetentionDays} days",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Slider(
                            value = state.localRetentionDays.toFloat(),
                            onValueChange = { viewModel.setLocalRetentionDays(it.toInt()) },
                            valueRange = 7f..90f,
                            steps = 82
                        )
                    }
                }
            }

            // ── Sync log card ───────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth(), colors = cardColors) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Sync Log", style = MaterialTheme.typography.titleMedium)
                        if (state.syncLog.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = {
                                        val allLogs = viewModel.getAllLogTextForSharing()
                                        if (allLogs.isNotBlank()) {
                                            shareSyncLogText(context, allLogs)
                                        } else {
                                            Toast.makeText(context, "No logs to share", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Share,
                                        contentDescription = "Share sync log",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Share", style = MaterialTheme.typography.labelSmall)
                                }
                                TextButton(
                                    onClick = {
                                        val logs = state.syncLog
                                        val deviceForGraph = if (state.deviceIds.size == 1) {
                                            state.deviceIds.first()
                                        } else {
                                            selectedGraphDevice.takeIf { it.isNotBlank() } ?: state.deviceIds.firstOrNull()
                                        }
                                        if (logs.isNotEmpty() && !deviceForGraph.isNullOrBlank()) {
                                            onNavigateToGraph(
                                                deviceForGraph,
                                                viewModel.getRawLogMessagesForDevice(deviceForGraph)
                                            )
                                        } else {
                                            Toast.makeText(context, "No logs to display", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Text("📈", style = MaterialTheme.typography.labelSmall)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Graph", style = MaterialTheme.typography.labelSmall)
                                }
                                TextButton(onClick = { viewModel.clearLog() }) {
                                    Text("Clear", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    if (state.syncLog.isEmpty()) {
                        Text(
                            "No events yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp)
                        ) {
                            items(state.syncLog) { entry ->
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Column(modifier = Modifier.width(92.dp)) {
                                            Text(
                                                entry.date,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                entry.time,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                entry.deviceId,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            entry.message,
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp
                                        )
                                    }
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                        thickness = 1.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── About card ─────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth(), colors = cardColors) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("About", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))

                    AboutRow(label = "App version", value = BuildConfig.VERSION_NAME)
                    AboutRow(label = "App commit", value = BuildConfig.BUILD_REPO_COMMIT)
                    AboutRow(label = "SDK version", value = BuildConfig.SDK_VERSION_INFO)
                    AboutRow(label = "SDK source", value = BuildConfig.SDK_SOURCE_INFO)
                }
            }
        }
    }
}

private fun shareSyncLogText(context: Context, logText: String) {
    runCatching {
        val shareDir = File(context.cacheDir, "shared").apply { mkdirs() }
        val outFile = File(shareDir, "sync-log-${System.currentTimeMillis()}.txt")
        outFile.writeText(logText)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            outFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Polar Background Sync Log")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, "sync-log", uri)
        }

        context.startActivity(Intent.createChooser(shareIntent, "Share sync log"))
    }.onFailure { error ->
        Toast.makeText(context, "Failed to share log: ${error.message}", Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
fun StatusChip(syncStatus: DeviceSyncStatus, connectionState: ConnectionState) {
    val syncingColor = Color(0xFF7E57C2)
    val cleanupColor = Color(0xFF42A5F5)
    // Show sync status if active, otherwise show connection state
    val (label, color) = when {
        syncStatus == DeviceSyncStatus.SYNCING -> "Syncing…" to syncingColor
        syncStatus == DeviceSyncStatus.CLEANUP -> "Cleanup" to cleanupColor
        else -> when (connectionState) {
            ConnectionState.DISCONNECTED -> "Disconnected" to MaterialTheme.colorScheme.error
            ConnectionState.CONNECTING -> "Connecting…" to MaterialTheme.colorScheme.tertiary
            ConnectionState.CONNECTED -> "Connected" to MaterialTheme.colorScheme.primary
            ConnectionState.FTU_CHECK -> "Initializing…" to MaterialTheme.colorScheme.tertiary
            ConnectionState.SYNCING -> "Syncing…" to syncingColor
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), shape = CircleShape)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, shape = CircleShape)
        )
        Text(label, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = color)
    }
}

@Composable
fun ConnectionStatusChip(state: ConnectionState) {
    val syncingColor = Color(0xFF7E57C2)
    val (label, color) = when (state) {
        ConnectionState.DISCONNECTED -> "Disconnected" to MaterialTheme.colorScheme.error
        ConnectionState.CONNECTING -> "Connecting…" to MaterialTheme.colorScheme.tertiary
        ConnectionState.CONNECTED -> "Connected" to MaterialTheme.colorScheme.primary
        ConnectionState.FTU_CHECK -> "Initializing…" to MaterialTheme.colorScheme.tertiary
        ConnectionState.SYNCING -> "Syncing…" to syncingColor
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), shape = CircleShape)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, shape = CircleShape)
        )
        Text(label, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = color)
    }
}

@Composable
fun BatteryIndicator(level: Int, modifier: Modifier = Modifier) {
    val color = when {
        level <= 20 -> MaterialTheme.colorScheme.error
        level <= 50 -> Color(0xFFF57C00)
        else -> Color(0xFF388E3C)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
    ) {
        Text("🔋", fontSize = 12.sp)
        Text("$level%", fontSize = 10.sp, color = color, fontWeight = FontWeight.Medium)
    }
}
