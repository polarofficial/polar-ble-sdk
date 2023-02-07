//  Copyright © 2022 Polar. All rights reserved.


import Foundation

internal class PolarDataUtils {
    
    static func mapToPmdClientMeasurementType(from polarDataType : PolarDeviceDataType) -> PmdMeasurementType {
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
    
    static func mapToPolarFeature(from pmdMeasurementType : PmdMeasurementType) throws -> PolarDeviceDataType {
        switch(pmdMeasurementType) {
        case .ecg:
            return PolarDeviceDataType.ecg
        case .ppg:
            return PolarDeviceDataType.ppg
        case .acc:
            return PolarDeviceDataType.acc
        case .ppi:
            return PolarDeviceDataType.ppi
        case .gyro:
            return PolarDeviceDataType.gyro
        case .mgn:
            return PolarDeviceDataType.magnetometer
        case .offline_hr:
            return PolarDeviceDataType.hr
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

