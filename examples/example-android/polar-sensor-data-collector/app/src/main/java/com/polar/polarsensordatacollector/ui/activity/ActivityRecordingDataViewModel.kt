package com.polar.polarsensordatacollector.ui.activity

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import com.polar.polarsensordatacollector.repository.DeviceConnectionState
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository
import com.polar.polarsensordatacollector.repository.ResultOfRequest
import com.polar.polarsensordatacollector.ui.utils.FileUtils
import com.polar.sdk.impl.utils.CaloriesType
import com.polar.sdk.api.PolarBleApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import javax.inject.Inject

internal data class ActivityRecordingDataDevConnectionState(
    val isConnected: Boolean
)

sealed class ActivityDataUiState {
    object IsFetching : ActivityDataUiState()
    class FetchedData(val data: ActivityRecordingData) : ActivityDataUiState()
    class Failure(
        val message: String,
        val throwable: Throwable?
    ) : ActivityDataUiState()
}

data class ActivityRecordingData(
    val startDate: String,
    val endDate: String,
    val uri: Uri,
    val dataType: PolarBleApi.PolarActivityDataType
)

private const val TAG: String = "ActivityRecordingDataViewModel"

@HiltViewModel
class ActivityRecordingDataViewModel @Inject constructor(
    private val polarDeviceStreamingRepository: PolarDeviceRepository,
    private val fileUtils: FileUtils,
    state: SavedStateHandle
) : ViewModel() {

    private val _devConnectionState = MutableStateFlow(ActivityRecordingDataDevConnectionState(true))
    internal var devConnectionState: StateFlow<ActivityRecordingDataDevConnectionState> = _devConnectionState.asStateFlow()

    private val deviceId = state.get<String>("deviceIdFragmentArgument") ?: throw Exception("ActivityRecordingDataViewModel model requires deviceId")
    private val type = state.get<String>("activityTypeFragmentArgument") ?: throw Exception("ActivityRecordingDataViewModel model requires type")
    private val startDate = state.get<String>("activityStartDateFragmentArgument") ?: throw Exception("ActivityRecordingDataViewModel model requires start date")
    private val endDate = state.get<String>("activityEndDateFragmentArgument") ?: throw Exception("ActivityRecordingDataViewModel model requires end date")
    private val caloriesTypeString = state.get<String>("caloriesTypeArgument") ?: "ACTIVITY"
    private val caloriesType = CaloriesType.valueOf(caloriesTypeString)

    var activityDataUiState: ActivityDataUiState by mutableStateOf(ActivityDataUiState.IsFetching)
        private set

    init {
        viewModelScope.launch {
            polarDeviceStreamingRepository.deviceConnectionStatus
                .collect { connectionStatus ->
                    when (connectionStatus) {
                        is DeviceConnectionState.DeviceConnected -> {
                            _devConnectionState.update {
                                it.copy(isConnected = true)
                            }
                        }
                        is DeviceConnectionState.DeviceConnecting,
                        is DeviceConnectionState.DeviceDisconnecting,
                        is DeviceConnectionState.DeviceNotConnected -> {
                            _devConnectionState.update {
                                it.copy(isConnected = false)
                            }
                        }
                    }
                }
        }

        if (startDate != null && endDate != null) {
            fetchRecording(
                LocalDate.parse(startDate, DateTimeFormatter.BASIC_ISO_DATE),
                LocalDate.parse(endDate, DateTimeFormatter.BASIC_ISO_DATE),
                deviceId,
                PolarBleApi.PolarActivityDataType.valueOf(type)
            )
        }
    }

    private fun fetchRecording(
        startDate: LocalDate,
        endDate: LocalDate,
        deviceId: String,
        activityRecordingType: PolarBleApi.PolarActivityDataType
    ) {
        Log.d(TAG, "fetchRecording $deviceId and type $activityRecordingType")
        viewModelScope.launch(Dispatchers.IO) {

            when (activityRecordingType) {
                PolarBleApi.PolarActivityDataType.SLEEP ->
                    when (val sleepRecording = polarDeviceStreamingRepository.getSleepData(deviceId, startDate, endDate)) {
                        is ResultOfRequest.Success -> {
                            if (sleepRecording.value != null) {
                                val gson = GsonBuilder()
                                    .registerTypeAdapter(LocalDate::class.java, JsonSerializer<LocalDate> { src, _, _ ->
                                        JsonPrimitive(src?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                                    })
                                    .registerTypeAdapter(LocalDateTime::class.java, JsonSerializer<LocalDateTime> { src, _, _ ->
                                        JsonPrimitive(src?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")))
                                    })
                                    .create()
                                val json = gson.toJson(sleepRecording.value)
                                val fileUri = fileUtils.saveToFile(
                                    json.encodeToByteArray(),
                                    "/SLEEP/$startDate-sleep.json"
                                )
                                val sleepRecordingData = ActivityRecordingData(startDate.toString(), endDate.toString(), fileUri, PolarBleApi.PolarActivityDataType.SLEEP)
                                updateActivityDataUiState(sleepRecordingData, fileUri, PolarBleApi.PolarActivityDataType.SLEEP)
                            } else {
                                activityDataUiState = ActivityDataUiState.Failure("fetch sleep recording responded with empty data", null)
                            }
                        }
                        is ResultOfRequest.Failure -> {
                            activityDataUiState = ActivityDataUiState.Failure(sleepRecording.message, sleepRecording.throwable)
                        }
                    }

                PolarBleApi.PolarActivityDataType.STEPS ->
                    when (val stepsRecording = polarDeviceStreamingRepository.getStepsData(
                        deviceId,
                        startDate,
                        endDate
                    )) {
                        is ResultOfRequest.Success -> {
                            if (stepsRecording.value != null) {
                                val fileUri = fileUtils.saveToFile(
                                    Gson().toJson(stepsRecording.value).encodeToByteArray(),
                                    "/STEPS/$startDate-steps.json"
                                )
                                val stepsRecording =  ActivityRecordingData(startDate.toString(), endDate.toString(), fileUri, PolarBleApi.PolarActivityDataType.STEPS)
                                updateActivityDataUiState(stepsRecording, fileUri, PolarBleApi.PolarActivityDataType.STEPS)
                            } else {
                                activityDataUiState = ActivityDataUiState.Failure("fetch recording responded with empty data", null)
                            }
                        }
                        is ResultOfRequest.Failure -> {
                            activityDataUiState = ActivityDataUiState.Failure(stepsRecording.message, stepsRecording.throwable)
                        }
                    }

                PolarBleApi.PolarActivityDataType.CALORIES ->
                    when (val caloriesRecording = polarDeviceStreamingRepository.getCaloriesData(
                        deviceId,
                        startDate,
                        endDate,
                        caloriesType
                    )) {
                        is ResultOfRequest.Success -> {
                            if (caloriesRecording.value != null) {
                                val fileUri = fileUtils.saveToFile(
                                    Gson().toJson(caloriesRecording.value).encodeToByteArray(),
                                    "/CALORIES/$startDate-calories.json"
                                )
                                val caloriesRecordingData = ActivityRecordingData(startDate.toString(), endDate.toString(), fileUri, PolarBleApi.PolarActivityDataType.CALORIES)
                                updateActivityDataUiState(caloriesRecordingData, fileUri, PolarBleApi.PolarActivityDataType.CALORIES)
                            } else {
                                activityDataUiState = ActivityDataUiState.Failure("fetch recording responded with empty data", null)
                            }
                        }
                        is ResultOfRequest.Failure -> {
                            activityDataUiState = ActivityDataUiState.Failure(caloriesRecording.message, caloriesRecording.throwable)
                        }
                    }

                PolarBleApi.PolarActivityDataType.HR_SAMPLES ->
                    when (val hrRecording = polarDeviceStreamingRepository.get247HrSamplesData(
                            deviceId,
                            Date.from(startDate.atStartOfDay(ZoneId.systemDefault()).toInstant()),
                            Date.from(endDate.atStartOfDay(ZoneId.systemDefault()).toInstant())
                    )) {
                        is ResultOfRequest.Success -> {
                            if (hrRecording.value != null) {
                                val fileUri = fileUtils.saveToFile(
                                        Gson().toJson(hrRecording.value).encodeToByteArray(),
                                        "/HR_SAMPLES/$startDate-hr-samples.json"
                                )
                                val hrRecording = ActivityRecordingData(startDate.toString(), endDate.toString(), fileUri, PolarBleApi.PolarActivityDataType.HR_SAMPLES)
                                updateActivityDataUiState(hrRecording, fileUri, PolarBleApi.PolarActivityDataType.HR_SAMPLES)
                            } else {
                                activityDataUiState = ActivityDataUiState.Failure("fetch recording responded with empty data", null)
                            }
                        }
                        is ResultOfRequest.Failure -> {
                            activityDataUiState = ActivityDataUiState.Failure(hrRecording.message, hrRecording.throwable)
                        }
                    }
                PolarBleApi.PolarActivityDataType.NIGHTLY_RECHARGE ->
                    when (val nightlyRechargeRecording = polarDeviceStreamingRepository.getNightlyRechargeData(
                        deviceId,
                        startDate,
                        endDate
                    )) {
                        is ResultOfRequest.Success -> {
                            if (nightlyRechargeRecording.value != null) {
                                val gson = GsonBuilder()
                                        .registerTypeAdapter(LocalDateTime::class.java, JsonSerializer<LocalDateTime> { src, _, _ ->
                                            JsonPrimitive(src?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd' 'HH:mm:ss")))
                                        })
                                        .create()
                                val json = gson.toJson(nightlyRechargeRecording.value)
                                val fileUri = fileUtils.saveToFile(
                                        json.encodeToByteArray(),
                                        "/NIGHTLY_RECHARGE/$startDate-nightly-recharge.json"
                                )
                                val nightlyRechargeRecording = ActivityRecordingData(startDate.toString(), endDate.toString(), fileUri, PolarBleApi.PolarActivityDataType.NIGHTLY_RECHARGE)
                                updateActivityDataUiState(nightlyRechargeRecording, fileUri, PolarBleApi.PolarActivityDataType.NIGHTLY_RECHARGE)
                            } else {
                                activityDataUiState = ActivityDataUiState.Failure("fetch recording responded with empty data", null)
                            }
                        }
                        is ResultOfRequest.Failure -> {
                            activityDataUiState = ActivityDataUiState.Failure(nightlyRechargeRecording.message, nightlyRechargeRecording.throwable)
                        }
                    }
                PolarBleApi.PolarActivityDataType.PPI_SAMPLES ->
                    when (val ppiRecording = polarDeviceStreamingRepository.get247PPiSamples (
                        deviceId,
                        Date.from(startDate.atStartOfDay(ZoneId.systemDefault()).toInstant()),
                        Date.from(endDate.atStartOfDay(ZoneId.systemDefault()).toInstant())
                    )) {
                        is ResultOfRequest.Success -> {
                            if (ppiRecording.value != null) {
                                val gson = GsonBuilder()
                                    .registerTypeAdapter(LocalTime::class.java, JsonSerializer<LocalTime> { src, _, _ ->
                                        JsonPrimitive(src?.format(DateTimeFormatter.ISO_LOCAL_TIME))
                                    })
                                    .registerTypeAdapter(LocalDate::class.java, JsonSerializer<LocalDate> { src, _, _ ->
                                        JsonPrimitive(src?.format(DateTimeFormatter.ISO_LOCAL_DATE))
                                    })
                                    .create()
                                val json = gson.toJson(ppiRecording.value)
                                val fileUri = fileUtils.saveToFile(
                                    json.encodeToByteArray(),
                                    "/PPI_SAMPLES/$startDate-ppi-samples.json"
                                )
                                val hrRecording = ActivityRecordingData(startDate.toString(), endDate.toString(), fileUri, PolarBleApi.PolarActivityDataType.PPI_SAMPLES)
                                updateActivityDataUiState(hrRecording, fileUri, PolarBleApi.PolarActivityDataType.PPI_SAMPLES)
                            } else {
                                activityDataUiState = ActivityDataUiState.Failure("fetch PPi data from a device responded with empty data", null)
                            }
                        }
                        is ResultOfRequest.Failure -> {
                            activityDataUiState = ActivityDataUiState.Failure(ppiRecording.message, ppiRecording.throwable)
                        }
                    }

                PolarBleApi.PolarActivityDataType.SKIN_TEMPERATURE ->
                    when (val skinTemperatureRecording = polarDeviceStreamingRepository.getSkinTemperatureData(
                            deviceId, startDate, endDate)) {
                        is ResultOfRequest.Success -> {
                            if (skinTemperatureRecording.value != null) {
                                val gson = GsonBuilder()
                                        .registerTypeAdapter(LocalDate::class.java, JsonSerializer<LocalDate> { src, _, _ ->
                                            JsonPrimitive(src?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                                        })
                                        .create()
                                val json = gson.toJson(skinTemperatureRecording.value)
                                val fileUri = fileUtils.saveToFile(
                                        json.encodeToByteArray(),
                                        "/SKIN_TEMPERATURE/$startDate-skin-temperature.json"
                                )
                                val skinTemperatureRecording = ActivityRecordingData(startDate.toString(), endDate.toString(), fileUri, PolarBleApi.PolarActivityDataType.SKIN_TEMPERATURE)
                                updateActivityDataUiState(skinTemperatureRecording, fileUri, PolarBleApi.PolarActivityDataType.SKIN_TEMPERATURE)
                            } else {
                                activityDataUiState = ActivityDataUiState.Failure("fetch skin temperature recording responded with empty data", null)
                            }
                        }
                        is ResultOfRequest.Failure -> {
                            activityDataUiState = ActivityDataUiState.Failure(skinTemperatureRecording.message, skinTemperatureRecording.throwable)
                        }
                    }

                PolarBleApi.PolarActivityDataType.ACTIVE_TIME ->
                    when (val activeTimeRecording = polarDeviceStreamingRepository.getActiveTimeData(
                        deviceId,
                        startDate,
                        endDate
                    )) {
                        is ResultOfRequest.Success -> {
                            if (activeTimeRecording.value != null) {
                                val gson = GsonBuilder()
                                    .registerTypeAdapter(LocalDate::class.java, JsonSerializer<LocalDate> { src, _, _ ->
                                        JsonPrimitive(src?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                                    })
                                    .create()
                                val json = gson.toJson(activeTimeRecording.value)
                                val fileUri = fileUtils.saveToFile(
                                    json.encodeToByteArray(),
                                    "/ACTIVE_TIME/$startDate-active-time.json"
                                )
                                val activeTimeRecording = ActivityRecordingData(startDate.toString(), endDate.toString(), fileUri, PolarBleApi.PolarActivityDataType.ACTIVE_TIME)
                                updateActivityDataUiState(activeTimeRecording, fileUri, PolarBleApi.PolarActivityDataType.ACTIVE_TIME)
                            } else {
                                activityDataUiState = ActivityDataUiState.Failure("fetch active time recording responded with empty data", null)
                            }
                        }
                        is ResultOfRequest.Failure -> {
                            activityDataUiState = ActivityDataUiState.Failure(activeTimeRecording.message, activeTimeRecording.throwable)
                        }
                    }

                PolarBleApi.PolarActivityDataType.ACTIVITY_SAMPLES ->
                    when (val activitySamplesData = polarDeviceStreamingRepository.getActivitySamplesData(
                        deviceId, Date.from(startDate.atStartOfDay(ZoneId.systemDefault()).toInstant()), Date.from(endDate.atStartOfDay(ZoneId.systemDefault()).toInstant()))) {
                        is ResultOfRequest.Success -> {
                            if (activitySamplesData.value != null) {
                                val gson = GsonBuilder()
                                    .registerTypeAdapter(LocalDateTime::class.java, JsonSerializer<LocalDateTime> { src, _, _ ->
                                        JsonPrimitive(src?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")))
                                    })
                                    .create()
                                val json = gson.toJson(activitySamplesData.value)
                                val fileUri = fileUtils.saveToFile(
                                    json.encodeToByteArray(),
                                    "/ACTIVITY_SAMPLES/$startDate-activity-samples.json"
                                )
                                val activitySamplesRecording = ActivityRecordingData(startDate.toString(), endDate.toString(), fileUri, PolarBleApi.PolarActivityDataType.ACTIVITY_SAMPLES)
                                updateActivityDataUiState(activitySamplesRecording, fileUri, PolarBleApi.PolarActivityDataType.ACTIVITY_SAMPLES)
                            } else {
                                activityDataUiState = ActivityDataUiState.Failure("fetch activity samples data responded with empty data", null)
                            }
                        }
                        is ResultOfRequest.Failure -> {
                            activityDataUiState = ActivityDataUiState.Failure(activitySamplesData.message, activitySamplesData.throwable)
                        }
                    }

                else -> { Log.d(TAG, "fetchRecording not implemented for $activityRecordingType") }
            }
        }
    }

    private suspend fun updateActivityDataUiState(activityRecording: ActivityRecordingData, uri: Uri, dataType: PolarBleApi.PolarActivityDataType) {
        val activityRecording = ActivityRecordingData(
            startDate = activityRecording.startDate,
            endDate = activityRecording.startDate,
            uri = uri,
            dataType = dataType
        )
        withContext(Dispatchers.Main) {
            activityDataUiState = ActivityDataUiState.FetchedData(data = activityRecording)
        }
    }
}