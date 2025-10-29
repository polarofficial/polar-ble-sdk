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
    
    private let fileNameFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd_HH-mm-ss"
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
                if let progress = bleSdkManager.offlineRecordingData.progress {
                    DataLoadProgressView(
                        progress: DataLoadProgress(
                            completedBytes: progress.bytesDownloaded,
                            totalBytes: progress.totalBytes,
                            progressPercent: progress.progressPercent,
                            path: offlineRecordingEntry.path
                        ),
                        dataType: "Offline Recording"
                    )
                } else {
                    ProgressView("Loading...")
                }
                
            case .success:
                let sizeString = sizeInKbString(size: offlineRecordingEntry.size)
                let speedString = downLoadSpeedInKbString(speed: bleSdkManager.offlineRecordingData.downloadSpeed)
                let pathString = offlineRecordingEntry.path
                let dateString = fileNameFormatter.string(from: offlineRecordingEntry.date)
                let typeName = getShortNameForDataType(offlineRecordingEntry.type)
                let shareFileName = "Offline_recording_\(typeName)_\(dateString).txt"

                ScrollView {
                    VStack(alignment: .leading) {
                        Group {
                            Text("Size")
                                .font(.headline)
                            HStack {
                                Text("\(sizeString) kB. (Download rate: \(speedString) kB/s)")
                            }
                            .foregroundColor(.secondary)

                            Divider()
                            Text("Path")
                                .font(.headline)
                            
                            HStack {
                                Text(pathString)
                            }
                            .foregroundColor(.secondary)
                            Divider()
                        }
                        
                        Group {
                            Text("Start time")
                                .font(.headline)
                            
                            HStack {
                                Text(dateString)
                            }
                            .foregroundColor(.secondary)
                            
                            Divider()
                            
                            if let settings = bleSdkManager.offlineRecordingData.usedSettings {
                                Text("Recording Settings")
                                    .font(.headline)
                                VStack(alignment: .leading) {
                                    HStack {
                                        Text("Sample rate: ")
                                        Text("\(settings.settings[.sampleRate]?.first ?? 0)Hz")
                                    }
                                    HStack {
                                        Text("Resolution: ")
                                        Text("\(settings.settings[.resolution]?.first ?? 0)bits")
                                    }
                                    HStack {
                                        Text("Range: ")
                                        Text("\(settings.settings[.range]?.first ?? 0)")
                                    }
                                    HStack {
                                        Text("Channels: ")
                                        Text("\(settings.settings[.channels]?.first ?? 0)")
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
                                }
                                .sheet(isPresented: $showingShareSheet) {
                                    ActivityView(
                                        text: bleSdkManager.offlineRecordingData.data,
                                        filename: shareFileName
                                    )
                                }

                                Spacer()
                            }
                        }
                    }
                    .padding()
                }
                
            case .notStarted:
                EmptyView()
            }
        }
        .task {
            await bleSdkManager.getOfflineRecording(offlineRecordingEntry: offlineRecordingEntry)
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
    let filename: String

    func makeUIViewController(context: Context) -> UIActivityViewController {
        let tempDir = FileManager.default.temporaryDirectory
        let fileURL = tempDir.appendingPathComponent(filename)

        do {
            try text.write(to: fileURL, atomically: true, encoding: .utf8)
        } catch {
            print("Failed to write to file: \(error)")
        }

        let activityVC = UIActivityViewController(activityItems: [fileURL], applicationActivities: nil)
        activityVC.excludedActivityTypes = [.assignToContact, .saveToCameraRoll]
        return activityVC
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}

struct OfflineRecordingDetailsView_Previews: PreviewProvider {
    private static let offlineRecordingData = OfflineRecordingData(
        loadState: .success,
        startTime: Date(),
        usedSettings: nil,
        downLoadTime: 0.06,
        dataSize: 11234,
        data: "test data",
        progress: nil as PolarOfflineRecordingProgress?
    )
    
    private static let polarBleSdkManager: PolarBleSdkManager = {
        let polarBleSdkManager = PolarBleSdkManager()
        polarBleSdkManager.offlineRecordingData = offlineRecordingData
        return polarBleSdkManager
    }()
    
    private static let polarBleSdkManagerInProgress: PolarBleSdkManager = {
        let polarBleSdkManager = PolarBleSdkManager()
        polarBleSdkManager.offlineRecordingData = OfflineRecordingData(
            loadState: .inProgress,
            data: "",
            progress: PolarOfflineRecordingProgress(
                bytesDownloaded: 5000,
                totalBytes: 10000,
                progressPercent: 50
            )
        )
        return polarBleSdkManager
    }()
    
    private static let polarBleSdkManagerFailed: PolarBleSdkManager = {
        let polarBleSdkManager = PolarBleSdkManager()
        polarBleSdkManager.offlineRecordingData = OfflineRecordingData(
            loadState: .failed(error: "Error while loading"),
            data: "",
            progress: nil as PolarOfflineRecordingProgress?
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
