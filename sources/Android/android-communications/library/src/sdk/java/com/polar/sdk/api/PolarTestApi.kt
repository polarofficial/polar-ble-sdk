// Copyright © 2026 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api

import com.polar.sdk.api.model.PolarSpo2TestData
import java.time.LocalDate

/**
 * Polar SPO2 test API.
 *
 * Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER]
 */
interface PolarTestApi {

    /**
     * Get SPO2 test data for a given period.
     *
     * Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SPO2_TEST_DATA].
     *
     * @param identifier The Polar device ID or BT address.
     * @param fromDate The starting date of the period to retrieve data from.
     * @param toDate The ending date of the period to retrieve data from.
     * @return A list of [PolarSpo2TestData] representing the SPO2 test data for the specified period.
     */
    suspend fun getSpo2Test(identifier: String, fromDate: LocalDate, toDate: LocalDate): List<PolarSpo2TestData>
}

