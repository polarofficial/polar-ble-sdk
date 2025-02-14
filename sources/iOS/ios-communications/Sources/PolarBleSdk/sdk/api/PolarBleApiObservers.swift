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
    /// -  Parameter pairingError: If true, it indicates that the disconnection was caused by a pairing error. In this case, try removing the pairing from the system settings.
    func deviceDisconnected(_ identifier: PolarDeviceInfo, pairingError: Bool)
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
    ///   - uuid: CBUUID key
    ///   - value: String value
    func disInformationReceived(_ identifier: String, uuid: CBUUID, value: String)

    ///  Received DIS info with String keys.
    ///
    /// - Parameters:
    ///   - identifier: Polar device id
    ///   - key: String key
    ///   - value: String value
    func disInformationReceivedWithKeysAsStrings(_ identifier: String, key: String, value: String)
}

/// Heart rate observer
public protocol PolarBleApiDeviceHrObserver: AnyObject {
    /// HR notification received. Notice when using OH1 and PPI stream is started this callback will produce 0 hr.
    ///
    /// - Parameters:
    ///   - identifier: Polar device id
    func hrValueReceived(_ identifier: String, data: (hr: UInt8, rrs: [Int], rrsMs: [Int], contact: Bool, contactSupported: Bool))
}

/// Data client observer
public protocol PolarBleApiDeviceFeaturesObserver: AnyObject {
    
    /// The feature is available in this device and it is ready.  Called only for the features which are specified in [PolarBleApi] construction.
    /// feature ready callback
    func bleSdkFeatureReady(_ identifier: String, feature: PolarBleSdkFeature)
}

/// logger observer
public protocol PolarBleApiLogger: AnyObject {
    
    /// log message from sdk
    ///
    /// - Parameter str: message
    func message(_ str: String)
}
