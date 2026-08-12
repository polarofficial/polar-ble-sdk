//  Copyright © 2022 Polar. All rights reserved.

import Foundation

public class EcgData {
    let timeStamp: UInt64
    
    struct EcgSample {
        let timeStamp: UInt64
        let microVolts: Int32
    }
    
    var samples: [EcgSample]
 
    init(timeStamp: UInt64 = 0, samples: [EcgSample] = [EcgSample]()) {
        self.timeStamp = timeStamp
        self.samples = samples
    }
    
    private static let  TYPE_0_SAMPLE_SIZE_IN_BYTES = 3
    private static let  TYPE_0_SAMPLE_SIZE_IN_BITS = TYPE_0_SAMPLE_SIZE_IN_BYTES * 8
    private static let  TYPE_0_CHANNELS_IN_SAMPLE: UInt8 = 1
    
    static func parseDataFromDataFrame(frame: PmdDataFrame) throws -> EcgData {
        if (frame.isCompressedFrame) {
            switch (frame.frameType) {
            case PmdDataFrameType.type_0: return try dataFromCompressedType0(frame: frame)
            default: throw BleGattException.gattDataError(description: "Compressed FrameType: \(frame.frameType) is not supported by ECG data parser")
            }
        } else {
            switch (frame.frameType) {
            case PmdDataFrameType.type_0: return try dataFromRawType0(frame: frame)
            default: throw BleGattException.gattDataError(description: "Raw FrameType: \(frame.frameType) is not supported by ECG data parser")
            }
        }
    }

    private static func dataFromCompressedType0(frame: PmdDataFrame) throws -> EcgData {
        let samples = Pmd.parseDeltaFramesToSamples(
            frame.dataContent,
            channels: TYPE_0_CHANNELS_IN_SAMPLE,
            resolution: TYPE_0_SAMPLE_SIZE_IN_BITS
        )
        let timeStamps = try PmdTimeStampUtils.getTimeStamps(
            previousFrameTimeStamp: frame.previousTimeStamp,
            frameTimeStamp: frame.timeStamp,
            samplesSize: UInt(samples.count),
            sampleRate: frame.sampleRate
        )

        var ecgSamples = [EcgSample]()
        for (index, sample) in samples.enumerated() {
            let microVolts: Int32
            if (frame.factor != 1.0) {
                microVolts = Int32(Float(sample[0]) * frame.factor)
            } else {
                microVolts = sample[0]
            }
            ecgSamples.append(EcgSample(timeStamp: timeStamps[index], microVolts: microVolts))
        }
        return EcgData(timeStamp: frame.timeStamp, samples: ecgSamples)
    }
    
    private static func dataFromRawType0(frame: PmdDataFrame) throws -> EcgData {
        var offset = 0
        let step = TYPE_0_SAMPLE_SIZE_IN_BYTES
        let samplesSize = Int(Double(frame.dataContent.count) / Double(step))
        let timeStamps = try PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp: frame.previousTimeStamp, frameTimeStamp: frame.timeStamp, samplesSize: UInt(samplesSize), sampleRate: frame.sampleRate)
        
        var timeStampIndex = 0
        var ecgSamples = [EcgSample]()
        while (offset < frame.dataContent.count) {
            let voltage = TypeUtils.convertArrayToSignedInt(frame.dataContent, offset: offset, size: Int(step))
            offset += step
            ecgSamples.append(EcgSample(timeStamp: timeStamps[timeStampIndex], microVolts: voltage))
            timeStampIndex += 1
        }
        return EcgData(timeStamp: frame.timeStamp, samples: ecgSamples)
    }
}
