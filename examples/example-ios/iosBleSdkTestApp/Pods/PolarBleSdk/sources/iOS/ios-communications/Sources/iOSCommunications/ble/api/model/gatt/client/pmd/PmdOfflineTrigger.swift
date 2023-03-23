//  Copyright © 2023 Polar. All rights reserved.

import Foundation

public enum PmdOfflineRecTriggerMode: UInt8, CaseIterable {
    case disabled = 0
    case systemStart = 1
    case exerciseStart = 2
    
    static func fromResponse(byte: UInt8) throws -> PmdOfflineRecTriggerMode {
        if let pmdTriggerMode = PmdOfflineRecTriggerMode(rawValue: byte) {
            return pmdTriggerMode
        } else {
            throw BleGattException.gattDataError(description: "PmdOfflineRecTriggerMode parsing failed no matching status for byte \(String(format: "0x%02X", byte))")
        }
    }
}

public enum PmdOfflineRecTriggerStatus: UInt8, CaseIterable {
    case disabled = 0
    case enabled = 1
    
    static func fromResponse(byte: UInt8) throws -> PmdOfflineRecTriggerStatus {
        if let pmdTriggerStatus = PmdOfflineRecTriggerStatus(rawValue: byte) {
            return pmdTriggerStatus
        } else {
            throw BleGattException.gattDataError(description: "PmdOfflineTriggerStatus parsing failed no matching status for byte \(String(format: "0x%02X", byte))")
        }
    }
}
public struct PmdOfflineTrigger {
    let triggerMode: PmdOfflineRecTriggerMode
    let triggers: [PmdMeasurementType : (status: PmdOfflineRecTriggerStatus, setting: PmdSetting?)]
    
    private static let TRIGGER_MODE_INDEX = 0
    private static let TRIGGER_MODE_FIELD_LENGTH = 1
    private static let TRIGGER_STATUS_FIELD_LENGTH = 1
    private static let TRIGGER_MEASUREMENT_TYPE_FIELD_LENGTH = 1
    private static let TRIGGER_MEASUREMENT_SETTINGS_SIZE_FIELD_LENGTH = 1
    
    static func fromResponse(data: Data) throws -> PmdOfflineTrigger {
        BleLogger.trace("parse offline trigger from response")
        var offset = TRIGGER_MODE_INDEX
        
        let triggerMode = try PmdOfflineRecTriggerMode.fromResponse(byte: data[TRIGGER_MODE_INDEX])
        offset += TRIGGER_MODE_FIELD_LENGTH
        
        var triggers = [PmdMeasurementType : (status: PmdOfflineRecTriggerStatus, setting: PmdSetting?)]()
        
        while (offset < data.count) {
            let triggerStatus = try PmdOfflineRecTriggerStatus.fromResponse(byte: data[offset])
            offset += TRIGGER_STATUS_FIELD_LENGTH
            
            let triggerMeasurementType = PmdMeasurementType.fromId(id: data[offset])
            offset += TRIGGER_MEASUREMENT_TYPE_FIELD_LENGTH
            
            if (triggerStatus == PmdOfflineRecTriggerStatus.enabled) {
                let triggerSettingsLength = Int(data[offset])
                offset += TRIGGER_MEASUREMENT_SETTINGS_SIZE_FIELD_LENGTH
                let settingBytes = data.subdata(in: offset..<(offset + triggerSettingsLength))
                let pmdSetting: PmdSetting?
                if settingBytes.isEmpty {
                    pmdSetting = nil
                } else {
                    pmdSetting = PmdSetting(settingBytes)
                }
                
                offset += triggerSettingsLength
                triggers[triggerMeasurementType] = (triggerStatus, pmdSetting)
                
            } else {
                triggers[triggerMeasurementType] = (triggerStatus,  nil)
            }
        }
        return PmdOfflineTrigger(triggerMode: triggerMode, triggers: triggers)
    }
}
