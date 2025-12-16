/// Copyright © 2025 Polar Electro Oy. All rights reserved.

import Foundation
import SwiftUI

struct TrainingSessionDatePickerView: View {
    
    @EnvironmentObject private var bleSdkManager: PolarBleSdkManager
    
    @Binding var isPresented: Bool
    @Binding var showTrainingSessionView: Bool
    
    @State private var dates: Set<DateComponents> = []
    @State private var startDate: DateComponents? = nil
    @State private var endDate:DateComponents? = nil

    var selectedDates: Binding<Set<DateComponents>> {
        Binding {
            return dates
        } set: { selectedDates in

            startDate = selectedDates.min()
            
            if (selectedDates.count > 1) {
                endDate = selectedDates.max()
            } else {
                endDate = startDate
            }
            
            if (startDate != nil && endDate != nil) {
                fillDatesBetween(for: startDate!, for: endDate!)
            }
        }
    }

    @State private var formattedDates: String = ""
    let formatter = DateFormatter()
    
    var body: some View {
        Group {
            Group {
                VStack {
                    
                    MultiDatePicker("", selection: selectedDates)
                        .frame(height: 300)
                    
                    Button(action: {
                        isPresented = false
                        Task { @MainActor in
                            if (!dates.isEmpty) {
                                var calendar = Calendar.current
                                calendar.timeZone = TimeZone(abbreviation: "UTC")!
                                let startDate = calendar.date(from: dates.min()!)!
                                let endDate = calendar.date(from: dates.max()!)!
                                bleSdkManager.trainingSessionData.startTime = startDate
                                bleSdkManager.trainingSessionData.endTime = endDate
                                showTrainingSessionView = true
                                isPresented = false
                                await bleSdkManager.listTrainingSessions(start: startDate, end: endDate)
                            } else {
                                bleSdkManager.trainingSessionData.loadState =  TrainingSessionDataLoadingState.failed(error: "No date selected!")
                                showTrainingSessionView = false
                            }
                        }
                    }, label: {
                        Text("Done").font(.title3)
                    }
                    ).padding(.vertical, 15)
                     .disabled(startDate == nil || endDate == nil)
                    
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
    
    private func fillDatesBetween(for startDate: DateComponents, for endDate: DateComponents) {
        let calendar = Calendar.current
        var currentDate = startDate
        dates = []
        dates.insert(startDate)
        while(currentDate < endDate) {
            let date: Date = calendar.date(from: currentDate)!
            if let dateOfDay = calendar.date(byAdding: .day, value: 1, to: date) {
                currentDate = calendar.dateComponents([.year, .month, .day], from: dateOfDay)
                let components = calendar.dateComponents([.year, .month, .day], from: dateOfDay)
                dates.insert(components)
            }
        }
    }
}
