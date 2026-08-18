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
    
    static func parseDataFromDataFrame(frame: PmdDataFrame) throws -> EcgData {
        if (frame.isCompressedFrame) {
            throw PmdDataParseError(message: "Compressed FrameType: \(frame.frameType) is not supported by ECG data parser")
        } else {
            switch (frame.frameType) {
            case PmdDataFrameType.type_0: return try dataFromRawType0(frame: frame)
            default: throw PmdDataParseError(message: "Raw FrameType: \(frame.frameType) is not supported by ECG data parser")
            }
        }
    }
    
    private static func dataFromRawType0(frame: PmdDataFrame) throws -> EcgData {
        let step = TYPE_0_SAMPLE_SIZE_IN_BYTES
        guard !frame.dataContent.isEmpty && frame.dataContent.count % step == 0 else {
            throw PmdDataParseError(
                message: "ECG raw TYPE_0 dataContent size \(frame.dataContent.count) is not a " +
                "non-zero multiple of expected sample size \(step). Data: \(PmdDataParseError.toHex(frame.dataContent))")
        }
        var offset = 0
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
