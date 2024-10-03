//  Copyright © 2024 Polar. All rights reserved.

import Foundation

public enum FirmwareUpdateStatus {
    case fetchingFwUpdatePackage(details: String)
    case preparingDeviceForFwUpdate(details: String)
    case writingFwUpdatePackage(details: String)
    case finalizingFwUpdate(details: String)
    case fwUpdateCompletedSuccessfully(details: String)
    case fwUpdateNotAvailable(details: String)
    case fwUpdateFailed(details: String)
}

public struct PolarFirmwareVersionInfo {
    public let deviceFwVersion: String
    public let deviceModelName: String
    public let deviceHardwareCode: String
}
