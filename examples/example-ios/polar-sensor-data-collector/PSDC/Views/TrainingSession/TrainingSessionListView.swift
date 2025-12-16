//  Copyright © 2025 Polar. All rights reserved.

import Foundation
import SwiftUI
import PolarBleSdk

struct TrainingSessionListView: View {
    @EnvironmentObject var bleSdkManager: PolarBleSdkManager
    
    var body: some View {
        NavigationView {

            if (!bleSdkManager.trainingSessionEntries.isFetching && !bleSdkManager.trainingSessionEntries.entries.isEmpty) {
                List(bleSdkManager.trainingSessionEntries.entries, id: \.path) { trainingSession in
                    NavigationLink {
                        TrainingSessionDetailsView(trainingSessionEntry: trainingSession)
                    } label: {
                        TrainingSessionEntriesRow(trainingSessionEntry: trainingSession)
                    }
                }
                .animation(.default, value: bleSdkManager.trainingSessionEntries.entries)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .edgesIgnoringSafeArea(.all)
                .navigationBarTitle("")
                .navigationBarHidden(true)
            } else if (!bleSdkManager.trainingSessionEntries.isFetching && bleSdkManager.trainingSessionEntries.entries.isEmpty) {
                Label("No training sessions found", systemImage: "nosign")
            }
        }.navigationViewStyle(StackNavigationViewStyle())
        .overlay {
            if bleSdkManager.trainingSessionEntries.isFetching {
                ProgressView("Fetching data, please wait...")
                    .progressViewStyle(CircularProgressViewStyle(tint: .accentColor))
            }
        }
        .onDisappear() {
            bleSdkManager.trainingSessionEntries.isFetching = true
            bleSdkManager.trainingSessionEntries.entries.removeAll()
        }
    }
}
