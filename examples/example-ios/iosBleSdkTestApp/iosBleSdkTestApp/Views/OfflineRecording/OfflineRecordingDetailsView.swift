/// Copyright © 2022 Polar Electro Oy. All rights reserved.

import Foundation
import SwiftUI
import PolarBleSdk

struct OfflineRecordingDetailsView: View {
    var offlineRecordingEntry: PolarOfflineRecordingEntry
    @EnvironmentObject var bleSdkManager: PolarBleSdkManager
    @State var showingShareSheet: Bool = false
    
    private let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
        return formatter
    }()
    
    var body: some View {
        ZStack {
            switch bleSdkManager.offlineRecordingData.loadState {
            case let .failed(error):
                VStack {
                    Image(systemName: "exclamationmark.triangle")
                        .imageScale(.large)
                        .foregroundColor(.red)
                    
                    Text("\(error)")
                        .foregroundColor(.red)
                }
                
            case .inProgress:
                Color.white.opacity(1.0).edgesIgnoringSafeArea(.all)
                ProgressView("Fetching \(getLongNameForDataType(offlineRecordingEntry.type)) data...")
                    .progressViewStyle(CircularProgressViewStyle(tint: .accentColor))
            case .success:
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
                                    self.showingShareSheet = true
                                }) {
                                    Image(systemName: "square.and.arrow.up")
                                        .foregroundColor(.blue)
                                        .imageScale(.large)
                                    Text("Share")
                                }.sheet(isPresented: $showingShareSheet) {
                                    ActivityView(text: bleSdkManager.offlineRecordingData.data)
                                }
                                
                                Spacer()
                            }
                        }
                    }
                    .padding()
                }
                
            }
        }.task {
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

struct ActivityView: UIViewControllerRepresentable {
    let text: String
    
    func makeUIViewController(context: UIViewControllerRepresentableContext<ActivityView>) -> UIActivityViewController {
        return UIActivityViewController(activityItems: [text], applicationActivities: nil)
    }
    
    func updateUIViewController(_ uiViewController: UIActivityViewController, context: UIViewControllerRepresentableContext<ActivityView>) {}
}

struct OfflineRecordingDetailsView_Previews: PreviewProvider {
    private static let offlineRecordingData = OfflineRecordingData(
        loadState: OfflineRecordingDataLoadingState.success,
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
    
    private static let polarBleSdkManagerInProgress: PolarBleSdkManager = {
        let polarBleSdkManager = PolarBleSdkManager()
        polarBleSdkManager.offlineRecordingData = OfflineRecordingData(
            loadState: OfflineRecordingDataLoadingState.inProgress
        )
        return polarBleSdkManager
    }()
    
    private static let polarBleSdkManagerFailed: PolarBleSdkManager = {
        let polarBleSdkManager = PolarBleSdkManager()
        polarBleSdkManager.offlineRecordingData = OfflineRecordingData(
            loadState: OfflineRecordingDataLoadingState.failed(error: "Error while loading")
        )
        return polarBleSdkManager
    }()
    
    static var previews: some View {
        
        Group {
            OfflineRecordingDetailsView(offlineRecordingEntry: PolarOfflineRecordingEntry(path: "url/1/example", size: 400, date:Date(), type: .acc))
                .environmentObject(polarBleSdkManager)
            
            OfflineRecordingDetailsView(offlineRecordingEntry: PolarOfflineRecordingEntry(path: "url/1/example", size: 400, date:Date(), type: .acc))
                .environmentObject(polarBleSdkManagerInProgress)
                .previewDisplayName("InProgress")
            
            OfflineRecordingDetailsView(offlineRecordingEntry: PolarOfflineRecordingEntry(path: "url/1/example", size: 400, date:Date(), type: .acc))
                .environmentObject(polarBleSdkManagerFailed)
                .previewDisplayName("Failed")
        }
    }
}
