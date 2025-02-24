//  Copyright © 2025 Polar. All rights reserved.

import Foundation

/// Polar Peak-to-peak interval data
/// - Parameters:
/// - date: date of the PPi activity data
/// - ppiSamples: PPi samples from sensor as PolarPpiDataSample object
public struct Polar247PPiSamplesData: Codable {
    public let date: Date
    public let ppiSamples: [PolarPpiDataSample]
    
    /// Polar 24/7 PPi data sample
    /// - Parameters:
    ///  - startTime: start time of the sample session
    ///  - triggerType: describes how the measurement was triggered
    ///  - ppiValueList: list of Peak-to-Peak interval values in the sample session
    ///  - ppiErrorEstimateList: list of error estimate  values in the sample session
    ///  - statusList: status values in the sample session
    public struct PolarPpiDataSample: Codable {
        public let startTime: String!
        public let triggerType: PPiSampleTriggerType!
        public let ppiValueList: [Int32]!
        public let ppiErrorEstimateList: [Int32]!
        public let statusList: [PPiSampleStatus]!
    }

    public enum PPiSampleTriggerType: String, Codable {
        case TRIGGER_TYPE_UNDEFINED = "TRIGGER_TYPE_UNDEFINED"
        case TRIGGER_TYPE_AUTOMATIC = "TRIGGER_TYPE_AUTOMATIC"
        case TRIGGER_TYPE_MANUAL = "TRIGGER_TYPE_MANUAL"

        static func getByValue(value: Data_PbPpIntervalAutoSamples.PbPpIntervalRecordingTriggerType) -> PPiSampleTriggerType {

            switch value {
            case .ppiTriggerTypeUndefined: return .TRIGGER_TYPE_UNDEFINED
            case .ppiTriggerTypeAutomatic: return .TRIGGER_TYPE_AUTOMATIC
            case .ppiTriggerTypeManual: return .TRIGGER_TYPE_MANUAL
            }
        }
    }

    public enum SkinContact: String, Codable {
        case NO_SKIN_CONTACT = "NO_SKIN_CONTACT"
        case SKIN_CONTACT_DETECTED = "SKIN_CONTACT_DETECTED"

        static func getByValue(value: Int) -> SkinContact? {

            switch value {
            case 0: return .NO_SKIN_CONTACT
            case 1: return .SKIN_CONTACT_DETECTED
            default:
                return nil
            }
        }
    }

    public enum Movement: String, Codable {
        case NO_MOVING_DETECTED = "NO_MOVING_DETECTED"
        case MOVING_DETECTED = "MOVING_DETECTED"

        static func getByValue(value: Int) -> Movement? {

            switch value {
            case 0: return .NO_MOVING_DETECTED
            case 1: return .MOVING_DETECTED
            default:
                return nil
            }
        }
    }

    public enum IntervalStatus: String, Codable {
        case INTERVAL_IS_ONLINE = "INTERVAL_IS_ONLINE"
        case INTERVAL_DENOTES_OFFLINE_PERIOD = "INTERVAL_DENOTES_OFFLINE_PERIOD"
        
        static func getByValue(value: Int) -> IntervalStatus? {

            switch value {
            case 0: return .INTERVAL_IS_ONLINE
            case 1: return .INTERVAL_DENOTES_OFFLINE_PERIOD
            default:
                return nil
            }
        }
    }

    public struct PPiSampleStatus: Codable {
        public var skinContact: SkinContact
        public var movement: Movement
        public var intervalStatus: IntervalStatus

        static func fromStatusByte(byte: UInt32) -> PPiSampleStatus {
            // 32-bit representation of the incoming byte as String
            let binary = String.binaryRepresentation(of: byte)
            return PPiSampleStatus(
                skinContact: SkinContact.getByValue(value: Int(binary[31])!)!,
                movement: Movement.getByValue(value: Int(binary[30])!)!,
                intervalStatus: IntervalStatus.getByValue(value: Int(binary[29])!)!
            )
        }
    }

    static func fromPbPPiDataSamples(ppiData: Data_PbPpIntervalAutoSamples) -> PolarPpiDataSample {

        var ppiSampleStatusList = [PPiSampleStatus]()
        var ppiValueList = [Int32]()
        var ppiErrorEstimateList = [Int32]()
        var previousSample: Int32 = 0

        for sample in ppiData.ppi.ppiDelta {
            let uncompressedSample = previousSample + sample
            ppiValueList.append(uncompressedSample)
            previousSample = uncompressedSample
        }

        previousSample = 0

        for sample in ppiData.ppi.ppiErrorEstimateDelta {
            let uncompressedSample = previousSample + sample
            ppiErrorEstimateList.append(uncompressedSample)
            previousSample = uncompressedSample
        }

        for sample in ppiData.ppi.status {
            ppiSampleStatusList.append(PPiSampleStatus.fromStatusByte(byte: sample))
        }

        return PolarPpiDataSample(
            startTime: PolarTimeUtils.pbTimeToTimeString(ppiData.recordingTime),
            triggerType: PPiSampleTriggerType.getByValue(value: ppiData.triggerType),
            ppiValueList: ppiValueList,
            ppiErrorEstimateList: ppiErrorEstimateList,
            statusList: ppiSampleStatusList
        )
    }
}

extension String {

    static func binaryRepresentation<F: FixedWidthInteger>(of value: F) -> String {

        let binaryString = String(value, radix: 2)

        if value.leadingZeroBitCount > 0 {
            return String(repeating: "0", count: value.leadingZeroBitCount) + binaryString
        }

        return binaryString
    }
}

extension String {

    var length: Int {
        return count
    }

    subscript (i: Int) -> String {
        return self[i ..< i + 1]
    }

    func substring(fromIndex: Int) -> String {
        return self[min(fromIndex, length) ..< length]
    }

    func substring(toIndex: Int) -> String {
        return self[0 ..< max(0, toIndex)]
    }

    subscript (r: Range<Int>) -> String {
        let range = Range(uncheckedBounds: (lower: max(0, min(length, r.lowerBound)),
                                            upper: min(length, max(0, r.upperBound))))
        let start = index(startIndex, offsetBy: range.lowerBound)
        let end = index(start, offsetBy: range.upperBound - range.lowerBound)
        return String(self[start ..< end])
    }
}
