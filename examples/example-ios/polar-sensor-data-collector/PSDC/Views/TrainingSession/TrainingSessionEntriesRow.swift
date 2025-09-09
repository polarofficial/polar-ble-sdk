//  Copyright © 2025 Polar. All rights reserved.

import Foundation
import SwiftUI
import PolarBleSdk

struct TrainingSessionEntriesRow: View {
    @EnvironmentObject private var bleSdkManager: PolarBleSdkManager
    var trainingSessionEntry: PolarTrainingSessionReference
    
    var body: some View {
        HStack {
            Image(systemName: "paperclip.circle")
                .resizable()
                .frame(width: 30, height: 30)
            
            VStack {
                HStack {
                    Text("Training session")
                        .font(.title3)
                        .foregroundColor(.red)
                    Spacer()
                }
                HStack {
                    Text( trainingSessionEntry.date.formatted())
                        .font(.caption)
                    Spacer()
                }
                HStack {
                    Text("Path: \(trainingSessionEntry.path)")
                        .font(.caption)
                    Spacer()
                }
            }
        }
    }
}
