//  Copyright © 2022 Polar. All rights reserved.

import Foundation

public class AccData {
    var timeStamp: UInt64 = 0
    
    public struct AccSample {
        let timeStamp: UInt64
        let x: Int32
        let y: Int32
        let z: Int32
    }
    
    var samples:[AccSample]
    
    init(timeStamp: UInt64 = 0, samples: [AccSample] = []) {
        self.timeStamp = timeStamp
        self.samples = samples
    }
    
    func parseFromFrame(frame: PmdDataFrame) throws {
        let data = try AccData.parseDataFromDataFrame(frame: frame)
        samples.append(contentsOf: data.samples)
    }
    
    private static let TYPE_0_SAMPLE_SIZE_IN_BYTES: UInt8 = 1
    private static let TYPE_0_SAMPLE_SIZE_IN_BITS: UInt8 = TYPE_0_SAMPLE_SIZE_IN_BYTES * 8
    private static let TYPE_0_CHANNELS_IN_SAMPLE: UInt8 = 3
    
    private static let TYPE_1_SAMPLE_SIZE_IN_BYTES: UInt8 = 2
    private static let TYPE_1_SAMPLE_SIZE_IN_BITS: UInt8 = TYPE_1_SAMPLE_SIZE_IN_BYTES * 8
    private static let TYPE_1_CHANNELS_IN_SAMPLE: UInt8 = 3
    
    private static let TYPE_2_SAMPLE_SIZE_IN_BYTES: UInt8 = 3
    private static let TYPE_2_SAMPLE_SIZE_IN_BITS: UInt8 = TYPE_2_SAMPLE_SIZE_IN_BYTES * 8
    private static let TYPE_2_CHANNELS_IN_SAMPLE: UInt8 = 3
    
    static func parseDataFromDataFrame(frame: PmdDataFrame) throws -> AccData {
        if (frame.isCompressedFrame) {
            switch (frame.frameType) {
            case PmdDataFrameType.type_0: return try dataFromCompressedType0(frame: frame)
            case PmdDataFrameType.type_1: return try dataFromCompressedType1(frame: frame)
            default: throw BleGattException.gattDataError(description: "Compressed FrameType: \(frame.frameType) is not supported by ACC data parser")
            }
        } else {
            switch (frame.frameType) {
            case PmdDataFrameType.type_0: return try dataFromRawType0(frame: frame)
            case PmdDataFrameType.type_1: return try dataFromRawType1(frame: frame)
            default: throw BleGattException.gattDataError(description: "Raw FrameType: \(frame.frameType) is not supported by ACC data parser")
            }
        }
    }
    
    private static func dataFromRawType0(frame: PmdDataFrame) throws -> AccData {
        var offset = 0
        let step = TYPE_0_SAMPLE_SIZE_IN_BYTES
        let samplesSize = Int(Double(frame.dataContent.count) / Double(step * TYPE_0_CHANNELS_IN_SAMPLE))
        let timeStamps = try PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp: frame.previousTimeStamp, frameTimeStamp: frame.timeStamp, samplesSize: UInt(samplesSize), sampleRate: frame.sampleRate)
        
        var timeStampIndex = 0
        var accSamples = [AccSample]()
        while offset < frame.dataContent.count {
            let x =  TypeUtils.convertArrayToSignedInt(frame.dataContent, offset: offset, size: Int(step))
            offset += Int(step)
            let y =  TypeUtils.convertArrayToSignedInt(frame.dataContent, offset: offset, size: Int(step))
            offset += Int(step)
            let z =  TypeUtils.convertArrayToSignedInt(frame.dataContent, offset: offset, size: Int(step))
            offset += Int(step)
            accSamples.append( AccSample( timeStamp: timeStamps[timeStampIndex], x: x, y: y, z: z))
            timeStampIndex += 1
        }
        return AccData(timeStamp: frame.timeStamp, samples: accSamples)
    }
    
    private static func dataFromRawType1(frame: PmdDataFrame) throws -> AccData {
        var offset = 0
        let step = TYPE_1_SAMPLE_SIZE_IN_BYTES
        let samplesSize = Int(Double(frame.dataContent.count) / Double(step * TYPE_1_CHANNELS_IN_SAMPLE))
        let timeStamps = try PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp: frame.previousTimeStamp, frameTimeStamp: frame.timeStamp, samplesSize: UInt(samplesSize), sampleRate: frame.sampleRate)
        
        var timeStampIndex = 0
        var accSamples = [AccSample]()
        while offset < frame.dataContent.count {
            let x =  TypeUtils.convertArrayToSignedInt(frame.dataContent, offset: offset, size: Int(step))
            offset += Int(step)
            let y =  TypeUtils.convertArrayToSignedInt(frame.dataContent, offset: offset, size: Int(step))
            offset += Int(step)
            let z =  TypeUtils.convertArrayToSignedInt(frame.dataContent, offset: offset, size: Int(step))
            offset += Int(step)
            accSamples.append( AccSample( timeStamp: timeStamps[timeStampIndex], x: x, y: y, z: z))
            timeStampIndex += 1
        }
        return AccData(timeStamp: frame.timeStamp, samples: accSamples)
    }
    
    private static func dataFromCompressedType0(frame: PmdDataFrame) throws -> AccData {
        //Note, special Wolfi type. See SAGRFC85.3
        let samples = Pmd.parseDeltaFramesToSamples(frame.dataContent, channels: 3, resolution: 16)
        let timeStamps = try PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp: frame.previousTimeStamp, frameTimeStamp: frame.timeStamp, samplesSize: UInt(samples.count), sampleRate: frame.sampleRate)
        let accFactor = frame.factor * 1000 // type 0 data arrives in G units, convert to milliG
        
        var accSamples = [AccSample]()
        for (index, sample) in samples.enumerated() {
            accSamples.append( AccSample( timeStamp: timeStamps[index],
                                          x: Int32((Float(sample[0]) * accFactor)),
                                          y: Int32((Float(sample[1]) * accFactor)),
                                          z: Int32((Float(sample[2]) * accFactor))
                                        ))
        }
        return AccData(timeStamp: frame.timeStamp, samples: accSamples)
    }
    
    private static func dataFromCompressedType1(frame: PmdDataFrame) throws -> AccData {
        let samples = Pmd.parseDeltaFramesToSamples(frame.dataContent, channels: TYPE_1_CHANNELS_IN_SAMPLE, resolution: TYPE_1_SAMPLE_SIZE_IN_BITS)
        let timeStamps = try PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp: frame.previousTimeStamp, frameTimeStamp: frame.timeStamp, samplesSize: UInt(samples.count), sampleRate: frame.sampleRate)
        
        var accSamples = [AccSample]()
        for (index, sample) in samples.enumerated() {
            let x:Int32
            let y:Int32
            let z:Int32
            if (frame.factor != 1.0){
                x = Int32((Float(sample[0]) * frame.factor))
                y = Int32((Float(sample[1]) * frame.factor))
                z = Int32((Float(sample[2]) * frame.factor))
            } else {
                x = sample[0]
                y = sample[1]
                z = sample[2]
            }
            accSamples.append( AccSample( timeStamp: timeStamps[index], x: x, y: y, z: z))
        }
        return AccData(timeStamp: frame.timeStamp, samples: accSamples)
    }
}
