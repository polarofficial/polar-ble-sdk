// Copyright © 2026 Polar Electro Oy. All rights reserved.
package com.polar.sdk.impl

import com.polar.androidcommunications.api.ble.BleDeviceListener
import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils
import com.polar.sdk.api.PolarTestApi
import com.polar.sdk.api.errors.PolarServiceNotAvailable
import com.polar.sdk.api.model.PolarSpo2TestData
import com.polar.sdk.impl.utils.PolarServiceClientUtils
import com.polar.sdk.impl.utils.PolarTestUtils
import java.time.LocalDate

/**
 * Implementation of [PolarTestApi].
 *
 * Handles fetching of SPO2 test data from Polar devices via the PFTP file transfer protocol.
 *
 * Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER].
 */
internal class PolarTestApiImpl(
    private val listener: BleDeviceListener
) : PolarTestApi {

    companion object {
        private const val TAG = "PolarTestApiImpl"
    }

    private fun getDatesBetween(startDate: LocalDate, endDate: LocalDate): List<LocalDate> =
        generateSequence(startDate) { it.plusDays(1) }.takeWhile { !it.isAfter(endDate) }.toList()

    override suspend fun getSpo2TestData(
        identifier: String,
        fromDate: LocalDate,
        toDate: LocalDate
    ): List<PolarSpo2TestData> {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        val result = mutableListOf<PolarSpo2TestData>()
        for (date in getDatesBetween(fromDate, toDate)) {
            val entries = PolarTestUtils.readSpo2TestProtoFromDayDirectory(client, date)
            for (entry in entries) {
                try {
                    result.add(PolarTestUtils.mapSpo2TestEntry(entry))
                } catch (error: Throwable) {
                    BleLogger.w(
                        TAG,
                        "getSpo2Test() failed to parse SPO2 proto for date $date time Directory ${entry.timeDirName}, error: $error"
                    )
                }
            }
        }
        return result
    }

    override suspend fun getSpo2Test(
        identifier: String,
        fromDate: LocalDate,
        toDate: LocalDate
    ): List<PolarSpo2TestData> {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        val result = mutableListOf<PolarSpo2TestData>()
        for (date in getDatesBetween(fromDate, toDate)) {
            val entries = PolarTestUtils.readSpo2TestProtoFromDayDirectory(client, date)
            for (entry in entries) {
                try {
                    result.add(PolarTestUtils.mapSpo2TestEntry(entry))
                } catch (error: Throwable) {
                    BleLogger.w(
                        TAG,
                        "getSpo2Test() failed to parse SPO2 proto for date $date timedir ${entry.timeDirName}, error: $error"
                    )
                }
            }
        }
        return result
    }
}

