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
    
    static func mapToPmdOfflineTrigger(from polarTrigger: PolarOfflineRecordingTrigger) throws -> PmdOfflineTrigger {
        let pmdTriggerMode = mapToPmdOfflineTriggerMode(from: polarTrigger.triggerMode)
        var pmdTriggers = [PmdMeasurementType : (PmdOfflineRecTriggerStatus, PmdSetting?)]()
        
        for trigger in polarTrigger.triggerFeatures {
            let pmdMeasurementType = mapToPmdClientMeasurementType(from: trigger.key)
            let pmdSettings = trigger.value?.map2PmdSetting() ?? nil
            pmdTriggers[pmdMeasurementType] = (PmdOfflineRecTriggerStatus.enabled , pmdSettings)
        }
        return PmdOfflineTrigger(triggerMode: pmdTriggerMode, triggers: pmdTriggers)
    }
    
    private static func mapToPmdOfflineTriggerMode(from polarTriggerMode: PolarOfflineRecordingTriggerMode) -> PmdOfflineRecTriggerMode {
        switch(polarTriggerMode) {
        case .triggerDisabled:
            return PmdOfflineRecTriggerMode.disabled
        case .triggerSystemStart:
            return PmdOfflineRecTriggerMode.systemStart
        case .triggerExerciseStart:
            return PmdOfflineRecTriggerMode.exerciseStart
        }
    }
    
    static func mapToPolarOfflineTrigger(from pmdTrigger: PmdOfflineTrigger) throws -> PolarOfflineRecordingTrigger {
        let triggerMode = mapToPolarOfflineTriggerMode(from: pmdTrigger.triggerMode)
        var polarTriggerSettings = [PolarDeviceDataType: PolarSensorSetting?]()

        for (pmdMeasurementType, triggerStatus) in pmdTrigger.triggers {
            let polarDataType = try PolarDataUtils.mapToPolarFeature(from: pmdMeasurementType)
            if triggerStatus.status == .enabled {
                // Map only the enabled
                let polarSettings = triggerStatus.setting.flatMap {
                    $0.mapToPolarSettings()
                }
                polarTriggerSettings[polarDataType] = polarSettings
            }
        }
        return PolarOfflineRecordingTrigger(
            triggerMode: triggerMode,
            triggerFeatures: polarTriggerSettings
        )
    }
    
    private static func mapToPolarOfflineTriggerMode(from pmdTriggerMode: PmdOfflineRecTriggerMode) -> PolarOfflineRecordingTriggerMode {
        switch pmdTriggerMode {
        case .disabled:
            return .triggerDisabled
        case .systemStart:
            return .triggerSystemStart
        case .exerciseStart:
            return .triggerExerciseStart
        }
    }
}

internal extension PmdSetting {
    func mapToPolarSettings() -> PolarSensorSetting {
        var settings: [PolarSensorSetting.SettingType : Set<UInt32>] = [:]
        for (key, value) in self.settings {
            switch(key) {
            case .sampleRate:
                settings[PolarSensorSetting.SettingType.sampleRate] = value
            case .resolution:
                settings[PolarSensorSetting.SettingType.resolution] = value
            case .range:
                settings[PolarSensorSetting.SettingType.range] = value
            case .rangeMilliUnit:
                settings[PolarSensorSetting.SettingType.rangeMilliunit] = value
            case .channels:
                settings[PolarSensorSetting.SettingType.channels] = value
            default:
                //nop
                break
            }
        }
        return PolarSensorSetting(settings)
    }
}
