package com.polar.polarsensordatacollector.ui.h10exercise

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.gson.GsonBuilder
import com.polar.polarsensordatacollector.R
import com.polar.sdk.api.model.PolarExerciseData
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

private const val TAG = "H10ExerciseFragment"

@AndroidEntryPoint
class H10ExerciseFragment : Fragment() {

    private val viewModel: H10ExerciseViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setContent {
            var statusText by remember { mutableStateOf("") }
            var isRecording by remember { mutableStateOf(viewModel.featureState.value.isEnabled) }

            MaterialTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    color = Color.Black,
                    elevation = 0.dp
                ) {
                    val context = LocalContext.current

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {

                        Button(
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color.Gray),
                            onClick = {
                                viewModel.listExercises(
                                    onResult = { count ->
                                        statusText = if (count > 0) {
                                            context.getString(R.string.exercises_found)
                                        } else {
                                            context.getString(R.string.no_exercise_found)
                                        }
                                    },
                                    onError = {
                                        statusText = context.getString(R.string.no_exercise_found)
                                    }
                                )
                            }
                        ) {
                            Text(context.getString(R.string.list_exercises))
                        }

                        Button(
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color.Gray),
                            onClick = {
                                viewModel.readFirstExercise(
                                    onExercise = { exercise ->
                                        shareExerciseData(context, exercise)
                                    },
                                    onError = {
                                        statusText = context.getString(R.string.no_exercise_found)
                                    }
                                )
                            }
                        ) {
                            Text(context.getString(R.string.read_exercise))
                        }

                        Button(
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color.Gray),
                            onClick = {
                                viewModel.removeFirstExercise(
                                    onComplete = {
                                        statusText = context.getString(R.string.exercise_removed_successfully)
                                    },
                                    onError = {
                                        statusText = context.getString(R.string.no_exercise_to_remove)
                                    }
                                )
                            }
                        ) {
                            Text(context.getString(R.string.remove_exercise))
                        }

                        Button(
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color.Gray),
                            onClick = {
                                viewModel.toggleRecording()
                                isRecording = !isRecording
                            }
                        ) {
                            Text(
                                if (isRecording)
                                    context.getString(R.string.stop_h10_recording)
                                else
                                    context.getString(R.string.start_h10_recording)
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        if (statusText.isNotEmpty()) {
                            Text(
                                text = statusText,
                                color = Color.LightGray
                            )
                        }
                    }
                }
            }
        }
    }

    private fun shareExerciseData(context: android.content.Context, exerciseData: PolarExerciseData) {
        try {
            val gson = GsonBuilder().setPrettyPrinting().create()
            val json = gson.toJson(
                mapOf(
                    "recordingInterval" to exerciseData.recordingInterval,
                    "hrSamples" to exerciseData.hrSamples
                )
            )

            val file = File(context.filesDir, "exercise.json")
            file.writeText(json)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.exported_exercise_data))
                putExtra(Intent.EXTRA_TEXT, context.getString(R.string.attached_is_your_exercise_data_in_json_format))
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(
                Intent.createChooser(
                    intent,
                    context.getString(R.string.share_exercise_data)
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to share exercise: $e", e)
            Toast.makeText(
                context,
                context.getString(R.string.failed_to_share, e.message),
                Toast.LENGTH_LONG
            ).show()
        }
    }
}