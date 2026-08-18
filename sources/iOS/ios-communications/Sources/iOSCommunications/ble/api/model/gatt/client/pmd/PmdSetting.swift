import Foundation

public struct PmdSetting: @unchecked Sendable {
    public enum PmdSettingType: UInt8, CaseIterable {
        case sampleRate = 0
        case resolution
        case range
        case rangeMilliUnit
        case channels
        case factor
        case security
        case derivedMeasurementMethod = 7
        case sourceMeasurementType = 8
        case sourceMeasurementSampleRate = 9
        case sourceMeasurementRange = 10
        case derivedMeasurementTimeWindow = 11
        case derivedMeasurementSettingsGroupId = 12
        case unknown = 0xff
    }
    
    static let mapTypeToFieldSize: [PmdSettingType: Int] = [
        .sampleRate: 2,
        .resolution: 2,
        .range: 2,
        .rangeMilliUnit: 4,
        .channels: 1,
        .factor: 4,
        .derivedMeasurementMethod: 1,       // 1 byte per method ID
        .sourceMeasurementType: 1,
        .sourceMeasurementSampleRate: 2,
        .sourceMeasurementRange: 4,         // milliunit range same width as rangeMilliUnit
        .derivedMeasurementTimeWindow: 4,
        .derivedMeasurementSettingsGroupId: 1,
    ]

    public var settings = [PmdSettingType : Set<UInt32>]()
    public var selected = [PmdSettingType : UInt32]()
    
    public init(_ selected: [PmdSettingType : UInt32]){
        self.selected = selected
    }
    
    public init(_ data: Data) throws {
        self.settings = try PmdSetting.parsePmdSettingsData(data)
        self.selected = settings.reduce(into: [:]) { (result, arg1) in
            let (key, value) = arg1
            result[PmdSetting.PmdSettingType(rawValue: UInt8(key.rawValue)) ?? PmdSetting.PmdSettingType.unknown] = value.max() ?? 0
        }
    }
    
    static func parsePmdSettingsData(_ data: Data) throws -> [PmdSettingType : Set<UInt32>] {
        var offset = 0
        var settings = [PmdSettingType : Set<UInt32>]()
        while (offset+2) < data.count {
            let type = PmdSettingType(rawValue: data[offset]) ?? .unknown
            offset += 1
            let count = Int(data[offset])
            offset += 1
            let advanceStep = mapTypeToFieldSize[type] ?? data.count
            settings[type] = try Set(stride(from: offset, to: offset + (count*advanceStep), by: advanceStep).map { (start) -> UInt32 in
                if (start.advanced(by: advanceStep) <= data.count ) {
                    let value = data.subdata(in: start..<start.advanced(by: advanceStep))
                    offset += advanceStep
                    return BlePmdClient.arrayToUInt(value, offset: 0, size: advanceStep)
                } else {
                    throw BlePmdError.invalidPMDData(description: "Broken PMD settings data.")
                }
            })
        }
        return settings
    }
    
    mutating func updatePmdSettingsFromStartResponse(_ data: Data) throws {
        let settingsFromStartResponse = try PmdSetting.parsePmdSettingsData(data)
        if let factor = settingsFromStartResponse[PmdSettingType.factor], let value = factor.first {
            selected[PmdSettingType.factor] = value
        }
    }
    
    public func serialize() -> Data {
        return selected.reduce(into: NSMutableData()) { (result, entry) in
            // factor and sourceMeasurementRange are response-only; do not include in request
            if entry.key == .factor || entry.key == .sourceMeasurementRange {
                return
            }

            // DERIVED_MEASUREMENT_METHOD: value is a bitmask (bit N = method N selected).
            // Expand to individual 1-byte method IDs on the wire per SAGRFC 85.15:
            //   e.g. bitmask=1 (bit 0) → [type=7][count=1][0x00]
            //        bitmask=3 (bits 0+1) → [type=7][count=2][0x00][0x01]
            if entry.key == .derivedMeasurementMethod {
                let bitmask = Int(entry.value)
                let methodIds = (0..<16).filter { (bitmask >> $0) & 1 == 1 }
                guard !methodIds.isEmpty else { return }
                result.append([entry.key.rawValue], length: 1)
                result.append([UInt8(methodIds.count)], length: 1)
                for id in methodIds { result.append([UInt8(id)], length: 1) }
                return
            }

            result.append([UInt8(entry.key.rawValue)], length: 1)
            result.append([0x01], length: 1)
            let fieldSize = UInt32(PmdSetting.mapTypeToFieldSize[entry.key] ?? 0)
            for i in 0..<fieldSize {
                result.append([UInt8((entry.value >> (i*8)) & 0xff)], length: 1)
            }
        } as Data
    }
}
