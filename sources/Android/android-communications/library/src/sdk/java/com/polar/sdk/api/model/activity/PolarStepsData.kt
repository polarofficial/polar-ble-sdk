package com.polar.sdk.api.model.activity

import java.util.Date

/**
 * [steps] count for given [date].
 */
data class PolarStepsData(val date: Date, val steps: Int)