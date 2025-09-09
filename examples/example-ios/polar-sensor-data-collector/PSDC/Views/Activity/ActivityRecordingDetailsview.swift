///  Copyright © 2024 Polar. All rights reserved.

import Foundation
import SwiftUI
import PolarBleSdk

struct ActivityRecordingDetailsView: View {
    @EnvironmentObject var bleSdkManager: PolarBleSdkManager
    @State var showingShareSheet: Bool = false
    @Binding var isPresented: Bool
    
    private let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()
    
    var body: some View {
        VStack {
#if targetEnvironment(macCatalyst)
            Spacer()
#endif
            ZStack {
                switch bleSdkManager.activityRecordingData.loadingState {
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
                    ProgressView("Fetching \(String(describing: bleSdkManager.activityRecordingData.activityType)) data...")
                        .progressViewStyle(CircularProgressViewStyle(tint: .accentColor))
                case .success:
                    ScrollView {
                        VStack(alignment: .leading) {
                            Group{
                                Text("ActivityType")
                                    .font(.headline)
                                HStack {
                                    Text("\(String(describing: bleSdkManager.activityRecordingData.activityType))")
                                    
                                }.foregroundColor(.secondary)
                            }
                            
                            Group {
                                Text("Start date")
                                    .font(.headline)
                                HStack {
                                    Text(dateFormatter.string(from: bleSdkManager.activityRecordingData.startDate))
                                }.foregroundColor(.secondary)
                                
                                Text("End date")
                                    .font(.headline)
                                HStack {
                                    Text(dateFormatter.string(from: bleSdkManager.activityRecordingData.endDate))
                                }
                                .foregroundColor(.secondary)
                                
                                Divider()
                                
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
                                        let dateString = dateFormatter.string(from: bleSdkManager.activityRecordingData.startDate)
                                            .elementsEqual(dateFormatter.string(from: bleSdkManager.activityRecordingData.endDate)) ? dateFormatter.string(from: bleSdkManager.activityRecordingData.startDate) : "\(dateFormatter.string(from: bleSdkManager.activityRecordingData.startDate))-\(dateFormatter.string(from: bleSdkManager.activityRecordingData.endDate))"

                                        ActivityView(text: bleSdkManager.activityRecordingData.data, filename: "\(bleSdkManager.activityRecordingData.activityType)_\(dateString).json")
                                    }
                                    
                                    Spacer()
                                }
                            }
                        }
                        .padding()
                    }
                }
            }
#if targetEnvironment(macCatalyst)
            Spacer()
            Button("Close", action: {
                isPresented = false
            })
            .padding(.bottom)
            .padding(.top)
#endif
        }
    }
}

struct ActivityRecordingShareView: UIViewControllerRepresentable {
    let text: String
    
    func makeUIViewController(context: UIViewControllerRepresentableContext<ActivityRecordingShareView>) -> UIActivityViewController {
        return UIActivityViewController(activityItems: [text], applicationActivities: nil)
    }
    
    func updateUIViewController(_ uiViewController: UIActivityViewController, context: UIViewControllerRepresentableContext<ActivityRecordingShareView>) {}
}

struct ActivityRecordingDetailsView_Previews: PreviewProvider {
    
    private static let activityRecordingData = ActivityRecordingData(
        loadingState: ActivityRecordingDataLoadingState.success,
        startDate: Date.now,
        endDate: Date.now,
        activityType: PolarActivityDataType.NONE,
        data: "test data"
    )
    
    private static let polarBleSdkManager: PolarBleSdkManager = {
        let polarBleSdkManager = PolarBleSdkManager()
        polarBleSdkManager.activityRecordingData = activityRecordingData
        return polarBleSdkManager
    }()
    
    private static let polarBleSdkManagerInProgress: PolarBleSdkManager = {
        let polarBleSdkManager = PolarBleSdkManager()
        polarBleSdkManager.activityRecordingData = 
        ActivityRecordingData(loadingState: ActivityRecordingDataLoadingState.inProgress)
        return polarBleSdkManager
    }()
    
    private static let polarBleSdkManagerFailed: PolarBleSdkManager = {
        let polarBleSdkManager = PolarBleSdkManager()
        polarBleSdkManager.activityRecordingData
        = ActivityRecordingData(loadingState: ActivityRecordingDataLoadingState.failed(error: "Failed"))
        return polarBleSdkManager
    }()
    
    private static let activityRecordingDetails = ActivityRecordingData(
        startDate: Date(),
        endDate: Date(),
        activityType: PolarActivityDataType.NONE,
        data: "test data"
    )
    
    static var previews: some View {
        
        Group {
            
            Group {
                ActivityRecordingDetailsView(isPresented: .constant(true))
                    .environmentObject(polarBleSdkManager)
                
                ActivityRecordingDetailsView(isPresented: .constant(true))
                    .environmentObject(polarBleSdkManagerInProgress)
                    .previewDisplayName("InProgress")
                
                ActivityRecordingDetailsView(isPresented: .constant(true))
                    .environmentObject(polarBleSdkManagerFailed)
                    .previewDisplayName("Failed")
            }
        }
    }
}

