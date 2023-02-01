/// Copyright © 2019 Polar Electro Oy. All rights reserved.

import Foundation
import CoreBluetooth

/// Polar Ble API connection observer.
public protocol PolarBleApiObserver: AnyObject {
    
    /// Callback when connection attempt is started to device
    ///
    /// - Parameter identifier: Polar device info
    func deviceConnecting(_ identifier: PolarDeviceInfo)
    
    /// Device connnection has been established.
    ///
    /// - Parameter identifier: Polar device info
    func deviceConnected(_ identifier: PolarDeviceInfo)
    
    /// Connection lost to device.
    /// If PolarBleApi#disconnectFromPolarDevice is not called, a new connection attempt is dispatched automatically.
    ///
    /// - Parameter identifier: Polar device info
    func deviceDisconnected(_ identifier: PolarDeviceInfo)
}

/// Bluetooth state observer.
public protocol PolarBleApiPowerStateObserver: AnyObject {
    /// Ble powered on event.
    func blePowerOn()
    
    /// Ble powered off event, no further actions are needed from the application.
    func blePowerOff()
}

/// Device info observer.
public protocol PolarBleApiDeviceInfoObserver: AnyObject {
    /// Battery level received from device.
    ///
    /// - Parameters:
    ///   - identifier: Polar device id
    ///   - batteryLevel: battery level in precentage 0-100%
    func batteryLevelReceived(_ identifier: String, batteryLevel: UInt)
    
    ///  Received DIS info.
    ///
    /// - Parameters:
    ///   - identifier: Polar device id
    ///   - fwVersion: firmware version in format major.minor.patch
    func disInformationReceived(_ identifier: String, uuid: CBUUID, value: String)
}

/// Heart rate observer
public protocol PolarBleApiDeviceHrObserver: AnyObject {
    /// HR notification received. Notice when using OH1 and PPI stream is started this callback will produce 0 hr.
    ///
    /// - Parameters:
    ///   - identifier: Polar device id
    func hrValueReceived(_ identifier: String, data: PolarHrData)
}

/// Data client observer
public protocol PolarBleApiDeviceFeaturesObserver: AnyObject {
    
    /// Device HR feature is ready. HR transmission is starting in a short while.
    ///
    /// - Parameter identifier: Polar device id
    func hrFeatureReady(_ identifier: String)
    
    /// Device file transfer protocol is ready.
    /// Notice all file transfer operations are preferred to be done at beginning of the connection
    ///
    /// - Parameter identifier: polar device id
    func ftpFeatureReady(_ identifier: String)
    
    /// feature ready callback
    func streamingFeaturesReady(_ identifier: String, streamingFeatures: Set<DeviceStreamingFeature>)
    
    /// The feature is available in this device and it is ready.  Called only for the features which are specified in [PolarBleApi] construction.
    func bleSdkFeatureReady(_ identifier: String, feature: PolarBleSdkFeature)
}

/// SDK Mode observer
public protocol PolarBleApiSdkModeFeatureObserver: AnyObject {
    /// sdk mode feature available in this device and ready for usage callback
    func sdkModeFeatureAvailable(_ identifier: String)
}

/// logger observer
public protocol PolarBleApiLogger: AnyObject {
    
    /// log message from sdk
    ///
    /// - Parameter str: message
    func message(_ str: String)
}

/// observer for ccc write enable
public protocol PolarBleApiCCCWriteObserver: AnyObject {
    func cccWrite(_ address: UUID, characteristic: CBUUID)
}
