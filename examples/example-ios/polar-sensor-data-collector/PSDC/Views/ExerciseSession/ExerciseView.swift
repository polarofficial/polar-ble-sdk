//  Copyright © 2025 Polar. All rights reserved.

import SwiftUI
import PolarBleSdk

struct ExerciseView: View {
    @EnvironmentObject private var bleSdkManager: PolarBleSdkManager

    @State private var saveOnStop: Bool = true
    @State private var toast: String? = nil

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Picker("exercise.sportProfile", selection: Binding(
                        get: { bleSdkManager.exerciseState.selectedSport },
                        set: { bleSdkManager.selectSportProfile($0) }
                    )) {
                        Text("exercise.defaultSport").tag(PolarExerciseSession.SportProfile?.none)
                        ForEach(0..<bleSdkManager.exerciseState.sportProfiles.count, id: \.self) { i in
                            let sport = bleSdkManager.exerciseState.sportProfiles[i]
                            Text(sport.displayName).tag(Optional(sport))
                        }
                    }
                    .pickerStyle(.menu)
                    .disabled(!bleSdkManager.exerciseState.canStart)

                    TextField("exercise.customSportId", text: $bleSdkManager.exerciseState.customSportIdText)
                        .textFieldStyle(.roundedBorder)
                        .keyboardType(.numberPad)
                        .accessibilityIdentifier("exercise.customSportId")
                        .disabled(!bleSdkManager.exerciseState.canStart)
                    Text(bleSdkManager.exerciseState.invalidSportId
                         ? String(localized: "exercise.invalidSportId")
                         : String(localized: "exercise.customSportId.help"))
                        .font(.caption)
                        .foregroundColor(bleSdkManager.exerciseState.invalidSportId ? .red : .secondary)

                    if let error = bleSdkManager.exerciseState.startError {
                        Text(error)
                            .font(.callout)
                            .foregroundColor(.red)
                    }

                    Text(bleSdkManager.exerciseState.statusText)
                        .font(.title3).fontWeight(.semibold)

                    HStack(spacing: 12) {
                        Button("exercise.start") {
                            toast = NSLocalizedString("exercise.toast.starting", comment: "Exercise start in progress")
                            bleSdkManager.startExercise()
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(!bleSdkManager.exerciseState.canStart || bleSdkManager.exerciseState.invalidSportId)

                        Button("exercise.pause") {
                            toast = NSLocalizedString("exercise.toast.pausing", comment: "Exercise pause in progress")
                            bleSdkManager.pauseExercise()
                        }
                        .buttonStyle(.bordered)
                        .disabled(!bleSdkManager.exerciseState.canPause)

                        Button("exercise.resume") {
                            toast = NSLocalizedString("exercise.toast.resuming", comment: "Exercise resume in progress")
                            bleSdkManager.resumeExercise()
                        }
                        .buttonStyle(.bordered)
                        .disabled(!bleSdkManager.exerciseState.canResume)

                        Button("exercise.stop") {
                            toast = NSLocalizedString("exercise.toast.stopping", comment: "Exercise stop in progress")
                            bleSdkManager.stopExercise(save: saveOnStop)
                        }
                        .buttonStyle(.bordered)
                        .disabled(!bleSdkManager.exerciseState.canStop)
                    }

                    if bleSdkManager.exerciseState.canStop {
                        HStack(spacing: 8) {
                            Toggle("", isOn: $saveOnStop)
                                .labelsHidden()
                                .toggleStyle(.automatic)
                            Text("exercise.saveOnStop")
                                .font(.subheadline)
                        }
                        .padding(.top, 4)
                    }

                    let startTimeString: String? = {
                        if let startTime = bleSdkManager.exerciseState.startTime {
                            let formatter = DateFormatter()
                            formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
                            formatter.timeZone = TimeZone.current
                            let formatted = formatter.string(from: startTime)
                            return formatted
                        }
                        return nil
                    }()

                    if let startTimeString {
                        Text(String(format: NSLocalizedString("exercise.startTime", comment: "Exercise start time"), startTimeString))
                            .font(.subheadline)
                            .padding(.top, 8)
                    }

                    Divider()
                        .padding(.top, 16)

                    Button(
                        bleSdkManager.exerciseState.isObservingNotifications
                        ? String(localized: "exercise.stopObserving")
                        : String(localized: "exercise.observeNotifications")
                    ) {
                        bleSdkManager.toggleExerciseNotificationObservation()
                    }
                    .buttonStyle(.bordered)

                    if let notificationEvent = bleSdkManager.exerciseState.notificationEvent {
                        Text(notificationEvent)
                            .font(.body)
                            .foregroundColor(.blue)
                    }
                }
                .padding(16)
            }
            .overlay(alignment: .bottom) {
                if let toast {
                    Text(toast)
                        .padding(.horizontal, 14).padding(.vertical, 10)
                        .background(.ultraThinMaterial, in: Capsule())
                        .transition(.opacity.combined(with: .move(edge: .bottom)))
                        .padding(.bottom, 24)
                        .onAppear {
                            DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) {
                                withAnimation { self.toast = nil }
                            }
                        }
                }
            }
            .animation(.easeInOut, value: toast)
            .navigationTitle("exercise.title")
        }
        .navigationViewStyle(.stack)
        .onAppear {

            Task { await bleSdkManager.refreshExerciseStatus() }
            bleSdkManager.startExerciseAutoRefresh()
        }
        .onDisappear {
            bleSdkManager.stopExerciseAutoRefresh()
            bleSdkManager.stopObservingExerciseNotifications()
        }
        .onChange(of: bleSdkManager.exerciseState.canStop) { canStop in
            if !canStop { saveOnStop = true }
        }
        .onChange(of: bleSdkManager.exerciseState.status) { newStatus in
            switch newStatus {
            case .inProgress: toast = NSLocalizedString("exercise.toast.inProgress", comment: "Exercise is active")
            case .paused:     toast = NSLocalizedString("exercise.toast.paused", comment: "Exercise is paused")
            case .syncRequired: toast = NSLocalizedString("exercise.toast.syncing", comment: "Exercise data is syncing")
            case .stopped:    toast = NSLocalizedString("exercise.toast.stopped", comment: "No active exercise")
            default: break
            }
        }
    }
}
