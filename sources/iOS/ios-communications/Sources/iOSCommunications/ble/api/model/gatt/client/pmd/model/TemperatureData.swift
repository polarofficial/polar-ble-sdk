//  Copyright © 2023 Polar. All rights reserved.

import Foundation

public class TemperatureData {

    struct TemperatureSample {
        let timeStamp: UInt64
        let temperature: Float
    }

    var samples: [TemperatureSample]

    init(samples: [TemperatureSample] = []) {
        self.samples = samples
    }

    private static let TYPE_0_SAMPLE_SIZE_IN_BYTES: UInt8 = 4
    private static let TYPE_0_SAMPLE_SIZE_IN_BITS: UInt8 = TYPE_0_SAMPLE_SIZE_IN_BYTES * 8
    private static let TYPE_0_CHANNELS_IN_SAMPLE: UInt8 = 1

    static func parseDataFromDataFrame(frame: PmdDataFrame) throws -> TemperatureData {
        if (frame.isCompressedFrame) {
            switch (frame.frameType) {
            case PmdDataFrameType.type_0: return try dataFromCompressedType0(frame: frame)
            default: throw BleGattException.gattDataError(description: "Raw FrameType: \(frame.frameType) is not supported by Temperature data parser")
            }
        } else {
            switch (frame.frameType) {
            case PmdDataFrameType.type_0: return try dataFromRawType0(frame: frame)
            default: throw BleGattException.gattDataError(description: "Raw FrameType: \(frame.frameType) is not supported by Temperature data parser")
            }
        }
    }

    private static func dataFromCompressedType0(frame: PmdDataFrame) throws -> TemperatureData {

        let temperatureData = TemperatureData()
        let samples = Pmd.parseDeltaFramesToSamples(frame.dataContent, channels: TYPE_0_CHANNELS_IN_SAMPLE, resolution: TYPE_0_SAMPLE_SIZE_IN_BITS)
        let timeStamps = try PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp: frame.previousTimeStamp, frameTimeStamp: frame.timeStamp, samplesSize: UInt(samples.count), sampleRate: frame.sampleRate)

        var temperatureSamples = [TemperatureSample]()

        for (index, sample) in samples.enumerated() {
            let temperature = Float.init(bitPattern: UInt32(sample.first!))
            temperatureSamples.append(TemperatureSample(timeStamp: timeStamps[index], temperature: temperature))
        }
        temperatureData.samples = temperatureSamples
        return temperatureData
    }

    private static func dataFromRawType0(frame: PmdDataFrame) throws -> TemperatureData {

        let temperatureData = TemperatureData()
        let step = TYPE_0_SAMPLE_SIZE_IN_BYTES
        let samplesSize = Int(Double(frame.dataContent.count) / Double(step))
        var offset = 0
        var timeStampIndex = 0

        let timeStamps = try PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp: frame.previousTimeStamp, frameTimeStamp: frame.timeStamp, samplesSize: UInt(samplesSize), sampleRate: frame.sampleRate)
    
        while (offset < frame.dataContent.count) {
            temperatureData.samples.append(TemperatureSample(timeStamp: timeStamps[timeStampIndex], temperature: Float(bitPattern: UInt32( frame.dataContent[offset ..< (offset + Int(TYPE_0_SAMPLE_SIZE_IN_BYTES))].withUnsafeBytes { $0.load(as: UInt32.self) }))))
            offset += Int(step)
            timeStampIndex += 1
        }
        return temperatureData
    }
}
