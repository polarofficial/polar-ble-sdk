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

    // The manager that most recently asked to connect, before its device id is known
    // (e.g. autoConnect). Used to resolve which manager owns a newly-seen device.
    private var pendingManager: PolarBleSdkManager?

    // Every PolarBleSdkManager created, including ones not yet tied to a device,
    // so BLE power state changes (received only here) can be broadcast to all of them.
    private struct WeakManagerWrapper { weak var manager: PolarBleSdkManager? }
    private var allManagers: [WeakManagerWrapper] = []

    init() {
        self.isBluetoothOn = false
        api.polarFilter(true)
        // This is the single shared api instance, so this device manager is the sole
        // owner of its delegate slots. Callbacks are routed per-device to the owning
        // PolarBleSdkManager below, instead of each manager overwriting these slots.
        api.observer = self
        api.deviceFeaturesObserver = self
        api.deviceInfoObserver = self
        api.powerStateObserver = self
        api.logger = self
    }

    /// Marks `manager` as the one that should own the next device connection whose id isn't yet known.
    func willConnect(_ manager: PolarBleSdkManager) {
        pendingManager = manager
    }

    private func manager(for device: PolarDeviceInfo) -> PolarBleSdkManager? {
        if let (_, manager) = managersByDeviceId[device.deviceId] {
            return manager
        }
        guard let pending = pendingManager else { return nil }
        managersByDeviceId[device.deviceId] = (device, pending)
        pendingManager = nil
        return pending
    }

    private func track(_ manager: PolarBleSdkManager) {
        allManagers.removeAll { $0.manager == nil }
        allManagers.append(WeakManagerWrapper(manager: manager))
        manager.isBluetoothOn = isBluetoothOn
    }

    /// Broadcasts a BLE power state change to every tracked manager, since only this
    /// device manager registers as the shared api's powerStateObserver.
    func broadcastBluetoothState(_ isOn: Bool) {
        allManagers.removeAll { $0.manager == nil }
        for box in allManagers {
            box.manager?.isBluetoothOn = isOn
        }
    }
    
    func setSearchPrefix(_ prefix: String) {
        deviceSearchNamePrefix = prefix
        startDevicesSearch()
    }
    
    func startDevicesSearch() {
        searchDevicesTask?.cancel()
        searchDevicesTask = nil
        deviceSearch.foundDevices.removeAll()
        deviceSearch.isSearching = .notStarted
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
            AppLogger.log(deviceSearchFailed)
            deviceSearch.isSearching = DeviceSearchState.failed(error: deviceSearchFailed)
        }
    }

    func makeSdkManager() -> PolarBleSdkManager {
        let manager = PolarBleSdkManager(api: api, owner: self)
        track(manager)
        return manager
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
        let manager = PolarBleSdkManager(api: api, owner: self)
        track(manager)
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
        managersByDeviceId.removeValue(forKey: device.deviceId)
        return managersByDeviceId.values.first
    }
    
    func disconnectAll() {
        // Collect device info before iterating to avoid mutation during iteration
        let devicesToDisconnect = managersByDeviceId.values.map { ($0.0, $0.1) }

        // Disconnect all devices
        devicesToDisconnect.forEach { (deviceInfo, manager) in
            manager.disconnectFromDevice(device: deviceInfo)
        }

        // Clear the dictionary after all disconnections
        managersByDeviceId.removeAll()
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
        AppLogger.log("Polar SDK log: \(str) [DevMgr]")
    }
}

// MARK: - PolarBleApiObserver (routes callbacks to the manager owning the device)
extension PolarBleDeviceManager : PolarBleApiObserver {
    nonisolated func deviceConnecting(_ polarDeviceInfo: PolarDeviceInfo) {
        Task { @MainActor in
            self.manager(for: polarDeviceInfo)?.deviceConnecting(polarDeviceInfo)
        }
    }

    nonisolated func deviceConnected(_ device: PolarDeviceInfo) {
        Task { @MainActor in
            self.manager(for: device)?.deviceConnected(device)
        }
    }

    nonisolated func deviceDisconnected(_ device: PolarDeviceInfo, info: PolarBleDisconnectInfo) {
        Task { @MainActor in
            self.managersByDeviceId[device.deviceId]?.1.deviceDisconnected(device, info: info)
        }
    }
}

// MARK: - PolarBleApiDeviceFeaturesObserver (routes callbacks to the manager owning the device)
extension PolarBleDeviceManager : PolarBleApiDeviceFeaturesObserver {
    nonisolated func bleSdkFeaturesReadiness(_ identifier: String, ready: [PolarBleSdkFeature], unavailable: [PolarBleSdkFeature]) {
        Task { @MainActor in
            self.managersByDeviceId[identifier]?.1.bleSdkFeaturesReadiness(identifier, ready: ready, unavailable: unavailable)
        }
    }

    nonisolated func bleSdkFeatureReady(_ identifier: String, feature: PolarBleSdkFeature) {
        Task { @MainActor in
            self.managersByDeviceId[identifier]?.1.bleSdkFeatureReady(identifier, feature: feature)
        }
    }
}

// MARK: - PolarBleApiDeviceInfoObserver (routes callbacks to the manager owning the device)
extension PolarBleDeviceManager : PolarBleApiDeviceInfoObserver {
    nonisolated func disInformationReceivedWithKeysAsStrings(_ identifier: String, key: String, value: String) {
        Task { @MainActor in
            self.managersByDeviceId[identifier]?.1.disInformationReceivedWithKeysAsStrings(identifier, key: key, value: value)
        }
    }

    nonisolated func batteryLevelReceived(_ identifier: String, batteryLevel: UInt) {
        Task { @MainActor in
            self.managersByDeviceId[identifier]?.1.batteryLevelReceived(identifier, batteryLevel: batteryLevel)
        }
    }

    nonisolated func batteryChargingStatusReceived(_ identifier: String, chargingStatus: BleBasClient.ChargeState) {
        Task { @MainActor in
            self.managersByDeviceId[identifier]?.1.batteryChargingStatusReceived(identifier, chargingStatus: chargingStatus)
        }
    }

    nonisolated func batteryPowerSourcesStateReceived(_ identifier: String, powerSourcesState: BleBasClient.PowerSourcesState) {
        Task { @MainActor in
            self.managersByDeviceId[identifier]?.1.batteryPowerSourcesStateReceived(identifier, powerSourcesState: powerSourcesState)
        }
    }

    nonisolated func disInformationReceived(_ identifier: String, uuid: CBUUID, value: String) {
        Task { @MainActor in
            self.managersByDeviceId[identifier]?.1.disInformationReceived(identifier, uuid: uuid, value: value)
        }
    }
}
