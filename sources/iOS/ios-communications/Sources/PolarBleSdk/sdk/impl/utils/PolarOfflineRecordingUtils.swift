//  Copyright © 2025 Polar. All rights reserved.

import Foundation
import RxSwift

class PolarOfflineRecordingUtils {

    static func mapOfflineRecordingFileNameToDeviceDataType(fileName: String) throws -> PolarDeviceDataType {
        let fileNameWithoutExtension = fileName.components(separatedBy: ".").first!
        switch fileNameWithoutExtension.replacingOccurrences(of: "\\d+", with: "", options: .regularExpression) {
        case "ACC": return .acc
        case "GYRO": return .gyro
        case "MAGNETOMETER" : return .magnetometer
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
        fetchRecursively: @escaping (_ client: BlePsFtpClient, _ path: String, _ condition: @escaping (String) -> Bool) -> Observable<(String, UInt)>
    ) -> Observable<PolarOfflineRecordingEntry> {

        BleLogger.trace("Start offline recording listing")

        return fetchRecursively(client, "/U/0/") { entry in
            entry.matches("^([0-9]{8})(\\/)") ||
            entry.matches("^([0-9]{6})(\\/)") ||
            entry == "R/" ||
            entry.contains(".REC")
        }
        .flatMap { (path, size) -> Observable<PolarOfflineRecordingEntry> in
            guard path.matches("^(\\/)U(\\/)0(\\/)([0-9]{8})(\\/)R(\\/)([0-9]{6})(\\/)([A-Z]+[0-9]*)\\.REC") else {
                BleLogger.error("Listing offline recording failed. Unexpected format for entry: \(path)")
                return Observable.empty()
            }
            let components = path.split(separator: "/")
            let dateFormatter = DateFormatter()
            dateFormatter.calendar = .init(identifier: .iso8601)
            dateFormatter.locale = Locale(identifier: "en_US_POSIX")

            guard components.count == 6 else {
                BleLogger.error("Listing offline recording failed. Unexpected number of path components for entry: \(path)")
                return Observable.empty()
            }

            if components[2].count == 8 && components[4].count == 6 {
                dateFormatter.dateFormat = "yyyyMMddHHmmss"
            } else {
                dateFormatter.dateFormat = "yyyyMMddHHmm"
            }

            guard let date = dateFormatter.date(from: String(components[2] + components[4])) else {
                BleLogger.error("Listing offline recording failed. Couldn't parse create data from date \(components[2]) and time \(components[4])")
                return Observable.empty()
            }

            guard let pmdMeasurementType = try? OfflineRecordingUtils.mapOfflineRecordingFileNameToMeasurementType(fileName: String(components[5])) else {
                BleLogger.error("Listing offline recording failed. Couldn't parse the pmd type from \(components[5])")
                return Observable.empty()
            }

            guard let type = try? PolarDataUtils.mapToPolarFeature(from: pmdMeasurementType) else {
                BleLogger.error("Listing offline recording failed. Couldn't parse the polar type from pmd type: \(pmdMeasurementType)")
                return Observable.empty()
            }

            let entry = PolarOfflineRecordingEntry(
                path: path,
                size: size,
                date: date,
                type: type
            )

            BleLogger.trace("Adding entry: \(entry)")
            return Observable.just(entry)
        }
        .groupBy { entry in
            entry.path.replacingOccurrences(of: "\\d+\\.REC$", with: ".REC", options: .regularExpression)
        }
        .flatMap { groupedEntries -> Observable<PolarOfflineRecordingEntry> in
            groupedEntries
                .reduce([]) { accumulator, entry -> [PolarOfflineRecordingEntry] in
                    var updatedAccumulator = accumulator
                    updatedAccumulator.append(entry)
                    return updatedAccumulator
                }
                .flatMap { entriesList -> Observable<PolarOfflineRecordingEntry> in
                    guard !entriesList.isEmpty else {
                        return Observable.empty()
                    }
                    
                    let sortedEntries = entriesList.sorted { $0.path < $1.path }
                    guard let firstEntry = sortedEntries.first else {
                        return Observable.empty()
                    }

                    var totalSize = 0
                    entriesList.forEach { entry in
                        totalSize += Int(entry.size)
                    }

                    let mergedEntry = PolarOfflineRecordingEntry(
                        path: firstEntry.path,
                        size: UInt(totalSize),
                        date: firstEntry.date,
                        type: firstEntry.type
                    )

                    BleLogger.trace("Merging entries: \(entriesList) into: \(mergedEntry)")
                    return Observable.just(mergedEntry)
                }
        }
    }

    static func listOfflineRecordingsV2(fileData: Data) -> Single<[PolarOfflineRecordingEntry]> {

        return Single.create { emitter in
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
            defer { stream.close() }
            defer { buffer.deallocate() }

            while stream.hasBytesAvailable {
                let bytesRead = stream.read(buffer, maxLength: bufferSize)
                if bytesRead > 0,
                   let chunk = String(bytesNoCopy: buffer, length: bytesRead, encoding: .utf8, freeWhenDone: false) {
                    accumulatedString += chunk
                    while let range = accumulatedString.range(of: "\n") {
                        let line = String(accumulatedString[..<range.lowerBound]).trimmingCharacters(in: .whitespacesAndNewlines)
                        if !line.isEmpty {
                            offlineFileLines.append(line)
                        }
                        accumulatedString.removeSubrange(..<range.upperBound)
                    }
                }
            }

            if !accumulatedString.isEmpty {
                let line = accumulatedString.trimmingCharacters(in: .whitespacesAndNewlines)
                if !line.isEmpty {
                    offlineFileLines.append(line)
                }
            }

            for line in offlineFileLines {
                guard !line.isEmpty else { continue }

                let parts = line.split(separator: " ")
                guard parts.count >= 2 else {
                    BleLogger.error("Invalid line format in PMDFILES.TXT: \(line)")
                    continue
                }

                let fileSize = parts[0]
                let recordingPath = parts[1]

                let pathComponents = recordingPath.split(separator: "/")
                guard pathComponents.count >= 6 else {
                    BleLogger.error("Invalid path format: \(recordingPath)")
                    continue
                }

                guard let deviceDataType = try? PolarOfflineRecordingUtils.mapOfflineRecordingFileNameToDeviceDataType(fileName: String(pathComponents[5])) else {
                    BleLogger.error("Failed to parse device data type from: \(pathComponents[5])")
                    continue
                }

                guard let date = formatter.date(from: String(pathComponents[2]) + " " + String(pathComponents[4])) else {
                    BleLogger.error("Failed to parse date from: \(pathComponents[2]) \(pathComponents[4])")
                    continue
                }

                entries.append(PolarOfflineRecordingEntry(
                    path: String(recordingPath),
                    size: UInt(String(fileSize)) ?? 0,
                    date: date,
                    type: deviceDataType
                ))
            }

            let merged: [PolarOfflineRecordingEntry] = Dictionary(
                grouping: entries,
                by: { entry in
                    entry.path.replacingOccurrences(
                        of: "\\d+\\.REC$",
                        with: ".REC",
                        options: .regularExpression
                    )
                }
            ).compactMap { (_, grouped) in
                guard let first = grouped.first else { return nil }

                let representativePath = grouped.sorted { $0.path < $1.path }.first?.path ?? first.path
                let totalSize = grouped.reduce(0) { $0 + $1.size }
                let latestDate = grouped.map(\.date).max() ?? first.date

                return PolarOfflineRecordingEntry(
                    path: representativePath,
                    size: totalSize,
                    date: latestDate,
                    type: first.type
                )
            }

            emitter(.success(merged))

            return Disposables.create()
        }
    }
}
