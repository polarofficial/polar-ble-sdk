package com.polar.polarsensordatacollector.ui.hrbroadcast

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.ui.landing.MainViewModel

class HrBroadcastDialogFragment : DialogFragment() {

    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                HrBroadcastDialogContent(
                    viewModel = viewModel,
                    onDismiss = { dismiss() }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(
                (resources.displayMetrics.widthPixels * 0.95).toInt(),
                (resources.displayMetrics.heightPixels * 0.8).toInt()
            )
            setBackgroundDrawableResource(android.R.color.black)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.stopListeningHr()
    }

    @Composable
    private fun HrBroadcastDialogContent(
        viewModel: MainViewModel,
        onDismiss: () -> Unit
    ) {
        val deviceHrDataMap = remember { mutableStateMapOf<String, HrBroadcastEntry>() }
        var stoppedDevices by remember { mutableStateOf(setOf<String>()) }
        val context = LocalContext.current
        val buttonColor = Color(context.getColor(R.color.colorButtonConnect))

        LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(1000)
                val currentTime = System.currentTimeMillis()
                val devicesToRemove = mutableListOf<String>()

                deviceHrDataMap.forEach { (deviceId, entry) ->
                    if (entry.isListening && (currentTime - entry.lastUpdateTime) > 10000) {
                        devicesToRemove.add(deviceId)
                        Log.d(TAG, "Removing stale device: $deviceId")
                    }
                }

                devicesToRemove.forEach { deviceId ->
                    deviceHrDataMap.remove(deviceId)
                }
            }
        }

        LaunchedEffect(stoppedDevices) {
            viewModel.startListeningHr(stoppedDevices)
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.hr_broadcast_listener),
                    style = MaterialTheme.typography.h6,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Button(
                    onClick = {
                        viewModel.stopListeningHr()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = buttonColor,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.close), color = Color.White)
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (deviceHrDataMap.isEmpty()) {
                    Text(
                        text = stringResource(R.string.devices_will_appear_here_note),
                        style = MaterialTheme.typography.body2,
                        color = Color.White,
                        modifier = Modifier.padding(8.dp)
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = deviceHrDataMap.values.sortedBy { it.deviceId }.toList(),
                        key = { it.deviceId }
                    ) { entry ->
                        HrDeviceCard(
                            entry = entry,
                            buttonColor = buttonColor,
                            onToggleListening = { deviceId, isListening ->
                                if (isListening) {
                                    stoppedDevices = stoppedDevices + deviceId
                                    deviceHrDataMap[deviceId]?.let {
                                        deviceHrDataMap[deviceId] = it.copy(
                                            isListening = false,
                                            hr = -1
                                        )
                                    }
                                } else {
                                    stoppedDevices = stoppedDevices - deviceId
                                    deviceHrDataMap[deviceId]?.let {
                                        deviceHrDataMap[deviceId] = it.copy(
                                            isListening = true,
                                            lastUpdateTime = System.currentTimeMillis()
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        val hrData by viewModel.hrData.collectAsState()

        LaunchedEffect(hrData) {
            hrData?.let { data ->
                val deviceId = data.polarDeviceInfo.deviceId
                if (!stoppedDevices.contains(deviceId)) {
                    deviceHrDataMap[deviceId] = HrBroadcastEntry(
                        deviceId = deviceId,
                        deviceName = data.polarDeviceInfo.name,
                        hr = data.hr,
                        isListening = true,
                        lastUpdateTime = System.currentTimeMillis()
                    )
                }
            }
        }
    }

    @Composable
    private fun HrDeviceCard(
        entry: HrBroadcastEntry,
        buttonColor: Color,
        onToggleListening: (String, Boolean) -> Unit
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 4.dp,
            backgroundColor = Color.DarkGray
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.deviceName,
                        style = MaterialTheme.typography.subtitle1,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (entry.hr >= 0) stringResource(R.string.hr_value, entry.hr) else "---",
                        style = MaterialTheme.typography.h5,
                        color = buttonColor
                    )
                }

                Button(
                    onClick = {
                        onToggleListening(entry.deviceId, entry.isListening)
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = buttonColor,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = if (entry.isListening) stringResource(R.string.stop_listening) else stringResource(
                            R.string.start_listening
                        ),
                        color = Color.White
                    )
                }
            }
        }
    }

    data class HrBroadcastEntry(
        val deviceId: String,
        val deviceName: String,
        val hr: Int,
        val isListening: Boolean = true,
        val lastUpdateTime: Long = System.currentTimeMillis()
    )

    companion object {
        const val TAG = "HrBroadcastDialogFragment"

        fun newInstance(): HrBroadcastDialogFragment {
            return HrBroadcastDialogFragment()
        }
    }
}