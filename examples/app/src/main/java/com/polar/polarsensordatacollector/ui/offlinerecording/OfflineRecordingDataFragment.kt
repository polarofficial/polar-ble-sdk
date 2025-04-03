package com.polar.polarsensordatacollector.ui.offlinerecording

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.polar.polarsensordatacollector.ui.theme.PolarsensordatacollectorTheme
import com.polar.sdk.api.model.PolarSensorSetting
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.*

private lateinit var path: String

@AndroidEntryPoint
class OfflineRecordingDataFragment : Fragment() {
    private val viewModel: RecordingDataViewModel by viewModels()
    private lateinit var deviceId: String
    private val args: OfflineRecordingDataFragmentArgs by navArgs()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        deviceId = args.deviceIdFragmentArgument
        path = args.recordingPathFragmentArgument

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.devConnectionState.collect {
                    if (!it.isConnected) {
                        val navigateActionToHome = OfflineRecordingDataFragmentDirections.offlineRecordingDataToHome()
                        findNavController().navigate(navigateActionToHome)
                    }
                }
            }
        }

        return ComposeView(requireContext()).apply {
            setContent {
                PolarsensordatacollectorTheme {
                    Surface {
                        ShowRecordingData(viewModel = viewModel, onNavigateBack = { findNavController().popBackStack() })
                    }
                }
            }
        }
    }
}

@Composable
fun ShowRecordingData(viewModel: RecordingDataViewModel = viewModel(), onNavigateBack: () -> Boolean) {
    when (val uiState = viewModel.recordingDataUiState) {
        is RecordingDataUiState.FetchedData -> {
            val context = LocalContext.current
            val intent = Intent().apply {
                action = Intent.ACTION_SEND_MULTIPLE
                putExtra(Intent.EXTRA_SUBJECT, "Offline recorded data")
                type = "plain/text"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(uiState.data.uri))
            }
            ShowData(
                onShare = { context.startActivity(intent) },
                onDelete = { viewModel.deleteRecording() },
                uiState,
                path
            )
        }
        RecordingDataUiState.IsDeleting,
        RecordingDataUiState.IsFetching -> ShowIsLoading()
        is RecordingDataUiState.Failure -> {
            ShowFailed(uiState)
        }
        RecordingDataUiState.RecordingDeleted -> {
            Toast.makeText(LocalContext.current, "Deleted $path ", Toast.LENGTH_LONG).show()
            onNavigateBack()
        }
    }
}

@Composable
fun ShowIsLoading() {
    Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.size(size = 64.dp),
            color = Color.Blue,
            strokeWidth = 6.dp
        )
    }
}

@Composable
fun ShowData(onShare: () -> Unit, onDelete: () -> Unit, recording: RecordingDataUiState.FetchedData, path: String, modifier: Modifier = Modifier) {
    Column {

        val sizeInKb = String.format("%.2f", (recording.data.size / 1000.0))
        Text(text = "Size", style = MaterialTheme.typography.h6)
        Text(
            text = "$sizeInKb kB", modifier = Modifier
                .padding(8.dp)
        )

        val downLoadRate = String.format("%.2f", recording.data.downloadSpeed)
        Text(text = "Downloaded with rate", style = MaterialTheme.typography.h6)
        Text(
            text = "$downLoadRate kB/s", modifier = Modifier
                .padding(8.dp)
        )

        Text(text = "Path", style = MaterialTheme.typography.h6)
        Text(
            text = path, modifier = Modifier
                .padding(8.dp)
        )

        Text(text = "Start time", style = MaterialTheme.typography.h6)
        Row(
            modifier = Modifier
                .padding(8.dp)
        ) {
            Text(text = recording.data.startTime)
        }
        if (recording.data.usedSettings != null) {
            Text(text = "Recording settings", style = MaterialTheme.typography.h6)
            Row {
                Column(
                    modifier = Modifier
                        .padding(8.dp)
                ) {

                    Row {
                        Text(text = "Sample rate: ")
                        Text(text = "${recording.data.usedSettings.settings[PolarSensorSetting.SettingType.SAMPLE_RATE]?.first()}Hz")
                    }
                    Row {
                        Text(text = "Resolution: ")
                        Text(text = "${recording.data.usedSettings.settings[PolarSensorSetting.SettingType.RESOLUTION]?.first()}bits")
                    }
                    Row {
                        Text(text = "Range: ")
                        Text(text = recording.data.usedSettings.settings[PolarSensorSetting.SettingType.RANGE]?.first().toString())
                    }
                    Row {
                        Text(text = "Channels: ")
                        Text(text = recording.data.usedSettings.settings[PolarSensorSetting.SettingType.CHANNELS]?.first().toString())
                    }
                }
            }
        }

        Button(
            onClick = { onShare() },
            colors = ButtonDefaults.buttonColors(backgroundColor = Color.Blue),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = 12.dp,
                end = 20.dp,
                bottom = 12.dp

            ),
            modifier = Modifier.align(CenterHorizontally)
        ) {
            // Inner content including an icon and a text label
            Icon(
                Icons.Filled.Share,
                contentDescription = "Share",
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text("Share")
        }

        Button(
            onClick = { onDelete() },
            // Uses ButtonDefaults.ContentPadding by default
            contentPadding = PaddingValues(
                start = 20.dp,
                top = 12.dp,
                end = 20.dp,
                bottom = 12.dp

            ),
            modifier = Modifier.align(CenterHorizontally)
        ) {
            // Inner content including an icon and a text label
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Delete",
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text("Delete")
        }
    }
}

@Composable
private fun ShowFailed(failure: RecordingDataUiState.Failure) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(10.dp)
    ) {
        Column(modifier = Modifier.align(Alignment.Center)) {
            Column(horizontalAlignment = CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Warning, "warning", tint = Color.Yellow)
                Text(text = failure.message, color = Color.Red, textAlign = TextAlign.Center)

                if (failure.throwable != null) {
                    Column {
                        Text(text = "Details:")
                        Text(text = "${failure.throwable}")
                    }
                }
            }
        }
    }
}

@Preview("ShowIsLoading")
@Composable
private fun RecordingDataLoadingPreview() {
    PolarsensordatacollectorTheme(content = {
        Surface {
            ShowIsLoading()
        }
    })
}

@Preview("Recording")
@Composable
private fun ShowDataPreview() {
    val selectedSetting = mutableMapOf(
        PolarSensorSetting.SettingType.SAMPLE_RATE to 0xFFFF,
        PolarSensorSetting.SettingType.RESOLUTION to 16,
        PolarSensorSetting.SettingType.RANGE to 8,
        PolarSensorSetting.SettingType.CHANNELS to 3
    )

    PolarsensordatacollectorTheme(content = {
        Surface {
            ShowData(
                onShare = {},
                onDelete = {},
                recording = RecordingDataUiState.FetchedData(
                    data = RecordingData(
                        startTime = "** not implemented yet **",
                        usedSettings = PolarSensorSetting(selectedSetting),
                        uri = Uri.EMPTY,
                        size = 100000,
                        downloadSpeed = 10.0
                    )
                ),
                path = "/U/0/192992/"
            )
        }
    })
}

@Preview("Recording without settings")
@Composable
private fun ShowDataWithOutSettingsPreview() {
    PolarsensordatacollectorTheme(content = {
        Surface {
            ShowData(
                onShare = {},
                onDelete = {},
                recording = RecordingDataUiState.FetchedData(
                    data = RecordingData(
                        startTime = "** not implemented yet **",
                        usedSettings = null,
                        uri = Uri.EMPTY,
                        size = 256000,
                        downloadSpeed = 20.0
                    )
                ),
                path = "/U/0/192992/"
            )
        }
    })
}


@Preview("Recording entries fetch failed")
@Composable
private fun RecordingFetchFailedPreview() {
    PolarsensordatacollectorTheme(content = {
        Surface {
            ShowFailed(
                RecordingDataUiState.Failure(
                    message = "Recording fetch failed",
                    throwable = Exception("Some exception")
                )
            )
        }
    }
    )
}