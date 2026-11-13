package com.polar.polarsensordatacollector.ui.exercise

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.rememberSaveable
import com.polar.sdk.api.model.PolarExerciseSession
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.polar.polarsensordatacollector.R

private fun PolarExerciseSession.SportProfile.displayName(): String =
    name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ExerciseScreen(
    vm: ExerciseViewModel,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: (save: Boolean) -> Unit,
    onStatusToast: (String) -> Unit
) {
    val status by vm.status.observeAsState(PolarExerciseSession.ExerciseStatus.NOT_STARTED)
    val statusText by vm.statusText.observeAsState("Not started")
    val canStart by vm.canStart.observeAsState(true)
    val canPause by vm.canPause.observeAsState(false)
    val canResume by vm.canResume.observeAsState(false)
    val canStop by vm.canStop.observeAsState(false)
    val startTime by vm.startTime.observeAsState()

    val isObserving by vm.isObservingNotifications.observeAsState(false)
    val notificationEvent by vm.notificationEvent.observeAsState()
    val errorEvent by vm.errorEvent.observeAsState()

    val context = LocalContext.current

    var expanded by remember { mutableStateOf(false) }
    var selectedIndex by rememberSaveable { mutableStateOf(0) }
    var customSportIdText by rememberSaveable { mutableStateOf("") }
    val enteredSportId = customSportIdText.trim()
    val customSportId = enteredSportId.toLongOrNull()
    val invalidSportId = enteredSportId.isNotEmpty() && customSportId == null
    val selectedProfile = vm.sportProfiles.getOrElse(selectedIndex) { PolarExerciseSession.SportProfile.RUNNING }
    val selectedSportId = if (enteredSportId.isEmpty()) {
        selectedProfile.id.toLong()
    } else {
        customSportId
    }
    var saveOnStop by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(canStop) {
        if (!canStop) saveOnStop = true
    }

    LaunchedEffect(selectedSportId, invalidSportId) {
        if (!invalidSportId) vm.selectSportId(selectedSportId)
    }

    var lastStatus by remember { mutableStateOf<PolarExerciseSession.ExerciseStatus?>(null) }
    LaunchedEffect(status) {
        if (status != lastStatus) {
            val msg = when {
                lastStatus == PolarExerciseSession.ExerciseStatus.PAUSED &&
                        status == PolarExerciseSession.ExerciseStatus.IN_PROGRESS ->
                    context.getString(R.string.exercise_in_progress)

                lastStatus != PolarExerciseSession.ExerciseStatus.IN_PROGRESS &&
                        status == PolarExerciseSession.ExerciseStatus.IN_PROGRESS ->
                    context.getString(R.string.exercise_in_progress)

                status == PolarExerciseSession.ExerciseStatus.PAUSED ->
                    context.getString(R.string.exercise_paused)

                status == PolarExerciseSession.ExerciseStatus.SYNC_REQUIRED ->
                    context.getString(R.string.exercise_syncing)

                status == PolarExerciseSession.ExerciseStatus.STOPPED ->
                    context.getString(R.string.exercise_idle)

                else -> null
            }
            msg?.let(onStatusToast)
            lastStatus = status
        }
    }

    LaunchedEffect(errorEvent) {
        errorEvent?.let { error ->
            onStatusToast(error)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // --- SPORT "SPINNER" ---
        val label = selectedProfile.displayName()

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { if (canStart) expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = label,
                onValueChange = { /* readOnly */ },
                label = { Text(stringResource(R.string.exercise_sport_optional), color = MaterialTheme.colors.onSurface) },
                enabled = canStart,
                readOnly = true,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    textColor = MaterialTheme.colors.onSurface,
                    focusedBorderColor = MaterialTheme.colors.primary,
                    unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled),
                    focusedLabelColor = MaterialTheme.colors.primary,
                    unfocusedLabelColor = MaterialTheme.colors.onSurface
                )
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                vm.sportProfiles.forEachIndexed { idx, profile ->
                    DropdownMenuItem(onClick = {
                        selectedIndex = idx
                        customSportIdText = ""
                        expanded = false
                    }) {
                        Text(
                            profile.displayName(),
                            color = MaterialTheme.colors.onSurface
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = customSportIdText,
            onValueChange = { customSportIdText = it },
            label = { Text(stringResource(R.string.exercise_custom_sport_id)) },
            enabled = canStart,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                textColor = Color.White,
                disabledTextColor = Color.White.copy(alpha = ContentAlpha.disabled),
                cursorColor = Color.White,
                focusedLabelColor = Color.White,
                unfocusedLabelColor = Color.White,
                disabledLabelColor = Color.White.copy(alpha = ContentAlpha.disabled)
            )
        )
        Text(
            text = stringResource(R.string.exercise_custom_sport_id_help),
            color = MaterialTheme.colors.onSurface,
            style = MaterialTheme.typography.caption
        )

        Spacer(Modifier.height(12.dp))

        // --- STATUS ---
        Text(
            text = statusText,
            style = MaterialTheme.typography.h6,
            color = MaterialTheme.colors.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        Spacer(Modifier.height(8.dp))

        // --- BUTTON ROW ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Button(
                onClick = {
                    vm.selectSportId(selectedSportId)
                    onStart()
                },
                enabled = canStart && !invalidSportId,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 6.dp)
            ) { Text(stringResource(R.string.btn_start)) }

            Button(
                onClick = onPause,
                enabled = canPause,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp)
            ) { Text(stringResource(R.string.btn_pause)) }

            Button(
                onClick = onResume,
                enabled = canResume,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp)
            ) { Text(stringResource(R.string.btn_resume)) }

            Button(
                onClick = { onStop(saveOnStop) },
                enabled = canStop,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 6.dp)
            ) { Text(stringResource(R.string.btn_stop)) }
        }

        if (canStop) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                Checkbox(
                    checked = saveOnStop,
                    onCheckedChange = { saveOnStop = it }
                )
                Text(
                    text = stringResource(R.string.exercise_save_on_stop),
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        startTime?.let { date ->
            val formatted: String = date.toString()
            Text(
                text = stringResource(R.string.exercise_start_time, formatted),
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            )
        }

        Spacer(Modifier.height(24.dp))
        Divider()
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { vm.toggleNotificationObservation() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (isObserving)
                    stringResource(R.string.stop_observing_button)
                else
                    stringResource(R.string.observe_exercise_state_button)
            )
        }

        Spacer(Modifier.height(8.dp))

        notificationEvent?.let { event ->
            Text(
                text = event,
                style = MaterialTheme.typography.body1,
                color = MaterialTheme.colors.primary
            )
        }
    }
}
