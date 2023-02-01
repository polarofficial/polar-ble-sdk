//  Copyright © 2022 Polar. All rights reserved.


import Foundation

internal class PolarDataUtils {
    
    static func mapToPmdClientMeasurementType(from polarDataType : DeviceStreamingFeature) -> PmdMeasurementType {
        switch(polarDataType) {
        case .ecg:
            return PmdMeasurementType.ecg
        case .acc:
            return PmdMeasurementType.acc
        case .ppg:
            return PmdMeasurementType.ppg
        case .ppi:
            return PmdMeasurementType.ppi
        case .gyro:
            return PmdMeasurementType.gyro
        case .magnetometer:
            return PmdMeasurementType.mgn
        case .hr:
            return PmdMeasurementType.offline_hr
        }
    }
    
    static func mapToPolarFeature(from pmdMeasurementType : PmdMeasurementType) throws -> DeviceStreamingFeature {
        switch(pmdMeasurementType) {
        case .ecg:
            return DeviceStreamingFeature.ecg
        case .ppg:
            return DeviceStreamingFeature.ppg
        case .acc:
            return DeviceStreamingFeature.acc
        case .ppi:
            return DeviceStreamingFeature.ppi
        case .gyro:
            return DeviceStreamingFeature.gyro
        case .mgn:
            return DeviceStreamingFeature.magnetometer
        case .offline_hr:
            return DeviceStreamingFeature.hr
        default:
            throw PolarErrors.polarBleSdkInternalException(description: "Error when map measurement type \(pmdMeasurementType) to Polar feature" )
        }
    }
    
    static func mapToPmdSecret(from polarSecret: PolarRecordingSecret) throws -> PmdSecret {
        return try PmdSecret(
            strategy: PmdSecret.SecurityStrategy.aes128,
            key: polarSecret.key
        )
    }
}

