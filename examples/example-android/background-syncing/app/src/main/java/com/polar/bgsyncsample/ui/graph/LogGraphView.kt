// Copyright © 2026 Polar Electro Oy. All rights reserved.

package com.polar.bgsyncsample.ui.graph

import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.ui.unit.Dp
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

// Zoom levels: 0.5m, 1m, 2m, 5m, 15m, 30m, 1h, 2h, 6h, 12h, 24h, 48h, 1w, 1month, 1year
private val ZOOM_LEVELS = listOf(
    1.0 / 60, 5.0 / 60, 10.0 / 60,  // 1 second, 5 seconds, 10 seconds
    0.5, 1.0, 2.0, 5.0, 15.0, 30.0, 60.0, 120.0, 360.0, 720.0, 1440.0, 2880.0, 10080.0, 43200.0, 525600.0
)

// Where to anchor the view after a zoom-level change
private enum class ZoomAnchorMode { START, CENTER, END }
private data class ZoomAnchor(val mode: ZoomAnchorMode, val fraction: Float)

/**
 * Graph view for sync logs, showing battery levels, sync phases, and cleanup phases.
 * Displays as a timeline with Y-axis for battery percentage (0-100%) and X-axis for time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogGraphView(
    graphData: LogGraphData,
    allDevices: List<String>,
    onBackClick: () -> Unit,
    loadGraphData: (String) -> LogGraphData = { graphData },  // Closure to reload graph for a device
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember { context as? ComponentActivity }
    
    // Set landscape orientation on enter, restore portrait on exit
    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
    
    var selectedDevice by remember { mutableStateOf(graphData.deviceId) }
    var showDeviceSelector by remember { mutableStateOf(false) }
    var currentGraphData by remember { mutableStateOf(graphData) }
    var zoomIndex by remember { mutableStateOf(ZOOM_LEVELS.size) }  // MAX mode (show all)

    // Reload graph data when device selection changes
    LaunchedEffect(selectedDevice) {
        currentGraphData = loadGraphData(selectedDevice)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Updated header with device ID and optional Change button
        TopAppBar(
            title = { Text("Sync Log Graph - ${currentGraphData.deviceId}") },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                // Show Change button only if multiple devices
                if (allDevices.size > 1) {
                    TextButton(onClick = { showDeviceSelector = true }) {
                        Text("Change")
                    }
                }
            }
        )

        // Main graph area - uses all freed space
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            if (currentGraphData.batteryReadings.isEmpty() &&
                currentGraphData.syncPhases.isEmpty() &&
                currentGraphData.cleanupPhases.isEmpty()
            ) {
                Text(
                    "No data to display",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                TimelineGraph(
                    currentGraphData,
                    zoomIndex = zoomIndex,
                    onZoomIndexChange = { zoomIndex = it }
                )
            }
        }
    }
    
    // Device selector dialog
    if (showDeviceSelector) {  
        AlertDialog(
            onDismissRequest = { showDeviceSelector = false },
            title = { Text("Select Device") },
            text = {
                Column {
                    allDevices.forEach { deviceId ->
                        Button(
                            onClick = {
                                selectedDevice = deviceId
                                showDeviceSelector = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (deviceId == selectedDevice) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Text(
                                deviceId,
                                modifier = Modifier.fillMaxWidth(),
                                color = if (deviceId == selectedDevice) 
                                    MaterialTheme.colorScheme.onPrimary 
                                else 
                                    MaterialTheme.colorScheme.onSecondary
                            )
                            if (deviceId == selectedDevice) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("✓", color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDeviceSelector = false }) {
                    Text("Done")
                }
            }
        )
    }
}

private fun zoomLabel(minutes: Double): String {
    val tolerance = 0.0001
    return when {
        Math.abs(minutes - 1.0/60) < tolerance -> "1s"
        Math.abs(minutes - 5.0/60) < tolerance -> "5s"
        Math.abs(minutes - 10.0/60) < tolerance -> "10s"
        minutes == 0.5 -> "30s"
        minutes == 1.0 -> "1m"
        minutes == 2.0 -> "2m"
        minutes == 5.0 -> "5m"
        minutes == 15.0 -> "15m"
        minutes == 30.0 -> "30m"
        minutes == 60.0 -> "1h"
        minutes == 120.0 -> "2h"
        minutes == 360.0 -> "6h"
        minutes == 720.0 -> "12h"
        minutes == 1440.0 -> "24h"
        minutes == 2880.0 -> "48h"
        minutes == 10080.0 -> "1w"
        minutes == 43200.0 -> "1mo"
        minutes == 525600.0 -> "1y"
        else -> String.format("%.1fm", minutes)
    }
}

// Data class to represent a selected bar
data class SelectedBar(
    val type: String,  // "SYNC", "ACTIVITY", "Offline Recording", "Measurement", "CLEANUP"
    val dataType: String = "",  // Name of the data type (for activity/recording types)
    val deviceId: String = "",
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val durationMs: Long,
    val transferredBytes: Long? = null
)

private enum class BatteryMarkerState { CHARGING, DISCHARGING, UNKNOWN }

private data class SelectedBatteryMarker(
    val source: String, // Phone or Device
    val batteryPercent: Int,
    val deltaPercent: Int?,
    val chargingState: String
)

private val SyncStatusColor = Color(0xFF7E57C2)
private val CleanupStatusColor = Color(0xFF42A5F5)
private val RecordingBarColor = Color(0xFFFFA726)

private fun normalizedDataType(dataType: String): String = when (dataType.uppercase()) {
    "HR_SAMPLES_247", "OFFLINE_HR" -> "HR"
    "PPI_SAMPLES_247", "OFFLINE_PPI" -> "PPI"
    "OFFLINE_TEMPERATURE" -> "TEMPERATURE"
    "SKIN_TEMPERATURE", "OFFLINE_SKIN_TEMPERATURE" -> "SKIN_TEMPERATURE"
    "OFFLINE_ACC" -> "ACC"
    "OFFLINE_DERIVED_ACC" -> "DERIVED_ACC"
    "OFFLINE_ECG" -> "ECG"
    "OFFLINE_GYRO" -> "GYRO"
    "OFFLINE_MAGNETOMETER" -> "MAGNETOMETER"
    "OFFLINE_PPG" -> "PPG"
    "TRAINING" -> "TRAINING_SESSIONS"
    else -> dataType.uppercase()
}

private fun typeColor(dataType: String): Color = when (normalizedDataType(dataType)) {
    "STEPS" -> Color(0xFF66BB6A)
    "DISTANCE" -> Color(0xFF26A69A)
    "ACTIVE_TIME" -> Color(0xFF9CCC65)
    "CALORIES" -> Color(0xFFFF7043)
    "HR" -> Color(0xFFEF5350)
    "PPI" -> Color(0xFFFFCA28)
    "NIGHTLY_RECHARGE" -> Color(0xFF5C6BC0)
    "ACTIVITY_SAMPLES" -> Color(0xFF8D6E63)
    "DAILY_SUMMARY" -> Color(0xFF29B6F6)
    "SLEEP" -> Color(0xFFAB47BC)
    "TRAINING_SESSIONS" -> Color(0xFF00897B)
    "SKIN_TEMPERATURE" -> Color(0xFFEC407A)
    "TEMPERATURE" -> Color(0xFFFFA726)
    "ACC" -> Color(0xFF42A5F5)
    "DERIVED_ACC" -> Color(0xFF1E88E5)
    "ECG" -> Color(0xFF8E24AA)
    "GYRO" -> Color(0xFFD4E157)
    "MAGNETOMETER" -> Color(0xFFA1887F)
    "PPG" -> Color(0xFF00ACC1)
    else -> Color(0xFFB0BEC5)
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0)
    return String.format("%.1f MB", bytes / (1024.0 * 1024.0))
}

private fun batteryMarkerState(state: String): BatteryMarkerState = when (state.uppercase()) {
    "CHARGING", "FULL", "TRUE" -> BatteryMarkerState.CHARGING
    "UNPLUGGED", "FALSE", "DISCHARGING_ACTIVE", "DISCHARGING_INACTIVE", "DISCHARGINGACTIVE", "DISCHARGINGINACTIVE", "NOT_CHARGING" -> BatteryMarkerState.DISCHARGING
    else -> BatteryMarkerState.UNKNOWN
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimelineGraph(
    graphData: LogGraphData,
    zoomIndex: Int,
    onZoomIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    // Persistent window position (seconds from minTime to the viewport's left edge)
    val windowStartSecState = remember(graphData) { mutableStateOf(0.0) }
    var pendingZoomAnchor by remember(graphData) { mutableStateOf<Pair<ZoomAnchorMode, Double>?>(null) }
    var selectedBar by remember { mutableStateOf<SelectedBar?>(null) }
    var showDetailSheet by remember { mutableStateOf(false) }
    var selectedBatteryMarker by remember { mutableStateOf<SelectedBatteryMarker?>(null) }
    var showBatteryMarkerSheet by remember { mutableStateOf(false) }

    // Calculate time range
    val allTimestamps = mutableListOf<LocalDateTime>()
    // Use only phase times (sync/cleanup), not battery readings which may span hours
    allTimestamps.addAll(graphData.syncPhases.flatMap { listOf(it.startTime, it.endTime) })
    allTimestamps.addAll(graphData.cleanupPhases.flatMap { listOf(it.startTime, it.endTime) })

    if (allTimestamps.isEmpty()) {
        Text("No data to display", modifier = Modifier.padding(16.dp))
        return
    }

    val minTime = allTimestamps.minOrNull() ?: return  // Always first timestamp
    val lastEvent = allTimestamps.maxOrNull() ?: return  // Last stage end
    // Extend the end by 10 seconds so trailing timeline is visible after the last stage
    val maxTime = lastEvent.plusSeconds(10)

    val totalSeconds = ChronoUnit.SECONDS.between(minTime, maxTime).toDouble().coerceAtLeast(1.0)
    val timeRangeMinutes = (totalSeconds / 60.0).toInt().coerceAtLeast(5)

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val leftColumnWidth = 110.dp
        val topSectionHeight = maxHeight * 0.27f
        // Bottom fixed rows: battery heading + combined time-axis+scrollbar block.
        val bottomFixedHeight = 40.dp + 46.dp
        val batteryGraphHeight = (maxHeight - topSectionHeight - bottomFixedHeight).coerceAtLeast(80.dp)
        val availableWidth = (maxWidth - leftColumnWidth).coerceAtLeast(1.dp)
        val viewportPx = with(density) { availableWidth.toPx() }.toDouble()

        // Dynamic MAX (zoom-out cap): largest zoom level still more zoomed-in than the
        // data span; one step beyond that is MAX (fit all data).
        val maxUsefulIndex = ZOOM_LEVELS.indexOfLast { it < timeRangeMinutes.toDouble() }
        val maxZoomIndex = maxUsefulIndex + 1
        // No zoom-in cap needed: the timeline is virtualized to a sliding time window, so the
        // rendered content is always viewport-sized no matter how deep the zoom is.
        val effIndex = zoomIndex.coerceIn(0, maxZoomIndex)
        val zoomMinutes: Double? = if (effIndex <= maxUsefulIndex) ZOOM_LEVELS[effIndex] else null
        val zoomLabelText: String = if (effIndex <= maxUsefulIndex) zoomLabel(ZOOM_LEVELS[effIndex]) else "MAX"

        // The visible time window (seconds) that fills the viewport, and how far it can slide.
        val windowSec = (if (zoomMinutes != null) zoomMinutes * 60.0 else totalSeconds).coerceIn(1.0, totalSeconds)
        val scrollableSec = (totalSeconds - windowSec).coerceAtLeast(0.0)
        val startSec = windowStartSecState.value.coerceIn(0.0, scrollableSec)

        // Capture the anchor (start / end / center) before a zoom so we can restore it after.
        val captureAnchor = {
            val atStart = startSec <= 0.5
            val atEnd = startSec >= scrollableSec - 0.5
            val centerSec = startSec + windowSec / 2.0
            when {
                // Everything currently fits (MAX / not scrollable): keep the middle centered
                // when zooming in, instead of jumping to one edge and losing the data in view.
                scrollableSec <= 0.5 -> ZoomAnchorMode.CENTER to centerSec
                atEnd -> ZoomAnchorMode.END to centerSec
                atStart -> ZoomAnchorMode.START to centerSec
                else -> ZoomAnchorMode.CENTER to centerSec
            }
        }
        val doZoomIn = {
            pendingZoomAnchor = captureAnchor()
            onZoomIndexChange((effIndex - 1).coerceAtLeast(0))
        }
        val doZoomOut = {
            pendingZoomAnchor = captureAnchor()
            onZoomIndexChange((effIndex + 1).coerceAtMost(maxZoomIndex))
        }

        // After the window size changes (zoom), reposition it to keep the captured anchor.
        LaunchedEffect(effIndex) {
            val anchor = pendingZoomAnchor
            if (anchor != null) {
                val (mode, centerSec) = anchor
                val newStart = when (mode) {
                    ZoomAnchorMode.START -> 0.0
                    ZoomAnchorMode.END -> totalSeconds - windowSec
                    ZoomAnchorMode.CENTER -> centerSec - windowSec / 2.0
                }
                windowStartSecState.value = newStart.coerceIn(0.0, scrollableSec)
                pendingZoomAnchor = null
            }
        }

        // Horizontal drag slides the data window through the whole timeline.
        val dragModifier = Modifier.pointerInput(effIndex, viewportPx) {
            detectHorizontalDragGestures { change, dragAmount ->
                change.consume()
                if (scrollableSec > 0.0 && viewportPx > 0.0) {
                    val secPerPx = windowSec / viewportPx
                    windowStartSecState.value =
                        (windowStartSecState.value - dragAmount * secPerPx).coerceIn(0.0, scrollableSec)
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
        // Bar rows with Y-axis labels
        Row(modifier = Modifier.height(topSectionHeight)) {
            // Left column: Bar row labels (aligned with the five bar rows)
            Column(
                modifier = Modifier
                    .width(110.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF262626)),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.End
            ) {
                listOf("Sync", "Activity", "Offline Recording", "Measurement", "Cleanup").forEach { label ->
                    Text(
                        label,
                        fontSize = 9.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .wrapContentHeight(Alignment.CenterVertically)
                            .padding(end = 4.dp)
                    )
                }
            }
            
            // Windowed bar area (drag to slide the data window)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(0xFF1a1a1a))
                    .clipToBounds()
                    .then(dragModifier)
            ) {
                WindowedBarRows(
                    graphData, minTime, startSec, windowSec,
                    onBarTapped = { bar ->
                        selectedBar = bar
                        showDetailSheet = true
                    }
                )
            }
        }
        
        // Battery levels heading row: heading + color legend in the left column (matches iOS)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(Color(0xFF1a1a1a))
        ) {
            Column(
                modifier = Modifier
                    .width(110.dp)
                    .fillMaxHeight()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.Start
            ) {
                Text("Battery levels", fontSize = 9.sp, color = Color(0xFFFFFFFF), fontWeight = FontWeight.SemiBold, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(7.dp).background(Color.Red, shape = CircleShape))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Phone", fontSize = 9.sp, lineHeight = 9.sp, color = Color.Red, fontWeight = FontWeight.Bold, maxLines = 1)
                    Spacer(Modifier.width(8.dp))
                    Box(modifier = Modifier.size(7.dp).background(Color(0xFF00DD00), shape = CircleShape))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Device", fontSize = 9.sp, lineHeight = 9.sp, color = Color(0xFF00DD00), fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }

        // Battery percentage area with Y-axis
        Row(modifier = Modifier.height(batteryGraphHeight)) {
            // Y-axis labels (battery %); width matches the sync-bar left column so the canvas stays aligned.
            Column(
                modifier = Modifier
                    .width(110.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF1a1a1a))
                    .padding(vertical = 6.dp),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.End
            ) {
                for (pct in 100 downTo 0 step 20) {
                    Text(
                        "$pct%",
                        fontSize = 8.sp,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .wrapContentHeight(Alignment.CenterVertically)
                            .padding(end = 4.dp),
                        color = Color.White
                    )
                }
            }

            // Windowed battery graph (drag to slide the data window)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(0xFF2a2a2a))
                    .padding(8.dp)
                    .clipToBounds()
                    .then(dragModifier)
            ) {
                WindowedBattery(
                    graphData = graphData,
                    minTime = minTime,
                    startSec = startSec,
                    windowSec = windowSec,
                    onMarkerTapped = { marker ->
                        selectedBatteryMarker = marker
                        showBatteryMarkerSheet = true
                    }
                )
            }
        }
        
        // Combined time axis + scroll position indicator (no visual gap between them)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .background(Color(0xFF1a1a1a))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Zoom controls (horizontal, on the time-axis line)
                    Row(
                        modifier = Modifier
                            .width(110.dp)
                            .fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2196F3))
                                .clickable { doZoomIn() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            zoomLabelText,
                            fontSize = 8.sp,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.width(34.dp).padding(horizontal = 2.dp)
                        )
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2196F3))
                                .clickable { doZoomOut() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("−", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Windowed time axis (drag to slide the data window)
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().then(dragModifier)) {
                        WindowedTimeAxis(minTime, startSec, windowSec)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                ) {
                    Spacer(modifier = Modifier.width(110.dp))
                    Canvas(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color(0xFF151515))
                            .pointerInput(effIndex) {
                                detectTapGestures { offset ->
                                    if (scrollableSec > 0.0) {
                                        val trackW = size.width.toDouble().coerceAtLeast(1.0)
                                        val frac = (windowSec / totalSeconds).coerceIn(0.02, 1.0)
                                        val thumbW = (trackW * frac).coerceAtLeast(minOf(trackW, 64.0))
                                        val movable = (trackW - thumbW).coerceAtLeast(1.0)
                                        val targetCenter = offset.x.toDouble().coerceIn(0.0, trackW)
                                        val newThumbX = (targetCenter - thumbW / 2.0).coerceIn(0.0, movable)
                                        val newPos = (newThumbX / movable).coerceIn(0.0, 1.0)
                                        windowStartSecState.value = newPos * scrollableSec
                                    }
                                }
                            }
                            .pointerInput(effIndex) {
                                detectHorizontalDragGestures { change, dragAmount ->
                                    change.consume()
                                    if (scrollableSec > 0.0) {
                                        val trackW = size.width.toDouble()
                                        val frac = (windowSec / totalSeconds).coerceIn(0.02, 1.0)
                                        val thumbW = (trackW * frac).coerceAtLeast(minOf(trackW, 64.0))
                                        val movable = (trackW - thumbW).coerceAtLeast(1.0)
                                        val curPos = windowStartSecState.value.coerceIn(0.0, scrollableSec) / scrollableSec
                                        val newPos = (curPos + dragAmount / movable).coerceIn(0.0, 1.0)
                                        windowStartSecState.value = newPos * scrollableSec
                                    }
                                }
                            }
                    ) {
                        val trackW = size.width
                        val frac = (windowSec / totalSeconds).toFloat().coerceIn(0.02f, 1f)
                        val thumbW = (trackW * frac).coerceAtLeast(minOf(trackW, 64f))
                        val posFrac = if (scrollableSec > 0.0) (startSec / scrollableSec).toFloat() else 0f
                        val thumbX = (trackW - thumbW) * posFrac
                        drawRect(Color(0xFF333333), topLeft = Offset(0f, 0f), size = Size(trackW, size.height))
                        drawRoundRect(
                            Color(0xFF2196F3),
                            topLeft = Offset(thumbX, 0f),
                            size = Size(thumbW, size.height),
                            cornerRadius = CornerRadius(8f, 8f)
                        )
                    }
                }
            }
        }
        }  // Column
    }  // BoxWithConstraints
    
    // Detail sheet for tapped bar
    if (showDetailSheet && selectedBar != null) {
        val bar = selectedBar!!
        ModalBottomSheet(
            onDismissRequest = { showDetailSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    "Bar Details - ${bar.type}",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                DetailRow("Device ID", bar.deviceId)
                DetailRow("Data Type", bar.dataType.ifBlank { "N/A" })
                DetailRow("Start", bar.startTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")))
                DetailRow("End", bar.endTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")))
                DetailRow("Duration", "${bar.durationMs}ms")
                bar.transferredBytes?.let { DetailRow("Transferred Bytes", formatBytes(it)) }
                
                Button(
                    onClick = { showDetailSheet = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Text("Close")
                }
            }
        }
    }

    if (showBatteryMarkerSheet && selectedBatteryMarker != null) {
        val marker = selectedBatteryMarker!!
        AlertDialog(
            onDismissRequest = { showBatteryMarkerSheet = false },
            title = { Text("Battery Marker - ${marker.source}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Battery: ${marker.batteryPercent}%")
                    Text("Delta: ${marker.deltaPercent?.let { if (it >= 0) "+${it}%" else "${it}%" } ?: "N/A"}")
                    Text("State: ${marker.chargingState}")
                }
            },
            confirmButton = {
                TextButton(onClick = { showBatteryMarkerSheet = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.3f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.7f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

@Composable
private fun WindowedBarRows(
    graphData: LogGraphData,
    minTime: LocalDateTime,
    startSec: Double,
    windowSec: Double,
    onBarTapped: (SelectedBar) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var tapCoordinates by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    tapCoordinates = offset.x to offset.y
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val winStart = startSec
        val winEnd = startSec + windowSec
        // Seconds (with millisecond precision) from the timeline origin.
        fun secOf(t: LocalDateTime): Double =
            ChronoUnit.MILLIS.between(minTime, t).toDouble() / 1000.0
        
        // Five rows dividing the full canvas height equally
        val rowHeight = h / 5f
        val barGapPx = with(density) { 4.dp.toPx() }
        val barInsetY = (barGapPx / 2f).coerceAtMost((rowHeight - 2f) / 2f)
        val barHeight = (rowHeight - barGapPx).coerceAtLeast(2f)
        val rowY = floatArrayOf(
            0f,
            rowHeight,
            2f * rowHeight,
            3f * rowHeight,
            4f * rowHeight
        )

        // Background tints for each row
        for (row in 0..4) {
            drawRect(
                color = when (row) {
                    0 -> SyncStatusColor.copy(alpha = 0.1f)
                    1 -> Color(0xFF4DB6AC).copy(alpha = 0.1f)
                    2 -> RecordingBarColor.copy(alpha = 0.1f)
                    3 -> Color(0xFF00ACC1).copy(alpha = 0.1f)
                    else -> CleanupStatusColor.copy(alpha = 0.1f)
                },
                topLeft = Offset(0f, rowY[row]),
                size = Size(w, rowHeight)
            )
        }

        val minBarWidth = 4f
        // Track bars for hit testing
        data class BarHitBox(val rect: androidx.compose.ui.geometry.Rect, val selectedBar: SelectedBar)
        val hitBoxes = mutableListOf<BarHitBox>()
        
        // Clip each bar to the window IN SECONDS before converting to pixels. This guarantees the
        // resulting x coordinates are always within [0, w] (never huge off-screen values that the
        // renderer would cull), so bars stay visible while scrolled/zoomed — just like markers.
        val bar: (Double, Double, Float, Color, SelectedBar) -> Unit = { s, e, y, color, barData ->
            if (e > winStart && s < winEnd) {
                val cs = s.coerceIn(winStart, winEnd)
                val ce = e.coerceIn(winStart, winEnd)
                val left = (((cs - winStart) / windowSec) * w).toFloat()
                val right = (((ce - winStart) / windowSec) * w).toFloat()
                var l = left
                var bw = right - left
                if (bw < minBarWidth) {
                    bw = minBarWidth
                    l = left.coerceAtMost((w - minBarWidth).coerceAtLeast(0f))
                }
                drawRect(color, topLeft = Offset(l, y + barInsetY), size = Size(bw, barHeight))
                // Add hit box
                hitBoxes.add(BarHitBox(
                    androidx.compose.ui.geometry.Rect(l, y + barInsetY, l + bw, y + barInsetY + barHeight),
                    barData
                ))
            }
        }

        // Row 0: Overall sync phases
        graphData.syncPhases.forEach { 
            bar(secOf(it.startTime), secOf(it.endTime), rowY[0], SyncStatusColor,
                SelectedBar("SYNC", "", it.deviceId, it.startTime, it.endTime, it.durationMs, it.transferredBytes))
        }
        
        // Row 1: Individual activity type phases (color-coded, sequenced left-to-right)
        graphData.activityTypePhases
            .filter { it.category == "ACTIVITY" }
            .forEach { 
                bar(secOf(it.startTime), secOf(it.endTime), rowY[1], typeColor(it.dataType),
                    SelectedBar("ACTIVITY", it.dataType, it.deviceId, it.startTime, it.endTime, it.durationMs, it.transferredBytes))
            }
        
        // Row 2: Overall recording phases (orange)
        graphData.recordingPhases.forEach { 
            bar(secOf(it.startTime), secOf(it.endTime), rowY[2], RecordingBarColor,
                SelectedBar("Offline Recording", "", it.deviceId, it.startTime, it.endTime, it.durationMs, it.transferredBytes))
        }
        
        // Row 3: Individual recording type phases (color-coded, sequenced left-to-right)
        graphData.recordingTypePhases
            .filter { it.category == "OFFLINE" }
            .forEach { 
                bar(secOf(it.startTime), secOf(it.endTime), rowY[3], typeColor(it.dataType),
                    SelectedBar("Measurement", it.dataType, it.deviceId, it.startTime, it.endTime, it.durationMs, it.transferredBytes))
            }
        
        // Row 4: Cleanup phases
        graphData.cleanupPhases.forEach { 
            bar(secOf(it.startTime), secOf(it.endTime), rowY[4], CleanupStatusColor,
                SelectedBar("CLEANUP", "", it.deviceId, it.startTime, it.endTime, it.durationMs, it.transferredBytes))
        }
        
        // Hit testing: check if tap falls within any bar
        if (tapCoordinates != null) {
            val (tapX, tapY) = tapCoordinates!!
            val hitBar = hitBoxes.find { it.rect.contains(Offset(tapX, tapY)) }
            if (hitBar != null) {
                onBarTapped(hitBar.selectedBar)
            }
            tapCoordinates = null
        }
    }
}

@Composable
private fun WindowedBattery(
    graphData: LogGraphData,
    minTime: LocalDateTime,
    startSec: Double,
    windowSec: Double,
    onMarkerTapped: (SelectedBatteryMarker) -> Unit,
    modifier: Modifier = Modifier
) {
    val markerHotspots = remember(graphData, minTime, startSec, windowSec) { mutableStateListOf<Pair<androidx.compose.ui.geometry.Rect, SelectedBatteryMarker>>() }
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(graphData, minTime, startSec, windowSec) {
                detectTapGestures { tapOffset ->
                    val hit = markerHotspots.lastOrNull { (rect, _) ->
                        tapOffset.x in rect.left..rect.right && tapOffset.y in rect.top..rect.bottom
                    }
                    hit?.let { onMarkerTapped(it.second) }
                }
            }
    ) {
        markerHotspots.clear()
        val w = size.width
        val h = size.height
        val chartTop = 8f
        val chartBottom = 8f
        val chartHeight = (h - chartTop - chartBottom).coerceAtLeast(1f)
        fun xFor(t: LocalDateTime): Float {
            val tSec = ChronoUnit.SECONDS.between(minTime, t).toDouble()
            return (((tSec - startSec) / windowSec) * w).toFloat()
        }
        fun markerY(percent: Int, markerHalf: Float): Float {
            val raw = chartTop + chartHeight * (1f - percent / 100f)
            return raw.coerceIn(chartTop + markerHalf, chartTop + chartHeight - markerHalf)
        }
        fun markerHalfFor(state: BatteryMarkerState): Float = if (state == BatteryMarkerState.UNKNOWN) 5f else 10f
        fun addHotspot(
            centerX: Float,
            centerY: Float,
            half: Float,
            source: String,
            batteryPercent: Int,
            deltaPercent: Int?,
            chargingState: String
        ) {
            val hitPad = 14f
            markerHotspots.add(
                androidx.compose.ui.geometry.Rect(
                    centerX - half - hitPad,
                    centerY - half - hitPad,
                    centerX + half + hitPad,
                    centerY + half + hitPad
                ) to SelectedBatteryMarker(
                    source = source,
                    batteryPercent = batteryPercent,
                    deltaPercent = deltaPercent,
                    chargingState = chargingState.uppercase()
                )
            )
        }
        // Horizontal grid (every 20%)
        for (pct in 0..100 step 20) {
            val y = chartTop + chartHeight * (pct / 100f)
            drawLine(Color.LightGray, Offset(0f, y), Offset(w, y), strokeWidth = 0.5f)
        }
        // Vertical time grid within the window (bounded to ~12 lines)
        val windowMin = (windowSec / 60.0).coerceAtLeast(1.0)
        val stepMin = maxOf(1, kotlin.math.ceil(windowMin / 12.0).toInt())
        val startMin = startSec / 60.0
        val endMin = (startSec + windowSec) / 60.0
        var k = kotlin.math.floor(startMin / stepMin).toInt()
        while (k.toDouble() * stepMin <= endMin + stepMin) {
            val x = (((k.toDouble() * stepMin * 60.0 - startSec) / windowSec) * w).toFloat()
            if (x in 0f..w) {
                drawLine(Color.LightGray.copy(alpha = 0.3f), Offset(x, 0f), Offset(x, h), strokeWidth = 0.5f)
            }
            k++
        }
        // Phone battery line (red) — full polyline; x is clamped to a bounded range so far
        // off-screen readings don't produce huge coordinates that get culled by the renderer.
        if (graphData.batteryReadings.size > 1) {
            val path = androidx.compose.ui.graphics.Path()
            var first = true
            graphData.batteryReadings.forEach { r ->
                val x = xFor(r.timestamp).coerceIn(-w, 2f * w)
                val y = chartTop + chartHeight * (1f - r.phoneBatteryPercent / 100f)
                if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
            }
            drawPath(path, Color.Red, style = Stroke(width = 2f))
            graphData.batteryReadings.forEachIndexed { index, r ->
                val x = xFor(r.timestamp).coerceIn(0f, w)
                val state = batteryMarkerState(r.phoneChargingState)
                val markerHalf = markerHalfFor(state)
                val y = markerY(r.phoneBatteryPercent, markerHalf)
                when (state) {
                    BatteryMarkerState.CHARGING -> drawRect(Color.Red, topLeft = Offset(x - markerHalf, y - markerHalf), size = Size(markerHalf * 2, markerHalf * 2))
                    BatteryMarkerState.DISCHARGING -> drawRect(Color.Red, topLeft = Offset(x - markerHalf, y - markerHalf), size = Size(markerHalf * 2, markerHalf * 2), style = Stroke(width = 2f))
                    BatteryMarkerState.UNKNOWN -> drawRect(Color.Red, topLeft = Offset(x - markerHalf, y - markerHalf), size = Size(markerHalf * 2, markerHalf * 2))
                }
                val prev = graphData.batteryReadings.getOrNull(index - 1)
                val delta = prev?.let { r.phoneBatteryPercent - it.phoneBatteryPercent }
                addHotspot(x, y, markerHalf, "Phone", r.phoneBatteryPercent, delta, r.phoneChargingState)
            }
        } else if (graphData.batteryReadings.size == 1) {
            val reading = graphData.batteryReadings.first()
            drawCircle(
                color = Color.Red,
                radius = 4f,
                center = Offset(
                    xFor(reading.timestamp).coerceIn(0f, w),
                    chartTop + chartHeight * (1f - reading.phoneBatteryPercent / 100f)
                )
            )
            val mx = xFor(reading.timestamp).coerceIn(0f, w)
            val state = batteryMarkerState(reading.phoneChargingState)
            val markerHalf = markerHalfFor(state)
            val my = markerY(reading.phoneBatteryPercent, markerHalf)
            when (state) {
                BatteryMarkerState.CHARGING -> drawRect(Color.Red, topLeft = Offset(mx - markerHalf, my - markerHalf), size = Size(markerHalf * 2, markerHalf * 2))
                BatteryMarkerState.DISCHARGING -> drawRect(Color.Red, topLeft = Offset(mx - markerHalf, my - markerHalf), size = Size(markerHalf * 2, markerHalf * 2), style = Stroke(width = 2f))
                BatteryMarkerState.UNKNOWN -> drawRect(Color.Red, topLeft = Offset(mx - markerHalf, my - markerHalf), size = Size(markerHalf * 2, markerHalf * 2))
            }
            addHotspot(mx, my, markerHalf, "Phone", reading.phoneBatteryPercent, null, reading.phoneChargingState)
        }
        // Device battery line (green) — full polyline; clamped as above
        val dev = graphData.batteryReadings.filter { it.deviceBatteryPercent != null }
        if (dev.size > 1) {
            val path = androidx.compose.ui.graphics.Path()
            var first = true
            dev.forEach { r ->
                val x = xFor(r.timestamp).coerceIn(-w, 2f * w)
                val y = chartTop + chartHeight * (1f - (r.deviceBatteryPercent ?: 0) / 100f)
                if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
            }
            drawPath(path, Color(0xFF00DD00), style = Stroke(width = 2f))
            dev.forEachIndexed { index, r ->
                val x = xFor(r.timestamp).coerceIn(0f, w)
                val state = batteryMarkerState(r.deviceChargingState)
                val markerHalf = markerHalfFor(state)
                val percent = r.deviceBatteryPercent ?: 0
                val y = markerY(percent, markerHalf)
                when (state) {
                    BatteryMarkerState.CHARGING -> drawRect(Color(0xFF00DD00), topLeft = Offset(x - markerHalf, y - markerHalf), size = Size(markerHalf * 2, markerHalf * 2))
                    BatteryMarkerState.DISCHARGING -> drawRect(Color(0xFF00DD00), topLeft = Offset(x - markerHalf, y - markerHalf), size = Size(markerHalf * 2, markerHalf * 2), style = Stroke(width = 2f))
                    BatteryMarkerState.UNKNOWN -> drawRect(Color(0xFF00DD00), topLeft = Offset(x - markerHalf, y - markerHalf), size = Size(markerHalf * 2, markerHalf * 2))
                }
                val prevPercent = dev.getOrNull(index - 1)?.deviceBatteryPercent
                val delta = prevPercent?.let { percent - it }
                addHotspot(x, y, markerHalf, "Device", percent, delta, r.deviceChargingState)
            }
        } else if (dev.size == 1) {
            val reading = dev.first()
            drawCircle(
                color = Color(0xFF00DD00),
                radius = 4f,
                center = Offset(
                    xFor(reading.timestamp).coerceIn(0f, w),
                    chartTop + chartHeight * (1f - (reading.deviceBatteryPercent ?: 0) / 100f)
                )
            )
            val mx = xFor(reading.timestamp).coerceIn(0f, w)
            val state = batteryMarkerState(reading.deviceChargingState)
            val markerHalf = markerHalfFor(state)
            val percent = reading.deviceBatteryPercent ?: 0
            val my = markerY(percent, markerHalf)
            when (state) {
                BatteryMarkerState.CHARGING -> drawRect(Color(0xFF00DD00), topLeft = Offset(mx - markerHalf, my - markerHalf), size = Size(markerHalf * 2, markerHalf * 2))
                BatteryMarkerState.DISCHARGING -> drawRect(Color(0xFF00DD00), topLeft = Offset(mx - markerHalf, my - markerHalf), size = Size(markerHalf * 2, markerHalf * 2), style = Stroke(width = 2f))
                BatteryMarkerState.UNKNOWN -> drawRect(Color(0xFF00DD00), topLeft = Offset(mx - markerHalf, my - markerHalf), size = Size(markerHalf * 2, markerHalf * 2))
            }
            addHotspot(mx, my, markerHalf, "Device", percent, null, reading.deviceChargingState)
        }
    }
}

@Composable
private fun WindowedTimeAxis(
    minTime: LocalDateTime,
    startSec: Double,
    windowSec: Double,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewportPx = with(density) { maxWidth.toPx() }.toDouble()
        val targetLabelCount = 6.0
        val minPixelSpacing = 70.0
        val minStepByCountSec = (windowSec / targetLabelCount).coerceAtLeast(1.0)
        val minStepBySpacingSec = if (viewportPx > 0.0) (windowSec * (minPixelSpacing / viewportPx)).coerceAtLeast(1.0) else minStepByCountSec
        val requiredStepSec = maxOf(minStepByCountSec, minStepBySpacingSec)
        val niceSeconds = listOf(1, 2, 5, 10, 15, 30, 60, 120, 300, 600, 900, 1800, 3600, 7200, 10800, 21600, 43200, 86400)
        val stepSec = (niceSeconds.firstOrNull { it >= requiredStepSec } ?: 86400).toDouble()
        val startAligned = kotlin.math.floor(startSec / stepSec).toLong() * stepSec
        val endSec = startSec + windowSec
        val showSeconds = stepSec < 60.0

        // Tick marks
        Canvas(modifier = Modifier.fillMaxWidth().height(6.dp)) {
            var tick = startAligned
            while (tick <= endSec + stepSec) {
                val x = (((tick - startSec) / windowSec) * size.width).toFloat()
                if (x in 0f..size.width) {
                    drawLine(Color.White, Offset(x, 0f), Offset(x, 6f), strokeWidth = 1f)
                }
                tick += stepSec
            }
        }
        // Labels (only those visible in the window)
        val labels = mutableListOf<Triple<Int, String, String>>()
        var tick = startAligned
        while (tick <= endSec + stepSec) {
            if (tick >= 0) {
                val x = ((tick - startSec) / windowSec) * viewportPx
                if (x >= -30 && x <= viewportPx + 10) {
                    val t = minTime.plusNanos((tick * 1_000_000_000L).toLong())
                    val timeLabel = if (showSeconds) {
                        "%02d:%02d:%02d".format(t.hour, t.minute, t.second)
                    } else {
                        "%02d:%02d".format(t.hour, t.minute)
                    }
                    val dateLabel = "%04d-%02d-%02d".format(t.year, t.monthValue, t.dayOfMonth)
                    labels.add(Triple(x.toInt(), dateLabel, timeLabel))
                }
            }
            tick += stepSec
        }
        labels.forEach { (xPx, dateTxt, timeTxt) ->
            Text(
                text = "$dateTxt\n$timeTxt",
                fontSize = 8.sp,
                lineHeight = 10.sp,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Clip,
                modifier = Modifier.offset { IntOffset(xPx + 2.dp.roundToPx(), 2.dp.roundToPx()) }
            )
        }
    }
}
