//  Copyright © 2023 Polar. All rights reserved.

import Foundation

public class OfflineHrData {
    
    struct OfflineHrSample {
        let hr: UInt8
        let ppgQuality: UInt8
        let correctedHr: UInt8
    }
    
    var samples: [OfflineHrSample]
    
    init(samples: [OfflineHrSample] = []) {
        self.samples = samples
    }
    
    static func parseDataFromDataFrame(frame: PmdDataFrame) throws -> OfflineHrData {
        if (frame.isCompressedFrame) {
            throw PmdDataParseError(message: "Compressed FrameType: \(frame.frameType) is not supported by Offline HR data parser")
        } else {
            switch (frame.frameType) {
            case PmdDataFrameType.type_0: return try dataFromRawType0(frame: frame)
            case PmdDataFrameType.type_1: return try dataFromRawType1(frame: frame)
            default: throw PmdDataParseError(message: "Raw FrameType: \(frame.frameType) is not supported by Offline HR data parser")
            }
        }
    }
    
    private static func dataFromRawType0(frame: PmdDataFrame) throws -> OfflineHrData {
        let offlineHrData = OfflineHrData()
        var offset = 0
        while (offset < frame.dataContent.count) {
            offlineHrData.samples.append(OfflineHrSample(hr: UInt8(frame.dataContent[offset]), ppgQuality: 0, correctedHr: 0))
            offset += 1
        }
        return offlineHrData
    }

    private static func dataFromRawType1(frame: PmdDataFrame) throws -> OfflineHrData {
        let sampleByteSize = 3
        guard !frame.dataContent.isEmpty && frame.dataContent.count % sampleByteSize == 0 else {
            throw PmdDataParseError(
                message: "OfflineHR raw TYPE_1 dataContent size \(frame.dataContent.count) is not a " +
                "non-zero multiple of expected sample size \(sampleByteSize)")
        }
        let offlineHrData = OfflineHrData()
        var offset = 0
        while (offset < frame.dataContent.count) {
            let hr = UInt8(frame.dataContent[offset])
            offset += 1
            let ppgQual = UInt8(frame.dataContent[offset])
            offset += 1
            let correctedHr = UInt8(frame.dataContent[offset])
            offlineHrData.samples.append(OfflineHrSample(hr: hr, ppgQuality: ppgQual, correctedHr: correctedHr))
            offset += 1
        }
        return offlineHrData
    }
}
