//  Copyright © 2024 Polar. All rights reserved.

import Foundation

public class SkinTemperatureData {

    struct SkinTemperatureSample {
        let timeStamp: UInt64
        let skinTemperature: Float
        let isTimestampEstimated: Bool

        init(timeStamp: UInt64, skinTemperature: Float, isTimestampEstimated: Bool = false) {
            self.timeStamp = timeStamp
            self.skinTemperature = skinTemperature
            self.isTimestampEstimated = isTimestampEstimated
        }
    }

    var samples: [SkinTemperatureSample]

    init(samples: [SkinTemperatureSample] = []) {
        self.samples = samples
    }

    private static let TYPE_0_SAMPLE_SIZE_IN_BYTES: UInt8 = 4
    private static let TYPE_0_SAMPLE_SIZE_IN_BITS: UInt8 = TYPE_0_SAMPLE_SIZE_IN_BYTES * 8
    private static let TYPE_0_CHANNELS_IN_SAMPLE: UInt8 = 1
    private static let DEFAULT_SKIN_TEMP_SAMPLE_RATE: UInt = 4

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
        let samples = Pmd.parseDeltaFramesToSamples(
            frame.dataContent,
            channels: TYPE_0_CHANNELS_IN_SAMPLE,
            resolution: TYPE_0_SAMPLE_SIZE_IN_BITS
        )

        if samples.isEmpty {
            return skinTemperatureData
        }

        let sampleRate: UInt
        let isTimestampEstimated: Bool
        if frame.sampleRate > 0 {
            sampleRate = frame.sampleRate
            isTimestampEstimated = false
        } else {
            sampleRate = DEFAULT_SKIN_TEMP_SAMPLE_RATE
            isTimestampEstimated = true
            BleLogger.trace("SkinTemperatureData sampleRate was 0, using default \(DEFAULT_SKIN_TEMP_SAMPLE_RATE)")
        }

        let timeStamps = try PmdTimeStampUtils.getTimeStamps(
            previousFrameTimeStamp: frame.previousTimeStamp,
            frameTimeStamp: frame.timeStamp,
            samplesSize: UInt(samples.count),
            sampleRate: sampleRate
        )

        var skinTemperatureSamples = [SkinTemperatureSample]()

        for (index, sample) in samples.enumerated() {
            let skinTemperature = Float(bitPattern: UInt32(sample.first!))
            skinTemperatureSamples.append(
                SkinTemperatureSample(
                    timeStamp: timeStamps[index],
                    skinTemperature: skinTemperature,
                    isTimestampEstimated: isTimestampEstimated
                )
            )
        }

        skinTemperatureData.samples = skinTemperatureSamples
        return skinTemperatureData
    }

    private static func dataFromRawType0(frame: PmdDataFrame) throws -> SkinTemperatureData {

        let skinTemperatureData = SkinTemperatureData()
        let step = TYPE_0_SAMPLE_SIZE_IN_BYTES
        let samplesSize = Int(Double(frame.dataContent.count) / Double(step))

        if samplesSize == 0 {
            return skinTemperatureData
        }

        let sampleRate: UInt
        let isTimestampEstimated: Bool
        if frame.sampleRate > 0 {
            sampleRate = frame.sampleRate
            isTimestampEstimated = false
        } else {
            sampleRate = DEFAULT_SKIN_TEMP_SAMPLE_RATE
            isTimestampEstimated = true
            BleLogger.trace("SkinTemperatureData sampleRate was 0, using default \(DEFAULT_SKIN_TEMP_SAMPLE_RATE)")
        }

        let timeStamps = try PmdTimeStampUtils.getTimeStamps(
            previousFrameTimeStamp: frame.previousTimeStamp,
            frameTimeStamp: frame.timeStamp,
            samplesSize: UInt(samplesSize),
            sampleRate: sampleRate
        )

        var offset = 0
        var timeStampIndex = 0

        while (offset + Int(step) <= frame.dataContent.count) {
            skinTemperatureData.samples.append(
                SkinTemperatureSample(
                    timeStamp: timeStamps[timeStampIndex],
                    skinTemperature: Float(
                        bitPattern: UInt32(
                            frame.dataContent[offset ..< (offset + Int(TYPE_0_SAMPLE_SIZE_IN_BYTES))]
                                .withUnsafeBytes { $0.load(as: UInt32.self) }
                        )
                    ),
                    isTimestampEstimated: isTimestampEstimated
                )
            )
            offset += Int(step)
            timeStampIndex += 1
        }

        return skinTemperatureData
    }
}
