// Copyright © 2026 Polar Electro Oy. All rights reserved.
package com.polar.sdk.impl

import com.polar.androidcommunications.api.ble.BleDeviceListener
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils
import com.polar.sdk.api.PolarActivityApi
import com.polar.sdk.api.PolarTemperatureApi
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.errors.PolarServiceNotAvailable
import com.polar.sdk.api.model.PolarSkinTemperatureData
import com.polar.sdk.api.model.activity.Polar247HrSamplesData
import com.polar.sdk.api.model.activity.Polar247PPiSamplesData
import com.polar.sdk.api.model.activity.PolarActiveTimeData
import com.polar.sdk.api.model.activity.PolarActivitySamplesDayData
import com.polar.sdk.api.model.activity.PolarCaloriesData
import com.polar.sdk.api.model.activity.PolarDailySummaryData
import com.polar.sdk.api.model.activity.PolarDistanceData
import com.polar.sdk.api.model.activity.PolarStepsData
import com.polar.sdk.api.model.sleep.PolarNightlyRechargeData
import com.polar.sdk.impl.utils.CaloriesType
import com.polar.sdk.impl.utils.PolarActivityUtils
import com.polar.sdk.impl.utils.PolarAutomaticSamplesUtils
import com.polar.sdk.impl.utils.PolarNightlyRechargeUtils
import com.polar.sdk.impl.utils.PolarServiceClientUtils
import com.polar.sdk.impl.utils.PolarSkinTemperatureUtils
import java.time.LocalDate

/**
 * Implementation of [PolarActivityApi]

 * Handles fetching of activity data (steps, calories, heart rate samples, nightly recharge, etc.)
 * and skin temperature data from Polar devices via the PFTP file transfer protocol.
 *
 * Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA] for activity methods
 * and [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_TEMPERATURE_DATA] for temperature methods.
 */
internal class PolarActivityApiImpl(
    private val listener: BleDeviceListener
) : PolarActivityApi, PolarTemperatureApi {

    companion object {
        private const val TAG = "PolarActivityApiImpl"
    }

    private fun validateDateRange(fromDate: LocalDate, toDate: LocalDate) {
        if (toDate.isBefore(fromDate)) {
            throw PolarInvalidArgument("toDate $toDate must not be before fromDate $fromDate")
        }
    }

    private fun getDatesBetween(startDate: LocalDate, endDate: LocalDate): List<LocalDate> =
        generateSequence(startDate) { it.plusDays(1) }.takeWhile { !it.isAfter(endDate) }.toList()


    override suspend fun get247HrSamples(
        identifier: String,
        fromDate: LocalDate,
        toDate: LocalDate
    ): List<Polar247HrSamplesData> {
        validateDateRange(fromDate, toDate)
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        return PolarAutomaticSamplesUtils.read247HrSamples(client, fromDate, toDate)
    }

    override suspend fun get247PPiSamples(
        identifier: String,
        fromDate: LocalDate,
        toDate: LocalDate
    ): List<Polar247PPiSamplesData> {
        validateDateRange(fromDate, toDate)
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        return PolarAutomaticSamplesUtils.read247PPiSamples(client, fromDate, toDate)
    }

    override suspend fun getNightlyRecharge(
        identifier: String,
        fromDate: LocalDate,
        toDate: LocalDate
    ): List<PolarNightlyRechargeData> {
        validateDateRange(fromDate, toDate)
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        val result = mutableListOf<PolarNightlyRechargeData>()
        for (date in getDatesBetween(fromDate, toDate)) {
            PolarNightlyRechargeUtils.readNightlyRechargeData(client, date)?.let { result.add(it) }
        }
        return result
    }

    override suspend fun getSteps(
        identifier: String,
        fromDate: LocalDate,
        toDate: LocalDate
    ): List<PolarStepsData> {
        validateDateRange(fromDate, toDate)
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        return getDatesBetween(fromDate, toDate).map { date ->
            PolarStepsData(date, PolarActivityUtils.readStepsFromDayDirectory(client, date))
        }
    }

    override suspend fun getActivitySampleData(
        identifier: String,
        fromDate: LocalDate,
        toDate: LocalDate
    ): List<PolarActivitySamplesDayData> {
        validateDateRange(fromDate, toDate)
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        return getDatesBetween(fromDate, toDate).map { date ->
            PolarActivityUtils.readActivitySamplesDataFromDayDirectory(client, date)
        }
    }

    override suspend fun getDailySummaryData(
        identifier: String,
        fromDate: LocalDate,
        toDate: LocalDate
    ): List<PolarDailySummaryData> {
        validateDateRange(fromDate, toDate)
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        return getDatesBetween(fromDate, toDate).mapNotNull { date ->
            PolarActivityUtils.readDailySummaryDataFromDayDirectory(client, date)
        }
    }

    override suspend fun getDistance(
        identifier: String,
        fromDate: LocalDate,
        toDate: LocalDate
    ): List<PolarDistanceData> {
        validateDateRange(fromDate, toDate)
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        return getDatesBetween(fromDate, toDate).map { date ->
            PolarDistanceData(date, PolarActivityUtils.readDistanceFromDayDirectory(client, date))
        }
    }

    override suspend fun getCalories(
        identifier: String,
        fromDate: LocalDate,
        toDate: LocalDate,
        caloriesType: CaloriesType
    ): List<PolarCaloriesData> {
        validateDateRange(fromDate, toDate)
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        return getDatesBetween(fromDate, toDate).map { date ->
            PolarCaloriesData(date, PolarActivityUtils.readSpecificCaloriesFromDayDirectory(client, date, caloriesType))
        }
    }

    override suspend fun getActiveTime(
        identifier: String,
        fromDate: LocalDate,
        toDate: LocalDate
    ): List<PolarActiveTimeData> {
        validateDateRange(fromDate, toDate)
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        return getDatesBetween(fromDate, toDate).map { date ->
            PolarActivityUtils.readActiveTimeFromDayDirectory(client, date)
        }
    }


    override suspend fun getSkinTemperature(
        identifier: String,
        fromDate: LocalDate,
        toDate: LocalDate
    ): List<PolarSkinTemperatureData> {
        validateDateRange(fromDate, toDate)
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        val result = mutableListOf<PolarSkinTemperatureData>()
        for (date in getDatesBetween(fromDate, toDate)) {
            PolarSkinTemperatureUtils.readSkinTemperatureDataFromDayDirectory(client, date)
                ?.let { result.add(PolarSkinTemperatureData(date, it)) }
        }
        return result
    }

}