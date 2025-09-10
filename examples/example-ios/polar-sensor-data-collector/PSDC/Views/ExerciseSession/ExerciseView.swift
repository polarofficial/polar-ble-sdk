//  Copyright © 2025 Polar. All rights reserved.

import SwiftUI
import PolarBleSdk

struct ExerciseView: View {
    @EnvironmentObject private var bleSdkManager: PolarBleSdkManager

    @State private var selectedIndex: Int = 0

    @State private var toast: String? = nil

    var body: some View {
        NavigationView {
            VStack(alignment: .leading, spacing: 16) {

                Picker("Sport profile", selection: $selectedIndex) {
                    ForEach(0..<bleSdkManager.exerciseState.sportProfiles.count, id: \.self) { i in
                        Text(bleSdkManager.exerciseState.sportProfiles[i].displayName).tag(i)
                    }
                }
                .pickerStyle(.segmented)
                .onChange(of: selectedIndex) { i in
                    bleSdkManager.selectSportProfile(bleSdkManager.exerciseState.sportProfiles[i])
                }

                Text(bleSdkManager.exerciseState.statusText)
                    .font(.title3).fontWeight(.semibold)

                HStack(spacing: 12) {
                    Button("Start") {
                        toast = "Starting…"
                        bleSdkManager.startExercise()
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(!bleSdkManager.exerciseState.canStart)

                    Button("Pause") {
                        toast = "Pausing…"
                        bleSdkManager.pauseExercise()
                    }
                    .buttonStyle(.bordered)
                    .disabled(!bleSdkManager.exerciseState.canPause)

                    Button("Resume") {
                        toast = "Resuming…"
                        bleSdkManager.resumeExercise()
                    }
                    .buttonStyle(.bordered)
                    .disabled(!bleSdkManager.exerciseState.canResume)

                    Button("Stop") {
                        toast = "Stopping…"
                        bleSdkManager.stopExercise()
                    }
                    .buttonStyle(.bordered)
                    .disabled(!bleSdkManager.exerciseState.canStop)
                }

                Spacer()
            }
            .padding(16)
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
            .navigationTitle("Exercise")
        }
        .navigationViewStyle(.stack)
        .onAppear {
            selectedIndex = bleSdkManager.exerciseState
                .sportProfiles
                .firstIndex(of: bleSdkManager.exerciseState.selectedSport) ?? 0

            Task { await bleSdkManager.refreshExerciseStatus() }
            bleSdkManager.startExerciseAutoRefresh()
        }
        .onDisappear {
            bleSdkManager.stopExerciseAutoRefresh()
        }
        .onChange(of: bleSdkManager.exerciseState.status) { newStatus in
            switch newStatus {
            case .inProgress: toast = "Exercise in progress"
            case .paused:     toast = "Exercise paused"
            case .syncRequired: toast = "Syncing…"
            case .stopped:    toast = "No active exercise"
            default: break
            }
        }
    }
}
