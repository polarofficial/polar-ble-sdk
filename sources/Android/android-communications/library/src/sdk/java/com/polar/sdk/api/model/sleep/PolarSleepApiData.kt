package com.polar.sdk.api.model.sleep

import com.polar.sdk.api.RestApiEventPayload

data class PolarSleepRecordingState(
    val enabled: Int
): RestApiEventPayload()

data class PolarSleepApiServiceEventPayload(
    // property name from SLEEP.API D2H event JSON payload
    val sleep_recording_state: PolarSleepRecordingState
): RestApiEventPayload()