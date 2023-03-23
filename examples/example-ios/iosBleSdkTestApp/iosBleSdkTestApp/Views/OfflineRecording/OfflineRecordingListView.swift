/// Copyright © 2022 Polar Electro Oy. All rights reserved.

import Foundation
import SwiftUI
import PolarBleSdk

struct OfflineRecordingListView: View {
    @EnvironmentObject var bleSdkManager: PolarBleSdkManager
    
    var body: some View {
        NavigationView {
            List(bleSdkManager.offlineRecordingEntries.entries, id: \.path) { offlineRecording in
                NavigationLink {
                    OfflineRecordingDetailsView(offlineRecordingEntry: offlineRecording)
                } label: {
                    OfflineRecordingEntriesRow(offlineRecordingEntry: offlineRecording)
                }
            }
            .overlay {
                if bleSdkManager.offlineRecordingEntries.isFetching {
                    ProgressView("Fetching data, please wait...")
                        .progressViewStyle(CircularProgressViewStyle(tint: .accentColor))
                }
            }
            .animation(.default, value: bleSdkManager.offlineRecordingEntries.entries)
            .task {
                await bleSdkManager.listOfflineRecordings()
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .edgesIgnoringSafeArea(.all)
                .navigationBarTitle("")
                .navigationBarHidden(true)
            
        }.navigationViewStyle(StackNavigationViewStyle())
    }
}

struct OfflineRecordingListView_Previews: PreviewProvider {
    
    private static let offlineRecordingEntries = OfflineRecordingEntries(
        isFetching: false,
        entries: [
            PolarOfflineRecordingEntry(path: "/test/url", size: 500, date:Date(), type: .ppi),
            PolarOfflineRecordingEntry(path: "/test/url", size: 500, date:Date(), type: .acc),
            PolarOfflineRecordingEntry(path: "/test/url", size: 500, date:Date(), type: .magnetometer)]
    )
    
    private static let polarBleSdkManager: PolarBleSdkManager = {
        let polarBleSdkManager = PolarBleSdkManager()
        polarBleSdkManager.offlineRecordingEntries = offlineRecordingEntries
        return polarBleSdkManager
    }()
    
    static var previews: some View {
        ForEach(["iPhone 7 Plus", "iPad Pro (12.9-inch) (6th generation)"], id: \.self) { deviceName in
            OfflineRecordingListView()
                .previewDevice(PreviewDevice(rawValue: deviceName))
                .previewDisplayName(deviceName)
                .environmentObject(polarBleSdkManager)
        }
    }
}
