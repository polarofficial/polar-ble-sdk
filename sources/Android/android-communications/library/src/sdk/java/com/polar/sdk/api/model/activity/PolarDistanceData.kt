package com.polar.sdk.api.model.activity

import java.time.LocalDate

/**
 * Activity distance in meters for given [date].
 */
data class PolarDistanceData(val date: LocalDate, val distanceMeters: Float)