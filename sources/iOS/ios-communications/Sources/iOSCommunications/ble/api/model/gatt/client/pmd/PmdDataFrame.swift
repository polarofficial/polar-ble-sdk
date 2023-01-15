
import Foundation

struct PmdDataFrame {
    let measurementType: PmdMeasurementType
    let timeStamp: UInt64
    let frameType: PmdDataFrameType
    let isCompressedFrame: Bool
    let dataContent: Data
    let previousTimeStamp: UInt64
    let factor: Float
    let sampleRate: UInt
    
    private static let DELTA_FRAME_BIT_MASK: UInt8 = 0x80
    
    init(data: Data,
         _ getPreviousTimeStamp: (PmdMeasurementType) -> UInt64,
         _ getFactor: (PmdMeasurementType) -> Float,
         _ getSampleRate: (PmdMeasurementType) -> UInt) throws {
        
        measurementType = PmdMeasurementType.fromId(id: data[0])
        let timeBytes = data.subdata(in: 1..<9) as NSData
        var tempTimeStamp: UInt64 = 0
        memcpy(&tempTimeStamp, timeBytes.bytes, 8)
        timeStamp = tempTimeStamp
        
        let frameTypeByte = data[9]
        frameType = try PmdDataFrameType.getTypeFromDataFrameByte(byte: frameTypeByte)
        isCompressedFrame = PmdDataFrame.isCompressedFrame(byte: frameTypeByte)
        dataContent = data.subdata(in: 10..<data.count)
        
        previousTimeStamp = getPreviousTimeStamp(measurementType)
        factor = getFactor(measurementType)
        sampleRate = getSampleRate(measurementType)
    }
    
    private static func isCompressedFrame(byte: UInt8) -> Bool {
        return (byte & PmdDataFrame.DELTA_FRAME_BIT_MASK) > 0
    }

}

enum PmdDataFrameType: UInt8, CaseIterable {
    case type_0 = 0
    case type_1 = 1
    case type_2 = 2
    case type_3 = 3
    case type_4 = 4
    case type_5 = 5
    case type_6 = 6
    case type_7 = 7
    case type_8 = 8
    case type_9 = 9
    
    private static let DATA_FRAME_BIT_MASK: UInt8 = 0x7F
    
    static func getTypeFromDataFrameByte(byte: UInt8) throws -> PmdDataFrameType {
        for type in PmdDataFrameType.allCases {
            if (type.rawValue == (byte & PmdDataFrameType.DATA_FRAME_BIT_MASK)) {
                return type
            }
        }
        throw BleGattException.gattDataError(description: "FrameType id:\(byte) is not implemented")
    }
}
