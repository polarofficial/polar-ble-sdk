package com.polar.polarsensordatacollector.ui.activity

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository
import com.polar.polarsensordatacollector.ui.landing.ONLINE_OFFLINE_KEY_DEVICE_ID
import com.polar.polarsensordatacollector.ui.utils.MessageUiState
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.model.PolarSkinTemperatureData
import com.polar.sdk.api.model.activity.PolarStepsData
import com.polar.sdk.api.model.activity.PolarCaloriesData
import com.polar.sdk.api.model.sleep.PolarSleepData
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class ActivityRecordingViewModel @Inject constructor(
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
        class Failure(
            val message: String,
            val throwable: Throwable?
        ) : ActivityUiState()
    }

    val identifier = state.get<String>(ONLINE_OFFLINE_KEY_DEVICE_ID) ?: throw Exception("Activity record viewModel must know the identifier")

    private val _uiShowError = MutableSharedFlow<MessageUiState>(extraBufferCapacity = 1)
    val uiShowError: SharedFlow<MessageUiState> = _uiShowError.asSharedFlow()

    private val _uiShowInfo = MutableSharedFlow<MessageUiState>(extraBufferCapacity = 1)
    val uiShowInfo: SharedFlow<MessageUiState> = _uiShowInfo.asSharedFlow()

    private val compositeDisposable = CompositeDisposable()

    public override fun onCleared() {
        super.onCleared()
        compositeDisposable.dispose()
    }

    fun initView() {
    }
}