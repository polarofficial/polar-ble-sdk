package com.polar.sdk.api.model.activity

import java.util.Date

/**
 * Activity distance in meters for given [date].
 */
data class PolarDistanceData(val date: Date, val distanceMeters: Float)