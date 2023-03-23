/// Copyright © 2023 Polar Electro Oy. All rights reserved.

import Foundation
import SwiftUI
import PolarBleSdk

struct DeviceSearchView: View {
    @EnvironmentObject var bleSdkManager: PolarBleSdkManager
    @Binding var isPresented: Bool
    
    private var sortedDevices: [PolarDeviceInfo] {
        bleSdkManager.deviceSearch.foundDevices.sorted { $0.rssi > $1.rssi }
    }
    
    var body: some View {
        NavigationView {
            VStack {
                HStack {
                    Spacer()
                    Button(action: {
                        if case .inProgress = bleSdkManager.deviceSearch.isSearching {
                            bleSdkManager.stopDevicesSearch()
                        } else {
                            bleSdkManager.startDevicesSearch()
                        }
                    }) {
                        HStack {
                            Text(getSearchButtonText(bleSdkManager.deviceSearch.isSearching))
                        }
                    }
                    .padding(.trailing)
                    .padding(.top)
                }
                switch bleSdkManager.deviceSearch.isSearching {
                case .failed(let error):
                    Text("Error: \(error)")
                        .foregroundColor(.red)
                        .multilineTextAlignment(.center)
                        .padding()
                case .inProgress, .success:
                    List(sortedDevices, id: \.deviceId) { foundDevice in
                        Button(action: {
                            isPresented = false
                            bleSdkManager.updateSelectedDevice(deviceId: foundDevice.deviceId)
                        })
                        { DeviceSearchRow(polarDeviceInfo: foundDevice) }
                    }
                    .task {
                        bleSdkManager.startDevicesSearch()
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
        case .success:
            return "Start Search"
        case .failed(error: let error):
            return "Restart Search"
        }
    }
    
    private func getSearchButtonIcon(_ state: DeviceSearchState) -> String {
        switch(state) {
        case .inProgress:
            return "stop.circle"
        case .success:
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
    
    static var previews: some View {
        DeviceSearchView(isPresented: .constant(true))
            .environmentObject(polarBleSdkManager)
    }
}
