//
//  Copyright © 2024 Polar. All rights reserved.
//

import Foundation

public struct PolarNightlyRechargeData: Encodable {
    // Date and time of the result was created
    public let createdTimestamp: Date

    // Date and time when the file was last modified
    public let modifiedTimestamp: Date?

    // The limit of ansStatus is from -15.7068 to 15.7068. A value over 0 represents higher parasympathetic activity than usual (and lower sympathetic), and a value below 0 represents lower parasympathetic activity than usual (and higher sympathetic)
    public let ansStatus: Float?

    // The combination of normalized ANS status and normalized Sleep Score and Sleep Metric. Between 1 to 6.
    public let recoveryIndicator: Int?

    // Indicates the level of combination of ANS and Sleep inside the recoveryIndicator class. 0 indicates the bottom of the class, and ~100 indicates almost the next level.
    public let recoveryIndicatorSubLevel: Int?

    // Rating of ans status on a scale of 1-5 compared to own usual level. (5 = best, 1 = worst)
    public let ansRate: Int?

    // Rating of sleep score status on a scale of 1-5 compared to own usual level (5 = best, 1 = worst)
    public let scoreRateObsolete: Int?

    // Mean of the HR (after 0.5h from sleep start to 4.5h after sleep start hr) samples to beat interval time. (ms)
    public let meanNightlyRecoveryRRI: Int?

    // Mean of the PPI (after 0.5h from sleep start to 4.5h after sleep start PPI) calculated RMSSD values. (ms)
    public let meanNightlyRecoveryRMSSD: Int?

    // Mean of the respiration interval (after 0.5h from sleep start to 4.5h after sleep start) samples. (ms)
    public let meanNightlyRecoveryRespirationInterval: Int?

    // The mean RRI from the baseline calculation. (ms)
    public let meanBaselineRRI: Int?

    // The standard deviation of RRI from baseline calculation.
    public let sdBaselineRRI: Int?

    // The mean RMSSD from the baseline calculation. (ms)
    public let meanBaselineRMSSD: Int?

    // The standard deviation of RMSSD from baseline calculation.
    public let sdBaselineRMSSD: Int?

    // Mean Respiration Interval from the baseline calculation. (ms)
    public let meanBaselineRespirationInterval: Int?

    // The standard deviation of Respiration Interval from baseline calculation.
    public let sdBaselineRespirationInterval: Int?

    // Chosen sleep tip for the user.
    public let sleepTip: String?

    // Chosen vitality tip for the user.
    public let vitalityTip: String?

    // Chosen exercise tip for the user.
    public let exerciseTip: String?

    // Date for which the sleep result and nightly recovery result is for
    public let sleepResultDate: Date?
}