/// Copyright © 2025 Polar Electro Oy. All rights reserved.

import Foundation
import SwiftUI
import PolarBleSdk
import RxSwift

struct HrBroadcastView: View {
    
    @EnvironmentObject private var bleSdkManager: PolarBleSdkManager
    @Environment(\.dismiss) private var dismiss
    
    @State private var deviceHrDataMap: [String: HrBroadcastEntry] = [:]
    @State private var stoppedDevices: Set<String> = []
    @State private var hrDisposable: Disposable?
    @State private var staleCheckTimer: Timer?
    
    private let buttonColor = Color(red: 0xd4/255.0, green: 0x10/255.0, blue: 0x29/255.0)
    
    struct HrBroadcastEntry: Identifiable {
        let id: String
        let deviceId: String
        let deviceName: String
        var hr: Int
        var isListening: Bool
        var lastUpdateTime: TimeInterval
        
        init(deviceId: String, deviceName: String, hr: Int, isListening: Bool = true) {
            self.id = deviceId
            self.deviceId = deviceId
            self.deviceName = deviceName
            self.hr = hr
            self.isListening = isListening
            self.lastUpdateTime = Date().timeIntervalSince1970
        }
    }
    
    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            VStack(spacing: 16) {
                Text("HR Broadcast Listener")
                    .font(.title2)
                    .fontWeight(.semibold)
                    .foregroundColor(.white)
                    .padding(.top, 16)
                
                Button(action: {
                    stopListening()
                    dismiss()
                }) {
                    Text("Close")
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(buttonColor)
                        .cornerRadius(8)
                }
                .padding(.horizontal, 16)
                
                if deviceHrDataMap.isEmpty {
                    VStack(spacing: 8) {
                        Text("Devices will appear here when broadcast is received...")
                            .foregroundColor(.white)
                    }
                    .padding(.top, 32)
                    Spacer()
                } else {
                    ScrollView {
                        LazyVStack(spacing: 8) {
                            ForEach(sortedDevices) { entry in
                                HrDeviceCardView(
                                    entry: entry,
                                    buttonColor: buttonColor,
                                    onToggle: { deviceId in
                                        toggleListening(for: deviceId)
                                    }
                                )
                            }
                        }
                        .padding(.horizontal, 16)
                    }
                }
            }
        }
        .onAppear {
            startListening()
            startStaleDeviceCheck()
        }
        .onDisappear {
            stopListening()
            staleCheckTimer?.invalidate()
        }
    }
    
    private var sortedDevices: [HrBroadcastEntry] {
        deviceHrDataMap.values.sorted { $0.deviceId < $1.deviceId }
    }
    
    private func startListening() {
        hrDisposable?.dispose()
        let filteredDevices = stoppedDevices.isEmpty ? nil : stoppedDevices
        
        hrDisposable = bleSdkManager.startListenForPolarHrBroadcasts(filteredDevices)
            .observe(on: MainScheduler.instance)
            .subscribe(
                onNext: { [self] hrData in
                    let deviceId = hrData.deviceInfo.deviceId
                    guard !stoppedDevices.contains(deviceId) else { return }
                    let entry = HrBroadcastEntry(
                        deviceId: deviceId,
                        deviceName: hrData.deviceInfo.name,
                        hr: Int(hrData.hr),
                        isListening: true
                    )
                    deviceHrDataMap[deviceId] = entry
                },
                onError: { error in
                    NSLog("HR broadcast error: \(error)")
                }
            )
    }
    
    private func stopListening() {
        hrDisposable?.dispose()
        hrDisposable = nil
    }
    
    private func startStaleDeviceCheck() {
        staleCheckTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
            let currentTime = Date().timeIntervalSince1970
            var devicesToRemove: [String] = []
            for (deviceId, entry) in deviceHrDataMap {
                if entry.isListening && (currentTime - entry.lastUpdateTime) > 10.0 {
                    devicesToRemove.append(deviceId)
                }
            }
            for deviceId in devicesToRemove {
                deviceHrDataMap.removeValue(forKey: deviceId)
            }
        }
    }
    
    private func toggleListening(for deviceId: String) {
        guard var entry = deviceHrDataMap[deviceId] else { return }
        if entry.isListening {
            stoppedDevices.insert(deviceId)
            entry.isListening = false
            entry.hr = -1
        } else {
            stoppedDevices.remove(deviceId)
            entry.isListening = true
            entry.lastUpdateTime = Date().timeIntervalSince1970
        }
        deviceHrDataMap[deviceId] = entry
        startListening()
    }
}

struct HrDeviceCardView: View {
    let entry: HrBroadcastView.HrBroadcastEntry
    let buttonColor: Color
    let onToggle: (String) -> Void
    
    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(entry.deviceName)
                    .font(.system(size: 16, weight: .medium))
                    .foregroundColor(.white)
                
                Text(entry.hr >= 0 ? "Hr: \(entry.hr) bpm" : "---")
                    .font(.system(size: 24, weight: .bold))
                    .foregroundColor(buttonColor)
            }
            Spacer()
            Button(action: {
                onToggle(entry.deviceId)
            }) {
                Text(entry.isListening ? "Stop Listening" : "Start Listening")
                    .font(.system(size: 14))
                    .foregroundColor(.white)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(buttonColor)
                    .cornerRadius(6)
            }
        }
        .padding(12)
        .background(Color(white: 0.2))
        .cornerRadius(8)
    }
}