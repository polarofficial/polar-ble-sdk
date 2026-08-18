//  Copyright © 2024 Polar. All rights reserved.

import Foundation

public class PressureData {

    struct PressureSample {
        let timeStamp: UInt64
        let pressure: Float
    }

    var samples: [PressureSample]

    init(samples: [PressureSample] = []) {
        self.samples = samples
    }

    private static let TYPE_0_SAMPLE_SIZE_IN_BYTES: UInt8 = 4
    private static let TYPE_0_SAMPLE_SIZE_IN_BITS: UInt8 = TYPE_0_SAMPLE_SIZE_IN_BYTES * 8
    private static let TYPE_0_CHANNELS_IN_SAMPLE: UInt8 = 1

    static func parseDataFromDataFrame(frame: PmdDataFrame) throws -> PressureData {
        if (frame.isCompressedFrame) {
            switch (frame.frameType) {
            case PmdDataFrameType.type_0: return try dataFromCompressedType0(frame: frame)
            default: throw PmdDataParseError(message: "Compressed FrameType: \(frame.frameType) is not supported by Pressure data parser")
            }
        } else {
            switch (frame.frameType) {
            case PmdDataFrameType.type_0: return try dataFromRawType0(frame: frame)
            default: throw PmdDataParseError(message: "Raw FrameType: \(frame.frameType) is not supported by Pressure data parser")
            }
        }
    }

    private static func dataFromCompressedType0(frame: PmdDataFrame) throws -> PressureData {
        guard !frame.dataContent.isEmpty else {
            throw PmdDataParseError(message: "Pressure compressed TYPE_0 dataContent is empty")
        }
        let pressureData = PressureData()
        let samples = Pmd.parseDeltaFramesToSamples(frame.dataContent, channels: TYPE_0_CHANNELS_IN_SAMPLE, resolution: TYPE_0_SAMPLE_SIZE_IN_BITS)
        let timeStamps = try PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp: frame.previousTimeStamp, frameTimeStamp: frame.timeStamp, samplesSize: UInt(samples.count), sampleRate: frame.sampleRate)

        var pressureSamples = [PressureSample]()

        for (index, sample) in samples.enumerated() {
            guard let rawBits = sample.first else {
                throw PmdDataParseError(message: "Pressure compressed TYPE_0 sample at index \(index) is empty")
            }
            // The compressed delta values carry the IEEE-754 float bit pattern as Int32
            let pressure = Float(bitPattern: UInt32(bitPattern: rawBits))
            pressureSamples.append(PressureSample(timeStamp: timeStamps[index], pressure: pressure))
        }

        pressureData.samples = pressureSamples
        return pressureData
    }

    private static func dataFromRawType0(frame: PmdDataFrame) throws -> PressureData {
        let step = Int(TYPE_0_SAMPLE_SIZE_IN_BYTES)
        guard !frame.dataContent.isEmpty && frame.dataContent.count % step == 0 else {
            throw PmdDataParseError(
                message: "Pressure raw TYPE_0 dataContent size \(frame.dataContent.count) is not a " +
                "non-zero multiple of expected sample size \(step)")
        }
        let pressureData = PressureData()
        let samplesSize = frame.dataContent.count / step
        var offset = 0
        var timeStampIndex = 0
        
        let timeStamps = try PmdTimeStampUtils.getTimeStamps(
            previousFrameTimeStamp: frame.previousTimeStamp,
            frameTimeStamp: frame.timeStamp,
            samplesSize: UInt(samplesSize),
            sampleRate: frame.sampleRate
        )

        while (offset < frame.dataContent.count) {
            pressureData.samples.append(PressureSample(timeStamp: timeStamps[timeStampIndex], pressure: Float(bitPattern: UInt32( frame.dataContent[offset ..< (offset + Int(TYPE_0_SAMPLE_SIZE_IN_BYTES))].withUnsafeBytes { $0.load(as: UInt32.self) }))))
            offset += Int(step)
            timeStampIndex += 1
        }
        return pressureData
    }
}
