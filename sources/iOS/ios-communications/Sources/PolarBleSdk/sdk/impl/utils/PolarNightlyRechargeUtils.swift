//  Copyright © 2024 Polar. All rights reserved.

import Foundation

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
    enum PolarNightlyRechargeError: Error { case missingOrInvalidRecoveryDate }

    static func readNightlyRechargeData(client: BlePsFtpClient, date: Date) async -> PolarNightlyRechargeData? {
        BleLogger.trace(TAG, "readNightlyRechargeData: \(date)")
        let filePath = "\(ARABICA_USER_ROOT_FOLDER)\(dateFormat.string(from: date))/\(NIGHTLY_RECOVERY_DIRECTORY)\(NIGHTLY_RECOVERY_PROTO)"
        let operation = Protocol_PbPFtpOperation.with { $0.command = .get; $0.path = filePath }
        do {
            let response = try await client.request(try operation.serializedBytes())
            let recoveryStatus = try Data_PbNightlyRecoveryStatus(serializedBytes: Data(response))
            guard let recoveryDate = try? PolarTimeUtils.pbDateToDateComponents(pbDate: recoveryStatus.sleepResultDate) else {
                throw PolarNightlyRechargeError.missingOrInvalidRecoveryDate
            }
            let createdTimestamp = try PolarTimeUtils.pbSystemDateTimeToDate(pbSystemDateTime: recoveryStatus.createdTimestamp)
            let modifiedTimestamp = recoveryStatus.hasModifiedTimestamp ? try PolarTimeUtils.pbSystemDateTimeToDate(pbSystemDateTime: recoveryStatus.modifiedTimestamp) : nil
            return PolarNightlyRechargeData(
                createdTimestamp: createdTimestamp, modifiedTimestamp: modifiedTimestamp,
                ansStatus: Float(recoveryStatus.ansStatus), recoveryIndicator: Int(recoveryStatus.recoveryIndicator),
                recoveryIndicatorSubLevel: Int(recoveryStatus.recoveryIndicatorSubLevel), ansRate: Int(recoveryStatus.ansRate),
                scoreRateObsolete: Int(recoveryStatus.scoreRateObsolete), meanNightlyRecoveryRRI: Int(recoveryStatus.meanNightlyRecoveryRri),
                meanNightlyRecoveryRMSSD: Int(recoveryStatus.meanNightlyRecoveryRmssd),
                meanNightlyRecoveryRespirationInterval: Int(recoveryStatus.meanNightlyRecoveryRespirationInterval),
                meanBaselineRRI: Int(recoveryStatus.meanBaselineRri), sdBaselineRRI: Int(recoveryStatus.sdBaselineRri),
                meanBaselineRMSSD: Int(recoveryStatus.meanBaselineRmssd), sdBaselineRMSSD: Int(recoveryStatus.sdBaselineRmssd),
                meanBaselineRespirationInterval: Int(recoveryStatus.meanBaselineRespirationInterval),
                sdBaselineRespirationInterval: Int(recoveryStatus.sdBaselineRespirationInterval),
                sleepTip: recoveryStatus.sleepTip, vitalityTip: recoveryStatus.vitalityTip,
                exerciseTip: recoveryStatus.exerciseTip, sleepResultDate: recoveryDate
            )
        } catch {
            BleLogger.error("readNightlyRechargeData() failed for path: \(filePath), error: \(error)")
            return nil
        }
    }
}
