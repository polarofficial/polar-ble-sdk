//
//  Copyright © 2025 Polar. All rights reserved.
//

import Foundation
import RxSwift

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

    /// Read skin temperature data for the given date.
    static func readSkinTemperatureData(client: BlePsFtpClient, date: Date) -> Maybe<PolarSkinTemperatureData.PolarSkinTemperatureResult> {
        BleLogger.trace(TAG, "readSkinTemperatureData: \(date)")
        return Maybe<PolarSkinTemperatureData.PolarSkinTemperatureResult>.create { emitter in
            let skinTemperatureFilePath = "\(ARABICA_USER_ROOT_FOLDER)\(dateFormat.string(from: date))/\(SKIN_TEMPERATURE_DIRECTORY)\(SKIN_TEMPERATURE_PROTO)"
            let operation = Protocol_PbPFtpOperation.with {
                $0.command = .get
                $0.path = skinTemperatureFilePath
            }
            let disposable = client.request(try! operation.serializedData()).subscribe(
                onSuccess: { response in
                    do {
                        let skinTemp = try Data_TemperatureMeasurementPeriod(serializedData: Data(response))
                        let polarSkinTemperatureResult = PolarSkinTemperatureData.PolarSkinTemperatureResult(
                            date: date, sensorLocation: try PolarSkinTemperatureData.SkinTemperatureSensorLocation.getByValue(value: skinTemp.sensorLocation),
                            measurementType: try PolarSkinTemperatureData.SkinTemperatureMeasurementType.getByValue(value: skinTemp.measurementType),
                            skinTemperatureList: PolarSkinTemperatureData.fromPbTemperatureMeasurementSamples(pbTemperatureMeasurementData: skinTemp.temperatureMeasurementSamples)
                        )
                        emitter(.success(polarSkinTemperatureResult))
                    } catch {
                        BleLogger.error("readSkinTemperatureData() failed for path: \(skinTemperatureFilePath), error: \(error)")
                        emitter(.completed)
                    }
                },
                onFailure: { error in
                    BleLogger.error("readSkinTemperatureData() failed for path: \(skinTemperatureFilePath), error: \(error)")
                    emitter(.completed)
                }
            )
            return Disposables.create {
                disposable.dispose()
            }
        }
    }
}
