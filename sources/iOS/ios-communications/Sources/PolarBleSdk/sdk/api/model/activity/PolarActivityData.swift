//  Copyright © 2025 Polar. All rights reserved.

import Foundation

// [PolarActivitySamplesData] data for given [startTime].
public class PolarActivityData: Codable {

    public var samples: PolarActivitySamples?

    public struct PolarActivitySamples: Codable {
        public var startTime: Date
        public var metRecordingInterval: Int
        public var metSamples: [Float]!
        public var stepRecordingInterval: Int
        public var stepSamples: [UInt32]!
        public var activityInfoList: [PolarActivityInfo]
    }

    public struct PolarActivityInfo: Codable {
        public var activityClass: PolarActivityClass
        public var timeStamp: Date
        public var factor: Float
    }

    public enum PolarActivityClass: String, Codable {

        case SLEEP = "SLEEP"
        case SEDENTARY = "SEDENTARY"
        case LIGHT = "LIGHT"
        case CONTINUOUS_MODERATE = "CONTINUOUS_MODERATE"
        case INTERMITTENT_MODERATE = "INTERMITTENT_MODERATE"
        case CONTINUOUS_VIGOROUS = "CONTINUOUS_VIGOROUS"
        case INTERMITTENT_VIGOROUS = "INTERMITTENT_VIGOROUS"
        case NON_WEAR = "NON_WEAR"

        static func getByValue(value: Data_PbActivityInfo.ActivityClass) -> PolarActivityClass {

            switch value {

            case .sedentary:
                return .SEDENTARY
            case .sleep:
                return .SLEEP
            case .light:
                return .LIGHT
            case .nonWear:
                return .NON_WEAR
            case .continuousModerate:
                return .CONTINUOUS_MODERATE
            case .intermittentModerate:
                return .INTERMITTENT_MODERATE
            case .continuousVigorous:
                return .CONTINUOUS_VIGOROUS
            case .intermittentVigorous:
                return .INTERMITTENT_VIGOROUS
            }
        }
    }

    static func parsePbActivityInfoList(activityInfoList: [Data_PbActivityInfo]) throws -> [PolarActivityInfo] {
        var polarActivityInfoList: [PolarActivityData.PolarActivityInfo] = []
        do {
            for activityInfo in activityInfoList {
                polarActivityInfoList.append(
                    PolarActivityData.PolarActivityInfo(
                        activityClass: PolarActivityData.PolarActivityClass.getByValue(value: activityInfo.value),
                        timeStamp: try PolarTimeUtils.pbLocalDateTimeToDate(pbLocalDateTime: activityInfo.timeStamp),
                        factor: activityInfo.factor
                    )
                )
            }
        } catch {
            throw PolarErrors.dateTimeFormatFailed()
        }
        return polarActivityInfoList
    }
}

public struct PolarActivityDayData: Codable {
    public var polarActivityDataList: [PolarActivityData]
}
