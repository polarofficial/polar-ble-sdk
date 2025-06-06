package com.polar.sdk.api.model.activity

import java.time.LocalDate

/**
 * [steps] count for given [date].
 */
data class PolarStepsData(val date: LocalDate? = null, val steps: Int? = null)