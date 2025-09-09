package com.polar.polarsensordatacollector.ui.trainingsession

import android.content.Intent
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.gson.Gson
import com.polar.polarsensordatacollector.ui.theme.PolarsensordatacollectorTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.ui.utils.FileUtils
import com.polar.polarsensordatacollector.ui.utils.JsonUtils
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private lateinit var path: String

@AndroidEntryPoint
class TrainingSessionDataFragment : Fragment() {
    private val viewModel: TrainingSessionDataViewModel by viewModels()
    private lateinit var deviceId: String
    private val args: TrainingSessionDataFragmentArgs by navArgs()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        deviceId = args.deviceIdFragmentArgument
        path = args.trainingSessionPathFragmentArgument

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.devConnectionState.collect {
                    if (!it.isConnected) {
                        val navigateActionToHome = TrainingSessionDataFragmentDirections.trainingSessionDataToHome()
                        findNavController().navigate(navigateActionToHome)
                    }
                }
            }
        }

        return ComposeView(requireContext()).apply {
            setContent {
                PolarsensordatacollectorTheme {
                    Surface {
                        ShowTrainingSessionData(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun ShowTrainingSessionData(viewModel: TrainingSessionDataViewModel = viewModel()) {
    when (val uiState = viewModel.trainingSessionDataUiState) {
        is TrainingSessionDataUiState.FetchedData -> {
            val context = LocalContext.current
            val gson = GsonBuilder()
                .registerTypeAdapter(LocalDate::class.java, JsonSerializer<LocalDate> { src, _, _ ->
                    JsonPrimitive(src?.format(DateTimeFormatter.ISO_LOCAL_DATE))
                })
                .registerTypeAdapter(LocalDateTime::class.java, JsonSerializer<LocalDateTime> { src, _, _ ->
                    JsonPrimitive(src?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                })
                .setPrettyPrinting()
                .create()
            val jsonData = JsonUtils.cleanProtoJson(gson.toJson(uiState.data), gson)

            val fileUri = FileUtils(context).saveToFile(
                jsonData.encodeToByteArray(),
                "/TRAINING_SESSION/${uiState.data.reference.date}-training-session.json"
            )

            val intent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                putExtra(Intent.EXTRA_SUBJECT, stringResource(R.string.polar_training_session))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(intent,
                stringResource(R.string.share_training_session)))

            ShowData(
                onShare = { context.startActivity(Intent.createChooser(intent, "Share Training Session")) },
                uiState,
                fileUri.path ?: ""
            )
        }
        TrainingSessionDataUiState.IsFetching -> ShowIsLoading()
        is TrainingSessionDataUiState.Failure -> {
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
fun ShowData(
    onShare: () -> Unit,
    trainingSession: TrainingSessionDataUiState.FetchedData,
    path: String
) {
    Column {
        Text(text = stringResource(R.string.path), style = MaterialTheme.typography.h6)
        Text(
            text = path, modifier = Modifier
                .padding(8.dp)
        )

        Text(text = stringResource(R.string.start_time), style = MaterialTheme.typography.h6)
        Row(
            modifier = Modifier
                .padding(8.dp)
        ) {
            Text(text = trainingSession.data.reference.date.toString())
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
            Icon(
                Icons.Filled.Share,
                contentDescription = stringResource(R.string.share),
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text("Share")
        }
    }
}

@Composable
fun ShowFailed(failure: TrainingSessionDataUiState.Failure) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(10.dp)
    ) {
        Column(modifier = Modifier.align(Alignment.Center)) {
            Column(
                horizontalAlignment = CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Warning, "warning", tint = Color.Yellow)
                Text(text = failure.message, color = Color.Red, textAlign = TextAlign.Center)

                if (failure.throwable != null) {
                    Column {
                        Text(text = stringResource(R.string.details))
                        Text(text = "${failure.throwable}")
                    }
                }
            }
        }
    }
}