//  Copyright © 2022 Polar. All rights reserved.

import Foundation

public class MagData {
    let timeStamp: UInt64
    
    struct MagSample {
        let timeStamp: UInt64
        let x: Float
        let y: Float
        let z: Float
        var calibrationStatus: CalibrationStatus = CalibrationStatus.notAvailable
    }
    
    enum CalibrationStatus: Int {
        case notAvailable = -1
        case unknown = 0
        case poor = 1
        case ok = 2
        case good = 3
        
        static func getById(id: Int) -> CalibrationStatus {
            guard let status = CalibrationStatus(rawValue: id) else {
                BleLogger.error("Invalid CalibrationStatus ID: \(id)")
                return notAvailable
            }
            return status
        }
    }
    
    var samples: [MagSample]
    
    init(timeStamp: UInt64 = 0, samples: [MagSample] = []) {
        self.timeStamp = timeStamp
        self.samples = samples
    }
    
    private static let TYPE_0_SAMPLE_SIZE_IN_BYTES: UInt8 = 2
    private static let TYPE_0_SAMPLE_SIZE_IN_BITS: UInt8 = TYPE_0_SAMPLE_SIZE_IN_BYTES * 8
    private static let TYPE_0_CHANNELS_IN_SAMPLE: UInt8 = 3
    
    private static let TYPE_1_SAMPLE_SIZE_IN_BYTES: UInt8 = 2
    private static let TYPE_1_SAMPLE_SIZE_IN_BITS: UInt8 = TYPE_1_SAMPLE_SIZE_IN_BYTES * 8
    private static let TYPE_1_CHANNELS_IN_SAMPLE: UInt8 = 4
    
    
    static func parseDataFromDataFrame(frame: PmdDataFrame) throws -> MagData {
        if (frame.isCompressedFrame) {
            switch (frame.frameType) {
            case PmdDataFrameType.type_0: return try dataFromCompressedType0(frame: frame)
            case PmdDataFrameType.type_1: return try dataFromCompressedType1(frame: frame)
            default: throw BleGattException.gattDataError(description: "Compressed FrameType: \(frame.frameType) is not supported by Magnetometer data parser")
            }
        } else {
            throw BleGattException.gattDataError(description: "Raw FrameType: \(frame.frameType) is not supported by Magnetometer data parser")
        }
    }
    
    private static func dataFromCompressedType0(frame: PmdDataFrame) throws -> MagData {
        let samples = Pmd.parseDeltaFramesToSamples(frame.dataContent, channels: TYPE_0_CHANNELS_IN_SAMPLE, resolution: TYPE_0_SAMPLE_SIZE_IN_BITS)
        let timeStamps = try PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp: frame.previousTimeStamp, frameTimeStamp: frame.timeStamp, samplesSize: UInt(samples.count), sampleRate: frame.sampleRate)
        
        var magSamples = [MagSample]()
        for (index, sample) in samples.enumerated() {
            let x:Float
            let y:Float
            let z:Float
            
            if (frame.factor != 1.0) {
                x = Float(sample[0]) * frame.factor
                y = Float(sample[1]) * frame.factor
                z = Float(sample[2]) * frame.factor
            } else {
                x = Float(sample[0])
                y = Float(sample[1])
                z = Float(sample[2])
            }
            magSamples.append( MagSample( timeStamp: timeStamps[index], x: x, y: y, z: z))
        }
        return MagData(timeStamp: frame.timeStamp, samples: magSamples)
    }
    
    private static func dataFromCompressedType1(frame: PmdDataFrame) throws -> MagData {
        let samples = Pmd.parseDeltaFramesToSamples(frame.dataContent, channels: TYPE_1_CHANNELS_IN_SAMPLE, resolution: TYPE_1_SAMPLE_SIZE_IN_BITS)
        
        let timeStamps = try PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp: frame.previousTimeStamp, frameTimeStamp: frame.timeStamp, samplesSize: UInt(samples.count), sampleRate: frame.sampleRate)
        
        let unitConversionFactor = 1000 // type 1 data arrives in milliGauss units
        var magSamples = [MagSample]()
        for (index, sample) in samples.enumerated() {
            let x = ((frame.factor != 1.0) ? Float(sample[0]) * frame.factor : Float(sample[0])) / Float(unitConversionFactor)
            let y = ((frame.factor != 1.0) ? Float(sample[1]) * frame.factor : Float(sample[1])) / Float(unitConversionFactor)
            let z = ((frame.factor != 1.0) ? Float(sample[2]) * frame.factor : Float(sample[2])) / Float(unitConversionFactor)
            let status = CalibrationStatus.getById(id: Int(sample[3]))
            magSamples.append( MagSample( timeStamp: timeStamps[index], x: x, y: y, z: z, calibrationStatus: status))
        }
        return MagData(timeStamp: frame.timeStamp, samples: magSamples)
    }
}
