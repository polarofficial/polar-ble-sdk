/// Copyright © 2022 Polar Electro Oy. All rights reserved.

import Foundation
import SwiftUI
import PolarBleSdk

struct OnlineStreamsView: View {
    @EnvironmentObject private var bleSdkManager: PolarBleSdkManager
    @State private var urlToShare: IdentifiableURL?
    @State private var showHrGraph = false
    @State private var showAccGraph = false
    @State private var showEcgGraph = false
    @State private var selectedTypes: Set<PolarDeviceDataType> = []

    struct ShownData: Identifiable {
        let dataType: String
        let data: String
        var id: String { return "\(dataType)\(data)" }
    }
    
    @State private var shownData: ShownData? = nil
    
    func shareURL(url: URL) {
        urlToShare = IdentifiableURL(url: url)
    }

    private func toggleSelection(_ dataType: PolarDeviceDataType) {
        selectedTypes = OnlineStreamLogic.toggled(dataType, in: selectedTypes)
    }

    private func isAnyStreamActive() -> Bool {
        return PolarDeviceDataType.allCases.contains { bleSdkManager.isStreamOn(feature: $0) }
    }

    private func stopAllStreams() {
        for dataType in PolarDeviceDataType.allCases {
            if bleSdkManager.isStreamOn(feature: dataType) {
                bleSdkManager.onlineStreamStop(feature: dataType)
            }
        }
    }

    private func startSelected() {
        bleSdkManager.beginStreamingSession()
        let types = OnlineStreamLogic.typesToStart(
            selected: selectedTypes,
            available: bleSdkManager.onlineStreamingFeature.availableOnlineDataTypes,
            isStreaming: { bleSdkManager.isStreamOn(feature: $0) })
        for dataType in types {
            bleSdkManager.onlineStreamStartMaxSettings(feature: dataType)
        }
    }

    /// Returns true for data types that require a settings dialog before streaming.
    private func needsSettings(_ dataType: PolarDeviceDataType) -> Bool {
        OnlineStreamLogic.needsSettings(dataType)
    }

    var body: some View {

        if case .connected = bleSdkManager.deviceConnectionState,
           bleSdkManager.onlineStreamingFeature.isSupported {
            VStack(spacing: 0) {
                // Sensor rows — only show available types
                ForEach(PolarDeviceDataType.allCases.filter {
                    bleSdkManager.onlineStreamingFeature.availableOnlineDataTypes[$0] == true
                }) { dataType in
                    VStack(spacing: 0) {
                        HStack(spacing: 12) {
                            // Measurement type label — red
                            Text(dataType.displayName)
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundColor(.red)
                                .frame(minWidth: 70, alignment: .leading)

                            // Checkbox — red
                            Button(action: { toggleSelection(dataType) }) {
                                Image(systemName: selectedTypes.contains(dataType)
                                      ? "checkmark.square.fill" : "square")
                                    .font(.system(size: 22))
                                    .foregroundColor(.red)
                            }
                            .buttonStyle(.plain)
                            .accessibilityIdentifier("psdc.onlineStreaming.selectToggle.\(getShortNameForDataType(dataType))")

                            // SETTINGS button right after checkbox — red text & outline
                            if needsSettings(dataType) {
                                Button(action: {
                                    bleSdkManager.getOnlineStreamSettings(feature: dataType)
                                }) {
                                    Text("online.settings")
                                        .font(.system(size: 13, weight: .medium))
                                        .foregroundColor(.red)
                                        .padding(.horizontal, 14)
                                        .padding(.vertical, 7)
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 8)
                                                .stroke(Color.red, lineWidth: 1.5)
                                        )
                                }
                                .buttonStyle(.plain)
                                .accessibilityIdentifier("psdc.onlineStreaming.settingsButton.\(getShortNameForDataType(dataType))")
                            }

                            Spacer()

                            // Share & Show buttons when stream file is available
                            if case let .success(urlOptional) = bleSdkManager.onlineStreamingFeature.isStreaming[dataType],
                               let url = urlOptional {
                                ShareButton() { shareURL(url: url) }
                                ShowButton() {
                                    let data = (try? String(contentsOf: url, encoding: .utf8)) ?? ""
                                    shownData = ShownData(dataType: dataType.displayName, data: data)
                                }
                            }
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 10)
                        Divider()
                    }
                }

                let streaming = isAnyStreamActive()
                let canStart = OnlineStreamLogic.isButtonEnabled(streaming: streaming, hasSelection: !selectedTypes.isEmpty)
                Button(action: {
                    if streaming {
                        stopAllStreams()
                    } else {
                        startSelected()
                    }
                }) {
                    Text(OnlineStreamLogic.buttonTitle(streaming: streaming))
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(canStart ? Color.red : Color.gray)
                        .cornerRadius(8)
                        .opacity(1.0)
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("psdc.onlineStreaming.startStopButton")
                .disabled(!canStart)
                .padding(.horizontal, 12)
                .padding(.vertical, 10)

                // Live streaming values
                OnlineStreamValues(
                    showHrGraph: $showHrGraph,
                    showAccGraph: $showAccGraph,
                    showEcgGraph: $showEcgGraph
                )
                .padding(.horizontal, 12)
            }
            .fullScreenCover(item: $bleSdkManager.onlineStreamSettings) { streamSettings in
                let settings = streamSettings
                SettingsView(streamedFeature: settings.feature, streamSettings: settings, saveOnly: true)
            }
            .sheet(
                item: Binding(
                    get: { urlToShare },
                    set: { newValue in
                        if let url = urlToShare?.url {
                            bleSdkManager.onlineStreamLogFileShared(at: url)
                        }
                        urlToShare = newValue
                    }
                ),
                content: { identifiableURL in ActivityViewController(activityItems: [identifiableURL.url], applicationActivities: nil)}
            )
            .sheet(
                item: $shownData,
                content: { shownData in
                    NavigationView {
                        TextViewerView(
                            title: shownData.dataType,
                            text: shownData.data
                        )
                        .toolbar {
                            ToolbarItem(placement: .navigationBarTrailing) {
                                Button(action: {
                                    self.shownData = nil
                                }) {
                                    Image(systemName: "xmark.circle.fill")
                                        .foregroundColor(.blue)
                                }.accessibility(identifier: "Close \(shownData.dataType)")
                            }
                        }
                    }
                })
            .onChange(of: showHrGraph) { newValue in
                if newValue {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                        presentHrGraph()
                    }
                }
            }
            .onChange(of: showAccGraph) { newValue in
                if newValue {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                        presentAccGraph()
                    }
                }
            }
            .onChange(of: showEcgGraph) { newValue in
                if newValue {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                        presentEcgGraph()
                    }
                }
            }
        }
    }
    
    private func presentFullScreenModal(_ viewController: UIViewController, onFailure: () -> Void) {
        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let rootViewController = windowScene.windows.first?.rootViewController else {
            onFailure()
            return
        }

        var topController = rootViewController
        while let presented = topController.presentedViewController {
            topController = presented
        }

        viewController.modalPresentationStyle = .fullScreen
        topController.present(viewController, animated: true)
    }

    private func presentHrGraph() {
        presentFullScreenModal(HrGraphViewController(onClose: { showHrGraph = false }),
                               onFailure: { showHrGraph = false })
    }

    private func presentAccGraph() {
        presentFullScreenModal(AccGraphViewController(onClose: { showAccGraph = false }),
                               onFailure: { showAccGraph = false })
    }

    private func presentEcgGraph() {
        presentFullScreenModal(EcgGraphViewController(onClose: { showEcgGraph = false }),
                               onFailure: { showEcgGraph = false })
    }
}

struct OnlineStreamValues: View {
    @EnvironmentObject private var bleSdkManager: PolarBleSdkManager
    @Binding var showHrGraph: Bool
    @Binding var showAccGraph: Bool
    @Binding var showEcgGraph: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            ForEach(bleSdkManager.onlineRecordingDataTypes, id: \.self) { dataType in
                if let summary = StreamValueFormatter.summary(for: dataType, manager: bleSdkManager) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(summary.title).font(.system(size: 16)).bold()
                        ForEach(Array(summary.lines.enumerated()), id: \.offset) { _, line in
                            Text(line).font(.system(size: 14))
                        }
                        if let graph = summary.graph {
                            GraphButton { show(graph) }
                                .padding(.top, 4)
                        }
                        Divider()
                    }
                }
            }
        }
    }

    private func show(_ graph: StreamValueSummary.Graph) {
        switch graph {
        case .ecg: showEcgGraph = true
        case .acc: showAccGraph = true
        case .hr: showHrGraph = true
        }
    }
}

fileprivate struct ShareButton: View {
    var action: () -> Void
    
    var body: some View {
        Button(action: action) {
            Image(systemName: "square.and.arrow.up")
                .font(.system(size: 28))
        }
    }
}

fileprivate struct ShowButton: View {
    var action: () -> Void
    
    var body: some View {
        Button(action: action) {
            Image(systemName: "text.viewfinder")
                .font(.system(size: 28))

        }
    }
}

fileprivate struct GraphButton: View {
    var action: () -> Void
    
    var body: some View {
        Button(action: action) {
            Text("online.graph")
                .font(.system(size: 14))
                .foregroundColor(.red)
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .overlay(
                    RoundedRectangle(cornerRadius: 4)
                        .stroke(Color.red, lineWidth: 1)
                )
        }
    }
}


fileprivate struct ActivityViewController: UIViewControllerRepresentable {
    let activityItems: [Any]
    let applicationActivities: [UIActivity]?
    
    func makeUIViewController(context: Context) -> UIActivityViewController {
        let controller = UIActivityViewController(activityItems: activityItems, applicationActivities: applicationActivities)
        return controller
    }
    
    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}

fileprivate struct IdentifiableURL: Identifiable {
    let id = UUID()
    let url: URL
}

extension PolarDeviceDataType: Identifiable {
    public var id: Int {
        switch self {
        case .ecg:
            return 1
        case .acc:
            return 2
        case .ppg:
            return 3
        case .ppi:
            return 4
        case .gyro:
            return 5
        case .magnetometer:
            return 6
        case .hr:
            return 7
        case .temperature:
            return 8
        case .pressure:
            return 9
        case .skinTemperature:
            return 10
        }
    }
}

struct OnlineStreamsView_Previews: PreviewProvider {
    private static let onlineStreamingFeature = OnlineStreamingFeature(
        isSupported: true,
        availableOnlineDataTypes: [PolarDeviceDataType.hr: true, PolarDeviceDataType.acc: false, PolarDeviceDataType.ppi: true, PolarDeviceDataType.gyro: false, PolarDeviceDataType.magnetometer: true, PolarDeviceDataType.ecg: false, PolarDeviceDataType.temperature: false, PolarDeviceDataType.pressure: false, PolarDeviceDataType.skinTemperature: false],
        isStreaming: [PolarDeviceDataType.hr: .inProgress, PolarDeviceDataType.acc:  .inProgress, PolarDeviceDataType.ppi:  .inProgress, PolarDeviceDataType.gyro:  .inProgress, PolarDeviceDataType.magnetometer:  .inProgress, PolarDeviceDataType.ecg:  .inProgress, PolarDeviceDataType.temperature:  .inProgress, PolarDeviceDataType.pressure:  .inProgress, PolarDeviceDataType.skinTemperature:  .inProgress]
    )
    
    private static let polarBleSdkManager: PolarBleSdkManager = {
        let polarBleSdkManager = PolarBleSdkManager()
        polarBleSdkManager.onlineStreamingFeature = onlineStreamingFeature
        return polarBleSdkManager
    }()
    
    static var previews: some View {
        return OnlineStreamsView()
            .environmentObject(polarBleSdkManager)
    }
}
