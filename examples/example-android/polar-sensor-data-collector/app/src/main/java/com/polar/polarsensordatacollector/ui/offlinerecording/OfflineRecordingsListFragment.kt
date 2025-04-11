package com.polar.polarsensordatacollector.ui.offlinerecording

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.FilePresent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.polar.polarsensordatacollector.ui.theme.PolarsensordatacollectorTheme
import com.polar.polarsensordatacollector.ui.utils.showSnackBar
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.model.PolarOfflineRecordingEntry
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.*

private const val TAG = "OfflineRecordingsListFragment"

@AndroidEntryPoint
class OfflineRecordingsListFragment : Fragment() {
    private val viewModel: ListRecordingsViewModel by viewModels()
    private lateinit var deviceId: String
    private val args: OfflineRecordingsListFragmentArgs by navArgs()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        deviceId = args.deviceIdFragmentArgument

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.devConnectionState.collect {
                    if (!it.isConnected) {
                        val navigateActionToHome = OfflineRecordingsListFragmentDirections.offlineRecordingListToHome()
                        findNavController().navigate(navigateActionToHome)
                    }
                }
            }
        }

        return ComposeView(requireContext()).apply {
            setContent {
                PolarsensordatacollectorTheme {
                    Surface {
                        ListRecordings(
                            viewModel = viewModel,
                            onClickItem = { filePath, fileName ->
                                findNavController()
                                    .navigate(OfflineRecordingsListFragmentDirections.offlineRecordingNavigateToDataAction(deviceId, filePath, fileName))
                            },
                            onDeleteItem = { entry ->
                                viewModel.deleteRecording(entry)
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.listOfflineRecordings()
    }
}

@Composable
fun ListRecordings(viewModel: ListRecordingsViewModel = viewModel(), onClickItem: (String, String) -> Unit, onDeleteItem: (PolarOfflineRecordingEntry) -> Unit) {
    RecordedItemsList(onRefresh = { viewModel.listOfflineRecordings() }, onClickItem = onClickItem, onDelete = onDeleteItem, viewModel.offlineRecordingsUiState)
}

@Composable
fun RecordedItemsList(onRefresh: () -> Unit, onClickItem: (String, String) -> Unit, onDelete: (PolarOfflineRecordingEntry) -> Unit, offlineRecordingUiState: OfflineRecordingsUiState) {
    SwipeRefresh(
        state = rememberSwipeRefreshState(isRefreshing = offlineRecordingUiState.fetchStatus is OfflineRecordingFetch.InProgress),
        onRefresh = { onRefresh() },
    ) {
        when (val fetchStatus = offlineRecordingUiState.fetchStatus) {
            is OfflineRecordingFetch.Failure -> {
                ListingFailed(fetchStatus)
            }
            is OfflineRecordingFetch.InProgress -> {
                LazyColumn(
                    state = rememberLazyListState(),
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth()
                ) {
                    items(fetchStatus.fetchedRecordings) { recordingEntry ->
                        Recording(recordingEntry, onClickItem, onDelete = { })
                        Divider(startIndent = 72.dp)
                    }
                }
            }
            is OfflineRecordingFetch.Success ->
                if (fetchStatus.fetchedRecordings.isEmpty()) {
                    ListingEmpty()
                } else {
                    LazyColumn(
                        state = rememberLazyListState(),
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth()
                    ) {
                        items(fetchStatus.fetchedRecordings) { recordingEntry ->
                            Recording(recordingEntry, onClickItem, onDelete = { onDelete(recordingEntry) })
                            Divider(startIndent = 72.dp)
                        }
                    }
                }
        }
    }
}

@Composable
private fun ListingFailed(fetchStatus: OfflineRecordingFetch.Failure) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(10.dp)
    ) {
        Column(modifier = Modifier.align(Alignment.Center)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Warning, "warning", tint = Color.Yellow)
                Text(text = fetchStatus.message, color = Color.Red, textAlign = TextAlign.Center)

                if (fetchStatus.throwable != null) {
                    Column {
                        Text(text = "Details:")
                        Text(text = "${fetchStatus.throwable}")
                    }
                }
            }
        }
    }
}

@Composable
private fun ListingEmpty() {
    Box(
        Modifier
            .fillMaxSize()
            .padding(10.dp)
    ) {
        Column(modifier = Modifier.align(Alignment.Center)) {
            Text(text = "No recordings", color = Color.Yellow, textAlign = TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun Recording(recordingEntry: PolarOfflineRecordingEntry, onClick: (String, String) -> Unit, onDelete: (path: String) -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        modifier = modifier
            .clickable { onClick(recordingEntry.path, getFileNameFromPath(recordingEntry.path)) }
            .padding(vertical = 8.dp),
        icon = {
            Icon(
                imageVector = Icons.Outlined.FilePresent,
                contentDescription = null,
                modifier = Modifier.clip(shape = MaterialTheme.shapes.small)
            )
        },
        text = {
            Text(text = getFileNameFromPath(recordingEntry.path), color = getColor(recordingEntry.type))
        },
        secondaryText = {
            Column {
                Row {
                    Text(text = recordingEntry.date.toLocaleString())
                }
                Row {
                    val sizeInKb = String.format("%.2f", (recordingEntry.size / 1000.0))
                    Text(text = "Size: ${(sizeInKb)}kB")
                }
            }
        },
        trailing = {
            Row {
                IconButton(onClick = { onDelete(recordingEntry.path) }) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete file"
                    )
                }

            }
        }
    )
}

private fun getFileNameFromPath(path: String): String {
    return path.split("/").last()
}

private fun getColor(type: PolarBleApi.PolarDeviceDataType): Color {
    return when (type) {
        PolarBleApi.PolarDeviceDataType.ACC -> Color.Red
        PolarBleApi.PolarDeviceDataType.GYRO -> Color.Yellow
        PolarBleApi.PolarDeviceDataType.MAGNETOMETER -> Color.Cyan
        PolarBleApi.PolarDeviceDataType.PPG -> Color.Green
        PolarBleApi.PolarDeviceDataType.PPI -> Color.White
        PolarBleApi.PolarDeviceDataType.HR -> Color.Magenta
        else -> Color.Blue
    }
}

@Preview("Recording entries fetch failed")
@Composable
private fun RecordingEntriesListingFailedPreview() {
    PolarsensordatacollectorTheme(content = {
        Surface {
            RecordedItemsList(
                onRefresh = {},
                onClickItem = { _: String, _: String -> },
                onDelete = {},
                OfflineRecordingsUiState(
                    fetchStatus = OfflineRecordingFetch.Failure(
                        message = "Recording listing failed",
                        throwable = Exception("Some exception")
                    )
                )
            )
        }
    }
    )
}

@Preview("Recording entries empty")
@Composable
private fun RecordingEntriesEmptyPreview() {
    PolarsensordatacollectorTheme(content = {
        Surface {
            RecordedItemsList(
                onRefresh = {},
                onClickItem = { _: String, _: String -> },
                onDelete = {},
                OfflineRecordingsUiState(
                    fetchStatus = OfflineRecordingFetch.Success(
                        listOf()
                    )
                )
            )
        }
    }
    )
}

@Preview("RecordingList")
@Composable
private fun RecordingListPreview() {
    PolarsensordatacollectorTheme(content = {
        Surface {
            RecordedItemsList(
                onRefresh = {},
                onClickItem = { _: String, _: String -> },
                onDelete = {},
                OfflineRecordingsUiState(
                    fetchStatus = OfflineRecordingFetch.Success(
                        listOf(
                            PolarOfflineRecordingEntry(
                                path = "/U/0/20220824/R/075122/ACC.REC",
                                size = 50000L,
                                date = Date(),
                                type = PolarBleApi.PolarDeviceDataType.ACC
                            ),
                            PolarOfflineRecordingEntry(
                                path = "/U/0/20220824/R/075122/GYRO.REC",
                                size = 50000L,
                                date = Date(),
                                type = PolarBleApi.PolarDeviceDataType.GYRO
                            ),
                            PolarOfflineRecordingEntry(
                                path = "/U/0/20220824/R/075122/MAG.REC",
                                size = 50000L,
                                date = Date(),
                                type = PolarBleApi.PolarDeviceDataType.MAGNETOMETER
                            ),
                            PolarOfflineRecordingEntry(
                                path = "/U/0/20220824/R/075122/PPG.REC",
                                size = 50000L,
                                date = Date(),
                                type = PolarBleApi.PolarDeviceDataType.PPG
                            ),
                            PolarOfflineRecordingEntry(
                                path = "/U/0/20220824/R/075122/PPI.REC",
                                size = 50000L,
                                date = Date(),
                                type = PolarBleApi.PolarDeviceDataType.PPI
                            ),
                            PolarOfflineRecordingEntry(
                                path = "/U/0/20220824/R/075122/HR.REC",
                                size = 50000L,
                                date = Date(),
                                type = PolarBleApi.PolarDeviceDataType.HR
                            ),
                        )
                    )
                )
            )
        }
    })
}

@Preview("Recording")
@Composable
private fun RecordingPreview() {
    PolarsensordatacollectorTheme(content = {
        Surface {
            Recording(
                PolarOfflineRecordingEntry(
                    path = "/U/0/20220824/R/075122/ACC.REC",
                    size = 50,
                    date = Date(),
                    type = PolarBleApi.PolarDeviceDataType.ACC
                ),
                onClick = { _: String, _: String -> },
                onDelete = {}
            )
        }
    })
}