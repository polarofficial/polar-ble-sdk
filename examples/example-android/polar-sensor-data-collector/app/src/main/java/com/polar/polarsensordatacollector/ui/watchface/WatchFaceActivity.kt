package com.polar.polarsensordatacollector.ui.watchface

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository
import com.polar.polarsensordatacollector.ui.theme.PolarsensordatacollectorTheme
import com.polar.sdk.api.model.PolarWatchFaceComplication
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private val Red700 = Color(0xffdd0d3c)

private val ALL_OPTIONS: List<PolarWatchFaceComplication> = PolarWatchFaceComplication.entries

private fun displayName(c: PolarWatchFaceComplication?, emptyLabel: String): String = when (c) {
    null -> "—"
    PolarWatchFaceComplication.EMPTY -> emptyLabel
    else -> c.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}

@AndroidEntryPoint
class WatchFaceActivity : AppCompatActivity() {

    @Inject
    lateinit var polarDeviceRepository: PolarDeviceRepository

    private lateinit var viewModel: WatchFaceViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val deviceId = polarDeviceRepository.deviceConnectionStatus.value.deviceId
        if (deviceId.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_no_device), Toast.LENGTH_SHORT).show()
            finish(); return
        }

        viewModel = ViewModelProvider(
            this,
            WatchFaceViewModelFactory(polarDeviceRepository, deviceId, application)
        )[WatchFaceViewModel::class.java]

        setContent {
            PolarsensordatacollectorTheme {
                WatchFaceContent(viewModel = viewModel)
            }
        }
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, WatchFaceActivity::class.java))
        }
    }
}

@Composable
private fun WatchFaceContent(viewModel: WatchFaceViewModel) {
    val state by viewModel.uiState.collectAsState()
    val emptyLabel = stringResource(R.string.watch_face_complication_empty)
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())
    ) {
        Text(
            text = stringResource(R.string.watch_face_screen_title),
            style = MaterialTheme.typography.h5,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        val status = state.readStatus
        val readFailedPrefix = stringResource(R.string.watch_face_status_read_failed_prefix)
        if (status != null && status.startsWith(readFailedPrefix)) {
            Text(
                text = status,
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.error,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        if (state.isLoading) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(12.dp))
        }

        state.slots.forEachIndexed { slotIndex, current ->
            SlotDropdown(
                slotIndex = slotIndex,
                current = current,
                enabled = !state.isLoading,
                emptyLabel = emptyLabel,
                onSelect = { viewModel.setSlot(slotIndex, it) }
            )
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { viewModel.applyComplications() },
            enabled = !state.isLoading,
            colors = ButtonDefaults.buttonColors(backgroundColor = Red700),
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(8.dp)
        ) { Text(stringResource(R.string.watch_face_apply_button), color = Color.White) }

        state.resultMessage?.let { msg ->
            val failedPrefix = stringResource(R.string.watch_face_failed_prefix)
            Spacer(Modifier.height(16.dp))
            Text(
                text = msg,
                color = if (msg.startsWith(failedPrefix)) MaterialTheme.colors.error else Color.White,
                style = MaterialTheme.typography.body1
            )
        }
    }
}

@Composable
private fun SlotDropdown(
    slotIndex: Int,
    current: PolarWatchFaceComplication?,
    enabled: Boolean,
    emptyLabel: String,
    onSelect: (PolarWatchFaceComplication) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(
            text = stringResource(R.string.watch_face_slot_label, slotIndex + 1),
            style = MaterialTheme.typography.caption,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Box(
            modifier = Modifier.fillMaxWidth()
                .border(1.dp, Color.White.copy(alpha = if (enabled) 0.3f else 0.15f), RoundedCornerShape(8.dp))
                .clickable(enabled = enabled) { expanded = true }
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(displayName(current, emptyLabel), style = MaterialTheme.typography.body1, color = Color.White)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                ALL_OPTIONS.forEach { option ->
                    DropdownMenuItem(onClick = { onSelect(option); expanded = false }) {
                        Text(
                            text = displayName(option, emptyLabel),
                            style = MaterialTheme.typography.body2,
                            fontWeight = if (option == current) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}
