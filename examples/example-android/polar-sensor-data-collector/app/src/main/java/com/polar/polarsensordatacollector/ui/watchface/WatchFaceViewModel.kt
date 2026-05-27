package com.polar.polarsensordatacollector.ui.watchface

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository
import com.polar.polarsensordatacollector.repository.ResultOfRequest
import com.polar.sdk.api.model.PolarWatchFaceComplication
import com.polar.sdk.api.model.PolarWatchFaceConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "WatchFaceViewModel"
const val SLOT_COUNT = 4

data class WatchFaceUiState(
    val isLoading: Boolean = false,
    val resultMessage: String? = null,
    /**
     * Ordered list of [SLOT_COUNT] slots. Each entry is the complication for that slot.
     */
    val slots: List<PolarWatchFaceComplication?> = List(SLOT_COUNT) { null },
    val readStatus: String? = null,
)

class WatchFaceViewModel(
    private val repository: PolarDeviceRepository,
    private val deviceId: String,
    private val app: Application
) : ViewModel() {

    private val _uiState = MutableStateFlow(WatchFaceUiState())
    val uiState: StateFlow<WatchFaceUiState> = _uiState.asStateFlow()

    init {
        loadCurrentConfig()
    }

    /** Read the current watch face config from the device and pre-populate checkboxes. */
    fun loadCurrentConfig() {
        viewModelScope.launch {
            Log.d(TAG, "loadCurrentConfig: reading device=$deviceId")
            _uiState.update { it.copy(isLoading = true, readStatus = app.getString(R.string.watch_face_status_reading), resultMessage = null) }

            when (val result = repository.getWatchFaceConfig(deviceId)) {
                is ResultOfRequest.Success -> {
                    val config = result.value
                    if (config == null) {
                        Log.w(TAG, "loadCurrentConfig: SUCCESS but value is null")
                        _uiState.update { it.copy(isLoading = false, readStatus = app.getString(R.string.watch_face_status_read_null)) }
                        return@launch
                    }
                    Log.d(TAG, "loadCurrentConfig: SUCCESS — ${config.enabledComplications.size} complications from device")
                    config.enabledComplications.forEachIndexed { i, c ->
                        Log.d(TAG, "  slot[$i] ${c.name}  id=${c.id}")
                    }
                    // Map the device list into exactly SLOT_COUNT slots
                    val slots = List(SLOT_COUNT) { i ->
                        config.enabledComplications.getOrNull(i)
                    }
                    Log.d(TAG, "loadCurrentConfig: slots = ${slots.map { it?.name ?: "null" }}")
                    _uiState.update { s ->
                        s.copy(
                            isLoading = false,
                            slots = slots,
                            readStatus = app.getString(R.string.watch_face_status_loaded, config.enabledComplications.size),
                            resultMessage = null
                        )
                    }
                }
                is ResultOfRequest.Failure -> {
                    Log.w(TAG, "loadCurrentConfig: FAILED — ${result.message}")
                    _uiState.update { s ->
                        s.copy(
                            isLoading = false,
                            readStatus = app.getString(R.string.watch_face_status_read_failed, result.message),
                        )
                    }
                }
            }
        }
    }

    fun setSlot(slotIndex: Int, complication: PolarWatchFaceComplication) {
        require(slotIndex in 0 until SLOT_COUNT)
        Log.d(TAG, "setSlot[$slotIndex] = ${complication.name}")
        _uiState.update { s ->
            val updated = s.slots.toMutableList().also { it[slotIndex] = complication }
            s.copy(slots = updated, resultMessage = null)
        }
    }

    fun applyComplications() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, resultMessage = null) }
            val ordered = _uiState.value.slots.map { it ?: PolarWatchFaceComplication.EMPTY }
            Log.d(TAG, "applyComplications: writing ${ordered.size} slots for device=$deviceId")
            ordered.forEachIndexed { i, c ->
                Log.d(TAG, "  slot[$i] ${c.name}  id=${c.id}")
            }
            val result = repository.setWatchFaceConfig(deviceId, PolarWatchFaceConfig(ordered))
            if (result is ResultOfRequest.Success) {
                Log.d(TAG, "applyComplications: success: $deviceId")
            }
            _uiState.update { s ->
                s.copy(
                    isLoading = false,
                    resultMessage = when (result) {
                        is ResultOfRequest.Success -> app.getString(R.string.watch_face_applied_success)
                        is ResultOfRequest.Failure -> app.getString(R.string.watch_face_failed_prefix) + result.message
                    }
                )
            }
        }
    }
}

class WatchFaceViewModelFactory(
    private val repository: PolarDeviceRepository,
    private val deviceId: String,
    private val app: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        WatchFaceViewModel(repository, deviceId, app) as T
}
