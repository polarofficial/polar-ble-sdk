package com.polar.polarsensordatacollector.ui.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
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

@AndroidEntryPoint
class ActivityDataFragment : Fragment() {
    private val viewModel: ActivityRecordingDataViewModel by viewModels()
    private lateinit var deviceId: String
    private lateinit var startDate: String
    private lateinit var endDate: String
    private lateinit var type: String
    private val args: ActivityDataFragmentArgs by navArgs()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        deviceId = args.deviceIdFragmentArgument
        startDate = args.activityStartDateFragmentArgument
        endDate = args.activityEndDateFragmentArgument
        type = args.activityTypeFragmentArgument
        args.activityStartDateFragmentArgument

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.devConnectionState.collect {
                    if (!it.isConnected) {
                        val navigateActionToHome = ActivityRecordingFragmentDirections.activityToActivityDataAction(deviceId, startDate, endDate, type)
                        findNavController().navigate(navigateActionToHome)
                    }
                }
            }
        }

        return ComposeView(requireContext()).apply {
            setContent {
                PolarsensordatacollectorTheme {
                    Surface {
                        ShowActivityData(viewModel = viewModel, onNavigateBack = { findNavController().popBackStack() })
                    }
                }
            }
        }
    }
}

@Composable
fun ShowActivityData(viewModel: ActivityRecordingDataViewModel = viewModel(), onNavigateBack: () -> Boolean) {
    when (val uiState = viewModel.activityDataUiState) {
        is ActivityDataUiState.FetchedData -> {
            val context = LocalContext.current
            val intent = Intent().apply {
                action = Intent.ACTION_SEND_MULTIPLE
                putExtra(Intent.EXTRA_SUBJECT, "Recorded activity data")
                type = "plain/text"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(uiState.data.uri))
            }
            ShowData(
                onShare = { context.startActivity(intent) },
                uiState
            )
        }
        ActivityDataUiState.IsFetching -> ShowIsLoading()
        is ActivityDataUiState.Failure -> {
            ShowFailed(uiState)
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
fun ShowData(onShare: () -> Unit, recording: ActivityDataUiState.FetchedData) {
    Column {

        Text(text = "Path", style = MaterialTheme.typography.h6)
        Text(
            text = recording.data.uri.toString(), modifier = Modifier
                .padding(8.dp)
        )

        Text(text = "Start time", style = MaterialTheme.typography.h6)
        Row(
            modifier = Modifier
                .padding(8.dp)
        ) {
            Text(text = recording.data.startDate)
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
    }
}

@Composable
private fun ShowFailed(failure: ActivityDataUiState.Failure) {
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
private fun ActivityDataLoadingPreview() {
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
                recording = ActivityDataUiState.FetchedData(
                    data = ActivityRecordingData(
                        startDate = "** not implemented yet **",
                        endDate = "** not implemented yet **",
                        uri = Uri.EMPTY
                    )
                )
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
                recording = ActivityDataUiState.FetchedData(
                    data = ActivityRecordingData(
                        startDate = "** not implemented yet **",
                        endDate = "** not implemented yet **",
                        uri = Uri.EMPTY
                    )
                )
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
                ActivityDataUiState.Failure(
                    message = "Recording fetch failed",
                    throwable = Exception("Some exception")
                )
            )
        }
    }
    )
}