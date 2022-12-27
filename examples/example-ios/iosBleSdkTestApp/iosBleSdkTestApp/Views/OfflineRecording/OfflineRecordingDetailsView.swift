/// Copyright © 2022 Polar Electro Oy. All rights reserved.

import Foundation
import SwiftUI
import PolarBleSdk

struct OfflineRecordingDetailsView: View {
    var offlineRecordingEntry: PolarOfflineRecordingEntry
    @EnvironmentObject var bleSdkManager: PolarBleSdkManager
    
    private let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
        return formatter
    }()
    
    var body: some View {
        
        ScrollView {
            VStack(alignment: .leading) {
                Group{
                    Text("Size")
                        .font(.headline)
                    HStack {
                        Text("\(String(sizeInKbString(size: offlineRecordingEntry.size))) kB. (Download rate: \(downLoadSpeedInKbString(speed: bleSdkManager.offlineRecordingData.downloadSpeed)) kB/s)")
                        
                    }.foregroundColor(.secondary)
                    
                    Divider()
                    
                    Text("Path")
                        .font(.headline)
                    
                    HStack {
                        Text(offlineRecordingEntry.path)
                    }
                    .foregroundColor(.secondary)
                    Divider()
                }
                
                Group {
                    Text("Start time")
                        .font(.headline)
                    
                    HStack {
                        Text(dateFormatter.string(from: offlineRecordingEntry.date))
                    }
                    .foregroundColor(.secondary)
                    
                    Divider()
                    
                    if let settings = bleSdkManager.offlineRecordingData.usedSettings {
                        Text("Recording Settings")
                            .font(.headline)
                        VStack(alignment: .leading) {
                            HStack {
                                Text("Sample rate: ")
                                Text("\(settings.settings[PolarSensorSetting.SettingType.sampleRate]?.first ?? 0)Hz")
                            }
                            HStack {
                                Text("Resolution: ")
                                Text("\(settings.settings[PolarSensorSetting.SettingType.resolution]?.first ?? 0)bits")
                            }
                            HStack {
                                Text("Range: ")
                                Text("\(settings.settings[PolarSensorSetting.SettingType.range]?.first ?? 0) ")
                            }
                            HStack {
                                Text("Channels: ")
                                Text("\(settings.settings[PolarSensorSetting.SettingType.channels]?.first ?? 0) ")
                            }
                        }.foregroundColor(.secondary)
                        Divider()
                    }
                    
                    HStack {
                        Spacer()
                        Button(action: {
                            
                            guard let firstScene = UIApplication.shared.connectedScenes.first as? UIWindowScene else {
                                return
                            }
                            
                            guard let firstWindow = firstScene.windows.first else {
                                return
                            }
                            
                            let viewController = firstWindow.rootViewController
                            
                            let activityViewController = UIActivityViewController(activityItems: [ bleSdkManager.offlineRecordingData.data], applicationActivities: nil)
                            
                            viewController?.present(activityViewController, animated: true, completion: nil)
                        }
                        ) {
                            Image(systemName: "square.and.arrow.up")
                                .foregroundColor(.blue)
                                .imageScale(.large)
                            Text("Share")
                        }
                        Spacer()
                    }
                }
            }
            .padding()
        }.overlay {
            if bleSdkManager.offlineRecordingData.isFetching {
                Color.white.opacity(1.0).edgesIgnoringSafeArea(.all)
                ProgressView("Fetching \(getLongNameForDataType(offlineRecordingEntry.type)) data...")
                    .progressViewStyle(CircularProgressViewStyle(tint: .accentColor))
            }
        }
        
        .task {
            await bleSdkManager.getOfflineRecording(offlineRecordingEntry: offlineRecordingEntry )
        }
        .navigationTitle(getLongNameForDataType(offlineRecordingEntry.type))
        .navigationBarTitleDisplayMode(.inline)
        
    }
    
    private func sizeInKbString(size: UInt) -> String {
        let decimalPrecision = 2
        return String(format: "%.\(decimalPrecision)f", (Double(size) / 1000.0))
    }
    
    private func downLoadSpeedInKbString(speed: Double) -> String {
        let decimalPrecision = 2
        return String(format: "%.\(decimalPrecision)f", (speed / 1000.0))
    }
}

struct OfflineRecordingDetailsView_Previews: PreviewProvider {
    private static let offlineRecordingData = OfflineRecordingData(
        isFetching: false,
        startTime: Date(),
        usedSettings: nil,
        downLoadTime: 0.06,
        dataSize: 11234,
        data: "test data"
    )
    
    private static let polarBleSdkManager: PolarBleSdkManager = {
        let polarBleSdkManager = PolarBleSdkManager()
        polarBleSdkManager.offlineRecordingData = offlineRecordingData
        return polarBleSdkManager
    }()
    
    static var previews: some View {
        OfflineRecordingDetailsView(offlineRecordingEntry: PolarOfflineRecordingEntry(path: "url/1/example", size: 400, date:Date(), type: .acc))
            .environmentObject(polarBleSdkManager)
    }
}
