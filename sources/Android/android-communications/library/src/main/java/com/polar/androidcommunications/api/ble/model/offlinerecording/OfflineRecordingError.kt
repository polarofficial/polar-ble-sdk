package com.polar.androidcommunications.api.ble.model.offlinerecording

sealed class OfflineRecordingError(detailMessage: String) : Exception(detailMessage) {
    object OfflineRecordingEmptyFile : OfflineRecordingError("")
    object OfflineRecordingNoPayloadData : OfflineRecordingError("")
    object OfflineRecordingHasWrongSignature : OfflineRecordingError("")
    object OfflineRecordingErrorSecretMissing : OfflineRecordingError("")
    object OfflineRecordingErrorNoParserForData : OfflineRecordingError("")
    class OfflineRecordingSecurityStrategyMissMatch(detailMessage: String) : OfflineRecordingError(detailMessage)
    class OfflineRecordingErrorMetaDataParseFailed(detailMessage: String) : OfflineRecordingError(detailMessage)
}

