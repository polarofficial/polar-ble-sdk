//
//  Copyright © 2025 Polar. All rights reserved.
//

import Foundation

public class PolarSkinTemperatureData {

    public enum SkinTemperatureMeasurementType: String, Codable {
        case TM_UNKNOWN = "TM_UNKNOWN"
        case TM_SKIN_TEMPERATURE = "TM_SKIN_TEMPERATURE"
        case TM_CORE_TEMPERATURE = "TM_CORE_TEMPERATURE"

        static func getByValue(value: TemperatureMeasurementType) -> SkinTemperatureMeasurementType {

            switch value {
            case .tmUnknown: return .TM_UNKNOWN
            case .tmSkinTemperature: return .TM_SKIN_TEMPERATURE
            case .tmCoreTemperature: return .TM_CORE_TEMPERATURE
            default: return .TM_UNKNOWN
            }
        }
    }

    /**
     * SL_DISTAL, sensor is located away from torso, for example on wrist
     * SL_CORE, sensor is located on torso, for example on chest
     */
    public enum SkinTemperatureSensorLocation: String, Codable {
        case SL_UNKNOWN = "SL_UNKNOWN"
        case SL_DISTAL = "SL_DISTAL"
        case SL_PROXIMAL = "SL_PROXIMAL"

        static func getByValue(value: SensorLocation) -> SkinTemperatureSensorLocation {

            switch value {
            case .slUnknown: return .SL_UNKNOWN
            case.slDistal : return .SL_DISTAL
            case .slProximal: return .SL_PROXIMAL
            default: return .SL_UNKNOWN
            }
        }
    }

    public struct PolarSkinTemperatureDataSample: Codable {
        public let recordingTimeDeltaMs: UInt64!
        public let temperature: Float!
    }

    public struct PolarSkinTemperatureResult: Codable {
        public let date: Date!
        public let sensorLocation: SkinTemperatureSensorLocation!
        public let measurementType: SkinTemperatureMeasurementType!
        public let skinTemperatureList: [PolarSkinTemperatureDataSample]?
    }

    static func fromPbTemperatureMeasurementSamples(pbTemperatureMeasurementData: [Data_TemperatureMeasurementSample]) -> [PolarSkinTemperatureDataSample] {

        var skinTemperatureSampleList: [PolarSkinTemperatureDataSample] = Array()

        for sample in pbTemperatureMeasurementData {
            skinTemperatureSampleList.append(
                PolarSkinTemperatureDataSample(
                    recordingTimeDeltaMs: sample.recordingTimeDeltaMilliseconds, temperature: sample.temperatureCelsius
                )
            )
        }
        return skinTemperatureSampleList
    }
}
