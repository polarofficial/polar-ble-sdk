package com.polar.sdk.api

import com.polar.sdk.api.model.activity.PolarActiveTimeData
import com.polar.sdk.api.model.activity.PolarDistanceData
import com.polar.sdk.api.model.activity.PolarStepsData
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
}