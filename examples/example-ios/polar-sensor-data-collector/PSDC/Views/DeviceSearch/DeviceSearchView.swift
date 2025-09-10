/// Copyright © 2023 Polar Electro Oy. All rights reserved.

import Foundation
import SwiftUI
import PolarBleSdk
import RxSwift

struct DeviceSearchView: View {

    @EnvironmentObject var appState: AppState
    @EnvironmentObject var bleDeviceManager: PolarBleDeviceManager
    @Binding var isPresented: Bool
    @State private var deviceSearchNamePrefix: String = ""

    private var sortedConnectableDevices: [PolarDeviceInfo] {
        var devices = bleDeviceManager.deviceSearch.foundDevices
        devices.removeAll(where: { device in
            sortedKnownDevices.contains { $0.deviceId == device.deviceId }
        })
        return devices.sorted { $0.rssi > $1.rssi }
    }
    
    private var sortedKnownDevices: [PolarDeviceInfo] {
        return appState.bleDeviceManager.connectedDevices()
    }
    
    var body: some View {

        NavigationView {
            VStack {
                HStack {
                    TextField("Filter by name prefix", text: $deviceSearchNamePrefix)
                        .disableAutocorrection(true)
                        .padding(.top)
                        .padding(.leading)
                        .onChange(of: deviceSearchNamePrefix) { newValue in
                            print("New prefix: \(newValue)")
                            bleDeviceManager.setSearchPrefix(newValue)
                        }
                        .onAppear {
                            deviceSearchNamePrefix = bleDeviceManager.deviceSearchNamePrefix
                        }
                    Spacer()
                    Button(action: {
                        if case .inProgress = bleDeviceManager.deviceSearch.isSearching {
                            bleDeviceManager.stopDevicesSearch()
                        } else {
                            bleDeviceManager.startDevicesSearch()
                        }
                    }) {
                        HStack {
                            Text(getSearchButtonText(bleDeviceManager.deviceSearch.isSearching))
                        }
                    }
                    .padding(.trailing)
                    .padding(.top)
                }
                switch bleDeviceManager.deviceSearch.isSearching {
                case .notStarted:
                    Spacer()
                case .failed(let error):
                    Text("Error: \(error)")
                        .foregroundColor(.red)
                        .multilineTextAlignment(.center)
                        .padding()
                case .inProgress, .success:
                    
                    List {
                        ForEach(sortedKnownDevices, id: \.deviceId) { knownDevice in
                            Button(action: {
                                isPresented = false
                                let bleSdkManager = appState.bleDeviceManager.sdkManager(for: knownDevice)
                                bleSdkManager.connectToDevice(withId: knownDevice.deviceId)
                                appState.switchTo(bleSdkManager)
                            })
                            {
                                DeviceSearchRow(polarDeviceInfo: knownDevice, isKnownDevice: true)
                            }
                        }
                        Divider()
                        ForEach(sortedConnectableDevices, id: \.deviceId) { foundDevice in
                            Button(action: {
                                isPresented = false
                                let bleSdkManager = appState.bleDeviceManager.sdkManager(for: foundDevice)
                                bleSdkManager.connectToDevice(withId: foundDevice.deviceId)
                                appState.switchTo(bleSdkManager)                                
                            })
                            {
                                DeviceSearchRow(polarDeviceInfo: foundDevice, isKnownDevice: false)
                            }
                        }
                    }
                    .task {
                        bleDeviceManager.startDevicesSearch()
                    }
                }
            }.navigationBarTitle("")
                .navigationBarHidden(true)
        }.navigationViewStyle(StackNavigationViewStyle())
    }
    
    private func getSearchButtonText(_ state: DeviceSearchState) -> String {
        switch(state) {
        case .inProgress:
            return "Stop Search"
        case .notStarted, .success:
            return "Start Search"
        case .failed(error: let error):
            return "Restart Search"
        }
    }
    
    private func getSearchButtonIcon(_ state: DeviceSearchState) -> String {
        switch(state) {
        case .inProgress:
            return "stop.circle"
        case .notStarted, .success:
            return "play.circle"
        case .failed(error: let error):
            return "play.circle"
        }
    }
}

struct DeviceSearchView_Previews: PreviewProvider {
    
    private static let device1: PolarDeviceInfo = (deviceId: "device1", address: UUID(), rssi: -70, name: "Polar H10", connectable: true)
    private static let device2: PolarDeviceInfo = (deviceId: "device2", address: UUID(), rssi: -60, name: "Polar A370", connectable: true)
    private static let device3: PolarDeviceInfo = (deviceId: "device3", address: UUID(), rssi: -85, name: "Polar Vantage M", connectable: false)
    private static let device4: PolarDeviceInfo = (deviceId: "device4", address: UUID(), rssi: -55, name: "Polar Ignite", connectable: true)
    private static let device5: PolarDeviceInfo = (deviceId: "device5", address: UUID(), rssi: -90, name: "Polar OH1", connectable: false)
    
    private static let polarBleSdkManager: PolarBleSdkManager = {
        let polarBleSdkManager = PolarBleSdkManager()
        polarBleSdkManager.deviceSearch = DeviceSearch(
            isSearching: DeviceSearchState.success,
            foundDevices: [device1, device2, device3, device4, device5])
        
        return polarBleSdkManager
    }()
    
    private static let polarBlebleDeviceManager: PolarBleDeviceManager = {
        let polarBlebleDeviceManager = PolarBleDeviceManager()
        polarBlebleDeviceManager.deviceSearch = DeviceSearch(
            isSearching: DeviceSearchState.success,
            foundDevices: [device1, device2, device3, device4, device5])
        
        return polarBlebleDeviceManager
    }()
    
    private static let appState: AppState = {
        let appState = AppState()
        appState.bleSdkManager = polarBleSdkManager
        appState.bleDeviceManager = polarBlebleDeviceManager

        return appState
    }()
    
    static var previews: some View {
        let appState = appState
        DeviceSearchView(isPresented: .constant(true))
            .environmentObject(appState)
    }
}
