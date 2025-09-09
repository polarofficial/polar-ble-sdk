import Foundation
import SwiftUI
import PolarBleSdk

struct DateFolderDeletionView: View {
    
    @EnvironmentObject private var bleSdkManager: PolarBleSdkManager
    @Environment(\.dismiss) var dismiss
    
    @Binding var isPresented: Bool
    @Binding var showSettingsView: Bool
    
    @State private var fromDate: Date = Date()
    @State private var toDate: Date = Date()
    
    var body: some View {
        Group {
            VStack {
                
                HStack(alignment: .center) {
                    Text("From Date:")
                    DatePicker("Select a from date", selection: $fromDate, in: ...Date.now, displayedComponents: .date)
                }
                
                HStack(alignment: .center) {
                    Text("To Date:")
                    DatePicker("Select a to date", selection: $toDate, in: fromDate..., displayedComponents: .date)
                }
                
                Button(action: {
                    isPresented = false
                    Task { @MainActor in
                        var calendar = Calendar.current
                        calendar.timeZone = TimeZone(abbreviation: "UTC")!
                        showSettingsView = false
                        await bleSdkManager.deleteDeviceDataFolders(fromDate: fromDate, toDate: toDate)
                    }
                }, label: {
                    Text("Delete").font(.title3)
                }).padding(.vertical, 15)
                
                Button(action: {
                    isPresented = false
                }, label: {
                    Text("Cancel")
                        .font(.title3)
                        .foregroundStyle(.red)
                }).padding(.vertical, 10)
            }
        }
    }
}
