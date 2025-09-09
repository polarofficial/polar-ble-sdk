/// Copyright © 2024 Polar Electro Oy. All rights reserved.

import Foundation
import SwiftUI

extension DateComponents: Comparable {
    public static func < (lhs: DateComponents, rhs: DateComponents) -> Bool {
        let now = Date()
        let calendar = Calendar.current
        return calendar.date(byAdding: lhs, to: now)! < calendar.date(byAdding: rhs, to: now)!
    }
}

struct ActivityDatePickerView: View {
    
    @EnvironmentObject private var bleSdkManager: PolarBleSdkManager
    
    @Binding var isPresented: Bool
    @Binding var showActivityView: Bool
    
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
                                bleSdkManager.activityRecordingData.startDate = startDate
                                bleSdkManager.activityRecordingData.endDate = endDate
                                showActivityView = true
                                switch bleSdkManager.activityRecordingData.activityType {
                                case .SLEEP:
                                    await bleSdkManager.getSleep(start: startDate, end:  endDate)
                                case .STEPS:
                                    await bleSdkManager.getSteps(start: startDate, end:  endDate)
                                case .CALORIES:
                                    await bleSdkManager.getCalories(start: startDate, end: endDate, caloriesType: bleSdkManager.activityRecordingData.caloriesType)
                                case .HR_SAMPLES:
                                    await bleSdkManager.get247HrSamples(start: startDate, end: endDate)
                                case .NIGHTLY_RECHARGE:
                                    await bleSdkManager.getNightlyRecharge(start: startDate, end: endDate)
                                case .SKINTEMPERATURE:
                                    await bleSdkManager.getSkinTemperature(start: startDate, end: endDate)
                                case .PEAKTOPEAKINTERVAL:
                                    await bleSdkManager.get247PPiSamples(start: startDate, end: endDate)
                                case .ACTIVE_TIME:
                                    await bleSdkManager.getActiveTimeData(start: startDate, end: endDate)
                                case .ACTIVITY_SAMPLES:
                                    await bleSdkManager.getActivitySamplesData(start: startDate, end: endDate)
                                case .NONE:
                                    print("NOT IMPLEMENTED")
                                }
                            } else {
                                bleSdkManager.activityRecordingData.loadingState =  ActivityRecordingDataLoadingState.failed(error: "No date selected!")
                                showActivityView = true
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

extension Array {
    var middleIndex: Int {
        return (self.isEmpty ? self.first as! Int : self.count - 1) / 2
    }
}

