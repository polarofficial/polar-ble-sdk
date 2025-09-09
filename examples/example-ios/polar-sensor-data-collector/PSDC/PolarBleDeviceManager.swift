/// Copyright © 2021 Polar Electro Oy. All rights reserved.

import Foundation
import PolarBleSdk
import RxSwift
import CoreBluetooth

/// Device manager uses PolarBleSdk API to search for connectable devices and
/// maintains one PolarBleSdkManager instance per connected device
class PolarBleDeviceManager: ObservableObject {
    
    @Published var isBluetoothOn: Bool
    @Published var deviceSearch: DeviceSearch = DeviceSearch()
    
    public var deviceSearchNamePrefix:String = ""
    
    // NOTICE this example utilises all available features
    private var api =
        PolarBleApiDefaultImpl.polarImplementation(DispatchQueue.main, features: [])
    
    private let disposeBag = DisposeBag()
    private var h10ExerciseEntry: PolarExerciseEntry?
    private var searchDevicesTask: Task<Void, Never>? = nil
    
    init() {
        self.isBluetoothOn = api.isBlePowered
        api.polarFilter(true)
        api.powerStateObserver = self
        api.logger = self
    }
    
    func setSearchPrefix(_ prefix: String) {
        deviceSearchNamePrefix = prefix
        searchDevicesTask?.cancel()
        searchDevicesTask = nil
        Task { @MainActor in
            self.deviceSearch.foundDevices.removeAll()
            self.managersByDeviceId.removeAll()
            self.deviceSearch.isSearching = .notStarted
        }
    }
    
    func startDevicesSearch() {
        searchDevicesTask = Task {
            await searchDevicesAsync()
        }
    }
    
    func stopDevicesSearch() {
        searchDevicesTask?.cancel()
        searchDevicesTask = nil
        Task { @MainActor in
            self.deviceSearch.isSearching = DeviceSearchState.success
        }
    }
    
    private func searchDevicesAsync() async {
        Task { @MainActor in
            self.deviceSearch.foundDevices.removeAll()
            self.deviceSearch.isSearching = DeviceSearchState.inProgress
        }
        
        do {
            for try await value in api.searchForDevice(withRequiredDeviceNamePrefix: deviceSearchNamePrefix).values {
                Task { @MainActor in
                    self.deviceSearch.foundDevices.append(value)
                }
            }
            Task { @MainActor in
                self.deviceSearch.isSearching = DeviceSearchState.success
            }
        } catch let err {
            
            guard searchDevicesTask != nil else {
                // was cancelled by user
                return
            }
            
            let deviceSearchFailed = "device search failed: \(err)"
            NSLog(deviceSearchFailed)
            Task { @MainActor in
                self.deviceSearch.isSearching = DeviceSearchState.failed(error: deviceSearchFailed)
            }
        }
    }
    
    private var managersByDeviceId: [String: (PolarDeviceInfo, PolarBleSdkManager)] = [:]

    func sdkManager(for device: PolarDeviceInfo, autoConnect: Bool = true) -> PolarBleSdkManager {
        if let (_, manager) = managersByDeviceId[device.deviceId] {
            if autoConnect {
                manager.connectToDevice(withId: device.deviceId)
            }
            return manager
        }
        let manager = PolarBleSdkManager()
        managersByDeviceId[device.deviceId] = (device, manager)
        if autoConnect {
            manager.connectToDevice(withId: device.deviceId)
        }
        return manager
    }
    
    func disconnect(_ device: PolarDeviceInfo) -> (PolarDeviceInfo, PolarBleSdkManager)? {
        if let (device, manager) = managersByDeviceId.removeValue(forKey: device.deviceId) {
            manager.disconnectFromDevice(device: device)
        }
        return managersByDeviceId.values.first
    }
    
    func disconnectAll() {
        managersByDeviceId.values.forEach { $0.1.disconnectFromDevice(device: $0.0) }
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
extension PolarBleDeviceManager : PolarBleApiLogger {
    func message(_ str: String) {
        let timestamp = Date.now
        NSLog("\(timestamp) Polar SDK log:  \(str) [DevMgr]")
    }
}
