package com.polar.sdk.api

import com.polar.sdk.api.model.sleep.PolarSleepData
import io.reactivex.rxjava3.core.Single
import java.time.LocalDate

/**
 * Polar sleep API.
 * Requires feature FEATURE_POLAR_SLEEP_DATA
 */
interface PolarSleepApi {

    /**
     * Get sleep stages and duration for a given period.
     *
     * @param identifier The Polar device ID or BT address.
     * @param fromDate The starting date of the period to retrieve sleep data from.
     * @param toDate The ending date of the period to retrieve sleep data from.
     * @return A [Single] emitting a list of [PolarSleepData] representing the sleep data for the specified period.
     */
    fun getSleep(identifier: String, fromDate: LocalDate, toDate: LocalDate): Single<List<PolarSleepData>>
}