// Copyright © 2026 Polar Electro Oy. All rights reserved.
package com.polar.sdk.impl

import com.polar.androidcommunications.api.ble.BleDeviceListener
import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils
import com.polar.androidcommunications.api.ble.model.polar.BlePolarDeviceCapabilitiesUtility
import com.polar.sdk.api.PolarSleepApi
import com.polar.sdk.api.errors.PolarBleSdkInternalException
import com.polar.sdk.api.errors.PolarServiceNotAvailable
import com.polar.sdk.api.errors.PolarTimeoutException
import com.polar.sdk.api.model.sleep.PolarSleepData
import com.polar.sdk.api.model.sleep.PolarSleepRecordingStatus
import com.polar.sdk.impl.utils.PolarFileUtils.pFtpWriteOperation
import com.polar.sdk.impl.utils.PolarServiceClientUtils
import com.polar.sdk.impl.utils.PolarServiceClientUtils.fetchSession
import com.polar.sdk.impl.utils.PolarSleepUtils
import com.polar.sdk.impl.utils.receiveRestApiEvents
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

/**
 * Implementation of [PolarSleepApi].
 *
 * Handles sleep data retrieval and sleep recording state management on Polar devices.
 *
 * Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SLEEP_DATA].
 */
internal class PolarSleepApiImpl(
    private val listener: BleDeviceListener
) : PolarSleepApi {

    companion object {
        private const val TAG = "PolarSleepApiImpl"
        private const val SLEEP_RECORDING_STATE_EVENT = "sleep_recording_state"
    }


    private fun sleepRecordingStatus(enabled: JsonElement?): PolarSleepRecordingStatus {
        if (enabled == null || enabled.isJsonNull) {
            return PolarSleepRecordingStatus.UNKNOWN
        }
        if (!enabled.isJsonPrimitive) {
            throw PolarBleSdkInternalException("Unexpected sleep recording enabled value: $enabled")
        }
        val primitive = enabled.asJsonPrimitive
        return when {
            primitive.isBoolean -> if (primitive.asBoolean) PolarSleepRecordingStatus.ENABLED else PolarSleepRecordingStatus.DISABLED
            primitive.isNumber -> sleepRecordingStatus(primitive.asInt)
            primitive.isString -> when (primitive.asString.trim().lowercase()) {
                "1", "true" -> PolarSleepRecordingStatus.ENABLED
                "0", "false" -> PolarSleepRecordingStatus.DISABLED
                else -> throw PolarBleSdkInternalException("Unexpected sleep recording enabled value: ${primitive.asString}")
            }
            else -> throw PolarBleSdkInternalException("Unexpected sleep recording enabled value: $enabled")
        }
    }

    private fun sleepRecordingStatus(enabled: Int): PolarSleepRecordingStatus =
        when (enabled) {
            1 -> PolarSleepRecordingStatus.ENABLED
            0 -> PolarSleepRecordingStatus.DISABLED
            else -> throw PolarBleSdkInternalException("Unexpected sleep recording enabled value: $enabled")
        }

    override suspend fun getSleepRecordingStatus(identifier: String, timeoutMs: Long): PolarSleepRecordingStatus {
        // Resolve session and client up front so that PolarServiceNotAvailable is thrown
        // immediately, before any BLE traffic is started.
        val deviceType = fetchSession(identifier, listener)?.polarDeviceType
            ?: throw PolarServiceNotAvailable()
        if (!BlePolarDeviceCapabilitiesUtility.isActivityDataSupported(deviceType)) {
            throw PolarServiceNotAvailable()
        }
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()

        // The subscribe write is sent from inside onSubscription (see sendSubscribeRequest), so it
        // runs only after the event collector is registered on the shared notification flow. This
        // prevents the device's first event from being emitted while there is no subscriber and
        // dropped, which which would cause repeated overlapping calls to time out.
        // first() returns as soon as the first state arrives, even if the device keeps the
        // subscription open.
        val enabled = withTimeoutOrNull(timeoutMs) {
            client.receiveRestApiEvents(
                identifier,
                onSubscribed = { sendSubscribeRequest(identifier) },
                eventKey = SLEEP_RECORDING_STATE_EVENT
            )
                .mapNotNull { events -> firstStatus(events) }
                .first()
        } ?: throw PolarTimeoutException("getSleepRecordingState timed out after ${timeoutMs}ms")
        return enabled
    }

    override fun observeSleepRecordingStatus(identifier: String): Flow<Array<PolarSleepRecordingStatus>> = flow {
        val deviceType = fetchSession(identifier, listener)?.polarDeviceType
            ?: throw PolarServiceNotAvailable()
        if (!BlePolarDeviceCapabilitiesUtility.isActivityDataSupported(deviceType)) {
            throw PolarServiceNotAvailable()
        }
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()

        emitAll(
            client.receiveRestApiEvents(
                identifier,
                onSubscribed = { sendSubscribeRequest(identifier) },
                eventKey = SLEEP_RECORDING_STATE_EVENT
            )
                .map { list ->
                    list.mapNotNull { jsonString -> parseSleepRecordingStatus(jsonString) }.toTypedArray()
                }
        )
    }

    // Subscribes to the sleep_recording_state event. Called from onSubscription, after the event
    // collector is registered, so no event can be lost between subscribe and the first notification.
    private suspend fun sendSubscribeRequest(identifier: String) {
        pFtpWriteOperation(
            identifier = identifier,
            listener = listener,
            data = "{}".toByteArray(),
            path = "/REST/SLEEP.API?cmd=subscribe&event=sleep_recording_state&details=[enabled]",
            tag = TAG
        )
    }

    // Returns the status from the last parseable event in a notification batch, or null when
    // the batch holds no parseable sleep_recording_state event.
    private fun firstStatus(events: List<String>): PolarSleepRecordingStatus? {
        return events.mapNotNull { json -> parseSleepRecordingStatus(json) }.lastOrNull()
    }

    private fun parseSleepRecordingStatus(json: String): PolarSleepRecordingStatus? {
        val root: JsonElement = runCatching { JsonParser().parse(json) }.getOrNull() ?: return null
        if (!root.isJsonObject) return null
        val sleepRecordingState = root.asJsonObject.get("sleep_recording_state") ?: return null
        if (!sleepRecordingState.isJsonObject) return null
        return sleepRecordingStatus(sleepRecordingState.asJsonObject.get("enabled"))
    }

    @Deprecated(
        message = "Use getSleepRecordingStatus, which reports unknown state instead of off",
        replaceWith = ReplaceWith("getSleepRecordingStatus(identifier, timeoutMs)")
    )
    override suspend fun getSleepRecordingState(identifier: String, timeoutMs: Long): Boolean {
        return getSleepRecordingStatus(identifier, timeoutMs) == PolarSleepRecordingStatus.ENABLED
    }

    @Deprecated(
        message = "Use observeSleepRecordingStatus, which reports unknown state instead of off",
        replaceWith = ReplaceWith("observeSleepRecordingStatus(identifier)")
    )
    override fun observeSleepRecordingState(identifier: String): Flow<Array<Boolean>> {
        return observeSleepRecordingStatus(identifier)
            .map { statuses -> statuses.map { it == PolarSleepRecordingStatus.ENABLED }.toTypedArray() }
    }

    override suspend fun stopSleepRecording(identifier: String) {
        pFtpWriteOperation(
            identifier = identifier,
            listener = listener,
            data = "{}".toByteArray(),
            path = "/REST/SLEEP.API?cmd=post&endpoint=stop_sleep_recording",
            tag = TAG
        )
    }

    override suspend fun getSleep(
        identifier: String,
        fromDate: LocalDate,
        toDate: LocalDate
    ): List<PolarSleepData> {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        val dates = generateSequence(fromDate) { it.plusDays(1) }.takeWhile { !it.isAfter(toDate) }.toList()
        return dates.mapNotNull { date ->
            try {
                val result = PolarSleepUtils.readSleepDataFromDayDirectory(client, date)
                if (result.sleepStartTime != null) PolarSleepData(date, result) else null
            } catch (e: Throwable) {
                BleLogger.w(TAG, "Failed to read sleep data for $date: $e")
                null
            }
        }
    }
}

