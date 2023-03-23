//  Copyright © 2022 Polar. All rights reserved.

import Foundation

public class PpgData {
    let timeStamp: UInt64
    
    struct PpgSample {
        let timeStamp: UInt64
        let ppgDataSamples: [Int32]
        let ambientSample: Int32
    }
    
    var samples: [PpgSample]
    
    init(timeStamp: UInt64 = 0, samples: [PpgSample] = []) {
        self.timeStamp = timeStamp
        self.samples = samples
    }
    
    private static let TYPE_0_SAMPLE_SIZE_IN_BYTES: UInt8 = 3
    private static let TYPE_0_SAMPLE_SIZE_IN_BITS: UInt8 = TYPE_0_SAMPLE_SIZE_IN_BYTES * 8
    private static let TYPE_0_CHANNELS_IN_SAMPLE: UInt8 = 4
    
    static func parseDataFromDataFrame(frame: PmdDataFrame) throws -> PpgData {
        if (frame.isCompressedFrame) {
            switch (frame.frameType) {
            case PmdDataFrameType.type_0: return try dataFromCompressedType0(frame: frame)
            default: throw BleGattException.gattDataError(description: "Compressed FrameType: \(frame.frameType) is not supported by PPG data parser")
            }
        } else {
            switch (frame.frameType) {
            case PmdDataFrameType.type_0: return try dataFromRawType0(frame: frame)
            default: throw BleGattException.gattDataError(description: "Raw FrameType: \(frame.frameType) is not supported by PPG data parser")
            }
        }
    }
    
    private static func dataFromRawType0(frame: PmdDataFrame) throws -> PpgData {
        var offset = 0
        let step = TYPE_0_SAMPLE_SIZE_IN_BYTES
        let samplesSize = Int(Double(frame.dataContent.count) / Double(step * TYPE_0_CHANNELS_IN_SAMPLE))
        let timeStamps = try PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp: frame.previousTimeStamp, frameTimeStamp: frame.timeStamp, samplesSize: UInt(samplesSize), sampleRate: frame.sampleRate)
        
        var timeStampIndex = 0
        var ppgSamples = [PpgSample]()
        while offset < frame.dataContent.count {
            let ppg0 = TypeUtils.convertArrayToSignedInt(frame.dataContent, offset: offset, size: Int(step))
            offset += Int(step)
            let ppg1 = TypeUtils.convertArrayToSignedInt(frame.dataContent, offset: offset, size: Int(step))
            offset += Int(step)
            let ppg2 = TypeUtils.convertArrayToSignedInt(frame.dataContent, offset: offset, size: Int(step))
            offset += Int(step)
            let ambient = TypeUtils.convertArrayToSignedInt(frame.dataContent, offset: offset, size: Int(step))
            offset += Int(step)
            
            ppgSamples.append( PpgSample( timeStamp: timeStamps[timeStampIndex], ppgDataSamples: [ppg0, ppg1, ppg2], ambientSample: ambient))
            timeStampIndex += 1
        }
        return PpgData(timeStamp: frame.timeStamp, samples: ppgSamples)
    }
    
    private static func dataFromCompressedType0(frame: PmdDataFrame) throws -> PpgData {
        let samples = Pmd.parseDeltaFramesToSamples(frame.dataContent, channels: TYPE_0_CHANNELS_IN_SAMPLE, resolution: TYPE_0_SAMPLE_SIZE_IN_BITS)
        let timeStamps = try PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp: frame.previousTimeStamp, frameTimeStamp: frame.timeStamp, samplesSize: UInt(samples.count), sampleRate: frame.sampleRate)
        
        var ppgSamples = [PpgSample]()
        for (index, sample) in samples.enumerated() {
            let ppg0:Int32 = sample[0]
            let ppg1:Int32 = sample[1]
            let ppg2:Int32 = sample[2]
            let ambient: Int32 = sample[3]
            ppgSamples.append( PpgSample( timeStamp: timeStamps[index], ppgDataSamples: [ppg0, ppg1, ppg2], ambientSample: ambient))
        }
        return PpgData(timeStamp: frame.timeStamp, samples: ppgSamples)
    }
}
