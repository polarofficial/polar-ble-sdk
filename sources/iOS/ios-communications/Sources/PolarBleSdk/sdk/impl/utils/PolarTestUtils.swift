//  Copyright © 2026 Polar. All rights reserved.

import Foundation
import RxSwift

private let ARABICA_USER_ROOT_FOLDER = "/U/0/"
private let SPO2_TEST_DIRECTORY = "SPO2TEST/"
private let SPO2_TEST_PROTO = "SPO2TRES.BPB"
private let dateFormat: DateFormatter = {
    let formatter = DateFormatter()
    formatter.dateFormat = "yyyyMMdd"
    formatter.locale = Locale(identifier: "en_US_POSIX")
    return formatter
}()
private let TAG = "PolarTestUtils"

internal class PolarTestUtils {

    /// Read all SPO2 test data for a given date.
    ///
    /// Files reside at `/U/0/<yyyyMMdd>/SPO2TEST/<HHMMSS>/SPO2TRES.BPB`.
    /// Multiple time subdirectories may exist; all are read and emitted.
    static func readSpo2TestFromDayDirectory(client: BlePsFtpClient, date: Date) -> Observable<PolarSpo2TestData> {
        BleLogger.trace(TAG, "readSpo2TestFromDayDirectory: \(date)")

        let spo2TestDirPath = "\(ARABICA_USER_ROOT_FOLDER)\(dateFormat.string(from: date))/\(SPO2_TEST_DIRECTORY)"
        let listOperation = Protocol_PbPFtpOperation.with {
            $0.command = .get
            $0.path = spo2TestDirPath
        }

        return client.request(try! listOperation.serializedData())
            .asObservable()
            .flatMap { response -> Observable<PolarSpo2TestData> in
                do {
                    let dir = try Protocol_PbPFtpDirectory(serializedBytes: Data(response))
                    let timeSubDirs = dir.entries.filter { $0.name.hasSuffix("/") }
                    guard !timeSubDirs.isEmpty else {
                        BleLogger.trace(TAG, "No time subdirectory found in \(spo2TestDirPath)")
                        return Observable.empty()
                    }
                    return Observable.from(timeSubDirs)
                        .flatMap { subDir -> Observable<PolarSpo2TestData> in
                            let timeDirName = String(subDir.name.dropLast())
                            let filePath = "\(spo2TestDirPath)\(subDir.name)\(SPO2_TEST_PROTO)"
                            let fileOperation = Protocol_PbPFtpOperation.with {
                                $0.command = .get
                                $0.path = filePath
                            }
                            return client.request(try! fileOperation.serializedData())
                                .asObservable()
                                .flatMap { fileResponse -> Observable<PolarSpo2TestData> in
                                    do {
                                        let proto = try Data_PbSpo2TestResult(serializedBytes: Data(fileResponse))
                                        let testData = PolarTestUtils.fromProto(proto: proto, date: date, timeDirName: timeDirName)
                                        return Observable.just(testData)
                                    } catch {
                                        BleLogger.error(TAG, "Failed to parse SPO2 test data at \(filePath): \(error)")
                                        return Observable.empty()
                                    }
                                }
                                .catch { error in
                                    BleLogger.trace(TAG, "No SPO2 test proto at \(filePath): \(error)")
                                    return Observable.empty()
                                }
                        }
                } catch {
                    BleLogger.trace(TAG, "No SPO2TEST directory for \(date) at \(spo2TestDirPath): \(error)")
                    return Observable.empty()
                }
            }
            .catch { error in
                BleLogger.trace(TAG, "SPO2TEST directory listing failed for \(date): \(error)")
                return Observable.empty()
            }
    }

    // MARK: - Mapping helpers

    static func fromProto(proto: Data_PbSpo2TestResult, date: Date, timeDirName: String) -> PolarSpo2TestData {
        // timeZoneOffset is in minutes in the real proto field name
        let tzOffsetMinutes = proto.timeZoneOffset != 0 ? Int(proto.timeZoneOffset) : nil

        // Derive test date: prefer folder-name parsing, fall back to testTime (ms since epoch), then bare date
        let testDate = dateFromFolderNames(dayDate: date, timeDirName: timeDirName, tzOffsetMinutes: tzOffsetMinutes)
            ?? (proto.testTime != 0 ? Date(timeIntervalSince1970: TimeInterval(proto.testTime) / 1000.0) : nil)
            ?? date

        return PolarSpo2TestData(
            recordingDevice: proto.recordingDevice.isEmpty ? nil : proto.recordingDevice,
            date: testDate,
            timeZoneOffsetMinutes: tzOffsetMinutes,
            testStatus: PolarSpo2TestData.Spo2TestStatus(rawValue: proto.testStatus.rawValue),
            bloodOxygenPercent: proto.hasBloodOxygenPercent ? Int(proto.bloodOxygenPercent) : nil,
            spo2Class: proto.hasSpo2Class ? PolarSpo2TestData.Spo2Class(rawValue: proto.spo2Class.rawValue) : nil,
            spo2ValueDeviationFromBaseline: proto.hasSpo2ValueDeviationFromBaseline
                ? PolarSpo2TestData.DeviationFromBaseline(rawValue: proto.spo2ValueDeviationFromBaseline.rawValue)
                : nil,
            spo2QualityAveragePercent: proto.hasSpo2QualityAveragePercent ? proto.spo2QualityAveragePercent : nil,
            averageHeartRateBpm: proto.hasAverageHeartRateBpm ? UInt(proto.averageHeartRateBpm) : nil,
            heartRateVariabilityMs: proto.hasHeartRateVariabilityMs ? proto.heartRateVariabilityMs : nil,
            spo2HrvDeviationFromBaseline: proto.hasSpo2HrvDeviationFromBaseline
                ? PolarSpo2TestData.DeviationFromBaseline(rawValue: proto.spo2HrvDeviationFromBaseline.rawValue)
                : nil,
            altitudeMeters: proto.hasAltitudeMeters ? proto.altitudeMeters : nil,
            triggerType: proto.hasTriggerType
                ? PolarSpo2TestData.Spo2TestTriggerType(rawValue: proto.triggerType.rawValue)
                : nil
        )
    }

    static func dateFromFolderNames(dayDate: Date, timeDirName: String, tzOffsetMinutes: Int?) -> Date? {
        let deviceTz: TimeZone
        if let offsetMins = tzOffsetMinutes, let tz = TimeZone(secondsFromGMT: offsetMins * 60) {
            deviceTz = tz
        } else {
            deviceTz = TimeZone.current
        }

        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = deviceTz
        let dayComponents = cal.dateComponents([.year, .month, .day], from: dayDate)

        guard timeDirName.count == 6,
              let hh = Int(timeDirName.prefix(2)),
              let mm = Int(timeDirName.dropFirst(2).prefix(2)),
              let ss = Int(timeDirName.suffix(2)) else {
            BleLogger.error(TAG, "Cannot parse time folder name: \(timeDirName)")
            return nil
        }

        var components = DateComponents()
        components.timeZone = deviceTz
        components.year   = dayComponents.year
        components.month  = dayComponents.month
        components.day    = dayComponents.day
        components.hour   = hh
        components.minute = mm
        components.second = ss
        return cal.date(from: components)
    }
}
