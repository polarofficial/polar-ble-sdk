//  Copyright © 2022 Polar. All rights reserved.

import Foundation

/// Offline recording errors
public enum OfflineRecordingError: Error {
    case emptyFile
    case offlineRecordingErrorMetaDataParseFailed(description: String = "")
    case offlineRecordingErrorSecretMissing
    case offlineRecordingSecurityStrategyMissMatch(description: String = "")
    case offlineRecordingHasWrongSignature
    case offlineRecordingNoPayloadData
    case offlineRecordingErrorNoParserForData
}
