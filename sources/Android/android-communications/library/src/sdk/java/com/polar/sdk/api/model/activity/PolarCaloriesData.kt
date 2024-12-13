package com.polar.sdk.api.model.activity

import java.util.Date

/**
 * Activity calories data for a given [date].
 * Includes the calories value based on the requested type (e.g., activity, training, or BMR).
 */
data class PolarCaloriesData(val date: Date? = null, val calories: Int? = null)