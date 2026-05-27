// Copyright © 2026 Polar Electro Oy. All rights reserved.

import SwiftUI
import PolarBleSdk

private let SLOT_COUNT = 4

struct WatchFaceView: View {
    @Environment(\.presentationMode) var presentationMode
    @EnvironmentObject private var bleSdkManager: PolarBleSdkManager

    @State private var slots: [PolarWatchFaceComplication?] = Array(repeating: nil, count: SLOT_COUNT)
    @State private var isLoading = false
    @State private var readStatus: String? = nil
    @State private var resultMessage: String? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Watch face complications")
                .font(.headline)
                .padding()

            if let status = readStatus {
                Text(status)
                    .font(.caption)
                    .foregroundColor(status.contains("Failed") ? .red : .secondary)
                    .padding(.horizontal)
            }

            Form {
                Section(header: Text("Complication Slots")) {
                    ForEach(0..<SLOT_COUNT, id: \.self) { index in
                        HStack {
                            Text("Slot \(index + 1)")
                                .frame(minWidth: 60, alignment: .leading)
                            Spacer()
                            Menu(displayName(slots[index])) {
                                ForEach(PolarWatchFaceComplication.allCases, id: \.complicationId) { option in
                                    Button(displayName(option)) {
                                        slots[index] = option
                                    }
                                }
                            }
                            .disabled(isLoading)
                        }
                    }
                }

                Section {
                    Button(action: applyComplications) {
                        HStack {
                            Spacer()
                            if isLoading {
                                ProgressView()
                            } else {
                                Text("Apply")
                            }
                            Spacer()
                        }
                    }
                    .disabled(isLoading)
                }

                if let msg = resultMessage {
                    Section {
                        Text(msg)
                            .foregroundColor(msg.contains("Failed") || msg.contains("failed") ? .red : .primary)
                    }
                }
            }
        }
        .task {
            await loadCurrentConfig()
        }
    }

    // MARK: - Helpers

    private func displayName(_ c: PolarWatchFaceComplication?) -> String {
        guard let c else { return "—" }
        if c == .empty { return "Empty" }
        return c.complicationId
            .replacingOccurrences(of: "-complication", with: "")
            .replacingOccurrences(of: "-", with: " ")
            .capitalized
    }

    // MARK: - Actions

    private func loadCurrentConfig() async {
        isLoading = true
        readStatus = "Fetching current configuration from device..."
        resultMessage = nil

        let result = await bleSdkManager.getWatchFaceConfig()
        await MainActor.run {
            isLoading = false
            switch result {
            case .success(let config):
                slots = (0..<SLOT_COUNT).map { config.enabledComplications.count > $0 ? config.enabledComplications[$0] : nil }
                readStatus = ""
            case .failure(let err):
                readStatus = "Failed to read: \(err.localizedDescription)"
            }
        }
    }

    private func applyComplications() {
        Task {
            await MainActor.run {
                isLoading = true
                resultMessage = nil
            }
            let ordered = slots.map { $0 ?? .empty }
            let config = PolarWatchFaceConfig(enabledComplications: ordered)
            let result = await bleSdkManager.setWatchFaceConfig(config: config)
            await MainActor.run {
                isLoading = false
                switch result {
                case .success:
                    resultMessage = "Applied successfully"
                case .failure(let err):
                    resultMessage = "Failed: \(err.localizedDescription)"
                }
            }
        }
    }
}