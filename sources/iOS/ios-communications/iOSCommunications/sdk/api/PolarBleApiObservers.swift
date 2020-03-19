/// Copyright © 2019 Polar Electro Oy. All rights reserved.

import Foundation
import CoreBluetooth

/// Polar Ble API connection observer.
public protocol PolarBleApiObserver: class {
    
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
public protocol PolarBleApiPowerStateObserver: class {
    /// Ble powered on event.
    func blePowerOn()
    
    /// Ble powered off event, no further actions are needed from the application.
    func blePowerOff()
}

/// Device info observer.
public protocol PolarBleApiDeviceInfoObserver: class {
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
public protocol PolarBleApiDeviceHrObserver: class {
    /// Polar hr data
    ///
    ///     - hr in BPM
    ///     - rrs RR interval in 1/1024. R is a the top highest peak in the QRS complex of the ECG wave and RR is the interval between successive Rs. 
    ///     - rrs RR interval in ms.
    ///     - contact status between the device and the users skin
    ///     - contactSupported if contact is supported
    typealias PolarHrData = (hr: UInt8, rrs: [Int], rrsMs: [Int], contact: Bool, contactSupported: Bool)
    /// HR notification received. Notice when using OH1 and PPI stream is started this callback will produce 0 hr.
    ///
    /// - Parameters:
    ///   - identifier: Polar device id
    func hrValueReceived(_ identifier: String, data: PolarHrData)
}

/// Data client observer
public protocol PolarBleApiDeviceFeaturesObserver: class {
    
    /// Device HR feature is ready. HR transmission is starting in a short while.
    ///
    /// - Parameter identifier: Polar device id
    func hrFeatureReady(_ identifier: String)

    /// Device ECG feature is ready. Application can now start ECG streaming.
    ///
    /// - Parameters:
    ///   - identifier: Polar device id
    func ecgFeatureReady(_ identifier: String)

    /// Device ACC feature is ready. Application can now start ACC streaming.
    ///
    /// - Parameters:
    ///   - identifier: Polar device id
    func accFeatureReady(_ identifier: String)

    /// Device OHR PPG feature is ready.
    ///
    /// - Parameter identifier: polar device id
    func ohrPPGFeatureReady(_ identifier: String)

    /// Device OHR PPG feature is ready.
    ///
    /// - Parameter identifier: polar device id
    func ohrPPIFeatureReady(_ identifier: String)

    func biozFeatureReady(_ identifier: String)

    /// Device file transfer protocol is ready.
    /// Notice all file transfer operations are preferred to be done at beginning of the connection
    ///
    /// - Parameter identifier: polar device id
    func ftpFeatureReady(_ identifier: String)
}

public extension PolarBleApiDeviceFeaturesObserver {
    func biozFeatureReady(_ identifier: String) {
        // default empty
    }
}

/// logger observer
public protocol PolarBleApiLogger: class {
    
    /// log message from sdk
    ///
    /// - Parameter str: message
    func message(_ str: String)
}

/// observer for ccc write enable
public protocol PolarBleApiCCCWriteObserver: class {
    func cccWrite(_ address: UUID, characteristic: CBUUID)
}
