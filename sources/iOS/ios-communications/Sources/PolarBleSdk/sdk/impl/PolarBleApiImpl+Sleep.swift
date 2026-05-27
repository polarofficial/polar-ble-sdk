/// Copyright © 2019 Polar Electro Oy. All rights reserved.

import Foundation
import CoreBluetooth

#if os(iOS)
import UIKit
#endif

/// Implementation of PolarSleepApi
/// Depends on PolarRestServiceApi

extension PolarBleApiImpl: PolarSleepApi {

    enum SleepApiFailure: Error {
        case sleepApiNotSupported
        var localizedDescription: String {
            switch self {
            case .sleepApiNotSupported: return "Device does not support PolarSleepApi"
            }
        }
    }

    func stopSleepRecording(identifier: String) async throws {
        do {
            _ = try await self.fileUtils.getFile(identifier: identifier, filePath: "/REST/SLEEP.API")
        } catch {
            if case let BlePsFtpException.responseError(code) = error {
                if code == 103 { throw SleepApiFailure.sleepApiNotSupported }
            }
            throw error
        }
        try await putNotification(identifier: identifier, notification: "{}", path: "/REST/SLEEP.API?cmd=post&endpoint=stop_sleep_recording")
    }

    internal struct SleepRecordingState: Decodable {
        let enabled: Int?
        var isEnabled: Bool { return enabled ?? 0 == 1 }
    }
    internal struct SleepRecordingStateWrapper: Decodable {
        private let sleep_recording_state: SleepRecordingState
        var sleepRecordingState: SleepRecordingState { return sleep_recording_state }
    }

    func getSleepRecordingState(identifier: String) async throws -> Bool {
        var firstResult: Bool? = nil
        for try await items in observeSleepRecordingState(identifier: identifier) {
            if !items.isEmpty {
                firstResult = items.last!
                break
            }
        }
        return firstResult ?? false
    }

    func observeSleepRecordingState(identifier: String) -> AsyncThrowingStream<[Bool], Error> {
        return AsyncThrowingStream { continuation in
            Task {
                do {
                    do {
                        _ = try await self.fileUtils.getFile(identifier: identifier, filePath: "/REST/SLEEP.API")
                    } catch {
                        if case let BlePsFtpException.responseError(code) = error, code == 103 {
                            continuation.finish(throwing: SleepApiFailure.sleepApiNotSupported)
                            return
                        }
                        throw error
                    }
                    try await putNotification(identifier: identifier, notification: "{}",
                                              path: "/REST/SLEEP.API?cmd=subscribe&event=sleep_recording_state&details=[enabled]")
                    for try await items in self.receiveRestApiEvents(identifier: identifier) as AsyncThrowingStream<[SleepRecordingStateWrapper], Error> {
                        let bools = items.map { $0.sleepRecordingState.isEnabled }
                        continuation.yield(bools)
                    }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
        }
    }

    func getSleep(identifier: String, fromDate: Date, toDate: Date) async throws -> [PolarSleepData.PolarSleepAnalysisResult] {
        if fromDate > toDate {
            throw PolarErrors.invalidArgument(description: "toDate cannot be smaller than fromDate.")
        }
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
            throw PolarErrors.serviceNotFound
        }
        var datesList = [Date]()
        let calendar = Calendar.current
        var currentDate = fromDate
        if fromDate == toDate {
            datesList.append(fromDate)
        } else {
            while currentDate <= toDate {
                datesList.append(currentDate)
                guard let next = calendar.date(byAdding: .day, value: 1, to: currentDate) else { break }
                currentDate = next
            }
        }
        var sleepDataList: [PolarSleepData.PolarSleepAnalysisResult] = []
        for date in datesList {
            let result = try await PolarSleepUtils.readSleepFromDayDirectory(client: client, date: date)
            sleepDataList.append(result)
        }
        // Filter out entries with nil sleepStartTime
        return sleepDataList.filter { $0.sleepStartTime != nil }
    }

    @available(*, deprecated, renamed: "getSleep(identifier:fromDate:toDate:)")
    func getSleepData(identifier: String, fromDate: Date, toDate: Date) async throws -> [PolarSleepData.PolarSleepAnalysisResult] {
        return try await getSleep(identifier: identifier, fromDate: fromDate, toDate: toDate)
    }
}
