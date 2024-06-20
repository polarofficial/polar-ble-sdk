//  Copyright © 2024 Polar. All rights reserved.

import Foundation
import RxSwift

private let ARABICA_USER_ROOT_FOLDER = "/U/0/"
private let SLEEP_DIRECTORY = "SLEEP/"
private let SLEEP_PROTO = "SLEEPRES.BPB"
private let dateFormat: DateFormatter = {
    let formatter = DateFormatter()
    formatter.dateFormat = "yyyyMMdd"
    formatter.locale = Locale(identifier: "en_US_POSIX")
    return formatter
}()
private let TAG = "PolarSleepUtils"

internal class PolarSleepUtils {

    /// Read sleep for a given date.
    static func readSleepFromDayDirectory(client: BlePsFtpClient, date: Date) -> Single<PolarSleepData.PolarSleepAnalysisResult> {
        BleLogger.trace(TAG, "readsleepFromDayDirectory: \(date)")
        return Single<PolarSleepData.PolarSleepAnalysisResult>.create { emitter in
            let sleepDataFilePath = "\(ARABICA_USER_ROOT_FOLDER)\(dateFormat.string(from: date))/\(SLEEP_DIRECTORY)\(SLEEP_PROTO)"
            let operation = Protocol_PbPFtpOperation.with {
                $0.command = .get
                $0.path = sleepDataFilePath
            }
            let disposable = client.request(try! operation.serializedData()).subscribe(
                onSuccess: { response in
                    do {
                        let proto = try Data_PbSleepAnalysisResult(serializedData: Data(response))
                        let result = PolarSleepData.PolarSleepAnalysisResult.init(
                            sleepStartTime: try PolarTimeUtils.pbLocalDateTimeToDate(pbLocalDateTime: proto.sleepStartTime),
                            sleepEndTime: try PolarTimeUtils.pbLocalDateTimeToDate(pbLocalDateTime: proto.sleepEndTime),
                            lastModified: proto.hasLastModified ? try PolarTimeUtils.pbSystemDateTimeToDate(pbSystemDateTime: proto.lastModified) : nil,
                            sleepGoalMinutes: proto.sleepGoalMinutes,
                            sleepWakePhases: PolarSleepData.fromPbSleepwakePhasesListProto(pbSleepwakePhasesList: proto.sleepwakePhases),
                            snoozeTime: try PolarSleepData.convertSnoozeTimeListToLocalTime(snoozeTimeList: proto.snoozeTime),
                            alarmTime: proto.hasAlarmTime ? try PolarTimeUtils.pbLocalDateTimeToDate(pbLocalDateTime: proto.alarmTime) : nil,
                            sleepStartOffsetSeconds: proto.sleepStartOffsetSeconds,
                            sleepEndOffsetSeconds: proto.sleepStartOffsetSeconds,
                            userSleepRating: proto.hasUserSleepRating ? PolarSleepData.SleepRating(rawValue: proto.userSleepRating.rawValue) : nil,
                            deviceId: proto.hasRecordingDevice ? proto.recordingDevice.deviceID : nil,
                            batteryRanOut: proto.hasBatteryRanOut ? proto.batteryRanOut : nil,
                            sleepCycles: PolarSleepData.fromPbSleepCyclesList(pbSleepCyclesList: proto.sleepCycles),
                            sleepResultDate: try? PolarTimeUtils.pbDateToDate(pbDate: proto.sleepResultDate),
                            originalSleepRange: try PolarSleepData.fromPbOriginalSleepRange(pbOriginalSleepRange: proto.originalSleepRange)
                        )
                        emitter(.success(result))
                    } catch {
                        BleLogger.error("readsleepFromDayDirectory() failed for path: \(sleepDataFilePath), error: \(error). No sleep data?")
                        emitter(.failure(error))
                    }
                },
                onFailure: { error in
                    BleLogger.trace("readsleepFromDayDirectory() failed for path: \(sleepDataFilePath), error: \(error). No sleep data?")
                    emitter(.success(PolarSleepData.PolarSleepAnalysisResult(sleepStartTime: nil, sleepEndTime: nil, lastModified: nil,sleepGoalMinutes: nil, sleepWakePhases: nil, snoozeTime: nil,alarmTime: nil, sleepStartOffsetSeconds: nil, sleepEndOffsetSeconds: nil,userSleepRating: nil, deviceId: nil, batteryRanOut: nil,sleepCycles: nil, sleepResultDate: nil, originalSleepRange: nil)))
                }
            )
            return Disposables.create {
                disposable.dispose()
            }
        }
    }
}
