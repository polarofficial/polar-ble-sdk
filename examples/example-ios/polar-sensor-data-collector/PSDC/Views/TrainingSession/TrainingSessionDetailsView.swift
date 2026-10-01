//  Copyright © 2025 Polar. All rights reserved.

import Foundation
import SwiftUI
import PolarBleSdk

struct TrainingSessionDetailsView: View {
    var trainingSessionEntry: PolarTrainingSessionReference
    @EnvironmentObject var bleSdkManager: PolarBleSdkManager
    @State var showingShareSheet: Bool = false
    
    private let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
        return formatter
    }()
    
    var body: some View {
        ZStack {
            switch bleSdkManager.trainingSessionData.loadState {
            case let .failed(error):
                VStack {
                    Image(systemName: "exclamationmark.triangle")
                        .imageScale(.large)
                        .foregroundColor(.red)
                    
                    Text("\(error)")
                        .foregroundColor(.red)
                }
                
            case .inProgress:
                if let progress = bleSdkManager.trainingSessionData.progress {
                    DataLoadProgressView(
                        progress: DataLoadProgress(
                            completedBytes: progress.completedBytes,
                            totalBytes: progress.totalBytes,
                            progressPercent: progress.progressPercent,
                            path: trainingSessionEntry.path
                        ),
                        dataType: "Training Session"
                    )
                } else {
                    ProgressView("Loading...")
                }
                
            case .success:
                ScrollView {
                    VStack(alignment: .leading) {
                        Group{
                            
                            Text("Path")
                                .font(.headline)
                            
                            HStack {
                                Text(trainingSessionEntry.path)
                            }
                            .foregroundColor(.secondary)
                            Divider()
                        }
                        
                        Group {
                            Text("Start time")
                                .font(.headline)
                            
                            HStack {
                                Text(dateFormatter.string(from: trainingSessionEntry.date))
                            }
                            .foregroundColor(.secondary)
                            
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
                                    TrainingView(text: bleSdkManager.trainingSessionData.data, dateTime: trainingSessionEntry.date)
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
        }.task {
            await bleSdkManager.getTrainingSession(trainingSessionReference: trainingSessionEntry)
        }
        .navigationTitle(trainingSessionEntry.path)
        .navigationBarTitleDisplayMode(.inline)
    }
}

struct TrainingView: UIViewControllerRepresentable {
    let text: String
    let dateTime: Date

    private let fileNameFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd_HH-mm-ss"
        return formatter
    }()

    private var isJSONContent: Bool {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let firstChar = trimmed.first, (firstChar == "{" || firstChar == "[") else { return false }
        return (try? JSONSerialization.jsonObject(with: Data(trimmed.utf8))) != nil
    }

    private func makeShareURL() -> URL? {
        let fileExtension = isJSONContent ? "json" : "txt"
        let fileName = "TrainingSession_\(fileNameFormatter.string(from: dateTime)).\(fileExtension)"
        let fileURL = FileManager.default.temporaryDirectory.appendingPathComponent(fileName)
        do {
            try text.write(to: fileURL, atomically: true, encoding: .utf8)
            return fileURL
        } catch {
            return nil
        }
    }

    func makeUIViewController(context: UIViewControllerRepresentableContext<TrainingView>) -> UIActivityViewController {
        let activityItems: [Any] = [makeShareURL() ?? text]
        return UIActivityViewController(activityItems: activityItems, applicationActivities: nil)
    }
    
    func updateUIViewController(_ uiViewController: UIActivityViewController, context: UIViewControllerRepresentableContext<TrainingView>) {}
}
