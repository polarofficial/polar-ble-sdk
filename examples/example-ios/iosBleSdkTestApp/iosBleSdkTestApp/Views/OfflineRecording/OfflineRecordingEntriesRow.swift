
import Foundation
import SwiftUI
import PolarBleSdk

struct OfflineRecordingEntriesRow: View {
    @EnvironmentObject private var bleSdkManager: PolarBleSdkManager
    var offlineRecordingEntry: PolarOfflineRecordingEntry
    @State private var isPerformingDelete = false
    
    var body: some View {
        HStack {
            
            Image(systemName: "paperclip.circle")
                .resizable()
                .frame(width: 30, height: 30)
            
            VStack {
                HStack {
                    Text(getShortNameForDataType(offlineRecordingEntry.type))
                        .font(.title3)
                        .foregroundColor(getDataTypeColor(type: offlineRecordingEntry.type))
                    Spacer()
                }
                HStack {
                    Text( offlineRecordingEntry.date.formatted())
                        .font(.caption)
                    Spacer()
                }
                HStack {
                    let sizeString = "Size: \(sizeInKbString(size: offlineRecordingEntry.size))kB"
                    Text(sizeString)
                        .font(.caption)
                    Spacer()
                    
                }
                HStack {
                    Button("Delete", role: .destructive,
                           action: {
                        isPerformingDelete = true
                        Task {
                            await bleSdkManager.removeOfflineRecording(offlineRecordingEntry:offlineRecordingEntry)
                            isPerformingDelete = false
                        }
                    }
                    ).buttonStyle(.bordered)
                        .disabled(isPerformingDelete)
                        .overlay {
                            if isPerformingDelete {
                                ProgressView()
                            }
                        }
                    Spacer()
                }
            }
        }
    }
    
    private func sizeInKbString(size: UInt) -> String {
        let decimalPrecision = 2
        return String(format: "%.\(decimalPrecision)f", (Double(size) / 1000.0))
    }
    
    private func getDataTypeColor(type: PolarDeviceDataType) -> Color {
        switch(type) {
        case .ecg:
            return .blue
        case .acc:
            return .red
        case .ppg:
            return .green
        case .ppi:
            return .mint
        case .gyro:
            return .yellow
        case .magnetometer:
            return .orange
        case .hr:
            return .pink
        }
    }
}

struct OfflineRecordingEntriesRow_Previews: PreviewProvider {
    static var previews: some View {
        Group {
            VStack {
                OfflineRecordingEntriesRow(offlineRecordingEntry: PolarOfflineRecordingEntry(path: "url/1/example", size: 400, date: Date(), type: .acc))
                
                OfflineRecordingEntriesRow(offlineRecordingEntry: PolarOfflineRecordingEntry(path: "url/1/example", size: 400, date: Date(), type: .ppg))
                
                OfflineRecordingEntriesRow(offlineRecordingEntry: PolarOfflineRecordingEntry(path: "url/1/example", size: 400, date: Date(), type: .ppi))
                OfflineRecordingEntriesRow(offlineRecordingEntry: PolarOfflineRecordingEntry(path: "url/1/example", size: 400, date: Date(), type: .magnetometer))
                OfflineRecordingEntriesRow(offlineRecordingEntry: PolarOfflineRecordingEntry(path: "url/1/example", size: 10000, date: Date(), type: .hr))
                
            }
            
        }
        .previewLayout(.fixed(width: 300, height: 70))
    }
}
