// Copyright © 2026 Polar Electro Oy. All rights reserved.

package com.polar.bgsyncsample.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.polar.polarbgsync.api.SyncableDataType
import com.polar.polarbgsync.api.SyncedDataEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataBrowserScreen(
    onBack: () -> Unit,
    viewModel: SyncViewModel
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val deviceIds = state.deviceIds

    var selectedDevice by remember { mutableStateOf(deviceIds.firstOrNull() ?: "") }
    var selectedType by remember { mutableStateOf(SyncableDataType.STEPS) }
    var entries by remember { mutableStateOf<List<SyncedDataEntry>>(emptyList()) }
    var selectedEntry by remember { mutableStateOf<SyncedDataEntry?>(null) }
    var jsonContent by remember { mutableStateOf<String?>(null) }

    // Entry counts per data type for the selected device
    val entryCounts = remember(selectedDevice, entries) {
        if (selectedDevice.isBlank()) emptyMap()
        else SyncableDataType.entries.associateWith { type ->
            viewModel.getSyncedData(selectedDevice, type).size
        }
    }

    LaunchedEffect(selectedDevice, selectedType) {
        entries = if (selectedDevice.isNotBlank()) {
            viewModel.getSyncedData(selectedDevice, selectedType).sortedByDescending { it.date }
        } else {
            emptyList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Synced Data") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
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
                .padding(16.dp)
        ) {
            // Device selector
            if (deviceIds.size > 1) {
                Text("Device", style = MaterialTheme.typography.labelMedium,
                     modifier = Modifier.padding(bottom = 4.dp))
                ScrollableTabRow(
                    selectedTabIndex = deviceIds.indexOf(selectedDevice).coerceAtLeast(0),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    deviceIds.forEach { id ->
                        Tab(
                            selected = id == selectedDevice,
                            onClick = {
                                selectedDevice = id
                                selectedEntry = null
                                jsonContent = null
                            },
                            text = {
                                Text(id, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            } else if (deviceIds.size == 1) {
                selectedDevice = deviceIds.first()
            }

            // Data type selector
            Text("Data Type", style = MaterialTheme.typography.labelMedium,
                 modifier = Modifier.padding(bottom = 4.dp))
            ScrollableTabRow(
                selectedTabIndex = SyncableDataType.entries.indexOf(selectedType),
                modifier = Modifier.fillMaxWidth()
            ) {
                SyncableDataType.entries.forEach { type ->
                    Tab(
                        selected = type == selectedType,
                        onClick = {
                            selectedType = type
                            selectedEntry = null
                            jsonContent = null
                        },
                        text = {
                            val label = type.name.lowercase().replace('_', ' ')
                                .replaceFirstChar { it.uppercase() }
                            val count = entryCounts[type] ?: 0
                            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                Text(label, fontSize = 11.sp, maxLines = 1)
                                Text("($count)", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (selectedEntry != null && jsonContent != null) {
                // JSON detail view
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "${selectedEntry!!.dataType} — ${selectedEntry!!.date}",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(
                                        Intent.EXTRA_SUBJECT,
                                        "${selectedEntry!!.dataType} ${selectedEntry!!.date}"
                                    )
                                    putExtra(Intent.EXTRA_TEXT, jsonContent!!)
                                }
                                context.startActivity(
                                    Intent.createChooser(shareIntent, "Share synced data")
                                )
                            }) {
                                Text("Share")
                            }
                            TextButton(onClick = {
                                selectedEntry = null
                                jsonContent = null
                            }) {
                                Text("Close")
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            jsonContent!!,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }
            } else {
                // Entry list
                if (entries.isEmpty()) {
                    Text(
                        "No synced data for ${selectedType.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(entries) { entry ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedEntry = entry
                                        jsonContent = viewModel.readSyncedData(entry)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            entry.date.toString(),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        if (deviceIds.size > 1) {
                                            Text(
                                                entry.deviceId,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Text(
                                        "${entry.sizeBytes} bytes",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
