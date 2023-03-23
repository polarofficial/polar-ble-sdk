//  Copyright © 2023 Polar. All rights reserved.

import Foundation

/// Polar offline recording trigger mode. Offline recording trigger can be used to start the offline recording automatically in device, based on selected trigger mode.
public enum PolarOfflineRecordingTriggerMode {
    // The automatic start of offline recording is disabled
    case triggerDisabled
    
    // Triggers the offline recording when device is powered on
    case triggerSystemStart
    
    // Triggers the offline recording when exercise is started in device
    case triggerExerciseStart
}

/// Polar offline recording trigger
public struct PolarOfflineRecordingTrigger {
    /// The mode of the trigger
    public let triggerMode: PolarOfflineRecordingTriggerMode
    
    /// Dictionary containing the `PolarDeviceDataType` keys for enabled triggers. Dictionary
    /// is empty if `triggerMode` is `PolarOfflineRecordingTriggerMode.triggerDisabled`.
    /// In case of the `PolarDeviceDataType.ppi` or `PolarDeviceDataType.hr`  the `settings` is nil
    public let triggerFeatures: [PolarDeviceDataType : PolarSensorSetting?]
    
    public init(triggerMode: PolarOfflineRecordingTriggerMode, triggerFeatures: [PolarDeviceDataType : PolarSensorSetting?] ) {
        self.triggerMode = triggerMode
        self.triggerFeatures = triggerFeatures
    }
}
