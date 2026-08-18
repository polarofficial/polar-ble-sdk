//  Copyright © 2022 Polar. All rights reserved.

import Foundation

public class PpiData {
    
    struct PpiSample {
        var timeStamp: UInt64
        let hr: Int
        let ppInMs: UInt16
        let ppErrorEstimate: UInt16
        let blockerBit: Int
        let skinContactStatus: Int
        let skinContactSupported: Int
    }
    
    var samples: [PpiSample]
    
    init(samples: [PpiSample] = []) {
        self.samples = samples
    }
    
    private static let PPI_SAMPLE_CHUNK = 6
    
    static func parseDataFromDataFrame(frame: PmdDataFrame) throws -> PpiData {
        if (frame.isCompressedFrame) {
            throw PmdDataParseError(message: "Compressed FrameType: \(frame.frameType) is not supported by PPI data parser")
        } else {
            switch (frame.frameType) {
            case PmdDataFrameType.type_0: return try dataFromRawType0(frame: frame)
            default: throw PmdDataParseError(message: "Raw FrameType: \(frame.frameType) is not supported by PPI data parser")
            }
        }
    }
    
    private static func dataFromRawType0(frame: PmdDataFrame) throws -> PpiData {
        guard frame.dataContent.count % PPI_SAMPLE_CHUNK == 0 else {
            throw PmdDataParseError(
                message: "PPI raw TYPE_0 dataContent size \(frame.dataContent.count) is not a " +
                "non-zero multiple of expected sample chunk size \(PPI_SAMPLE_CHUNK)")
        }
        let data = PpiData(samples: try stride(from: 0, to: frame.dataContent.count, by: PPI_SAMPLE_CHUNK)
            .map { (start) -> Data in
                let end = start.advanced(by: PPI_SAMPLE_CHUNK)
                guard end <= frame.dataContent.count else {
                    throw PmdDataParseError(message: "Incomplete PPI sample chunk at offset \(start): expected \(PPI_SAMPLE_CHUNK) bytes")
                }
                return frame.dataContent.subdata(in: start..<end)
            }
            .map { (data) throws -> PpiSample in
                guard data.count >= PPI_SAMPLE_CHUNK else {
                    throw PmdDataParseError(message: "Insufficient data for PPI sample: expected \(PPI_SAMPLE_CHUNK) bytes, got \(data.count)")
                }

                let hr = Int(data[0])
                let ppInMs = UInt16(UInt16(data[2]) << 8 | UInt16(data[1]))
                let ppErrorEstimate = UInt16(UInt16(data[4]) << 8 | UInt16(data[3]))
                let blockerBit = Int(data[5]) & 0x01
                let skinContactStatus = (Int(data[5]) & 0x02) >> 1
                let skinContactSupported = (Int(data[5]) & 0x04) >> 2
                return PpiSample(
                    timeStamp: 0, // time stamp will set below
                    hr: hr,
                    ppInMs: ppInMs,
                    ppErrorEstimate: ppErrorEstimate,
                    blockerBit: blockerBit,
                    skinContactStatus: skinContactStatus,
                    skinContactSupported: skinContactSupported)
            }
        )

        if (frame.timeStamp != 0) {
            var currentTimeStamp: UInt64 = frame.timeStamp
            var currentIndex = data.samples.count - 1
            for (sample) in data.samples.reversed() {
                data.samples[currentIndex].timeStamp = currentTimeStamp
                currentIndex -= 1
                currentTimeStamp -= UInt64(sample.ppInMs) * 1000000
            }
        }

        return data
    }
}
