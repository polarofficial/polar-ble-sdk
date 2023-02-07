//  Copyright © 2022 Polar. All rights reserved.

import Foundation

/// Polar offline recording entry container.
public struct PolarOfflineRecordingEntry : Equatable {
    /// Recording entry path in device.
    public let path: String
    /// Recording size in bytes.
    public let size: UInt
    /// The date and time of the recording entry i.e. the moment recording is started
    public let date: Date
    ///  data type of the recording
    public let type: PolarDeviceDataType
    
    public init(path: String, size: UInt, date: Date, type: PolarDeviceDataType) {
        self.path = path
        self.size = size
        self.date = date
        self.type = type
    }
}
