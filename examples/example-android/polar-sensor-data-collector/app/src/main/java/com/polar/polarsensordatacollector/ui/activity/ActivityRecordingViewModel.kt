package com.polar.polarsensordatacollector.ui.activity

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository
import com.polar.polarsensordatacollector.repository.ResultOfRequest
import com.polar.polarsensordatacollector.ui.landing.ONLINE_OFFLINE_KEY_DEVICE_ID
import com.polar.polarsensordatacollector.ui.utils.MessageUiState
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.model.PolarSkinTemperatureData
import com.polar.sdk.api.model.activity.PolarStepsData
import com.polar.sdk.api.model.activity.PolarCaloriesData
import com.polar.sdk.api.model.sleep.PolarSleepData
import com.polar.sdk.impl.utils.CaloriesType
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class ActivityRecordingViewModel @Inject constructor(
    private val polarDeviceStreamingRepository: PolarDeviceRepository,
    private val state: SavedStateHandle,
) : ViewModel() {
    companion object {
        private const val TAG = "ActivityRecordingViewModel"
    }

    data class ActivityData(
        val startDate: LocalDate,
        val endDate: LocalDate,
        val activityType: PolarBleApi.PolarActivityDataType
    )

    sealed class ActivityUiState {
        object IsFetching : ActivityUiState()
        class FetchedData(val data: ActivityData) : ActivityUiState()
        class Failure(
            val message: String,
            val throwable: Throwable?
        ) : ActivityUiState()
    }

    val deviceId = state.get<String>(ONLINE_OFFLINE_KEY_DEVICE_ID) ?: throw Exception("Activity record viewModel must know the deviceId")

    private val _uiShowError: MutableStateFlow<MessageUiState> = MutableStateFlow(MessageUiState("", ""))
    val uiShowError: StateFlow<MessageUiState> = _uiShowError.asStateFlow()

    private val _uiShowInfo: MutableStateFlow<MessageUiState> = MutableStateFlow(MessageUiState("", ""))
    val uiShowInfo: StateFlow<MessageUiState> = _uiShowInfo.asStateFlow()

    private val _sleepLiveData = MutableLiveData<List<PolarSleepData>>()
    private val _stepsLiveData = MutableLiveData<List<PolarStepsData>>()
    private var activityUiState: ActivityUiState by mutableStateOf(ActivityUiState.IsFetching)
    private val _caloriesLiveData = MutableLiveData<List<PolarCaloriesData>>()
    private val _skinTemperatureLiveData = MutableLiveData<List<PolarSkinTemperatureData>>()

    private val compositeDisposable = CompositeDisposable()

    public override fun onCleared() {
        super.onCleared()
        compositeDisposable.dispose()
    }

    fun getSleep(from: LocalDate, to: LocalDate) {
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = polarDeviceStreamingRepository.getSleepData(deviceId, from, to)) {
                is ResultOfRequest.Success -> {
                    val result = result.value ?: listOf(PolarSleepData())
                    var sleepData = ActivityData(
                        startDate = from,
                        endDate = to,
                        activityType = PolarBleApi.PolarActivityDataType.SLEEP
                    )
                    activityUiState = ActivityUiState.FetchedData(data = sleepData)
                    _sleepLiveData.postValue(result)
                }
                is ResultOfRequest.Failure -> {
                    Log.w(TAG, "Failed to read sleep analysis result from $from to $to")
                }
            }
        }
    }

    fun getSteps(from: Date, to: Date) {
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = polarDeviceStreamingRepository.getStepsData(deviceId, from, to)) {
                is ResultOfRequest.Success -> {
                    val result = result.value ?: listOf(PolarStepsData())
                    var stepsData = ActivityData(
                        startDate =  Instant.ofEpochMilli(from.toInstant().toEpochMilli()).atZone(ZoneId.systemDefault()).toLocalDate(),
                        endDate = Instant.ofEpochMilli(to.toInstant().toEpochMilli()).atZone(ZoneId.systemDefault()).toLocalDate(),
                        activityType = PolarBleApi.PolarActivityDataType.STEPS
                    )
                    activityUiState = ActivityUiState.FetchedData(data = stepsData)
                    _stepsLiveData.postValue(result)
                }
                is ResultOfRequest.Failure -> {
                    Log.w(TAG, "Failed to read steps data from $from to $to")
                }
            }
        }
    }

    fun getCalories(from: Date, to: Date, caloriesType: CaloriesType) {
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = polarDeviceStreamingRepository.getCaloriesData(deviceId, from, to, caloriesType)) {
                is ResultOfRequest.Success -> {
                    val result = result.value ?: listOf(PolarCaloriesData())
                    val caloriesData = ActivityData(
                        startDate = Instant.ofEpochMilli(from.toInstant().toEpochMilli()).atZone(ZoneId.systemDefault()).toLocalDate(),
                        endDate = Instant.ofEpochMilli(to.toInstant().toEpochMilli()).atZone(ZoneId.systemDefault()).toLocalDate(),
                        activityType = PolarBleApi.PolarActivityDataType.CALORIES
                    )
                    activityUiState = ActivityUiState.FetchedData(data = caloriesData)
                    _caloriesLiveData.postValue(result)
                }
                is ResultOfRequest.Failure -> {
                    Log.w(TAG, "Failed to read calories data from $from to $to")
                }
            }
        }
    }

    fun getSkinTemperature(from: LocalDate, to: LocalDate) {
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = polarDeviceStreamingRepository.getSkinTemperatureData(deviceId, from, to)) {
                is ResultOfRequest.Success -> {
                    val result = result.value ?: listOf(PolarSkinTemperatureData())
                    val skinTemperatureData = ActivityData(
                        startDate = Instant.ofEpochMilli(from.toEpochDay()).atZone(ZoneId.systemDefault()).toLocalDate(),
                        endDate = Instant.ofEpochMilli(to.toEpochDay()).atZone(ZoneId.systemDefault()).toLocalDate(),
                        activityType = PolarBleApi.PolarActivityDataType.CALORIES
                    )
                    activityUiState = ActivityUiState.FetchedData(data = skinTemperatureData)
                    _skinTemperatureLiveData.postValue(result)
                }
                is ResultOfRequest.Failure -> {
                    Log.w(TAG, "Failed to read skin temperature data from $from to $to")
                }
            }
        }
    }

    fun initView() {
        _uiShowInfo.update {
            MessageUiState("", null)
        }
    }

    private fun showError(errorHeader: String, errorDescription: String = "") {
        Log.e(TAG, " Error: $errorHeader ${if (errorDescription.isNotEmpty()) "Description: $errorDescription" else ""}")
        _uiShowError.update {
            MessageUiState(errorHeader, errorDescription)
        }
    }

    private fun showInfo(header: String, description: String = "") {
        _uiShowInfo.update {
            MessageUiState(header, description)
        }
    }
}