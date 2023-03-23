//  Copyright © 2022 Polar. All rights reserved.

import Foundation

public class PpiData {
    
    struct PpiSample {
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
            throw BleGattException.gattDataError(description: "Compressed FrameType: \(frame.frameType) is not supported by PPI data parser")
        } else {
            switch (frame.frameType) {
            case PmdDataFrameType.type_0: return try dataFromRawType0(frame: frame)
            default: throw BleGattException.gattDataError(description: "Raw FrameType: \(frame.frameType) is not supported by PPI data parser")
            }
        }
    }
    
    private static func dataFromRawType0(frame: PmdDataFrame) throws -> PpiData {
        return PpiData(samples: stride(from: 0, to: frame.dataContent.count, by: PPI_SAMPLE_CHUNK)
            .map { (start) -> Data in
                return frame.dataContent.subdata(in: start..<start.advanced(by: PPI_SAMPLE_CHUNK))
            }
            .map { (data) -> PpiSample in
                let hr = Int(data[0])
                let ppInMs = UInt16(UInt16(data[2]) << 8 | UInt16(data[1]))
                let ppErrorEstimate = UInt16(UInt16(data[4]) << 8 | UInt16(data[3]))
                let blockerBit = Int(data[5]) & 0x01
                let skinContactStatus = (Int(data[5]) & 0x02) >> 1
                let skinContactSupported = (Int(data[5]) & 0x04) >> 2
                return PpiSample(
                    hr: hr,
                    ppInMs: ppInMs,
                    ppErrorEstimate: ppErrorEstimate,
                    blockerBit: blockerBit,
                    skinContactStatus: skinContactStatus,
                    skinContactSupported: skinContactSupported)
            })
    }
}
