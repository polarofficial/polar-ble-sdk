package com.polar.sdk.api.model

import com.polar.sdk.api.PolarBleApi

/**
 * Polar offline recording trigger mode. Based on the trigger mode the
 * device starts the offline recording automatically
 */
enum class PolarOfflineRecordingTriggerMode {
    // The automatic start of offline recording is disabled
    TRIGGER_DISABLED,

    // Triggers the offline recording when device is powered on
    TRIGGER_SYSTEM_START,

    // Triggers the offline recording when exercise is started in device
    TRIGGER_EXERCISE_START
}

/**
 * Polar offline recording trigger
 *  @property triggerMode the mode of the trigger
 *  @property triggerFeatures features enabled with the trigger, empty if [triggerMode] is [PolarOfflineRecordingTriggerMode.TRIGGER_DISABLED].
 *  In case of the [PolarBleApi.PolarDeviceDataType.PPI] and [PolarBleApi.PolarDeviceDataType.HR] the [PolarSensorSetting] is null
 */
data class PolarOfflineRecordingTrigger(
    val triggerMode: PolarOfflineRecordingTriggerMode,
    val triggerFeatures: Map<PolarBleApi.PolarDeviceDataType, PolarSensorSetting?>
)