/// Copyright © 2024 Polar Electro Oy. All rights reserved.

import Foundation
import SwiftUI
import PolarBleSdk

struct ActivityRecordingView: View {
    
    @EnvironmentObject private var bleSdkManager: PolarBleSdkManager
    
    @State private var sleepTextTitle = "Get sleep data"
    @State private var stepsTextTitle = "Get steps data"
    @State private var hrSamplesTextTitle = "Get 24/7 HR samples data"
    @State private var nightlyRechargeTitle = "Get nightly recharge data"
    @State private var skinTemperatureTitle = "Get skin temperature data"
    @State private var caloriesTitle = "Get calories data"
    @State private var ppiSamplesTextTitle = "Get 24/7 PPi samples data"
    @State private var trainingSessionsTitle = "Get training sessions"
    @State private var showDatePickerView = false
    @State private var showActivityView = false
    @State private var showTrainingSessionList = false
    @State private var selectedCaloriesType: CaloriesType = .activity
    @State private var activeTimeTitle = "Get active time data"
    @State private var activitySamplesTitle = "Get activity samples data"
    
    var body: some View {
        if case .connected = bleSdkManager.deviceConnectionState,
           bleSdkManager.deviceInfoFeature.isSupported {
            HStack {
                VStack {
                    VStack {
                        Text(sleepRecordingStateText)
                        Button("Stop sleep recording",
                               action: {
                            Task {
                                bleSdkManager.sleepRecordingStop()
                            }
                        }).buttonStyle(SecondaryButtonStyle(buttonState: ButtonState.released))
                            .opacity(bleSdkManager.sleepRecordingFeature.sleepRecordingEnabled ? 1.0 : 0.5)
                            .disabled(bleSdkManager.sleepRecordingFeature.sleepRecordingEnabledAvailable == false || bleSdkManager.sleepRecordingFeature.sleepRecordingEnabled == false)
                        Divider()
                    }
                    .task {
                       bleSdkManager.observeSleepRecordingState()
                    }
                    
                    Button(sleepTextTitle) {
                        showDatePickerView = true
                        showActivityView = false
                        bleSdkManager.activityRecordingData.loadingState = ActivityRecordingDataLoadingState.inProgress
                        bleSdkManager.activityRecordingData.activityType = PolarActivityDataType.SLEEP
                    }.alignmentGuide(.leading) { d in d[.leading] }
                    .sheet(isPresented: $showDatePickerView) {
                        ActivityDatePickerView(isPresented: $showDatePickerView, showActivityView: $showActivityView)
                    }
                    .sheet(isPresented: $showActivityView) {
                        ActivityRecordingDetailsView(isPresented: $showActivityView)
                            .environmentObject(bleSdkManager)
                    }

                    Button(stepsTextTitle) {
                        showDatePickerView = true
                        showActivityView = false
                        bleSdkManager.activityRecordingData.loadingState = ActivityRecordingDataLoadingState.inProgress
                        bleSdkManager.activityRecordingData.activityType = PolarActivityDataType.STEPS
                    }.alignmentGuide(.leading) { d in d[.leading] }
                    .sheet(isPresented: $showDatePickerView) {
                        ActivityDatePickerView(isPresented: $showDatePickerView, showActivityView: $showActivityView)
                    }
                    .sheet(isPresented: $showActivityView) {
                        ActivityRecordingDetailsView(isPresented: $showActivityView)
                            .environmentObject(bleSdkManager)
                    }
                    
                    Button(hrSamplesTextTitle) {
                        showDatePickerView = true
                        showActivityView = false
                        bleSdkManager.activityRecordingData.loadingState = ActivityRecordingDataLoadingState.inProgress
                        bleSdkManager.activityRecordingData.activityType = PolarActivityDataType.HR_SAMPLES
                    }.alignmentGuide(.leading) { d in d[.leading] }
                    .sheet(isPresented: $showDatePickerView) {
                        ActivityDatePickerView(isPresented: $showDatePickerView, showActivityView: $showActivityView)
                    }
                    .sheet(isPresented: $showActivityView) {
                        ActivityRecordingDetailsView(isPresented: $showActivityView)
                            .environmentObject(bleSdkManager)
                    }

                    Button(ppiSamplesTextTitle) {
                        showDatePickerView = true
                        showActivityView = false
                        bleSdkManager.activityRecordingData.loadingState = ActivityRecordingDataLoadingState.inProgress
                        bleSdkManager.activityRecordingData.activityType = PolarActivityDataType.PEAKTOPEAKINTERVAL
                    }.alignmentGuide(.leading) { d in d[.leading] }
                    .sheet(isPresented: $showDatePickerView) {
                        ActivityDatePickerView(isPresented: $showDatePickerView, showActivityView: $showActivityView)
                    }
                    .sheet(isPresented: $showActivityView) {
                        ActivityRecordingDetailsView(isPresented: $showActivityView)
                            .environmentObject(bleSdkManager)
                    }

                    Button(nightlyRechargeTitle) {
                        showDatePickerView = true
                        showActivityView = false
                        bleSdkManager.activityRecordingData.loadingState = ActivityRecordingDataLoadingState.inProgress
                        bleSdkManager.activityRecordingData.activityType = PolarActivityDataType.NIGHTLY_RECHARGE
                    }.alignmentGuide(.leading) { d in d[.leading] }
                    .sheet(isPresented: $showDatePickerView) {
                        ActivityDatePickerView(isPresented: $showDatePickerView, showActivityView: $showActivityView)
                    }
                    .sheet(isPresented: $showActivityView) {
                        ActivityRecordingDetailsView(isPresented: $showActivityView)
                            .environmentObject(bleSdkManager)
                    }

                    Button(skinTemperatureTitle) {
                        showDatePickerView = true
                        showActivityView = false
                        bleSdkManager.activityRecordingData.loadingState = ActivityRecordingDataLoadingState.inProgress
                        bleSdkManager.activityRecordingData.activityType = PolarActivityDataType.SKINTEMPERATURE
                    }.alignmentGuide(.leading) { d in d[.leading] }
                    .sheet(isPresented: $showDatePickerView) {
                        ActivityDatePickerView(isPresented: $showDatePickerView, showActivityView: $showActivityView)
                    }
                    .sheet(isPresented: $showActivityView) {
                        ActivityRecordingDetailsView(isPresented: $showActivityView)
                            .environmentObject(bleSdkManager)
                    }

                    Button(activeTimeTitle) {
                        showDatePickerView = true
                        showActivityView = false
                        bleSdkManager.activityRecordingData.loadingState = ActivityRecordingDataLoadingState.inProgress
                        bleSdkManager.activityRecordingData.activityType = PolarActivityDataType.ACTIVE_TIME
                    }.alignmentGuide(.leading) { d in d[.leading] }
                    .sheet(isPresented: $showDatePickerView) {
                        ActivityDatePickerView(isPresented: $showDatePickerView, showActivityView: $showActivityView)
                    }
                    .sheet(isPresented: $showActivityView) {
                        ActivityRecordingDetailsView(isPresented: $showActivityView)
                            .environmentObject(bleSdkManager)
                    }

                    Button(activitySamplesTitle) {
                        showDatePickerView = true
                        showActivityView = false
                        bleSdkManager.activityRecordingData.loadingState = ActivityRecordingDataLoadingState.inProgress
                        bleSdkManager.activityRecordingData.activityType = PolarActivityDataType.ACTIVITY_SAMPLES
                    }.alignmentGuide(.leading) { d in d[.leading] }
                    .sheet(isPresented: $showDatePickerView) {
                        ActivityDatePickerView(isPresented: $showDatePickerView, showActivityView: $showActivityView)
                    }
                    .sheet(isPresented: $showActivityView) {
                        ActivityRecordingDetailsView(isPresented: $showActivityView)
                            .environmentObject(bleSdkManager)
                    }

                    HStack {
                        Button(caloriesTitle) {
                            showDatePickerView = true
                            showActivityView = false
                            bleSdkManager.activityRecordingData.loadingState = ActivityRecordingDataLoadingState.inProgress
                            bleSdkManager.activityRecordingData.caloriesType = selectedCaloriesType
                            bleSdkManager.activityRecordingData.activityType = PolarActivityDataType.CALORIES
                        }
                        .alignmentGuide(.leading) { d in d[.leading] }
                        .sheet(isPresented: $showDatePickerView) {
                            ActivityDatePickerView(isPresented: $showDatePickerView, showActivityView: $showActivityView)
                        }
                        .sheet(isPresented: $showActivityView) {
                            ActivityRecordingDetailsView(isPresented: $showDatePickerView)
                        }

                        Text("Type:")

                        Picker("Select Calories Type", selection: $selectedCaloriesType) {
                            ForEach(CaloriesType.allCases, id: \.self) { type in
                                Text(type.displayName).tag(type)
                            }
                        }
                        .pickerStyle(MenuPickerStyle())
                        .padding()
                    }
    
                    Button(trainingSessionsTitle) {
                        showTrainingSessionList = true
                    }
                    .alignmentGuide(.leading) { d in d[.leading] }
                    .sheet(isPresented: $showTrainingSessionList) {
                        TrainingSessionListView()
                            .environmentObject(bleSdkManager)
                    }
                }
                .padding(.vertical, 20)
                .buttonStyle(SecondaryButtonStyle(buttonState: ButtonState.released))
            }
        }
    }
        
    var sleepRecordingStateText: String {
        if bleSdkManager.sleepRecordingFeature.sleepRecordingEnabledAvailable {
            let onOrOff = bleSdkManager.sleepRecordingFeature.sleepRecordingEnabled ? "on" : "off"
            return "Sleep recording is \(onOrOff)"
        } else {
            return "Sleep recording control not available"
        }
    }
}
