// Copyright © 2025 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api

import com.polar.sdk.api.model.PolarSkinTemperatureData
import java.time.LocalDate

/**
 * Polar temperature API.
 *
 * Requires feature [FEATURE_POLAR_TEMPERATURE_DATA]
 *
 */
interface PolarTemperatureApi {

    /**
     * Get skin temperature from a device for a given period. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_TEMPERATURE_DATA]
     *
     * @param identifier The Polar device ID or BT address.
     * @param fromDate The starting date of the period to retrieve skin temperature data from.
     * @param toDate The ending date of the period to retrieve skin temperature data from.
     * @return A list of [PolarSkinTemperatureData] representing the skin
     * temperature data for the specified period.
     * @throws Throwable if the operation fails
     */
    suspend fun getSkinTemperature(identifier: String, fromDate: LocalDate, toDate: LocalDate): List<PolarSkinTemperatureData>
}