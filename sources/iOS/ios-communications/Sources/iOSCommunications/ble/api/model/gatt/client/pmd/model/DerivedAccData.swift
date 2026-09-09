// Copyright © 2026 Polar Electro Oy. All rights reserved.

import Foundation

/// Internal PMD model for derived ACC data.
/// One `DerivedAccSample` is produced per time window (e.g. 1 Hz with a 1 000 ms window).
public class DerivedAccData {

    /// One derived ACC output sample aligned to the end of its time window.
    /// `methodValues`: method ID → values; component methods (0–3) give [x,y,z], scalars give [v].
    public struct DerivedAccSample {
        public let timeStamp: UInt64
        public let methodValues: [Int: [Int32]]
    }

    public let activeMethods: Set<Int>
    public var derivedSamples: [DerivedAccSample] = []

    public init(activeMethods: Set<Int> = []) {
        self.activeMethods = activeMethods
    }

    // MARK: - Parsing

    // Component methods for DERIVED_MEASUREMENT (type-15) frames per spec: 0–4 → [x,y,z].
    private static let componentMethodsDerivedFrame: Set<Int> = [0, 1, 2, 3, 4]

    // Component methods for current firmware (ACC type-2 frames): 0–3 → [x,y,z]; 4 and 5–9 are scalar.
    private static let componentMethodsFirmware: Set<Int> = [0, 1, 2, 3]

    // Methods whose outputs are unsigned-promoted.
    private static let unsignedMethods: Set<Int> = [4, 5, 6, 7, 8, 9]

    private static let derivedFrameHeaderSize = 4
    private static let derivedFrameMethodBitsOffset = 2

    static func parseDataFromDataFrame(frame: PmdDataFrame, activeMethods: Set<Int>) throws -> DerivedAccData {
        let result = DerivedAccData(activeMethods: activeMethods)
        guard !frame.dataContent.isEmpty else { return result }

        let sampleDataBytes: Data
        let effectiveMethods: [Int]
        let componentMethods: Set<Int>

        if frame.measurementType == .derivedMeasurement {
            guard frame.dataContent.count >= derivedFrameHeaderSize else { return result }
            let b3 = Int(frame.dataContent[derivedFrameMethodBitsOffset])
            let b4 = Int(frame.dataContent[derivedFrameMethodBitsOffset + 1])
            let methodBits = b3 | (b4 << 8)
            effectiveMethods = (0..<16).filter { (methodBits >> $0) & 1 == 1 }
            sampleDataBytes = frame.dataContent.subdata(in: derivedFrameHeaderSize..<frame.dataContent.count)
            componentMethods = componentMethodsDerivedFrame
        } else {
            let sampleSz = sampleSizeFromFrameType(frame.frameType)
            let sortedHint = activeMethods.sorted()

            let fullValuesPerSample = sortedHint.reduce(0) { acc, m in
                acc + (componentMethodsFirmware.contains(m) ? 3 : 1)
            }
            let fullBytesPerSample = fullValuesPerSample * sampleSz
            if fullBytesPerSample > 0 && frame.dataContent.count % fullBytesPerSample == 0 {
                effectiveMethods = sortedHint
            } else {
                var cumulative = 0
                var trimmed: [Int] = []
                for method in sortedHint {
                    let vals = componentMethodsFirmware.contains(method) ? 3 : 1
                    let newTotal = cumulative + vals
                    if newTotal * sampleSz <= frame.dataContent.count {
                        cumulative = newTotal
                        trimmed.append(method)
                    }
                }
                effectiveMethods = trimmed
            }
            sampleDataBytes = frame.dataContent
            componentMethods = componentMethodsFirmware
        }

        guard !effectiveMethods.isEmpty else { return result }

        let sampleSizeBytes = sampleSizeFromFrameType(frame.frameType)
        let valuesPerSample = effectiveMethods.reduce(0) { acc, m in acc + (componentMethods.contains(m) ? 3 : 1) }
        let bytesPerSample = valuesPerSample * sampleSizeBytes
        guard bytesPerSample > 0 else { return result }

        let samplesCount = sampleDataBytes.count / bytesPerSample
        guard samplesCount > 0 else { return result }

        let timeStamps = computeTimeStamps(
            frame: frame,
            samplesCount: samplesCount
        )

        var offset = 0
        for sampleIndex in 0..<samplesCount {
            var methodValues: [Int: [Int32]] = [:]
            for methodId in effectiveMethods {
                let isUnsigned = unsignedMethods.contains(methodId)
                if componentMethods.contains(methodId) {
                    let x = readValue(sampleDataBytes, offset: offset, length: sampleSizeBytes, unsigned: isUnsigned); offset += sampleSizeBytes
                    let y = readValue(sampleDataBytes, offset: offset, length: sampleSizeBytes, unsigned: isUnsigned); offset += sampleSizeBytes
                    let z = readValue(sampleDataBytes, offset: offset, length: sampleSizeBytes, unsigned: isUnsigned); offset += sampleSizeBytes
                    methodValues[methodId] = [x, y, z]
                } else {
                    let v = readValue(sampleDataBytes, offset: offset, length: sampleSizeBytes, unsigned: true); offset += sampleSizeBytes
                    methodValues[methodId] = [v]
                }
            }
            result.derivedSamples.append(DerivedAccSample(timeStamp: timeStamps[sampleIndex], methodValues: methodValues))
        }
        return result
    }

    private static func computeTimeStamps(frame: PmdDataFrame, samplesCount: Int) -> [UInt64] {
        if samplesCount == 1 || frame.previousTimeStamp == 0 {
            return Array(repeating: frame.timeStamp, count: samplesCount)
        }
        let delta = Double(frame.timeStamp - frame.previousTimeStamp) / Double(samplesCount)
        return (0..<samplesCount).map { i in
            i == samplesCount - 1
                ? frame.timeStamp
                : frame.previousTimeStamp + UInt64(delta * Double(i + 1))
        }
    }

    private static func sampleSizeFromFrameType(_ frameType: PmdDataFrameType) -> Int {
        switch frameType {
        case .type_0: return 2
        case .type_1: return 4
        case .type_2: return 3
        default: return 2 // fallback
        }
    }

    private static func readValue(_ data: Data, offset: Int, length: Int, unsigned: Bool) -> Int32 {
        guard offset + length <= data.count else { return 0 }
        if unsigned {
            var uVal: UInt32 = 0
            for i in 0..<min(length, 4) {
                uVal |= UInt32(data[offset + i]) << (i * 8)
            }
            return Int32(bitPattern: uVal)
        } else {
            return TypeUtils.convertArrayToSignedInt(data, offset: offset, size: length)
        }
    }
}

