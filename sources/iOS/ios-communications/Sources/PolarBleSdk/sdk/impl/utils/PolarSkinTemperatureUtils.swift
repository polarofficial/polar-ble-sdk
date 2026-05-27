//  Copyright © 2025 Polar. All rights reserved.

import Foundation

private let ARABICA_USER_ROOT_FOLDER = "/U/0/"
private let SKIN_TEMPERATURE_DIRECTORY = "SKINTEMP/"
private let SKIN_TEMPERATURE_PROTO = "TEMPCONT.BPB"
private let dateFormat: DateFormatter = {
    let formatter = DateFormatter()
    formatter.dateFormat = "yyyyMMdd"
    formatter.locale = Locale(identifier: "en_US_POSIX")
    return formatter
}()
private let TAG = "PolarSkinTemperatureUtils"

internal class PolarSkinTemperatureUtils {
    static func readSkinTemperatureData(client: BlePsFtpClient, date: Date) async -> PolarSkinTemperatureData.PolarSkinTemperatureResult? {
        BleLogger.trace(TAG, "readSkinTemperatureData: \(date)")
        let filePath = "\(ARABICA_USER_ROOT_FOLDER)\(dateFormat.string(from: date))/\(SKIN_TEMPERATURE_DIRECTORY)\(SKIN_TEMPERATURE_PROTO)"
        let operation = Protocol_PbPFtpOperation.with { $0.command = .get; $0.path = filePath }
        do {
            let response = try await client.request(try operation.serializedBytes())
            let skinTemp = try Data_TemperatureMeasurementPeriod(serializedBytes: Data(response))
            return PolarSkinTemperatureData.PolarSkinTemperatureResult(
                date: date,
                sensorLocation: try PolarSkinTemperatureData.SkinTemperatureSensorLocation.getByValue(value: skinTemp.sensorLocation),
                measurementType: try PolarSkinTemperatureData.SkinTemperatureMeasurementType.getByValue(value: skinTemp.measurementType),
                skinTemperatureList: PolarSkinTemperatureData.fromPbTemperatureMeasurementSamples(pbTemperatureMeasurementData: skinTemp.temperatureMeasurementSamples)
            )
        } catch {
            BleLogger.error("readSkinTemperatureData() failed for path: \(filePath), error: \(error)")
            return nil
        }
    }
}
