// Copyright © 2026 Polar Electro Oy. All rights reserved.
package com.polar.sdk.impl

import com.polar.androidcommunications.api.ble.BleDeviceListener
import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils
import com.polar.androidcommunications.api.ble.model.polar.BlePolarDeviceCapabilitiesUtility
import com.polar.sdk.api.PolarSleepApi
import com.polar.sdk.api.errors.PolarServiceNotAvailable
import com.polar.sdk.api.errors.PolarTimeoutException
import com.polar.sdk.api.model.sleep.PolarSleepApiServiceEventPayload
import com.polar.sdk.api.model.sleep.PolarSleepData
import com.polar.sdk.impl.utils.PolarFileUtils.pFtpWriteOperation
import com.polar.sdk.impl.utils.PolarServiceClientUtils
import com.polar.sdk.impl.utils.PolarServiceClientUtils.fetchSession
import com.polar.sdk.impl.utils.PolarSleepUtils
import com.polar.sdk.impl.utils.receiveRestApiEvents
import com.google.gson.Gson
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
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
    }


    override suspend fun getSleepRecordingState(identifier: String, timeoutMs: Long): Boolean {
        return withTimeoutOrNull(timeoutMs) {
            observeSleepRecordingState(identifier)
                .filter { it.isNotEmpty() }
                .take(1)
                .map { it.last() }
                .let { flow ->
                    var result = false
                    flow.collect { result = it }
                    result
                }
        } ?: throw PolarTimeoutException("getSleepRecordingState timed out after ${timeoutMs}ms")
    }

    override fun observeSleepRecordingState(identifier: String): Flow<Array<Boolean>> {
        return channelFlow {
            val deviceType = fetchSession(identifier, listener)?.polarDeviceType
                ?: throw PolarServiceNotAvailable()
            if (!BlePolarDeviceCapabilitiesUtility.isActivityDataSupported(deviceType)) {
                throw PolarServiceNotAvailable()
            }
            val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
            val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
                ?: throw PolarServiceNotAvailable()

            val eventJob = launch {
                client.receiveRestApiEvents(identifier = identifier)
                    .map { list ->
                        list.map { jsonString ->
                            Gson().fromJson(jsonString, PolarSleepApiServiceEventPayload::class.java)
                        }.map { it.sleep_recording_state.enabled == 1 }.toTypedArray()
                    }
                    .collect { send(it) }
            }

            pFtpWriteOperation(
                identifier = identifier,
                listener = listener,
                data = "{}".toByteArray(),
                path = "/REST/SLEEP.API?cmd=subscribe&event=sleep_recording_state&details=[enabled]",
                tag = TAG
            )

            eventJob.join()
        }
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

