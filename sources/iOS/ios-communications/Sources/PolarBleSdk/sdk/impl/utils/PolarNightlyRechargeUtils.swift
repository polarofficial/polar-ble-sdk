//  Copyright © 2024 Polar. All rights reserved.

import Foundation
import RxSwift

private let ARABICA_USER_ROOT_FOLDER = "/U/0/"
private let NIGHTLY_RECOVERY_DIRECTORY = "NR/"
private let NIGHTLY_RECOVERY_PROTO = "NR.BPB"
private let dateFormat: DateFormatter = {
    let formatter = DateFormatter()
    formatter.dateFormat = "yyyyMMdd"
    formatter.locale = Locale(identifier: "en_US_POSIX")
    return formatter
}()
private let TAG = "PolarNightlyRechargeUtils"

internal class PolarNightlyRechargeUtils {

    enum PolarNightlyRechargeError: Error {
        case missingOrInvalidRecoveryDate
    }

    /// Read nightly recharge data for given date.
    static func readNightlyRechargeData(client: BlePsFtpClient, date: Date) -> Maybe<PolarNightlyRechargeData> {
        BleLogger.trace(TAG, "readNightlyRechargeData: \(date)")
        return Maybe<PolarNightlyRechargeData>.create { emitter in
            let nightlyRecoveryFilePath = "\(ARABICA_USER_ROOT_FOLDER)\(dateFormat.string(from: date))/\(NIGHTLY_RECOVERY_DIRECTORY)\(NIGHTLY_RECOVERY_PROTO)"
            let operation = Protocol_PbPFtpOperation.with {
                $0.command = .get
                $0.path = nightlyRecoveryFilePath
            }
            let disposable = client.request(try! operation.serializedData()).subscribe(
                onSuccess: { response in
                    do {
                        let recoveryStatus = try Data_PbNightlyRecoveryStatus(serializedData: Data(response))
                        let recoveryDateProto = recoveryStatus.sleepResultDate
                        guard let recoveryDate = Calendar.current.date(from: DateComponents(
                            year: Int(recoveryDateProto.year),
                            month: Int(recoveryDateProto.month),
                            day: Int(recoveryDateProto.day)
                        )) else {
                            throw PolarNightlyRechargeError.missingOrInvalidRecoveryDate
                        }

                        let createdTimestamp = try PolarTimeUtils.pbSystemDateTimeToDate(pbSystemDateTime: recoveryStatus.createdTimestamp)
                        let modifiedTimestamp = recoveryStatus.hasModifiedTimestamp ? try PolarTimeUtils.pbSystemDateTimeToDate(pbSystemDateTime: recoveryStatus.modifiedTimestamp) : nil

                        let nightlyRechargeData = PolarNightlyRechargeData(
                            createdTimestamp: createdTimestamp,
                            modifiedTimestamp: modifiedTimestamp,
                            ansStatus: Float(recoveryStatus.ansStatus),
                            recoveryIndicator: Int(recoveryStatus.recoveryIndicator),
                            recoveryIndicatorSubLevel: Int(recoveryStatus.recoveryIndicatorSubLevel),
                            ansRate: Int(recoveryStatus.ansRate),
                            scoreRateObsolete: Int(recoveryStatus.scoreRateObsolete),
                            meanNightlyRecoveryRRI: Int(recoveryStatus.meanNightlyRecoveryRri),
                            meanNightlyRecoveryRMSSD: Int(recoveryStatus.meanNightlyRecoveryRmssd),
                            meanNightlyRecoveryRespirationInterval: Int(recoveryStatus.meanNightlyRecoveryRespirationInterval),
                            meanBaselineRRI: Int(recoveryStatus.meanBaselineRri),
                            sdBaselineRRI: Int(recoveryStatus.sdBaselineRri),
                            meanBaselineRMSSD: Int(recoveryStatus.meanBaselineRmssd),
                            sdBaselineRMSSD: Int(recoveryStatus.sdBaselineRmssd),
                            meanBaselineRespirationInterval: Int(recoveryStatus.meanBaselineRespirationInterval),
                            sdBaselineRespirationInterval: Int(recoveryStatus.sdBaselineRespirationInterval),
                            sleepTip: recoveryStatus.sleepTip,
                            vitalityTip: recoveryStatus.vitalityTip,
                            exerciseTip: recoveryStatus.exerciseTip,
                            sleepResultDate: recoveryDate
                        )
                        emitter(.success(nightlyRechargeData))
                    } catch {
                        BleLogger.error("readNightlyRechargeData() failed for path: \(nightlyRecoveryFilePath), error: \(error)")
                        emitter(.completed)
                    }
                },
                onFailure: { error in
                    BleLogger.error("readNightlyRechargeData() failed for path: \(nightlyRecoveryFilePath), error: \(error)")
                    emitter(.completed)
                }
            )
            return Disposables.create {
                disposable.dispose()
            }
        }
    }
}
