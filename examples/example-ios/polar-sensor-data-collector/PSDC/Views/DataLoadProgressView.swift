//  Copyright © 2025 Polar. All rights reserved.

import SwiftUI

struct DataLoadProgress {
    let completedBytes: Int64
    let totalBytes: Int64
    let progressPercent: Int
    let path: String?
}

struct DataLoadProgressView: View {
    let progress: DataLoadProgress?
    let dataType: String
    
    @Environment(\.colorScheme) var colorScheme
    
    private let progressColor = Color.red
    
    var body: some View {
        ZStack {
            Color(uiColor: .systemBackground)
                .opacity(0.95)
                .edgesIgnoringSafeArea(.all)
            
            VStack(spacing: 16) {
                if let progress = progress {
                    Text("Loading \(dataType)...")
                        .font(.subheadline)
                        .fontWeight(.medium)
                        .foregroundColor(.primary)
                        .multilineTextAlignment(.center)
                    
                    Spacer().frame(height: 8)
                    
                    if let path = progress.path {
                        Text(path)
                            .font(.subheadline)
                            .fontWeight(.medium)
                            .foregroundColor(.primary)
                            .multilineTextAlignment(.center)
                    } else {
                        Text(formatBytes(progress.totalBytes))
                            .font(.title2)
                            .fontWeight(.medium)
                            .foregroundColor(.primary)
                    }
                    
                    Spacer().frame(height: 16)
                    
                    Text("\(progress.progressPercent)%")
                        .font(.title)
                        .fontWeight(.bold)
                        .foregroundColor(progressColor)
                    
                    Spacer().frame(height: 8)
                    
                    ProgressView(value: Double(progress.progressPercent), total: 100.0)
                        .progressViewStyle(LinearProgressViewStyle(tint: progressColor))
                        .frame(height: 10)
                        .background(Color.primary.opacity(0.12))
                        .cornerRadius(5)
                        .padding(.horizontal, 24)
                    
                    Spacer().frame(height: 8)
                    
                    Text("\(formatBytes(progress.completedBytes)) / \(formatBytes(progress.totalBytes))")
                        .font(.body)
                        .foregroundColor(.primary)
                    
                } else {
                    Text("Preparing \(dataType)...")
                        .font(.body)
                        .fontWeight(.medium)
                        .foregroundColor(.primary)
                }
            }
            .padding(.horizontal, 24)
        }
    }

    private func formatBytes(_ bytes: Int64) -> String {
        if bytes == 0 {
            return "0 B"
        } else if bytes < 1024 {
            return "\(bytes) B"
        } else if bytes < 1024 * 1024 {
            return String(format: "%.1f KB", Double(bytes) / 1024.0)
        } else if bytes < 1024 * 1024 * 1024 {
            return String(format: "%.2f MB", Double(bytes) / (1024.0 * 1024.0))
        } else {
            return String(format: "%.2f GB", Double(bytes) / (1024.0 * 1024.0 * 1024.0))
        }
    }
}