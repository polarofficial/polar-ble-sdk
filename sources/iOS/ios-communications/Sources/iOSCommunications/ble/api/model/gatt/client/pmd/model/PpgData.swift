//  Copyright © 2022 Polar. All rights reserved.

import Foundation

public class PpgData {
    let timeStamp: UInt64
    
    struct PpgSample {
        let timeStamp: UInt64?
        let ppgDataSamples: [Int32]
        let ambientSample: Int32!
        let status: Int32!
        let frameType: PmdDataFrameType
    }
    
    var samples: [PpgSample]
    
    
    init(timeStamp: UInt64 = 0, samples: [PpgSample] = []) {
        self.timeStamp = timeStamp
        self.samples = samples
    }
    
    private static let TYPE_0_SAMPLE_SIZE_IN_BYTES: UInt8 = 3
    private static let TYPE_0_SAMPLE_SIZE_IN_BITS: UInt8 = TYPE_0_SAMPLE_SIZE_IN_BYTES * 8
    private static let TYPE_0_CHANNELS_IN_SAMPLE: UInt8 = 4
    private static let TYPE_6_SAMPLE_SIZE_IN_BYTES: Int = 8
    private static let TYPE_7_SAMPLE_SIZE_IN_BYTES: UInt8 = 3
    private static let TYPE_7_SAMPLE_SIZE_IN_BITS: UInt8 = TYPE_7_SAMPLE_SIZE_IN_BYTES * 8
    private static let TYPE_7_CHANNELS_IN_SAMPLE: UInt8 = 17
    private static let TYPE_9_NUM_INTS_SIZE = 12
    private static let TYPE_9_CHANNEL_0_AND_1_SIZE = 24
    private static let TYPE_9_SAMPLE_SIZE_IN_BYTES =
    TYPE_9_NUM_INTS_SIZE + TYPE_9_CHANNEL_0_AND_1_SIZE
    private static let TYPE_10_SAMPLE_SIZE_IN_BYTES: UInt8 = 3
    private static let TYPE_10_SAMPLE_SIZE_IN_BITS: UInt8 = TYPE_10_SAMPLE_SIZE_IN_BYTES * 8
    private static let TYPE_10_STATUS_SIZE: UInt8 = 20
    private static let TYPE_10_CHANNELS_IN_SAMPLE: UInt8 = 21
    
    static func parseDataFromDataFrame(frame: PmdDataFrame) throws -> PpgData {
        if (frame.isCompressedFrame) {
            switch (frame.frameType) {
            case PmdDataFrameType.type_0: return try dataFromCompressedType0(frame: frame)
            case PmdDataFrameType.type_7: return try dataFromCompressedType7(frame: frame)
            case PmdDataFrameType.type_10: return try dataFromCompressedType10(frame: frame)
            default: throw BleGattException.gattDataError(description: "Compressed FrameType: \(frame.frameType) is not supported by PPG data parser")
            }
        } else {
            switch (frame.frameType) {
            case PmdDataFrameType.type_0: return try dataFromRawType0(frame: frame)
            case PmdDataFrameType.type_6: return try dataFromRawType6(frame: frame)
            case PmdDataFrameType.type_9: return try dataFromRawType9(frame: frame)
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
            
            ppgSamples.append( PpgSample( timeStamp: timeStamps[timeStampIndex], ppgDataSamples: [ppg0, ppg1, ppg2], ambientSample: ambient, status: nil, frameType: frame.frameType))
            timeStampIndex += 1
        }
        
        return PpgData(timeStamp: frame.timeStamp, samples: ppgSamples)
    }
    
    private static func dataFromRawType6(frame: PmdDataFrame) throws -> PpgData {
        var ppgSamples = [PpgSample]()
        
        let sportId = TypeUtils.convertArrayToUnsignedInt64(frame.dataContent, offset: 0, size: TYPE_6_SAMPLE_SIZE_IN_BYTES)
        let samplesSize = frame.dataContent.count / TYPE_6_SAMPLE_SIZE_IN_BYTES
        let timeStamps = try PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp: frame.previousTimeStamp, frameTimeStamp: frame.timeStamp, samplesSize: UInt(samplesSize), sampleRate: frame.sampleRate)
        ppgSamples.append(
            PpgSample( timeStamp: timeStamps.first!, ppgDataSamples: [Int32(sportId)], ambientSample: nil, status: nil, frameType: frame.frameType)
        )
        
        return PpgData(timeStamp: timeStamps.first!, samples: ppgSamples)
    }
    
    private static func dataFromRawType9(frame: PmdDataFrame) throws -> PpgData {
        
        let step = TYPE_0_SAMPLE_SIZE_IN_BYTES
        let samplesSize = Int(Double(frame.dataContent.count) / Double(step * TYPE_0_CHANNELS_IN_SAMPLE))
        let timeStamps = try PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp: frame.previousTimeStamp, frameTimeStamp: frame.timeStamp, samplesSize: UInt(samplesSize), sampleRate: frame.sampleRate)
        var ppgSamples = [PpgSample]()
        var timeStampIndex = 0
        
        var offset = 0
        while offset < frame.dataContent.count {
            let numIntTs =
            frame.dataContent[offset..<(offset + TYPE_9_NUM_INTS_SIZE)].map(Int32.init)
            
            offset += TYPE_9_NUM_INTS_SIZE
            var channel1GainTs = [Int32]()
            for (index, value) in frame.dataContent[offset..<(offset + TYPE_9_CHANNEL_0_AND_1_SIZE)]
                .enumerated() {
                if (index % 2 == 0) {
                    channel1GainTs.append(Int32(value & 0x07))
                }
            }
            
            var channel2GainTs = [Int32]()
            for (index, value) in frame.dataContent[offset..<(offset + TYPE_9_CHANNEL_0_AND_1_SIZE)]
                .enumerated() {
                if (index % 2 == 1) {
                    channel2GainTs.append(Int32(value & 0x07))
                }
            }
            offset += TYPE_9_CHANNEL_0_AND_1_SIZE

            ppgSamples.append( PpgSample( timeStamp: timeStamps[timeStampIndex], ppgDataSamples: numIntTs, ambientSample: nil, status: nil, frameType: frame.frameType))
            ppgSamples.append( PpgSample( timeStamp: timeStamps[timeStampIndex], ppgDataSamples: channel1GainTs, ambientSample: nil, status: nil, frameType: frame.frameType))
            ppgSamples.append( PpgSample( timeStamp: timeStamps[timeStampIndex], ppgDataSamples: channel2GainTs, ambientSample: nil, status: nil, frameType: frame.frameType))
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
            ppgSamples.append( PpgSample( timeStamp: timeStamps[index], ppgDataSamples: [ppg0, ppg1, ppg2], ambientSample: ambient, status: nil, frameType: frame.frameType))
        }
        return PpgData(timeStamp: frame.timeStamp, samples: ppgSamples)
    }
    
    private static func dataFromCompressedType7(frame: PmdDataFrame) throws -> PpgData {
            let samples = Pmd.parseDeltaFramesToSamples(frame.dataContent, channels: TYPE_7_CHANNELS_IN_SAMPLE, resolution: TYPE_7_SAMPLE_SIZE_IN_BITS)
        let timeStamps = try PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp: frame.previousTimeStamp, frameTimeStamp: frame.timeStamp, samplesSize: UInt(samples.count), sampleRate: frame.sampleRate)
        var ppgSamplesFrameType7 = [PpgSample]()
        for (index, sample) in samples.enumerated() {
            let channelSamples = sample.map{ item in
                if (frame.factor != 1.0) {
                    return Int32(Float(item) * frame.factor)
                }
                else {
                    return item
                }
            }
            let status = Int32(sample[16] & 0xFFFFFF)
            ppgSamplesFrameType7.append( PpgSample( timeStamp: timeStamps[index], ppgDataSamples: channelSamples, ambientSample: nil, status: status, frameType: frame.frameType))
        }
        return PpgData(timeStamp: frame.timeStamp, samples: ppgSamplesFrameType7)
    }
    
    private static func dataFromCompressedType10(frame: PmdDataFrame) throws -> PpgData {
        
        let samples = Pmd.parseDeltaFramesToSamples(frame.dataContent, channels: TYPE_10_CHANNELS_IN_SAMPLE, resolution: TYPE_10_SAMPLE_SIZE_IN_BITS)

        let timeStamps = try PmdTimeStampUtils.getTimeStamps(
            previousFrameTimeStamp: frame.previousTimeStamp,
            frameTimeStamp: frame.timeStamp,
            samplesSize: UInt(samples.count),
            sampleRate: frame.sampleRate
        )
        var ppgSamples = [PpgSample]()
        var timeStampIndex = 0
        
        for (index, sample) in samples.enumerated() {
            
            let greenSamples = sample[0..<8].map { sample in
                if (frame.factor != Float(1.0)) {
                    Int32((Float(sample) * frame.factor))
                } else {
                    sample
                }
            }
            
            let redSamples = sample[8..<14].map { sample in
                if (frame.factor != Float(1.0)) {
                    Int32((Float(sample) * frame.factor))
                } else {
                    sample
                }
            }
            
            let irSamples = sample[14..<20].map { sample in
                if (frame.factor != Float(1.0)) {
                    Int32((Float(sample) * frame.factor))
                } else {
                    sample
                }
            }
            
            let ppgSample = PpgSample(
                timeStamp: timeStamps[index],
                ppgDataSamples: greenSamples + redSamples + irSamples,
                ambientSample: 0,
                status: sample[sample.endIndex - 1],
                frameType: frame.frameType
            )
            
            ppgSamples.append(ppgSample)
            timeStampIndex+=1
        }
        return PpgData(timeStamp: frame.timeStamp, samples: ppgSamples)
    }
}
