package com.polar.sdk.api

import com.polar.sdk.api.model.activity.Polar247HrSamplesData
import com.polar.sdk.api.model.activity.Polar247PPiSamplesData
import com.polar.sdk.api.model.activity.PolarActiveTimeData
import com.polar.sdk.api.model.activity.PolarCaloriesData
import com.polar.sdk.api.model.activity.PolarDistanceData
import com.polar.sdk.api.model.activity.PolarStepsData
import com.polar.sdk.api.model.sleep.PolarNightlyRechargeData
import com.polar.sdk.impl.utils.CaloriesType
import io.reactivex.rxjava3.core.Single
import java.util.Date

/**
 * Polar activity API.
 * Requires feature FEATURE_POLAR_ACTIVITY_DATA
 */
interface PolarActivityApi {

    /**
     * Get steps for a given period.
     *
     * @param identifier The Polar device ID or BT address.
     * @param fromDate The starting date of the period to retrieve steps from.
     * @param toDate The ending date of the period to retrieve steps from.
     * @return A [Single] emitting a list of [PolarStepsData] representing the steps data for the specified period.
     */
    fun getSteps(identifier: String, fromDate: Date, toDate: Date): Single<List<PolarStepsData>>

    /**
     * Get distance for a given period.
     *
     * @param identifier The Polar device ID or BT address.
     * @param fromDate The starting date of the period to retrieve distance from.
     * @param toDate The ending date of the period to retrieve distance from.
     * @return A [Single] emitting a list of [PolarDistanceData] representing the distance data for the specified period.
     */
    fun getDistance(identifier: String, fromDate: Date, toDate: Date): Single<List<PolarDistanceData>>

    /**
     * Get active time for a given period.
     *
     * @param identifier The Polar device ID or BT address.
     * @param fromDate The starting date of the period to retrieve active time from.
     * @param toDate The ending date of the period to retrieve active time from.
     * @return A [Single] emitting a list of [PolarActiveTimeData] representing the active time data for the specified period.
     */
    fun getActiveTime(identifier: String, fromDate: Date, toDate: Date): Single<List<PolarActiveTimeData>>

    /**
     * Get specific calories type for a given period.
     *
     * @param identifier The Polar device ID or BT address.
     * @param fromDate The starting date of the period to retrieve calories data from.
     * @param toDate The ending date of the period to retrieve calories data from.
     * @param caloriesType The type of calories data to retrieve (e.g., ACTIVITY, TRAINING, BMR).
     * @return A [Single] emitting a list of [PolarCaloriesData] representing the calories data for the specified period.
     */
    fun getCalories(identifier: String, fromDate: Date, toDate: Date, caloriesType: CaloriesType): Single<List<PolarCaloriesData>>

    /**
     * Get 24/7 heart rate samples for a given period.
     *
     * @param identifier The Polar device ID or BT address.
     * @param fromDate The starting date of the period to retrieve heart rate samples from.
     * @param toDate The ending date of the period to retrieve heart rate samples from.
     * @return A [Single] emitting a list of [Polar247HrSamplesData] representing the heart rate samples for the specified period.
     */
    fun get247HrSamples(identifier: String, fromDate: Date, toDate: Date): Single<List<Polar247HrSamplesData>>

    /**
     * Get nightly recharge for a given period.
     *
     * @param identifier The Polar device ID or BT address.
     * @param fromDate The starting date of the period to retrieve nightly recharge from.
     * @param toDate The ending date of the period to retrieve nightly recharge from.
     * @return A [Single] emitting a list of [PolarNightlyRechargeData] representing the nightly recharge data for the specified period.
     */
    fun getNightlyRecharge(identifier: String, fromDate: Date, toDate: Date): Single<List<PolarNightlyRechargeData>>

    /**
     * Load 24/7 PPi data from a device for a given period.
     *
     * @param identifier, Polar device ID or BT address
     * @param fromDate The starting date of the period to retrieve 24/7 PPi data from.
     * @param toDate The ending date of the period to retrieve 24/7 PPi data from.
     * @return A [Single] emitting a list of [Polar247PPiSamplesData] representing the 24/7 PPi data for the specified period.
     */
    abstract fun get247PPiSamples(identifier: String, fromDate: Date, toDate: Date): Single<List<Polar247PPiSamplesData>>
}