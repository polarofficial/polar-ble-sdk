package com.polar.polarsensordatacollector.ui.exercisev2

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.ui.landing.ONLINE_OFFLINE_KEY_DEVICE_ID
import dagger.hilt.android.AndroidEntryPoint


@AndroidEntryPoint
class ExerciseV2Fragment : Fragment() {

    private val viewModel: ExerciseV2ViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.getString(ONLINE_OFFLINE_KEY_DEVICE_ID)?.let { deviceId ->
            if (savedInstanceState == null) {
                arguments = Bundle().apply {
                    putString(ONLINE_OFFLINE_KEY_DEVICE_ID, deviceId)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setContent {
            var statusText by remember { mutableStateOf("") }

            val context = LocalContext.current
            val shareTitleText = stringResource(R.string.exercise_v2_share_title)
            val startPrefix = stringResource(R.string.btn_start).uppercase()
            val stopPrefix = stringResource(R.string.btn_stop).uppercase()
            val checkStatusPrefix = stringResource(R.string.exercise_v2_check_status).uppercase()
            val fetchPrefix = stringResource(R.string.exercise_v2_prefix_fetch).uppercase()
            val removePrefix = stringResource(R.string.exercise_v2_prefix_remove).uppercase()
            val outline = BorderStroke(1.dp, Color.Red)
            val buttonColors = ButtonDefaults.outlinedButtonColors(
                backgroundColor = Color.Black,
                contentColor = Color.Red,
                disabledContentColor = Color.Red.copy(alpha = 0.5f)
            )

            MaterialTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    color = Color.Black,
                    elevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {

                        OutlinedButton(
                            border = outline,
                            colors = buttonColors,
                            onClick = {
                                statusText = "[$startPrefix]\n${context.getString(R.string.exercise_v2_status_exercise_started)}"
                                viewModel.startOfflineExercise(
                                    onResult = {
                                        statusText = "[$startPrefix]\n${context.getString(R.string.exercise_v2_status_exercise_started)}"
                                    },
                                    onError = { err ->
                                        statusText = "[$startPrefix]\n${context.getString(R.string.exercise_v2_status_failed_to_start, err)}"
                                    }
                                )
                            }
                        ) {
                            Text(stringResource(R.string.exercise_v2_start_exercise))
                        }

                        OutlinedButton(
                            border = outline,
                            colors = buttonColors,
                            onClick = {
                                statusText = "[$stopPrefix]\n${context.getString(R.string.exercise_v2_status_exercise_stopped)}"
                                viewModel.stopOfflineExercise(
                                    onResult = {
                                        statusText = "[$stopPrefix]\n${context.getString(R.string.exercise_v2_status_exercise_stopped)}"
                                    },
                                    onError = { err ->
                                        statusText = "[$stopPrefix]\n${context.getString(R.string.exercise_v2_status_failed_to_stop, err)}"
                                    }
                                )
                            }
                        ) {
                            Text(stringResource(R.string.exercise_v2_stop_exercise))
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            OutlinedButton(
                                border = outline,
                                colors = buttonColors,
                                onClick = {
                                    viewModel.checkStatusAndListOfflineExercises(
                                        onResult = { running, hasExercise, _ ->
                                            val runningText = if (running) {
                                                context.getString(R.string.exercise_v2_status_running)
                                            } else {
                                                context.getString(R.string.exercise_v2_status_not_running)
                                            }
                                            val exerciseText = if (hasExercise) {
                                                context.getString(R.string.exercise_v2_status_exercise_found)
                                            } else {
                                                context.getString(R.string.exercise_v2_status_no_exercises)
                                            }
                                            statusText = "[$checkStatusPrefix]\n$runningText\n$exerciseText"
                                        },
                                        onError = { err ->
                                            statusText = "[$checkStatusPrefix]\n${context.getString(R.string.exercise_v2_status_failed_to_get_status, err)}"
                                        }
                                    )
                                }
                            ) {
                                Text(stringResource(R.string.exercise_v2_check_status))
                            }

                            OutlinedButton(
                                border = outline,
                                colors = buttonColors,
                                onClick = {
                                    statusText = "[$fetchPrefix]\n${context.getString(R.string.exercise_v2_status_fetching_exercise)}"
                                    viewModel.fetchOfflineExercise(
                                        onResult = { data ->
                                            val samplesText = data.hrSamples.joinToString(",")
                                            val shareText = context.getString(
                                                R.string.exercise_v2_share_text,
                                                "Exercise data",
                                                samplesText
                                            )
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, shareText)
                                            }
                                            context.startActivity(
                                                Intent.createChooser(
                                                    shareIntent,
                                                    shareTitleText
                                                )
                                            )
                                            statusText = "[$fetchPrefix]\n${context.getString(R.string.exercise_v2_status_share_opened)}"
                                        },
                                        onError = { err ->
                                            statusText = "[$fetchPrefix]\n${context.getString(R.string.exercise_v2_status_failed_to_fetch, err)}"
                                        }
                                    )
                                }
                            ) {
                                Text(stringResource(R.string.exercise_v2_fetch_exercise))
                            }
                        }

                        OutlinedButton(
                            border = outline,
                            colors = buttonColors,
                            onClick = {
                                statusText = "[$removePrefix]\n${context.getString(R.string.exercise_v2_status_removing_exercise)}"
                                viewModel.removeOfflineExercise(
                                    onResult = {
                                        statusText = "[$removePrefix]\n${context.getString(R.string.exercise_v2_status_removed_exercise)}"
                                    },
                                    onError = { err ->
                                        statusText = "[$removePrefix]\n${context.getString(R.string.exercise_v2_status_failed_to_remove, err)}"
                                    }
                                )
                            }
                        ) {
                            Text(stringResource(R.string.exercise_v2_remove_exercise))
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        if (statusText.isNotEmpty()) {
                            Text(
                                text = statusText,
                                color = Color.Red
                            )
                        }
                    }
                }
            }
        }
    }
}