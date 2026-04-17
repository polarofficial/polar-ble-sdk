package com.polar.sdk.api

import com.polar.sdk.api.model.activity.Polar247HrSamplesData
import com.polar.sdk.api.model.activity.Polar247PPiSamplesData
import com.polar.sdk.api.model.activity.PolarActiveTimeData
import com.polar.sdk.api.model.activity.PolarCaloriesData
import com.polar.sdk.api.model.activity.PolarDistanceData
import com.polar.sdk.api.model.activity.PolarStepsData
import com.polar.sdk.api.model.sleep.PolarNightlyRechargeData
import com.polar.sdk.impl.utils.CaloriesType
import com.polar.sdk.api.model.activity.PolarActivitySamplesDayData
import com.polar.sdk.api.model.activity.PolarDailySummaryData
import java.time.LocalDate

/**
 * Polar activity API.
 * Requires feature FEATURE_POLAR_ACTIVITY_DATA
 */
interface PolarActivityApi {

    /**
     * Get steps for a given period. Requires feature [PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA]
     *
     * @param identifier The Polar device ID or BT address.
     * @param fromDate The starting date of the period to retrieve steps from.
     * @param toDate The ending date of the period to retrieve steps from.
     * @return A list of [PolarStepsData] representing the steps data for the specified period.
     */
    suspend fun getSteps(identifier: String, fromDate: LocalDate, toDate: LocalDate): List<PolarStepsData>

    /**
     * Get activity sample data for a given period. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA]
     *
     * @param identifier The Polar device ID or BT address.
     * @param fromDate The starting date of the period to retrieve activity sample data from.
     * @param toDate The ending date of the period to retrieve activity sample data from.
     * @return A list of [PolarActivitySamplesDayData] containing all
     * activity sample data for the given date range.
     */
    suspend fun getActivitySampleData(identifier: String, fromDate: LocalDate, toDate: LocalDate): List<PolarActivitySamplesDayData>

    /**
     * Get daily summary sample data for a given period. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA] Daily summary data is a cumulative sum for the activity per given date.
     *
     * @param identifier The Polar device ID or BT address.
     * @param fromDate The starting date of the period to retrieve daily summary data from.
     * @param toDate The ending date of the period to retrieve daily summary data from.
     * @return A list of [PolarDailySummaryData] containing all
     * daily summary data for the given date range.
     */
    suspend fun getDailySummaryData(identifier: String, fromDate: LocalDate, toDate: LocalDate): List<PolarDailySummaryData>

    /**
     * Get distance for a given period. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA]
     *
     * @param identifier The Polar device ID or BT address.
     * @param fromDate The starting date of the period to retrieve distance from.
     * @param toDate The ending date of the period to retrieve distance from.
     * @return A list of [PolarDistanceData] representing the distance data for the specified period.
     */
    suspend fun getDistance(identifier: String, fromDate: LocalDate, toDate: LocalDate): List<PolarDistanceData>

    /**
     * Get active time for a given period. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA]
     *
     * @param identifier The Polar device ID or BT address.
     * @param fromDate The starting date of the period to retrieve active time from.
     * @param toDate The ending date of the period to retrieve active time from.
     * @return A list of [PolarActiveTimeData] representing the active time data for the specified period.
     */
    suspend fun getActiveTime(identifier: String, fromDate: LocalDate, toDate: LocalDate): List<PolarActiveTimeData>

    /**
     * Get specific calories type for a given period. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA]
     *
     * @param identifier The Polar device ID or BT address.
     * @param fromDate The starting date of the period to retrieve calories data from.
     * @param toDate The ending date of the period to retrieve calories data from.
     * @param caloriesType The type of calories data to retrieve (e.g., ACTIVITY, TRAINING, BMR).
     * @return A list of [PolarCaloriesData] representing the calories data for the specified period.
     */
    suspend fun getCalories(identifier: String, fromDate: LocalDate, toDate: LocalDate, caloriesType: CaloriesType): List<PolarCaloriesData>

    /**
     * Get 24/7 heart rate samples for a given period. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA]
     *
     * @param identifier The Polar device ID or BT address.
     * @param fromDate The starting date of the period to retrieve heart rate samples from.
     * @param toDate The ending date of the period to retrieve heart rate samples from.
     * @return A list of [Polar247HrSamplesData] representing the heart rate samples for the specified period.
     */
    suspend fun get247HrSamples(identifier: String, fromDate: LocalDate, toDate: LocalDate): List<Polar247HrSamplesData>

    /**
     * Get nightly recharge for a given period. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA]
     *
     * @param identifier The Polar device ID or BT address.
     * @param fromDate The starting date of the period to retrieve nightly recharge from.
     * @param toDate The ending date of the period to retrieve nightly recharge from.
     * @return A list of [PolarNightlyRechargeData] representing the nightly recharge data for the specified period.
     */
    suspend fun getNightlyRecharge(identifier: String, fromDate: LocalDate, toDate: LocalDate): List<PolarNightlyRechargeData>

    /**
     * Load 24/7 PPi data from a device for a given period. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA]
     *
     * @param identifier, Polar device ID or BT address
     * @param fromDate The starting date of the period to retrieve 24/7 PPi data from.
     * @param toDate The ending date of the period to retrieve 24/7 PPi data from.
     * @return A list of [Polar247PPiSamplesData] representing the 24/7 PPi data for the specified period.
     */
    suspend fun get247PPiSamples(identifier: String, fromDate: LocalDate, toDate: LocalDate): List<Polar247PPiSamplesData>
}