/// Copyright © 2026 Polar Electro Oy. All rights reserved.

import SwiftUI
import PolarBleSdk

struct OfflineExerciseV2View: View {

    @EnvironmentObject var bleSdkManager: PolarBleSdkManager

    @State private var statusHeader: String = ""
    @State private var statusDetail: String = ""
    @State private var shareFileURL: URL?
    @State private var isSharePresented = false

    private enum ExerciseRunState {
        case stopped, running, paused
    }

    private var runState: ExerciseRunState {
        guard bleSdkManager.isOfflineExcerciseV2Ongoing else { return .stopped }
        return bleSdkManager.isOfflineExerciseV2Paused ? .paused : .running
    }

    private var startButtonText: String {
        runState == .stopped ? "Start exercise" : "Stop exercise"
    }

    private var pauseButtonText: String {
        runState == .paused ? "Resume exercise" : "Pause exercise"
    }

    var body: some View {

        VStack(spacing: 10) {
            Button(startButtonText) {
                Task { @MainActor in
                    if (runState == .stopped) {
                        do {
                            let result = try await bleSdkManager.startOfflineExerciseV2()
                            statusHeader = "Exercise status"
                            statusDetail = result.result == .success ? "Exercise started" : "Start failed: \(result.result)"
                        } catch {
                            statusDetail = "Start failed: \(error.localizedDescription)"
                        }
                    } else {
                        do {
                            try await bleSdkManager.stopOfflineExerciseV2()
                            statusHeader = "Exercise status"
                            statusDetail = "Exercise stopped"
                        } catch {
                            statusDetail = "Stop failed: \(error.localizedDescription)"
                        }
                    }
                }
            }
            .buttonStyle(SecondaryButtonStyle(buttonState: getDefaultButtonState()))

            if (runState != .stopped) {
                Button(pauseButtonText) {
                    statusHeader = "Exercise status"
                    statusDetail = runState == .paused ? "Resuming exercise..." : "Pausing exercise..."
                    Task { @MainActor in
                        do {
                            if (runState == .running) {
                                try await bleSdkManager.pauseOfflineExerciseV2()
                                statusDetail = "Exercise paused"
                            } else {
                                try await bleSdkManager.resumeOfflineExerciseV2()
                                statusDetail = "Exercise resumed"
                            }
                        } catch {
                            statusDetail = runState == .paused
                                ? "Resume failed: \(error.localizedDescription)"
                                : "Pause failed: \(error.localizedDescription)"
                        }
                    }
                }
                .buttonStyle(SecondaryButtonStyle(buttonState: getDefaultButtonState()))
            }

            Button("Check Exercise") {
                statusHeader = "Check Exercise"
                statusDetail = "Checking..."

                Task { @MainActor in
                    await bleSdkManager.listOfflineExercisesV2()
                    await bleSdkManager.getOfflineExerciseStatusV2()

                    let running = bleSdkManager.isOfflineExcerciseV2Ongoing
                    let hasEntries = !bleSdkManager.offlineExerciseV2Entries.isEmpty

                    statusHeader = "Exercise check"
                    statusDetail = ""
                    if running {
                        statusDetail = runState == .paused ? "Exercise paused" : "Exercise is running"
                    } else {
                        statusDetail = "No exercise running"
                        if hasEntries {
                            statusDetail += "\nExercise exists on device"
                        } else {
                            statusDetail += "\nNo exercise found"
                        }
                    }
                }
            }
            .buttonStyle(SecondaryButtonStyle(buttonState: getDefaultButtonState()))

            Button("Fetch Exercise") {
                statusHeader = "Fetch Exercise"
                statusDetail = "Fetching..."

                Task {
                    do {
                        await bleSdkManager.listOfflineExercisesV2()
                        shareFileURL = try await bleSdkManager.fetchAndExportOfflineExerciseV2()
                        isSharePresented = true
                        statusDetail = "Opening share dialog..."
                    } catch {
                        statusDetail = "Fetch failed: \(error.localizedDescription)"
                    }
                }
            }
            .buttonStyle(SecondaryButtonStyle(buttonState: getDefaultButtonState()))

            Button("Remove Exercise") {
                guard !bleSdkManager.offlineExerciseV2Entries.isEmpty else {
                    statusHeader = "Remove Exercise"
                    statusDetail = "No exercise to remove"
                    return
                }

                statusHeader = "Remove Exercise"
                statusDetail = "Removing..."

                Task { @MainActor in
                    do {
                        try await bleSdkManager.removeOfflineExerciseV2()
                        statusDetail = "Exercise removed"
                    } catch {
                        statusDetail = "Remove failed: \(error.localizedDescription)"
                    }
                }
            }
            .buttonStyle(SecondaryButtonStyle(buttonState: getDefaultButtonState()))

            Divider()

            VStack(alignment: .leading, spacing: 4) {
                if !statusHeader.isEmpty {
                    Text(statusHeader)
                }
                if !statusDetail.isEmpty {
                    Text(statusDetail)
                    .lineLimit(2)
                }
            }
            .foregroundColor(.red)

            Spacer()
        }
        .padding()
        .navigationTitle("Offline Exercise V2")
        .sheet(isPresented: $isSharePresented, onDismiss: {
              statusHeader = ""
              statusDetail = ""
          }) {
              if let url = shareFileURL {
                  ShareView(fileURL: url)
              }
          }.onAppear {
              Task {
                  await bleSdkManager.getOfflineExerciseStatusV2()
              }
          }
    }

    private func getDefaultButtonState() -> ButtonState {
        switch bleSdkManager.deviceConnectionState {
        case .connected:
            return .released
        default:
            return .disabled
        }
    }
}

struct ShareView: UIViewControllerRepresentable {
    let fileURL: URL

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(
            activityItems: ["Exercise Data", fileURL],
            applicationActivities: nil
        )
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
