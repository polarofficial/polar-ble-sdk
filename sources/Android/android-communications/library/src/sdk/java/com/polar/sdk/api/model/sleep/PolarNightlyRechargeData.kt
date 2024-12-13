package com.polar.sdk.api.model.sleep

import java.time.LocalDateTime
import java.util.Date

data class PolarNightlyRechargeData(
        // Date and time of the result was created
        val createdTimestamp: LocalDateTime,

        // Date and time when the file was last modified
        val modifiedTimestamp: LocalDateTime?,

        // The limit of ansStatus is from -15.7068 to 15.7068. A value over 0 represents higher parasympathetic activity than usual (and lower sympathetic), and a value below 0 represents lower parasympathetic activity than usual (and higher sympathetic)
        val ansStatus: Float?,

        // The combination of normalized ANS status and normalized Sleep Score and Sleep Metric. Between 1 to 6.
        val recoveryIndicator: Int?,

        // Indicates the level of combination of ANS and Sleep inside the recoveryIndicator class. 0 indicates the bottom of the class, and ~100 indicates almost the next level.
        val recoveryIndicatorSubLevel: Int?,

        // Rating of ans status on a scale of 1-5 compared to own usual level. (5 = best, 1 = worst)
        val ansRate: Int?,

        // Rating of sleep score status on a scale of 1-5 compared to own usual level (5 = best, 1 = worst)
        val scoreRateObsolete: Int?,

        // Mean of the HR (after 0.5h from sleep start to 4.5h after sleep start hr) samples to beat interval time. (ms)
        val meanNightlyRecoveryRRI: Int?,

        // Mean of the PPI (after 0.5h from sleep start to 4.5h after sleep start PPI) calculated RMSSD values. (ms)
        val meanNightlyRecoveryRMSSD: Int?,

        // Mean of the respiration interval (after 0.5h from sleep start to 4.5h after sleep start) samples. (ms)
        val meanNightlyRecoveryRespirationInterval: Int?,

        // The mean RRI from the baseline calculation. (ms)
        val meanBaselineRRI: Int?,

        // The standard deviation of RRI from baseline calculation.
        val sdBaselineRRI: Int?,

        // The mean RMSSD from the baseline calculation. (ms)
        val meanBaselineRMSSD: Int?,

        // The standard deviation of RMSSD from baseline calculation.
        val sdBaselineRMSSD: Int?,

        // Mean Respiration Interval from the baseline calculation. (ms)
        val meanBaselineRespirationInterval: Int?,

        // The standard deviation of Respiration Interval from baseline calculation.
        val sdBaselineRespirationInterval: Int?,

        // Chosen sleep tip for the user.
        val sleepTip: String?,

        // Chosen vitality tip for the user.
        val vitalityTip: String?,

        // Chosen exercise tip for the user.
        val exerciseTip: String?,

        // Date for which the sleep result and nightly recovery result is for
        val sleepResultDate: Date?
)