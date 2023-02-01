import Foundation

public enum PmdMeasurementType: UInt8, CaseIterable {
    case ecg = 0
    case ppg = 1
    case acc = 2
    case ppi = 3
    case gyro = 5
    case mgn = 6
    case sdkMode = 9
    case location = 10
    case pressure = 11
    case temperature = 12
    case offline_recording = 13
    case offline_hr = 14
    case unknown_type = 0x3f
    
    private static let MEASUREMENT_BIT_MASK: UInt8 = 0x3F
    
    func isDataType() -> Bool {
        switch(self) {
        case .sdkMode, .offline_recording, .unknown_type:
            return false
        default :
            return true
        }
    }
    
    static func fromId(id: UInt8) -> PmdMeasurementType {
        for type in PmdMeasurementType.allCases {
            if (type.rawValue == (id & PmdMeasurementType.MEASUREMENT_BIT_MASK)) {
                return type
            }
        }
        return .unknown_type
    }
    
    static func fromByteArray(_ data: Data) -> Set<PmdMeasurementType> {
        var measurementTypes:Set<PmdMeasurementType> = []
        if (data[1] & 0x01 != 0) {
            measurementTypes.insert(PmdMeasurementType.ecg)
        }
        if (data[1] & 0x02 != 0) {
            measurementTypes.insert(PmdMeasurementType.ppg)
        }
        if (data[1] & 0x04 != 0) {
            measurementTypes.insert(PmdMeasurementType.acc)
        }
        if (data[1] & 0x08 != 0) {
            measurementTypes.insert(PmdMeasurementType.ppi)
        }
        if (data[1] & 0x20 != 0) {
            measurementTypes.insert(PmdMeasurementType.gyro)
        }
        if (data[1] & 0x40 != 0) {
            measurementTypes.insert(PmdMeasurementType.mgn)
        }
        if (data[2] & 0x04 != 0) {
            measurementTypes.insert(PmdMeasurementType.location)
        }
        if (data[2] & 0x08 != 0) {
            measurementTypes.insert(PmdMeasurementType.pressure)
        }
        if (data[2] & 0x10 != 0) {
            measurementTypes.insert(PmdMeasurementType.temperature)
        }
        if (data[2] & 0x40 != 0) {
            measurementTypes.insert(PmdMeasurementType.offline_hr)
        }
        
        if (data[2] & 0x02 != 0) {
            measurementTypes.insert(PmdMeasurementType.sdkMode)
        }
        if (data[2] & 0x20 != 0) {
            measurementTypes.insert(PmdMeasurementType.offline_recording)
        }
        
        return measurementTypes
    }
}
