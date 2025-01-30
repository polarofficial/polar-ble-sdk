//  Copyright © 2024 Polar. All rights reserved.

import Foundation

public class SkinTemperatureData {

    struct SkinTemperatureSample {
        let timeStamp: UInt64
        let skinTemperature: Float
    }

    var samples: [SkinTemperatureSample]

    init(samples: [SkinTemperatureSample] = []) {
        self.samples = samples
    }

    private static let TYPE_0_SAMPLE_SIZE_IN_BYTES: UInt8 = 4
    private static let TYPE_0_SAMPLE_SIZE_IN_BITS: UInt8 = TYPE_0_SAMPLE_SIZE_IN_BYTES * 8
    private static let TYPE_0_CHANNELS_IN_SAMPLE: UInt8 = 1

    static func parseDataFromDataFrame(frame: PmdDataFrame) throws -> SkinTemperatureData {
        if (frame.isCompressedFrame) {
            switch (frame.frameType) {
            case PmdDataFrameType.type_0: return try dataFromCompressedType0(frame: frame)
            default: throw BleGattException.gattDataError(description: "Raw FrameType: \(frame.frameType) is not supported by SkinTemperature data parser")
            }
        } else {
            switch (frame.frameType) {
            case PmdDataFrameType.type_0: return try dataFromRawType0(frame: frame)
            default: throw BleGattException.gattDataError(description: "Raw FrameType: \(frame.frameType) is not supported by SkinTemperature data parser")
            }
        }
    }

    private static func dataFromCompressedType0(frame: PmdDataFrame) throws -> SkinTemperatureData {

        let skinTemperatureData = SkinTemperatureData()
        let samples = Pmd.parseDeltaFramesToSamples(frame.dataContent, channels: TYPE_0_CHANNELS_IN_SAMPLE, resolution: TYPE_0_SAMPLE_SIZE_IN_BITS)
        let timeStamps = try PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp: frame.previousTimeStamp, frameTimeStamp: frame.timeStamp, samplesSize: UInt(samples.count), sampleRate: frame.sampleRate)

        var skinTemperatureSamples = [SkinTemperatureSample]()

        for (index, sample) in samples.enumerated() {
            let skinTemperature = Float.init(bitPattern: UInt32(sample.first!))
            skinTemperatureSamples.append(SkinTemperatureSample(timeStamp: timeStamps[index], skinTemperature: skinTemperature))
        }
        
        skinTemperatureData.samples = skinTemperatureSamples
        return skinTemperatureData
    }

    private static func dataFromRawType0(frame: PmdDataFrame) throws -> SkinTemperatureData {

        let skinTemperatureData = SkinTemperatureData()
        let step = TYPE_0_SAMPLE_SIZE_IN_BYTES
        let samplesSize = Int(Double(frame.dataContent.count) / Double(step))
        var offset = 0
        var timeStampIndex = 0

        let timeStamps = try PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp: frame.previousTimeStamp, frameTimeStamp: frame.timeStamp, samplesSize: UInt(samplesSize), sampleRate: frame.sampleRate)
    
        while (offset < frame.dataContent.count) {
            skinTemperatureData.samples.append(SkinTemperatureSample(timeStamp: timeStamps[timeStampIndex], skinTemperature: Float(bitPattern: UInt32( frame.dataContent[offset ..< (offset + Int(TYPE_0_SAMPLE_SIZE_IN_BYTES))].withUnsafeBytes { $0.load(as: UInt32.self) }))))
            offset += Int(step)
            timeStampIndex += 1
        }
        return skinTemperatureData
    }
}
