//  Copyright © 2024 Polar. All rights reserved.

import Foundation

private let ARABICA_USER_ROOT_FOLDER = "/U/0/"
private let ACTIVITY_DIRECTORY = "ACT/"
private let DAILY_SUMMARY_DIRECTORY = "DSUM/"
private let DAILY_SUMMARY_PROTO = "DSUM.BPB"
private let dateFormat: DateFormatter = {
    let formatter = DateFormatter()
    formatter.dateFormat = "yyyyMMdd"
    formatter.locale = Locale(identifier: "en_US_POSIX")
    return formatter
}()
private let TAG = "PolarActivityUtils"

internal class PolarActivityUtils {

    /// Read step count for given date.
    static func readStepsFromDayDirectory(client: BlePsFtpClient, date: Date) async throws -> Int {
        BleLogger.trace(TAG, "readStepsFromDayDirectory: \(date)")
        let activityFileDir = "\(ARABICA_USER_ROOT_FOLDER)\(dateFormat.string(from: date))/\(ACTIVITY_DIRECTORY)"
        let filePaths: [String]
        do {
            filePaths = try await listFiles(client: client, folderPath: activityFileDir) { entry in
                entry.matches("^\(activityFileDir)") || entry == "ASAMPL" || entry.contains(".BPB")
            }
        } catch {
            BleLogger.error("Failed to list Activity sample files.")
            return 0
        }
        guard !filePaths.isEmpty else { return 0 }
        var stepCount: UInt32 = 0
        for path in filePaths {
            let operation = Protocol_PbPFtpOperation.with { $0.command = .get; $0.path = path }
            do {
                let response = try await client.request(try operation.serializedBytes())
                let proto = try Data_PbActivitySamples(serializedBytes: Data(response))
                stepCount += proto.stepsSamples.reduce(0, +)
            } catch {
                BleLogger.error("readStepsFromDayDirectory() failed for path: \(activityFileDir), error: \(error)")
                return 0
            }
        }
        return Int(stepCount)
    }

    /// Read distance in meters for given date.
    static func readDistanceFromDayDirectory(client: BlePsFtpClient, date: Date) async throws -> Float {
        BleLogger.trace(TAG, "readDistanceFromDayDirectory: \(date)")
        let dailySummaryFilePath = "\(ARABICA_USER_ROOT_FOLDER)\(dateFormat.string(from: date))/\(DAILY_SUMMARY_DIRECTORY)\(DAILY_SUMMARY_PROTO)"
        let operation = Protocol_PbPFtpOperation.with { $0.command = .get; $0.path = dailySummaryFilePath }
        do {
            let response = try await client.request(try operation.serializedBytes())
            let proto = try Data_PbDailySummary(serializedBytes: Data(response))
            return Float(proto.activityDistance)
        } catch {
            BleLogger.error("readDistanceFromDayDirectory() failed for path: \(dailySummaryFilePath), error: \(error)")
            return 0
        }
    }

    /// Read active time for given date.
    static func readActiveTimeFromDayDirectory(client: BlePsFtpClient, date: Date) async throws -> PolarActiveTimeData {
        BleLogger.trace(TAG, "readActiveTimeFromDayDirectory: \(date)")
        let dailySummaryFilePath = "\(ARABICA_USER_ROOT_FOLDER)\(dateFormat.string(from: date))/\(DAILY_SUMMARY_DIRECTORY)\(DAILY_SUMMARY_PROTO)"
        let operation = Protocol_PbPFtpOperation.with { $0.command = .get; $0.path = dailySummaryFilePath }
        do {
            let response = try await client.request(try operation.serializedBytes())
            let proto = try Data_PbDailySummary(serializedBytes: Data(response))
            return PolarActiveTimeData(
                date: date,
                timeNonWear: PolarActiveTime.fromProto(proto.activityClassTimes.timeNonWear),
                timeSleep: PolarActiveTime.fromProto(proto.activityClassTimes.timeSleep),
                timeSedentary: PolarActiveTime.fromProto(proto.activityClassTimes.timeSedentary),
                timeLightActivity: PolarActiveTime.fromProto(proto.activityClassTimes.timeLightActivity),
                timeContinuousModerateActivity: PolarActiveTime.fromProto(proto.activityClassTimes.timeContinuousModerate),
                timeIntermittentModerateActivity: PolarActiveTime.fromProto(proto.activityClassTimes.timeIntermittentModerate),
                timeContinuousVigorousActivity: PolarActiveTime.fromProto(proto.activityClassTimes.timeContinuousVigorous),
                timeIntermittentVigorousActivity: PolarActiveTime.fromProto(proto.activityClassTimes.timeIntermittentVigorous)
            )
        } catch {
            BleLogger.error("readActiveTimeFromDayDirectory() failed for path: \(dailySummaryFilePath), error: \(error)")
            return PolarActiveTimeData(date: date, timeNonWear: PolarActiveTime())
        }
    }

    /// Read calories for given date.
    static func readCaloriesFromDayDirectory(client: BlePsFtpClient, date: Date, caloriesType: CaloriesType) async throws -> Int {
        BleLogger.trace(TAG, "readCaloriesFromDayDirectory: \(date), type: \(caloriesType)")
        let dailySummaryFilePath = "\(ARABICA_USER_ROOT_FOLDER)\(dateFormat.string(from: date))/\(DAILY_SUMMARY_DIRECTORY)\(DAILY_SUMMARY_PROTO)"
        let operation = Protocol_PbPFtpOperation.with { $0.command = .get; $0.path = dailySummaryFilePath }
        do {
            let response = try await client.request(try operation.serializedBytes())
            let proto = try Data_PbDailySummary(serializedBytes: Data(response))
            switch caloriesType {
            case .activity: return Int(proto.activityCalories)
            case .training: return Int(proto.trainingCalories)
            case .bmr:      return Int(proto.bmrCalories)
            }
        } catch {
            BleLogger.error("readCaloriesFromDayDirectory() failed for path: \(dailySummaryFilePath), error: \(error)")
            return 0
        }
    }

    /// Read and return activity samples data for a given date.
    static func readActivitySamplesDataFromDayDirectory(client: BlePsFtpClient, date: Date) async throws -> PolarActivityDayData {
        BleLogger.trace(TAG, "readActivitySamplesDataFromDayDirectory: \(date)")
        let activityFileDir = "\(ARABICA_USER_ROOT_FOLDER)\(dateFormat.string(from: date))/\(ACTIVITY_DIRECTORY)"
        let filePaths: [String]
        do {
            filePaths = try await listFiles(client: client, folderPath: activityFileDir) { entry in
                entry.matches("^\(activityFileDir)") || entry == "ASAMPL" || entry.contains(".BPB")
            }
        } catch {
            if error.localizedDescription.contains("103") {
                BleLogger.error("No activity files found for date: \(dateFormat.string(from: date))")
                return PolarActivityDayData(polarActivityDataList: [])
            }
            BleLogger.error("Failed to list activity sample files.")
            throw error
        }
        guard !filePaths.isEmpty else {
            return PolarActivityDayData(polarActivityDataList: [])
        }
        var polarActivityDataList: [PolarActivityData] = []
        for path in filePaths {
            let operation = Protocol_PbPFtpOperation.with { $0.command = .get; $0.path = path }
            let response = try await client.request(try operation.serializedBytes())
            let proto = try Data_PbActivitySamples(serializedBytes: Data(response))
            let activitySamplesData = PolarActivityData.PolarActivitySamples(
                startTime: try PolarTimeUtils.pbLocalDateTimeToDate(pbLocalDateTime: proto.startTime),
                metRecordingInterval: PolarTimeUtils.pbDurationToMillis(pbDuration: proto.metRecordingInterval)/1000,
                metSamples: proto.metSamples,
                stepRecordingInterval: PolarTimeUtils.pbDurationToMillis(pbDuration: proto.stepsRecordingInterval)/1000,
                stepSamples: proto.stepsSamples,
                activityInfoList: try PolarActivityData.parsePbActivityInfoList(activityInfoList: proto.activityInfo)
            )
            let activityData = PolarActivityData()
            activityData.samples = activitySamplesData
            polarActivityDataList.append(activityData)
        }
        return PolarActivityDayData(polarActivityDataList: polarActivityDataList)
    }

    /// Read daily summary data for given date. Returns nil if not found (error 103).
    static func readDailySummaryDataFromDayDirectory(client: BlePsFtpClient, date: Date) async throws -> PolarDailySummary? {
        BleLogger.trace(TAG, "readDailySummaryDataFromDayDirectory: \(date)")
        let dailySummaryFilePath = "\(ARABICA_USER_ROOT_FOLDER)\(dateFormat.string(from: date))/\(DAILY_SUMMARY_DIRECTORY)\(DAILY_SUMMARY_PROTO)"
        let operation = Protocol_PbPFtpOperation.with { $0.command = .get; $0.path = dailySummaryFilePath }
        do {
            let response = try await client.request(try operation.serializedBytes())
            let proto = try Data_PbDailySummary(serializedBytes: Data(response))
            return try PolarDailySummary.fromProto(proto: proto)
        } catch {
            if error.localizedDescription.contains("103") {
                BleLogger.error("No activity files found for date: \(dateFormat.string(from: date))")
                return nil
            }
            BleLogger.error("Read daily summary failed for date: \(date), error: \(error)")
            throw PolarErrors.fileError(description: error.localizedDescription)
        }
    }

    // MARK: - Private helpers

    private static func listFiles(
        client: BlePsFtpClient,
        folderPath: String = "/",
        condition: @escaping (_ p: String) -> Bool
    ) async throws -> [String] {
        var path = folderPath
        if path.first != "/" { path.insert("/", at: path.startIndex) }
        if path.last  != "/" { path.insert("/", at: path.endIndex) }
        let entries = try await fetchRecursive(path, client: client, condition: condition)
        return entries.map { $0.name }
    }

    private static func fetchRecursive(
        _ path: String,
        client: BlePsFtpClient,
        condition: @escaping (_ p: String) -> Bool
    ) async throws -> [(name: String, size: UInt64)] {
        var operation = Protocol_PbPFtpOperation()
        operation.command = .get
        operation.path = path
        let request = try operation.serializedData()
        let data = try await client.request(request)
        let dir = try Protocol_PbPFtpDirectory(serializedBytes: data as Data)
        var results: [(name: String, size: UInt64)] = []
        for entry in dir.entries where condition(entry.name) {
            let fullPath = path + entry.name
            if fullPath.hasSuffix("/") {
                let children = try await fetchRecursive(fullPath, client: client, condition: condition)
                results.append(contentsOf: children)
            } else {
                results.append((name: fullPath, size: entry.size))
            }
        }
        return results
    }
}
