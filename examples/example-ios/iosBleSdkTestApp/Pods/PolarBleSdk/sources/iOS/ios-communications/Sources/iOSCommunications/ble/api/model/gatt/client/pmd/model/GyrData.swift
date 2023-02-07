//  Copyright © 2022 Polar. All rights reserved.

import Foundation

public class GyrData {
    let timeStamp: UInt64
    
    struct GyrSample {
        let timeStamp: UInt64
        let x: Float
        let y: Float
        let z: Float
    }
    
    var samples: [GyrSample]
    
    init(timeStamp: UInt64 = 0, samples: [GyrSample] = []) {
        self.timeStamp = timeStamp
        self.samples = samples
    }
    
    private static let TYPE_0_SAMPLE_SIZE_IN_BYTES : UInt8 = 2
    private static let TYPE_0_SAMPLE_SIZE_IN_BITS: UInt8 = TYPE_0_SAMPLE_SIZE_IN_BYTES * 8
    private static let TYPE_0_CHANNELS_IN_SAMPLE: UInt8 = 3
    
    private static let TYPE_1_SAMPLE_SIZE_IN_BYTES: UInt8 = 4
    private static let TYPE_1_SAMPLE_SIZE_IN_BITS: UInt8 = TYPE_1_SAMPLE_SIZE_IN_BYTES * 8
    private static let TYPE_1_CHANNELS_IN_SAMPLE: UInt8 = 3
    
    static func parseDataFromDataFrame(frame: PmdDataFrame) throws -> GyrData {
        if (frame.isCompressedFrame) {
            switch (frame.frameType) {
            case PmdDataFrameType.type_0: return try dataFromCompressedType0(frame: frame)
            default: throw BleGattException.gattDataError(description: "Compressed FrameType: \(frame.frameType) is not supported by Gyro data parser")
            }
        } else {
            throw BleGattException.gattDataError(description: "Raw FrameType: \(frame.frameType) is not supported by Gyro data parser")
        }
    }
    
    private static func dataFromCompressedType0(frame: PmdDataFrame) throws -> GyrData {
        let samples = Pmd.parseDeltaFramesToSamples(frame.dataContent, channels: TYPE_0_CHANNELS_IN_SAMPLE, resolution: TYPE_0_SAMPLE_SIZE_IN_BITS)
        let timeStamps = try PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp: frame.previousTimeStamp, frameTimeStamp: frame.timeStamp, samplesSize: UInt(samples.count), sampleRate: frame.sampleRate)
        
        var gyrSamples = [GyrSample]()
        for (index, sample) in samples.enumerated() {
            gyrSamples.append( GyrSample( timeStamp: timeStamps[index],
                                          x: (Float(sample[0]) * frame.factor),
                                          y: (Float(sample[1]) * frame.factor),
                                          z: (Float(sample[2]) * frame.factor))
            )
        }
        return GyrData(timeStamp: frame.timeStamp, samples: gyrSamples)
    }
}
