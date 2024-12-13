//  Copyright © 2024 Polar. All rights reserved.

import Foundation
import RxSwift

public let DEVICE_SETTINGS_FILE_PATH = "/U/0/S/UDEVSET.BPB"
private let TAG = "PolarUserDeviceSettingsUtils"

internal class PolarUserDeviceSettingsUtils {

    /// Read user device settings for the device
    static func getUserDeviceSettings(client: BlePsFtpClient) -> Single<PolarUserDeviceSettings.PolarUserDeviceSettingsResult> {
        BleLogger.trace(TAG, "getDeviceUserLocation")
        return Single<PolarUserDeviceSettings.PolarUserDeviceSettingsResult>.create { emitter in
            let operation = Protocol_PbPFtpOperation.with {
                $0.command = .get
                $0.path = DEVICE_SETTINGS_FILE_PATH
            }
            let disposable = client.request(try! operation.serializedData()).subscribe(
                onSuccess: { response in
                    do {
                        let proto = try Data_PbUserDeviceSettings(serializedData: Data(response))
                        
                        if (proto.hasGeneralSettings && proto.generalSettings.hasDeviceLocation) {
                            let result = PolarUserDeviceSettings.fromProto(pBDeviceUserLocation: proto)
                            emitter(.success(result))
                        } else {
                            emitter(.success(PolarUserDeviceSettings.PolarUserDeviceSettingsResult.init(deviceLocation: PolarUserDeviceSettings.DeviceLocation.UNDEFINED)))
                        }
                    } catch {
                        BleLogger.error("getDeviceUserLocation() failed for device: \(client), error: \(error).")
                        emitter(.failure(error))
                    }
                },
                onFailure: { error in
                    BleLogger.error("getDeviceUserLocation() failed for device: \(client), error: \(error).")
                    emitter(.success(PolarUserDeviceSettings.PolarUserDeviceSettingsResult(deviceLocation: .UNDEFINED)))
                }
            )
            return Disposables.create {
                disposable.dispose()
            }
        }
    }
}
