/// Copyright © 2026 Polar Electro Oy. All rights reserved.

import SwiftUI
import PolarBleSdk
import RxSwift

struct OfflineExerciseV2View: View {

    @EnvironmentObject var bleSdkManager: PolarBleSdkManager

    @State private var statusHeader: String = ""
    @State private var statusDetail: String = ""
    @State private var shareFileURL: URL?
    @State private var isSharePresented = false

    private let disposeBag = DisposeBag()

    var body: some View {

        VStack(spacing: 10) {

            Button("Start Exercise") {
                statusHeader = "Start Exercise"
                statusDetail = "Starting..."

                bleSdkManager.startOfflineExerciseV2()
                .observe(on: MainScheduler.instance)
                .subscribe(
                    onSuccess: { result in
                        statusDetail = result.result == .success
                        ? "Exercise started"
                        : "Start failed: \(result.result)"
                    },
                    onFailure: { error in
                        statusDetail = "Start failed: \(error.localizedDescription)"
                    }
                )
                .disposed(by: disposeBag)
            }
            .buttonStyle(SecondaryButtonStyle(buttonState: getDefaultButtonState()))

            Button("Stop Exercise") {
                statusHeader = "Stop Exercise"
                statusDetail = "Stopping..."

                bleSdkManager.stopOfflineExerciseV2()
                .observe(on: MainScheduler.instance)
                .subscribe(
                    onCompleted: {
                        statusDetail = "Exercise stopped"
                    },
                    onError: { error in
                        statusDetail = "Stop failed: \(error.localizedDescription)"
                    }
                )
                .disposed(by: disposeBag)
            }
            .buttonStyle(SecondaryButtonStyle(buttonState: getDefaultButtonState()))

            Button("Check Exercise") {
                statusHeader = "Check Exercise"
                statusDetail = "Checking..."

                bleSdkManager.listOfflineExercisesV2()
                bleSdkManager.getOfflineExerciseStatusV2()

                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                    let running = bleSdkManager.offlineExerciseV2Status
                    let hasEntries = !bleSdkManager.offlineExerciseV2Entries.isEmpty

                    if running {
                        statusDetail = "Exercise is running"
                    } else if hasEntries {
                        statusDetail = "Exercise exists on device"
                    } else {
                        statusDetail = "No exercise found"
                    }
                }
            }
            .buttonStyle(SecondaryButtonStyle(buttonState: getDefaultButtonState()))

            Button("Fetch Exercise") {
                statusHeader = "Fetch Exercise"
                statusDetail = "Fetching..."

                Task {
                    do {
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

                bleSdkManager.removeOfflineExerciseV2()
                .observe(on: MainScheduler.instance)
                .subscribe(
                    onCompleted: {
                        statusDetail = "Exercise removed"
                    },
                    onError: { error in
                        statusDetail = "Remove failed: \(error.localizedDescription)"
                    }
                )
                .disposed(by: disposeBag)
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
        .sheet(isPresented: $isSharePresented) {
            if let url = shareFileURL {
                ShareView(fileURL: url)
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
