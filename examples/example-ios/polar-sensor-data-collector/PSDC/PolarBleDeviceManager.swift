/// Copyright © 2021 Polar Electro Oy. All rights reserved.

import Foundation
import PolarBleSdk
import CoreBluetooth

/// Device manager uses PolarBleSdk API to search for connectable devices and
/// maintains one PolarBleSdkManager instance per connected device
@MainActor
class PolarBleDeviceManager: ObservableObject {
    
    @Published var isBluetoothOn: Bool
    @Published var deviceSearch: DeviceSearch = DeviceSearch()
    
    public var deviceSearchNamePrefix:String = ""
    
    // Single shared api instance used for both scanning and device connections.
    // Sharing ensures peripherals discovered during scan are in the same session
    // map that connectToDevice looks up — preventing "session not found" errors.
    private var api =
        PolarBleApiDefaultImpl.polarImplementation(DispatchQueue.main, features: [], restoreIdentifier: "com.polar.PolarSensorDataCollector-iOS.scan")
    
    private var h10ExerciseEntry: PolarExerciseEntry?
    private var searchDevicesTask: Task<Void, Never>? = nil
    
    private var managersByDeviceId: [String: (PolarDeviceInfo, PolarBleSdkManager)] = [:]

    init() {
        self.isBluetoothOn = false
        api.polarFilter(true)
        api.powerStateObserver = self
        api.logger = self
    }
    
    func setSearchPrefix(_ prefix: String) {
        deviceSearchNamePrefix = prefix
        searchDevicesTask?.cancel()
        searchDevicesTask = nil
        deviceSearch.foundDevices.removeAll()
        deviceSearch.isSearching = .notStarted
    }
    
    func startDevicesSearch() {
        searchDevicesTask = Task {
            await searchDevicesAsync()
        }
    }
    
    func stopDevicesSearch() {
        searchDevicesTask?.cancel()
        searchDevicesTask = nil
        deviceSearch.isSearching = DeviceSearchState.success
    }
    
    private func searchDevicesAsync() async {
        deviceSearch.foundDevices.removeAll()
        deviceSearch.isSearching = DeviceSearchState.inProgress

        do {
            for try await value in api.searchForDevice(withRequiredDeviceNamePrefix: deviceSearchNamePrefix) {
                guard !deviceSearch.foundDevices.contains(where: { $0.deviceId == value.deviceId }) else { continue }
                deviceSearch.foundDevices.append(value)
            }
            deviceSearch.isSearching = DeviceSearchState.success
        } catch let err {
            guard searchDevicesTask != nil else {
                // was cancelled by user
                return
            }
            let deviceSearchFailed = "device search failed: \(err)"
            NSLog(deviceSearchFailed)
            deviceSearch.isSearching = DeviceSearchState.failed(error: deviceSearchFailed)
        }
    }

    func makeSdkManager() -> PolarBleSdkManager {
        return PolarBleSdkManager(api: api)
    }

    func sdkManager(for device: PolarDeviceInfo, autoConnect: Bool = true) -> PolarBleSdkManager {
        if let (_, manager) = managersByDeviceId[device.deviceId] {
            if autoConnect {
                manager.connectToDevice(withId: device.deviceId)
            }
            return manager
        }
        // Pass the shared api so the peripheral already in the scan session map
        // is immediately available to connectToDevice — no second api instance needed.
        let manager = PolarBleSdkManager(api: api)
        managersByDeviceId[device.deviceId] = (device, manager)
        if autoConnect {
            manager.connectToDevice(withId: device.deviceId)
        }
        return manager
    }
    
    func disconnect(_ device: PolarDeviceInfo) -> (PolarDeviceInfo, PolarBleSdkManager)? {
        if let (_, manager) = managersByDeviceId[device.deviceId] {
            manager.disconnectFromDevice(device: device)
        }
        return managersByDeviceId.values.first
    }
    
    func disconnectAll() {
        managersByDeviceId.values.forEach { $0.1.disconnectFromDevice(device: $0.0) }
    }
    
    func connectedDevices() -> [PolarDeviceInfo] {
        let connectedDevices = managersByDeviceId.values.compactMap { (deviceInfo, manager) in
            manager.deviceId == deviceInfo.deviceId ? deviceInfo : nil
        }
        return connectedDevices
    }
}

// MARK: - PolarBleApiLogger
extension PolarBleDeviceManager : @MainActor PolarBleApiLogger {
    func message(_ str: String) {
        let timestamp = Date.now
        NSLog("\(timestamp) Polar SDK log:  \(str) [DevMgr]")
    }
}
