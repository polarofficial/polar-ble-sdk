package com.polar.polarsensordatacollector.ui.trainingsession

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.ListItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.FilePresent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.findNavController
import androidx.navigation.fragment.navArgs
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.ui.theme.PolarsensordatacollectorTheme
import com.polar.polarsensordatacollector.ui.utils.DataLoadProgressIndicator
import com.polar.sdk.api.model.PolarOfflineRecordingEntry
import com.polar.sdk.api.model.trainingsession.PolarTrainingSessionReference
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TrainingSessionListFragment : Fragment() {
    private val viewModel: ListTrainingSessionsViewModel by viewModels()
    private lateinit var deviceId: String
    private lateinit var fromDate: String
    private lateinit var toDate: String
    private val args: TrainingSessionListFragmentArgs by navArgs()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        deviceId = args.deviceIdFragmentArgument

        return ComposeView(requireContext()).apply {
            setContent {
                PolarsensordatacollectorTheme {
                    Surface {
                        ListTrainingSessions(
                            viewModel = viewModel,
                            onClickItem = { filePath, fileName ->
                                findNavController()
                                    .navigate(TrainingSessionListFragmentDirections
                                        .trainingSessionNavigateToDataAction(deviceId, filePath, fileName, fromDate, toDate))
                            }, onDeleteItem = { entry ->
                                viewModel.deleteTrainingSession(entry)
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.listTrainingSessions()
    }
}

@Composable
fun ListTrainingSessions(viewModel: ListTrainingSessionsViewModel = viewModel(), onClickItem: (String, String) -> Unit, onDeleteItem: (PolarTrainingSessionReference) -> Unit) {
    TrainingSessionItemsList(
        onRefresh = { viewModel.listTrainingSessions() },
        onClickItem = onClickItem,
        viewModel.trainingSessionsUiState,
        onDelete = onDeleteItem
    )
}

@Composable
fun TrainingSessionItemsList(onRefresh: () -> Unit, onClickItem: (String, String) -> Unit, trainingSessionUiState: TrainingSessionsUiState,  onDelete: (PolarTrainingSessionReference) -> Unit) {
    SwipeRefresh(
        state = rememberSwipeRefreshState(isRefreshing = trainingSessionUiState.fetchStatus is TrainingSessionFetch.InProgress),
        onRefresh = { onRefresh() },
    ) {
        when (val fetchStatus = trainingSessionUiState.fetchStatus) {
            is TrainingSessionFetch.Failure -> {
                ListingFailed(fetchStatus)
            }
            is TrainingSessionFetch.InProgress -> {
                if (fetchStatus.fetchedTrainingSessions.isEmpty()) {
                    DataLoadProgressIndicator(
                        progress = null,
                        dataType = "Training Sessions"
                    )
                } else {
                    LazyColumn(
                        state = rememberLazyListState(),
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth()
                    ) {
                        items(fetchStatus.fetchedTrainingSessions) { trainingSessionEntry ->
                            TrainingSession(trainingSessionEntry, onClickItem, onDelete = { })
                            Divider(startIndent = 72.dp)
                        }
                    }
                }
            }
            is TrainingSessionFetch.Success ->
                if (fetchStatus.fetchedTrainingSessions.isEmpty()) {
                    ListingEmpty()
                } else {
                    LazyColumn(
                        state = rememberLazyListState(),
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth()
                    ) {
                        items(fetchStatus.fetchedTrainingSessions) { trainingSessionEntry ->
                            TrainingSession(trainingSessionEntry, onClickItem,  onDelete = { onDelete(trainingSessionEntry) })
                            Divider(startIndent = 72.dp)
                        }
                    }
                }
        }
    }
}

@Composable
private fun ListingFailed(fetchStatus: TrainingSessionFetch.Failure) {
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
                        Text(text = stringResource(R.string.details))
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
            Text(text = stringResource(R.string.no_training_sessions), color = Color.Yellow, textAlign = TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun TrainingSession(
    trainingSessionEntry: PolarTrainingSessionReference,
    onClick: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (path: String) -> Unit,
    isDeleting: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }

    ListItem(
        modifier = modifier
            .clickable(
                enabled = !isDeleting,
                interactionSource = interactionSource,
                indication = LocalIndication.current
            ) {
                onClick(trainingSessionEntry.path, trainingSessionEntry.path)
            }
            .padding(vertical = 8.dp),
        icon = {
            Icon(
                imageVector = Icons.Outlined.FilePresent,
                contentDescription = null,
                modifier = Modifier.clip(shape = MaterialTheme.shapes.small)
            )
        },
        text = {
            Text(text = trainingSessionEntry.path)
        },
        secondaryText = {
            Text(text = trainingSessionEntry.date.toLocaleString())
        },
        trailing = {
            if (isDeleting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                IconButton(onClick = { onDelete(trainingSessionEntry.path) }) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete file",
                        tint = Color.Red
                    )
                }
            }
        }
    )
}
