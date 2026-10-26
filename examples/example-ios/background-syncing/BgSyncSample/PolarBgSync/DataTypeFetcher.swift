// Copyright © 2026 Polar Electro Oy. All rights reserved.

import Foundation
import PolarBleSdk
import os.log

/// Fetches data from the device for each SyncableDataType using the appropriate SDK API,
/// then saves results to local storage.
@MainActor
class DataTypeFetcher {

    private static let logger = Logger(subsystem: "com.polar.bgsync", category: "DataFetcher")
    private static let defaultLookbackDays = 30
    private static let lastSyncKeyPrefix = "polar_bg_sync_last_"

    private let api: PolarBleApi
    private let localDataStore: LocalDataStore
    private let syncStateTracker: SyncStateTracker
    private let autoDeleteFromDevice: Bool
    private let offlineRecordingRetentionDays: Int
    private let onDiagnosticMessage: ((String) -> Void)?
    private let resumeDate: Date?
    private let onDayCompleted: ((Date) -> Void)?
    private let onResults: (([SyncResult]) -> Void)?
    private let hadSavedResults: Bool
    private let jsonEncoder = JSONEncoder()

    init(api: PolarBleApi, localDataStore: LocalDataStore,
         syncStateTracker: SyncStateTracker,
         autoDeleteFromDevice: Bool = false, offlineRecordingRetentionDays: Int = 0,
         onDiagnosticMessage: ((String) -> Void)? = nil,
         resumeDate: Date? = nil,
         onDayCompleted: ((Date) -> Void)? = nil,
         onResults: (([SyncResult]) -> Void)? = nil,
         hadSavedResults: Bool = false) {
        self.api = api
        self.localDataStore = localDataStore
        self.syncStateTracker = syncStateTracker
        self.autoDeleteFromDevice = autoDeleteFromDevice
        self.offlineRecordingRetentionDays = offlineRecordingRetentionDays
        self.onDiagnosticMessage = onDiagnosticMessage
        self.resumeDate = resumeDate
        self.onDayCompleted = onDayCompleted
        self.onResults = onResults
        self.hadSavedResults = hadSavedResults
        jsonEncoder.dateEncodingStrategy = .iso8601
        // Note: Save compact JSON; pretty-printing happens on read for consistency across platforms
    }

    func fetchAndSave(deviceId: String, dataType: SyncableDataType) async throws -> [SyncResult] {
        // Use local calendar: SDK dates represent local calendar dates (midnight local time),
        // matching Android's LocalDate.now() semantics.
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())
        var sdkCalendar = calendar
        if [.steps, .hrSamples247, .ppiSamples247].contains(dataType) {
            // Preserve the SDK's UTC range interpretation while splitting it into days.
            sdkCalendar = Calendar(identifier: .gregorian)
            sdkCalendar.timeZone = TimeZone(secondsFromGMT: 0)!
        }

        // For date-range types, use the sync watermark to skip already-synced past days.
        // For reference-based types (training sessions, offline recordings),
        // path-level dedup happens inside their fetch methods.
        let fromDate: Date
        if !dataType.isOfflineRecording && dataType != .trainingSessions {
            if let watermark = syncStateTracker.getWatermark(deviceId: deviceId, dataType: dataType) {
                let nextDate = calendar.date(byAdding: .day, value: 1, to: watermark)!
                if nextDate > today {
                    Self.logger.debug("Skipping \(dataType.rawValue) for \(deviceId) — fully synced")
                    return []
                }
                fromDate = nextDate
            } else {
                fromDate = getLastSyncDate(deviceId: deviceId, dataType: dataType)
                    ?? calendar.date(byAdding: .day, value: -Self.defaultLookbackDays, to: Date())!
            }
        } else {
            fromDate = getLastSyncDate(deviceId: deviceId, dataType: dataType)
                ?? calendar.date(byAdding: .day, value: -Self.defaultLookbackDays, to: Date())!
        }
        // iOS SDK activity APIs iterate days using an inclusive range (`currentDate <= toDate`).
        // Use today's start-of-day so the current day is included once and tomorrow is not fetched.
        let toDate = today

        Self.logger.debug("Fetching \(dataType.rawValue) for \(deviceId)")

        do {
            try Task.checkCancellation()
            let results: [SyncResult]
            if !dataType.isOfflineRecording && dataType != .trainingSessions {
                results = try await CalendarDayFetcher.fetch(
                    from: max(sdkCalendar.startOfDay(for: fromDate), resumeDate ?? .distantPast),
                    to: toDate, calendar: sdkCalendar,
                    onDayCompleted: { day in
                        self.onDayCompleted?(sdkCalendar.date(byAdding: .day, value: 1, to: day)!)
                    }
                ) { day in
                    self.onDiagnosticMessage?("PHASE:FETCH_DAY — \(dataType.rawValue) \(day): requesting")
                    let values = try await self.fetchData(
                        deviceId: deviceId, dataType: dataType, from: day, to: day)
                    try Task.checkCancellation()
                    self.onResults?(values)
                    self.onDiagnosticMessage?("PHASE:FETCH_DAY — \(dataType.rawValue) \(day): \(values.count) entries")
                    return values
                }
            } else {
                results = try await fetchData(deviceId: deviceId, dataType: dataType, from: fromDate, to: toDate)
            }
            try Task.checkCancellation()
            if !results.isEmpty || hadSavedResults {
                saveLastSyncDate(deviceId: deviceId, dataType: dataType, date: toDate)
                // Advance the watermark for date-range types
                if !dataType.isOfflineRecording && dataType != .trainingSessions {
                    syncStateTracker.advanceWatermark(
                        deviceId: deviceId, dataType: dataType, syncedUpTo: toDate)
                }
            }
            return results
        } catch {
            if Task.isCancelled { throw CancellationError() }
            Self.logger.error("SDK fetch error for \(dataType.rawValue) [\(deviceId)]: \(error)")
            throw error
        }
    }

    private func fetchData(
        deviceId: String, dataType: SyncableDataType,
        from: Date, to: Date
    ) async throws -> [SyncResult] {
        switch dataType {
        case .steps:
            let dataList = try await api.getSteps(identifier: deviceId, fromDate: from, toDate: to)
            return try saveDataList(deviceId: deviceId, dataType: dataType, items: dataList,
                                dateExtractor: { $0.date })
        case .distance:
            let dataList = try await api.getDistance(identifier: deviceId, fromDate: from, toDate: to)
            return try saveDataList(deviceId: deviceId, dataType: dataType, items: dataList,
                                dateExtractor: { $0.date })
        case .activeTime:
            let dataList = try await api.getActiveTime(identifier: deviceId, fromDate: from, toDate: to)
            return try saveDataList(deviceId: deviceId, dataType: dataType, items: dataList,
                                dateExtractor: { $0.date })
        case .calories:
            let dataList = try await api.getCalories(identifier: deviceId, fromDate: from, toDate: to, caloriesType: .activity)
            return try saveDataList(deviceId: deviceId, dataType: dataType, items: dataList,
                                dateExtractor: { $0.date })
        case .hrSamples247:
            let dataList = try await api.get247HrSamples(identifier: deviceId, fromDate: from, toDate: to)
            return try saveDataList(deviceId: deviceId, dataType: dataType, items: dataList,
                                dateExtractor: { self.dateFromComponents($0.date) })
        case .ppiSamples247:
            let dataList = try await api.get247PPiSamples(identifier: deviceId, fromDate: from, toDate: to)
            // The SDK returns one entry per PPI sample session — multiple entries can share the same date.
            // Group by date and save all sessions for a day as a JSON array to avoid overwriting.
            return try saveGroupedDataList(deviceId: deviceId, dataType: dataType, items: dataList,
                                       dateExtractor: { self.dateFromComponents($0.date) })
        case .nightlyRecharge:
            let dataList = try await api.getNightlyRecharge(identifier: deviceId, fromDate: from, toDate: to)
            return try saveDataList(deviceId: deviceId, dataType: dataType, items: dataList,
                                dateExtractor: { $0.createdTimestamp })
        case .activitySamples:
            let dataList = try await api.getActivitySampleData(identifier: deviceId, fromDate: from, toDate: to)
            return try saveDataList(deviceId: deviceId, dataType: dataType, items: dataList,
                                dateExtractor: { $0.polarActivityDataList.first?.samples?.startTime ?? from })
        case .dailySummary:
            let dataList = try await api.getDailySummaryData(identifier: deviceId, fromDate: from, toDate: to)
            return try saveDataList(deviceId: deviceId, dataType: dataType, items: dataList,
                                dateExtractor: { $0.date })
        case .sleep:
            let values = try await api.getSleep(identifier: deviceId, fromDate: from, toDate: to)
            return try saveDataList(deviceId: deviceId, dataType: dataType, items: values,
                                   dateExtractor: { $0.sleepStartTime })
        case .trainingSessions:
            return try await fetchTrainingSessions(deviceId: deviceId, from: from, to: to)
        case .skinTemperature:
            let dataList = try await api.getSkinTemperature(identifier: deviceId, fromDate: from, toDate: to)
            return try saveDataList(deviceId: deviceId, dataType: dataType, items: dataList,
                                dateExtractor: { $0.date })
        case .offlineAcc, .offlineDerivedAcc, .offlineEcg, .offlineGyro, .offlineMagnetometer,
               .offlinePpg, .offlinePpi, .offlineHr, .offlineTemperature, .offlineSkinTemperature:
            return try await fetchOfflineRecordings(deviceId: deviceId, dataType: dataType)
        }
    }

    private func fetchTrainingSessions(
        deviceId: String, from: Date, to: Date
    ) async throws -> [SyncResult] {
        let references = try await api.getTrainingSessionReferences(identifier: deviceId, fromDate: from, toDate: to)
        try Task.checkCancellation()
        guard !references.isEmpty else { return [] }

        // Filter out already-synced training sessions
        let newRefs = references.filter { ref in
            let alreadySynced = syncStateTracker.isPathSynced(
                deviceId: deviceId, dataType: .trainingSessions, path: ref.path)
            if alreadySynced {
                Self.logger.debug("Skipping already-synced training session: \(ref.path)")
            }
            return !alreadySynced
        }
        guard !newRefs.isEmpty else {
            Self.logger.debug("All \(references.count) training session(s) already synced")
            return []
        }

        var results: [SyncResult] = []
        for ref in newRefs {
            do {
                try Task.checkCancellation()
                let session = try await api.getTrainingSession(
                    identifier: deviceId,
                    trainingSessionReference: ref
                )
                try Task.checkCancellation()
                let json = serializeToJson(session)
                let sizeBytes = Int64(json.utf8.count)
                let path = try localDataStore.saveData(
                    deviceId: deviceId,
                    dataType: .trainingSessions,
                    date: ref.date,
                    jsonContent: json
                )
                try Task.checkCancellation()
                syncStateTracker.markPathSynced(
                    deviceId: deviceId, dataType: .trainingSessions, path: ref.path)
                let result = SyncResult(
                    deviceId: deviceId,
                    dataType: .trainingSessions,
                    date: ref.date,
                    localFilePath: path,
                    sizeBytes: sizeBytes
                )
                results.append(result)
                onResults?([result])
            } catch {
                if Task.isCancelled { throw CancellationError() }
                Self.logger.error("Failed to fetch training session \(ref.path): \(error)")
                throw error
            }
        }
        return results
    }

    // MARK: - Offline recording fetch

    private func fetchOfflineRecordings(
        deviceId: String, dataType: SyncableDataType
    ) async throws -> [SyncResult] {
        guard let deviceDataType = dataType.toPolarDeviceDataType() else {
            return []
        }

        // Collect matching entries from the stream
        var allEntries: [PolarOfflineRecordingEntry] = []
        for try await entry in api.listOfflineRecordings(deviceId) {
            try Task.checkCancellation()
            if entry.type == deviceDataType {
                allEntries.append(entry)
            }
        }
        guard !allEntries.isEmpty else { return [] }

        // Filter out already-synced offline recordings
        let entries = allEntries.filter { entry in
            let alreadySynced = self.syncStateTracker.isPathSynced(
                deviceId: deviceId, dataType: dataType, path: entry.path)
            if alreadySynced {
                Self.logger.debug("Skipping already-synced offline recording: \(entry.path)")
            }
            return !alreadySynced
        }
        guard !entries.isEmpty else {
            Self.logger.debug("All \(allEntries.count) offline \(dataType.rawValue) recording(s) already synced")
            return []
        }
        Self.logger.debug("Found \(entries.count) new offline \(dataType.rawValue) recording(s) (\(allEntries.count - entries.count) already synced)")

        var results: [SyncResult] = []
        for entry in entries {
            do {
                try Task.checkCancellation()
                let recordingData = try await api.getOfflineRecord(deviceId, entry: entry, secret: nil)
                try Task.checkCancellation()
                guard self.matchesOfflineRecordingDataType(dataType, recordingData: recordingData) else {
                    Self.logger.debug("Skipping \(entry.path): payload does not match requested \(dataType.rawValue)")
                    continue
                }
                let json = serializeOfflineRecordingToJson(recordingData)
                let bytes = Data(json.utf8)
                let path = try localDataStore.saveRawData(
                    deviceId: deviceId,
                    dataType: dataType,
                    dateTime: entry.date,
                    data: bytes
                )
                try Task.checkCancellation()
                syncStateTracker.markPathSynced(
                    deviceId: deviceId, dataType: dataType, path: entry.path)
                let result = SyncResult(
                    deviceId: deviceId,
                    dataType: dataType,
                    date: entry.date,
                    localFilePath: path,
                    sizeBytes: Int64(bytes.count)
                )
                results.append(result)
                onResults?([result])

                // Remove recording from device if auto-delete is enabled and retention period has passed
                if self.autoDeleteFromDevice {
                    try Task.checkCancellation()
                    var calendar = Calendar(identifier: .gregorian)
                    calendar.timeZone = TimeZone(abbreviation: "UTC") ?? TimeZone(secondsFromGMT: 0)!
                    let cutoffDate = calendar.date(
                        byAdding: .day, value: -self.offlineRecordingRetentionDays, to: Date())!
                    if entry.date <= cutoffDate {
                        do {
                            try await api.removeOfflineRecord(deviceId, entry: entry)
                            Self.logger.debug("Fetched & removed offline recording: \(entry.path)")
                        } catch {
                            if Task.isCancelled { throw CancellationError() }
                            Self.logger.warning("Failed to remove offline recording \(entry.path): \(error)")
                            throw error
                        }
                    } else {
                        Self.logger.debug("Fetched offline recording (kept on device, retention \(self.offlineRecordingRetentionDays)d): \(entry.path)")
                    }
                } else {
                    Self.logger.debug("Fetched offline recording (auto-delete disabled): \(entry.path)")
                }
            } catch {
                if Task.isCancelled { throw CancellationError() }
                Self.logger.error("Failed to fetch offline recording \(entry.path): \(error)")
                throw error
            }
        }
        return results
    }

    // MARK: - Helpers

    private func saveDataList<T>(
        deviceId: String,
        dataType: SyncableDataType,
        items: [T],
        dateExtractor: (T) -> Date
    ) throws -> [SyncResult] {
        try Task.checkCancellation()
        return try items.map { item in
            try Task.checkCancellation()
            let date = dateExtractor(item)
            let json = serializeToJson(item)
            let sizeBytes = Int64(json.utf8.count)
            let path = try localDataStore.saveData(
                deviceId: deviceId, dataType: dataType,
                date: date, jsonContent: json)
            return SyncResult(deviceId: deviceId, dataType: dataType,
                              date: date, localFilePath: path, sizeBytes: sizeBytes)
        }
    }

    /// Groups items by date and saves all items for a given date as a JSON array (one file per date).
    /// Use for data types where the SDK returns multiple entries per date (e.g. PPI samples).
    private func saveGroupedDataList<T>(
        deviceId: String,
        dataType: SyncableDataType,
        items: [T],
        dateExtractor: (T) -> Date
    ) throws -> [SyncResult] {
        try Task.checkCancellation()
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(abbreviation: "UTC") ?? TimeZone(secondsFromGMT: 0)!
        let grouped = Dictionary(grouping: items) { item in
            calendar.startOfDay(for: dateExtractor(item))
        }
        return try grouped.map { (date, entries) in
            try Task.checkCancellation()
            let jsonArray = "[" + entries.map { serializeToJson($0) }.joined(separator: ",") + "]"
            let sizeBytes = Int64(jsonArray.utf8.count)
            let path = try localDataStore.saveData(
                deviceId: deviceId, dataType: dataType,
                date: date, jsonContent: jsonArray)
            return SyncResult(deviceId: deviceId, dataType: dataType,
                              date: date, localFilePath: path, sizeBytes: sizeBytes)
        }
    }

    /// Serialize PolarOfflineRecordingData to a JSON string.
    /// All SDK offline data types are Swift tuple typealiases (not Codable), so this
    /// manually constructs JSON dictionaries for each enum case.
    private func serializeOfflineRecordingToJson(_ data: PolarOfflineRecordingData) -> String {
        let iso = ISO8601DateFormatter()
        let dict: [String: Any]

        switch data {
        case .accOfflineRecordingData(let accData, let startTime, let settings):
            dict = [
                "type": "acc",
                "startTime": iso.string(from: startTime),
                "settings": settingsToDict(settings),
                "samples": accData.map { [
                    "timeStamp": NSNumber(value: $0.timeStamp),
                    "x": $0.x, "y": $0.y, "z": $0.z
                ] }
            ]
        case .gyroOfflineRecordingData(let gyroData, let startTime, let settings):
            dict = [
                "type": "gyro",
                "startTime": iso.string(from: startTime),
                "settings": settingsToDict(settings),
                "samples": gyroData.map { [
                    "timeStamp": NSNumber(value: $0.timeStamp),
                    "x": $0.x, "y": $0.y, "z": $0.z
                ] }
            ]
        case .magOfflineRecordingData(let magData, let startTime, let settings):
            dict = [
                "type": "magnetometer",
                "startTime": iso.string(from: startTime),
                "settings": settingsToDict(settings),
                "samples": magData.map { [
                    "timeStamp": NSNumber(value: $0.timeStamp),
                    "x": $0.x, "y": $0.y, "z": $0.z
                ] }
            ]
        case .ppgOfflineRecordingData(let ppgData, let startTime, let settings):
            dict = [
                "type": "ppg",
                "startTime": iso.string(from: startTime),
                "settings": settingsToDict(settings),
                "ppgType": "\(ppgData.type)",
                "samples": ppgData.samples.map { s -> [String: Any] in
                    var sDict: [String: Any] = [
                        "timeStamp": NSNumber(value: s.timeStamp),
                        "channelSamples": s.channelSamples
                    ]
                    if let sb = s.statusBits { sDict["statusBits"] = sb.map { Int($0) } }
                    return sDict
                }
            ]
        case .ppiOfflineRecordingData(let ppiData, let startTime):
            dict = [
                "type": "ppi",
                "startTime": iso.string(from: startTime),
                "timeStamp": NSNumber(value: ppiData.timeStamp),
                "samples": ppiData.samples.map { [
                    "timeStamp": NSNumber(value: $0.timeStamp),
                    "hr": $0.hr,
                    "ppInMs": Int($0.ppInMs),
                    "ppErrorEstimate": Int($0.ppErrorEstimate),
                    "blockerBit": $0.blockerBit,
                    "skinContactStatus": $0.skinContactStatus,
                    "skinContactSupported": $0.skinContactSupported
                ] }
            ]
        case .hrOfflineRecordingData(let hrData, let startTime):
            dict = [
                "type": "hr",
                "startTime": iso.string(from: startTime),
                "samples": hrData.map { [
                    "hr": Int($0.hr),
                    "ppgQuality": Int($0.ppgQuality),
                    "correctedHr": Int($0.correctedHr),
                    "rrsMs": $0.rrsMs,
                    "rrAvailable": $0.rrAvailable,
                    "contactStatus": $0.contactStatus,
                    "contactStatusSupported": $0.contactStatusSupported
                ] }
            ]
        case .temperatureOfflineRecordingData(let tempData, let startTime):
            dict = [
                "type": "temperature",
                "startTime": iso.string(from: startTime),
                "timeStamp": NSNumber(value: tempData.timeStamp),
                "samples": tempData.samples.map { [
                    "timeStamp": NSNumber(value: $0.timeStamp),
                    "temperature": $0.temperature
                ] }
            ]
        case .skinTemperatureOfflineRecordingData(let tempData, let startTime):
            dict = [
                "type": "skinTemperature",
                "startTime": iso.string(from: startTime),
                "timeStamp": NSNumber(value: tempData.timeStamp),
                "samples": tempData.samples.map { [
                    "timeStamp": NSNumber(value: $0.timeStamp),
                    "temperature": $0.temperature
                ] }
            ]
        case .emptyData(let startTime):
            dict = ["type": "empty", "startTime": iso.string(from: startTime)]
        case .derivedAccOfflineRecordingData(let derivedData, startTime: let startTime, settings: let settings):
            let samples = derivedData.samples.map { sample -> [String: Any] in
                let methodValues = Dictionary(uniqueKeysWithValues: sample.methodValues.map {
                    ($0.key.csvLabel, $0.value.map { Int($0) })
                })
                return [
                    "timeStamp": NSNumber(value: sample.timeStamp),
                    "activeMethods": sample.activeMethods.map { $0.csvLabel }.sorted(),
                    "methodValues": methodValues
                ]
            }
            var derivedDict: [String: Any] = [
                "type": "derivedAcc",
                "startTime": iso.string(from: startTime),
                "samples": samples
            ]
            if let settings {
                derivedDict["settings"] = settingsToDict(settings)
            }
            dict = derivedDict
        }

        if let jsonData = try? JSONSerialization.data(withJSONObject: dict, options: []),
           let jsonStr = String(data: jsonData, encoding: .utf8) {
            return jsonStr
        }
        return "{}"
    }

    private func settingsToDict(_ settings: PolarSensorSetting) -> [String: Any] {
        var dict: [String: Any] = [:]
        for (key, values) in settings.settings {
            dict["\(key)"] = values.map { Int($0) }
        }
        return dict
    }

    private func serializeToJson<T>(_ value: T) -> String {
        // Use String(describing:) as a fallback since SDK models may not be Codable
        if let encodable = value as? Encodable {
            if let data = try? jsonEncoder.encode(AnyEncodable(encodable)),
               let str = String(data: data, encoding: .utf8) {
                return str
            }
        }
        return String(describing: value)
    }

    private func getLastSyncDate(deviceId: String, dataType: SyncableDataType) -> Date? {
        let key = "\(Self.lastSyncKeyPrefix)\(deviceId)_\(dataType.rawValue)"
        return UserDefaults.standard.object(forKey: key) as? Date
    }

    private func saveLastSyncDate(deviceId: String, dataType: SyncableDataType, date: Date) {
        let key = "\(Self.lastSyncKeyPrefix)\(deviceId)_\(dataType.rawValue)"
        UserDefaults.standard.set(date, forKey: key)
    }

    /// Convert DateComponents to Date in UTC timezone.
    /// DateComponents from the SDK (like for HR samples) represent a calendar date without timezone info.
    /// We treat them as UTC to ensure consistent date handling across platforms.
    private func dateFromComponents(_ components: DateComponents) -> Date {
        var utcCal = Calendar(identifier: .gregorian)
        utcCal.timeZone = TimeZone(abbreviation: "UTC") ?? TimeZone(secondsFromGMT: 0)!
        return utcCal.date(from: components) ?? Date()
    }
}

/// Type-erasing wrapper for Encodable.
private struct AnyEncodable: Encodable {
    private let _encode: (Encoder) throws -> Void

    init(_ wrapped: Encodable) {
        _encode = { encoder in
            try wrapped.encode(to: encoder)
        }
    }

    func encode(to encoder: Encoder) throws {
        try _encode(encoder)
    }
}

private extension SyncableDataType {
    func toPolarDeviceDataType() -> PolarDeviceDataType? {
        switch self {
        case .offlineAcc: return .acc
        case .offlineDerivedAcc: return .acc
        case .offlineEcg: return .ecg
        case .offlineGyro: return .gyro
        case .offlineMagnetometer: return .magnetometer
        case .offlinePpg: return .ppg
        case .offlinePpi: return .ppi
        case .offlineHr: return .hr
        case .offlineTemperature: return .temperature
        case .offlineSkinTemperature: return .skinTemperature
        default: return nil
        }
    }
}

private extension DataTypeFetcher {
    func matchesOfflineRecordingDataType(_ requestedType: SyncableDataType, recordingData: PolarOfflineRecordingData) -> Bool {
        switch requestedType {
        case .offlineAcc:
            if case .accOfflineRecordingData = recordingData { return true }
            return false
        case .offlineDerivedAcc:
            if case .derivedAccOfflineRecordingData = recordingData { return true }
            return false
        default:
            return true
        }
    }
}
