/// Copyright © 2024 Polar Electro Oy. All rights reserved.

import Foundation
import SwiftUI
import PolarBleSdk

struct DataDeletionSelections: View {
    
    @EnvironmentObject private var bleSdkManager: PolarBleSdkManager
    @Environment(\.dismiss) var dismiss
    
    @Binding var isPresented: Bool
    @Binding var showSettingsView: Bool
    
    @State private var selectedDate: Date = Date()
    @State private var showDatePickerView = false
    @State private var selectedDataTypeForDeletion: String = String(describing: PolarStoredDataType.StoredDataType.UNDEFINED.rawValue)
    
    var body: some View {
        Group {
            Group {
                VStack {
                    
                    HStack {
                        Text("Datatype:")
                        Picker("Select a data type for deletion", selection: $selectedDataTypeForDeletion) {
                            ForEach(PolarStoredDataType.getAllAsString(), id: \.self) {
                                Text($0)
                            }
                        }
                        .pickerStyle(.menu)
                    }
                    
                    HStack(alignment: .center)  {
                        DatePicker("Select an until date", selection: $selectedDate, in: ...Date.now, displayedComponents: .date)
                    }
                    
                    Button(action: {
                        isPresented = false
                        Task { @MainActor in
                            var calendar = Calendar.current
                            calendar.timeZone = TimeZone(abbreviation: "UTC")!
                            showDatePickerView = false
                            await bleSdkManager.deleteStoredUserData(untilDate: selectedDate, dataType: PolarStoredDataType.getValue(name: selectedDataTypeForDeletion))
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
}
