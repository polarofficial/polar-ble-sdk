//  Copyright © 2025 Polar. All rights reserved.

import Foundation

class PolarOfflineRecordingUtils {

    static func mapOfflineRecordingFileNameToDeviceDataType(fileName: String) throws -> PolarDeviceDataType {
        let fileNameWithoutExtension = fileName.components(separatedBy: ".").first!
        switch fileNameWithoutExtension.replacingOccurrences(of: "\\d+", with: "", options: .regularExpression) {
        case "ACC": return .acc
        case "GYRO": return .gyro
        case "MAGNETOMETER": return .magnetometer
        case "PPG": return .ppg
        case "PPI": return .ppi
        case "HR": return .hr
        case "TEMP": return .temperature
        case "SKINTEMP": return .skinTemperature
        default: throw BleGattException.gattDataError(description: "Unknown Polar Device Data type: \(fileName)")
        }
    }

    static func listOfflineRecordingsV1(
        client: BlePsFtpClient,
        fetchRecursively: @escaping (_ client: BlePsFtpClient, _ path: String, _ condition: @escaping (String) -> Bool) async throws -> [(String, UInt)]
    ) -> AsyncThrowingStream<PolarOfflineRecordingEntry, Error> {
        return AsyncThrowingStream { continuation in
            Task {
                do {
                    BleLogger.trace("Start offline recording listing")
                    let entries = try await fetchRecursively(client, "/U/0/") { entry in
                        entry.matches("^([0-9]{8})(\\/)") ||
                        entry.matches("^([0-9]{6})(\\/)") ||
                        entry == "R/" ||
                        entry.contains(".REC")
                    }
                    var grouped = [String: [PolarOfflineRecordingEntry]]()
                    let dateFormatter = DateFormatter()
                    dateFormatter.calendar = .init(identifier: .iso8601)
                    dateFormatter.locale = Locale(identifier: "en_US_POSIX")
                    for (path, size) in entries {
                        guard path.matches("^(\\/)U(\\/)0(\\/)([0-9]{8})(\\/)R(\\/)([0-9]{6})(\\/)([A-Z]+[0-9]*)\\.REC") else { continue }
                        let components = path.split(separator: "/")
                        guard size > 0 && components.count == 6 else { continue }
                        if components[2].count == 8 && components[4].count == 6 {
                            dateFormatter.dateFormat = "yyyyMMddHHmmss"
                        } else {
                            dateFormatter.dateFormat = "yyyyMMddHHmm"
                        }
                        guard let date = dateFormatter.date(from: String(components[2] + components[4])),
                              let pmdMeasurementType = try? OfflineRecordingUtils.mapOfflineRecordingFileNameToMeasurementType(fileName: String(components[5])),
                              let type = try? PolarDataUtils.mapToPolarFeature(from: pmdMeasurementType) else { continue }
                        let entry = PolarOfflineRecordingEntry(path: path, size: size, date: date, type: type)
                        let key = path.replacingOccurrences(of: "\\d+\\.REC$", with: ".REC", options: .regularExpression)
                        grouped[key, default: []].append(entry)
                    }
                    for (_, entriesList) in grouped {
                        guard !entriesList.isEmpty else { continue }
                        let sorted = entriesList.sorted { $0.path < $1.path }
                        let totalSize = entriesList.reduce(0) { $0 + $1.size }
                        let merged = PolarOfflineRecordingEntry(path: sorted[0].path, size: totalSize, date: sorted[0].date, type: sorted[0].type)
                        continuation.yield(merged)
                    }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
        }
    }

    static func listOfflineRecordingsV2(fileData: Data) throws -> [PolarOfflineRecordingEntry] {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyyMMdd HHmmss"
        formatter.locale = Locale(identifier: "en_US_POSIX")
        let stream = InputStream(data: fileData)
        let bufferSize = 1024
        let buffer = UnsafeMutablePointer<UInt8>.allocate(capacity: bufferSize)
        var offlineFileLines: [String] = []
        var accumulatedString = ""
        var entries = [PolarOfflineRecordingEntry]()
        stream.open()
        defer { stream.close(); buffer.deallocate() }
        while stream.hasBytesAvailable {
            let bytesRead = stream.read(buffer, maxLength: bufferSize)
            if bytesRead > 0, let chunk = String(bytesNoCopy: buffer, length: bytesRead, encoding: .utf8, freeWhenDone: false) {
                accumulatedString += chunk
                while let range = accumulatedString.range(of: "\n") {
                    let line = String(accumulatedString[..<range.lowerBound]).trimmingCharacters(in: .whitespacesAndNewlines)
                    if !line.isEmpty { offlineFileLines.append(line) }
                    accumulatedString.removeSubrange(..<range.upperBound)
                }
            }
        }
        if !accumulatedString.isEmpty {
            let line = accumulatedString.trimmingCharacters(in: .whitespacesAndNewlines)
            if !line.isEmpty { offlineFileLines.append(line) }
        }
        for line in offlineFileLines {
            guard !line.isEmpty else { continue }
            let parts = line.split(separator: " ")
            guard parts.count >= 2, Int(String(parts[0])) ?? 0 > 0 else { continue }
            let recordingPath = parts[1]
            let pathComponents = recordingPath.split(separator: "/")
            guard pathComponents.count >= 6,
                  let deviceDataType = try? PolarOfflineRecordingUtils.mapOfflineRecordingFileNameToDeviceDataType(fileName: String(pathComponents[5])),
                  let date = formatter.date(from: String(pathComponents[2]) + " " + String(pathComponents[4])) else { continue }
            entries.append(PolarOfflineRecordingEntry(path: String(recordingPath), size: UInt(String(parts[0])) ?? 0, date: date, type: deviceDataType))
        }
        return Dictionary(grouping: entries, by: { $0.path.replacingOccurrences(of: "\\d+\\.REC$", with: ".REC", options: .regularExpression) })
            .compactMap { (_, grouped) -> PolarOfflineRecordingEntry? in
                guard let first = grouped.first else { return nil }
                let representativePath = grouped.sorted { $0.path < $1.path }.first?.path ?? first.path
                return PolarOfflineRecordingEntry(path: representativePath, size: grouped.reduce(0) { $0 + $1.size }, date: grouped.map(\.date).max() ?? first.date, type: first.type)
            }
    }
}
