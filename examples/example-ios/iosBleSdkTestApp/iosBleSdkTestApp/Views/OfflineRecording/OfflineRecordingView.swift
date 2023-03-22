/// Copyright © 2022 Polar Electro Oy. All rights reserved.

import Foundation
import SwiftUI
import PolarBleSdk

enum OfflineRecOptions: String, CaseIterable {
    case start = "Start"
    case read = "Read"
}

struct OfflineRecordingView: View {
    @EnvironmentObject private var bleSdkManager: PolarBleSdkManager
    @State private var selectedTab: OfflineRecOptions = .start
    
    var body: some View {
        if case .connected = bleSdkManager.deviceConnectionState, bleSdkManager.offlineRecordingFeature.isSupported {
            VStack {
                Picker("Choose action", selection: $selectedTab) {
                    ForEach(OfflineRecOptions.allCases, id: \.self) {
                        Text($0.rawValue)
                    }
                }.pickerStyle(SegmentedPickerStyle())
                    .padding(.horizontal)
                
                ChosenOfflineRecordingView(offlineRecOptions: selectedTab)
                Spacer()
            }
        } else if case .connected = bleSdkManager.deviceConnectionState, !bleSdkManager.offlineRecordingFeature.isSupported {
            Text("Offline recording is not supported by this device")
        }
    }
}

struct ChosenOfflineRecordingView: View {
    @EnvironmentObject var bleSdkManager: PolarBleSdkManager
    let offlineRecOptions: OfflineRecOptions
    
    var body: some View {
        switch offlineRecOptions {
        case .start:
            OfflineRecordingStartView()
        case .read:
            OfflineRecordingListView()
        }
    }
}

struct OfflineRecordingView_Previews: PreviewProvider {
    private static let offlineRecordingEntries = OfflineRecordingEntries(
        isFetching: false,
        entries: [
            PolarOfflineRecordingEntry(path: "/test/url", size: 500, date:Date(), type: .gyro),
            PolarOfflineRecordingEntry(path: "/test/url", size: 500, date:Date(), type: .acc),
            PolarOfflineRecordingEntry(path: "/test/url", size: 500, date:Date(), type: .magnetometer)]
    )
    
    private static let offlineRecordingFeature = OfflineRecordingFeature(
        isSupported: true,
        availableOfflineDataTypes: [PolarDeviceDataType.hr: true, PolarDeviceDataType.acc: false, PolarDeviceDataType.ppi: true, PolarDeviceDataType.gyro: false, PolarDeviceDataType.magnetometer: true, PolarDeviceDataType.ecg: false],
        isRecording: [PolarDeviceDataType.hr: true, PolarDeviceDataType.acc: false, PolarDeviceDataType.ppi: true, PolarDeviceDataType.gyro: false, PolarDeviceDataType.magnetometer: true, PolarDeviceDataType.ecg: true]
    )
    
    private static let polarBleSdkManager: PolarBleSdkManager = {
        let polarBleSdkManager = PolarBleSdkManager()
        polarBleSdkManager.offlineRecordingFeature = offlineRecordingFeature
        polarBleSdkManager.offlineRecordingEntries = offlineRecordingEntries
        return polarBleSdkManager
    }()
    
    static var previews: some View {
        return OfflineRecordingView()
            .environmentObject(polarBleSdkManager)
    }
}

