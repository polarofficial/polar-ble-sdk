//  Copyright © 2024 Polar. All rights reserved.

import Foundation

public enum CheckFirmwareUpdateStatus {
    case checkFwUpdateAvailable(version: String)
    case checkFwUpdateNotAvailable(details: String)
    case checkFwUpdateFailed(details: String)
}

public enum FirmwareUpdateStatus {
    case fetchingFwUpdatePackage(details: String)
    case preparingDeviceForFwUpdate(details: String)
    case writingFwUpdatePackage(details: String)
    case finalizingFwUpdate(details: String)
    case fwUpdateCompletedSuccessfully(details: String)
    case fwUpdateNotAvailable(details: String)
    case fwUpdateFailed(details: String)
}

struct PolarFirmwareVersionInfo {
    let deviceFwVersion: String
    let deviceModelName: String
    let deviceHardwareCode: String
}
