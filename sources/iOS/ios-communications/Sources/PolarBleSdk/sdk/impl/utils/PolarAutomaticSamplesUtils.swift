//  Copyright © 2024 Polar. All rights reserved.

import Foundation

private let ARABICA_USER_ROOT_FOLDER = "/U/0/"
private let AUTOMATIC_SAMPLES_DIRECTORY = "AUTOS/"
private let AUTOMATIC_SAMPLES_PATTERN = #"AUTOS\d{3}\.BPB"#
private let TAG = "PolarAutomaticSamplesUtils"

internal class PolarAutomaticSamplesUtils {

    static func read247HrSamples(client: BlePsFtpClient, fromDate: Date, toDate: Date) async throws -> [Polar247HrSamplesData] {
        BleLogger.trace(TAG, "read247HrSamples: from \(fromDate) to \(toDate)")
        let autoSamplesPath = "\(ARABICA_USER_ROOT_FOLDER)\(AUTOMATIC_SAMPLES_DIRECTORY)"
        let listOperation = Protocol_PbPFtpOperation.with { $0.command = .get; $0.path = autoSamplesPath }
        let response = try await client.request(try listOperation.serializedBytes())
        let dir = try Protocol_PbPFtpDirectory(serializedBytes: Data(response))
        let regex = try NSRegularExpression(pattern: AUTOMATIC_SAMPLES_PATTERN)
        let filteredFiles = dir.entries.compactMap { entry -> String? in
            let range = NSRange(location: 0, length: entry.name.count)
            return regex.firstMatch(in: entry.name, range: range) != nil ? entry.name : nil
        }
        let dateFrom = Calendar.current.dateComponents([.year, .month, .day], from: fromDate)
        let dateTo = Calendar.current.dateComponents([.year, .month, .day], from: toDate)
        var results = [Polar247HrSamplesData]()
        for fileName in filteredFiles {
            let filePath = "\(autoSamplesPath)\(fileName)"
            let fileOp = Protocol_PbPFtpOperation.with { $0.command = .get; $0.path = filePath }
            do {
                let fileResponse = try await client.request(try fileOp.serializedBytes())
                let sampleSessions = try Data_PbAutomaticSampleSessions(serializedBytes: Data(fileResponse))
                let sampleDateProto = sampleSessions.day
                let sampleDate = DateComponents(year: Int(sampleDateProto.year), month: Int(sampleDateProto.month), day: Int(sampleDateProto.day))
                guard sampleDate >= dateFrom && sampleDate <= dateTo else { continue }
                let samples = try Polar247HrSamplesData.fromPbHrDataSamples(samples: sampleSessions.samples)
                let date = Calendar.current.dateComponents([.year, .month, .day], from: Calendar.current.date(from: sampleDate)!)
                results.append(Polar247HrSamplesData(date: date, samples: samples))
            } catch {
                BleLogger.error(TAG, "Failed to parse HR in \(fileName): \(error)")
            }
        }
        return results
    }

    static func read247PPiSamples(client: BlePsFtpClient, fromDate: Date, toDate: Date) async throws -> [Polar247PPiSamplesData] {
        BleLogger.trace(TAG, "read247PPiSamples: from \(fromDate) to \(toDate)")
        let autoSamplesPath = "\(ARABICA_USER_ROOT_FOLDER)\(AUTOMATIC_SAMPLES_DIRECTORY)"
        let operation = Protocol_PbPFtpOperation.with { $0.command = .get; $0.path = autoSamplesPath }
        let response = try await client.request(try operation.serializedBytes())
        let dir = try Protocol_PbPFtpDirectory(serializedBytes: Data(response))
        let regex = try NSRegularExpression(pattern: AUTOMATIC_SAMPLES_PATTERN)
        let filteredFiles = dir.entries.compactMap { entry -> String? in
            let range = NSRange(location: 0, length: entry.name.count)
            return regex.firstMatch(in: entry.name, range: range) != nil ? entry.name : nil
        }
        let dateFrom = Calendar.current.dateComponents([.year, .month, .day], from: fromDate)
        let dateTo = Calendar.current.dateComponents([.year, .month, .day], from: toDate)
        var results = [Polar247PPiSamplesData]()
        for fileName in filteredFiles {
            let filePath = "\(autoSamplesPath)\(fileName)"
            let fileOp = Protocol_PbPFtpOperation.with { $0.command = .get; $0.path = filePath }
            do {
                let fileResponse = try await client.request(try fileOp.serializedBytes())
                let sampleSessions = try Data_PbAutomaticSampleSessions(serializedBytes: Data(fileResponse))
                let sampleDateProto = sampleSessions.day
                let sampleDate = DateComponents(year: Int(sampleDateProto.year), month: Int(sampleDateProto.month), day: Int(sampleDateProto.day))
                guard sampleDate >= dateFrom && sampleDate <= dateTo else { continue }
                let samples = sampleSessions.ppiSamples.map { Polar247PPiSamplesData.fromPbPPiDataSamples(ppiData: $0) }
                let date = Calendar.current.dateComponents([.year, .month, .day], from: Calendar.current.date(from: sampleDate)!)
                results.append(Polar247PPiSamplesData(date: date, samples: samples))
            } catch {
                BleLogger.error(TAG, "Failed to parse PPI in \(fileName): \(error)")
            }
        }
        return results
    }
}

extension DateComponents: Comparable {
    public static func < (lhs: DateComponents, rhs: DateComponents) -> Bool {
        let now = Date()
        let calendar = Calendar.current
        return calendar.date(byAdding: lhs, to: now)! < calendar.date(byAdding: rhs, to: now)!
    }
}
