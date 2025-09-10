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
                Color.white.opacity(1.0).edgesIgnoringSafeArea(.all)
                ProgressView("Fetching \(trainingSessionEntry.path)")
                    .progressViewStyle(CircularProgressViewStyle(tint: .accentColor))
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
                                    TrainingView(text: bleSdkManager.trainingSessionData.data)
                                }
                                
                                Spacer()
                            }
                        }
                    }
                    .padding()
                }

                
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
    
    func makeUIViewController(context: UIViewControllerRepresentableContext<TrainingView>) -> UIActivityViewController {
        return UIActivityViewController(activityItems: [text], applicationActivities: nil)
    }
    
    func updateUIViewController(_ uiViewController: UIActivityViewController, context: UIViewControllerRepresentableContext<TrainingView>) {}
}