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
        logApiCall("stopSleepRecording", ("identifier", identifier))
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
        // nil means the device did not report the field; the state is unknown, not off.
        var isEnabled: Bool? {
            guard let enabled = enabled else { return nil }
            return enabled == 1
        }
    }
    internal struct SleepRecordingStateWrapper: Decodable {
        private let sleep_recording_state: SleepRecordingState
        var sleepRecordingState: SleepRecordingState { return sleep_recording_state }
    }

    private func sleepRecordingStatus(_ isEnabled: Bool?) -> PolarSleepRecordingStatus {
        switch isEnabled {
        case .some(true): return .enabled
        case .some(false): return .disabled
        case .none: return .unknown
        }
    }

    func getSleepRecordingStatus(identifier: String, timeoutMs: UInt64 = 30_000) async throws -> PolarSleepRecordingStatus {
        logApiCall("getSleepRecordingStatus", ("identifier", identifier), ("timeoutMs", timeoutMs))
        return try await withThrowingTaskGroup(of: PolarSleepRecordingStatus.self) { group in
            group.addTask {
                for try await items in self.observeSleepRecordingStatus(identifier: identifier) {
                    if let last = items.last {
                        return last
                    }
                }
                throw PolarErrors.timeout(description: "Timed out(after (\(timeoutMs) ms)) waiting for sleep recording status for device \(identifier).")
            }

            group.addTask {
                try await Task.sleep(nanoseconds: timeoutMs * 1_000_000)
                throw PolarErrors.timeout(description: "Timed out waiting for sleep recording status for device \(identifier).")
            }

            let result = try await group.next()!
            group.cancelAll()
            return result
        }
    }

    func observeSleepRecordingStatus(identifier: String) -> AsyncThrowingStream<[PolarSleepRecordingStatus], Error> {
        logApiCall("observeSleepRecordingStatus", ("identifier", identifier))
        return AsyncThrowingStream { continuation in
            let task = Task {
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
                    try Task.checkCancellation()

                    // Important: create the event stream before subscribing to events:
                    // this registers the device-notification
                    // listener synchronously. The shared PS-FTP broadcast loop drops events that
                    // arrive with no subscriber, so subscribing before listening can lose the
                    // device's initial snapshot on repeated calls.
                    let events = self.receiveRestApiEvents(identifier: identifier) as AsyncThrowingStream<[SleepRecordingStateWrapper], Error>
                    try await putNotification(identifier: identifier, notification: "{}",
                                              path: "/REST/SLEEP.API?cmd=subscribe&event=sleep_recording_state&details=[enabled]")

                    for try await items in events {
                        try Task.checkCancellation()
                        let statuses = items.map { self.sleepRecordingStatus($0.sleepRecordingState.isEnabled) }
                        continuation.yield(statuses)
                    }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    func getSleepRecordingState(identifier: String, timeoutMs: UInt64 = 30_000) async throws -> Bool {
        logApiCall("getSleepRecordingState", ("identifier", identifier), ("timeoutMs", timeoutMs))
        return try await getSleepRecordingStatus(identifier: identifier, timeoutMs: timeoutMs) == .enabled
    }

    func observeSleepRecordingState(identifier: String) -> AsyncThrowingStream<[Bool], Error> {
        logApiCall("observeSleepRecordingState", ("identifier", identifier))
        return AsyncThrowingStream { continuation in
            let statuses = self.observeSleepRecordingStatus(identifier: identifier)
            let task = Task {
                do {
                    for try await batch in statuses {
                        continuation.yield(batch.map { $0 == .enabled })
                    }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    func getSleep(identifier: String, fromDate: Date, toDate: Date) async throws -> [PolarSleepData.PolarSleepAnalysisResult] {
        logApiCall("getSleep", ("identifier", identifier))
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
            do {
                let result = try await PolarSleepUtils.readSleepFromDayDirectory(client: client, date: date)
                sleepDataList.append(result)
            } catch {
                BleLogger.error("getSleep: per-date error for \(date): \(error)")
            }
        }
        // Filter out entries with nil sleepStartTime
        return sleepDataList.filter { $0.sleepStartTime != nil }
    }

    @available(*, deprecated, renamed: "getSleep(identifier:fromDate:toDate:)")
    func getSleepData(identifier: String, fromDate: Date, toDate: Date) async throws -> [PolarSleepData.PolarSleepAnalysisResult] {
        logApiCall("getSleepData", ("identifier", identifier))
        return try await getSleep(identifier: identifier, fromDate: fromDate, toDate: toDate)
    }
}
