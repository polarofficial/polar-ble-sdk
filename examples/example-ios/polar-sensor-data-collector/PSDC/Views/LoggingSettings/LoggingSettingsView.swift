/// Copyright © 2022 Polar Electro Oy. All rights reserved.

import Foundation
import SwiftUI
import PolarBleSdk
import Zip

struct LoggingSettingsView: View {
    
    @EnvironmentObject private var bleSdkManager: PolarBleSdkManager
    @State private var isAccLoggingEnabled = false
    @State private var isPPiLoggingEnabled = false
    @State private var isOhrLoggingEnabled = false
    @State private var isSkinTempLoggingEnabled = false
    @State private var isCaloriesLoggingEnabled = false
    @State private var isMetLoggingEnabled = false
    @State private var isSleepLoggingEnabled = false
    
    @State private var accTitle = "No Acc settings available"
    @State private var calTitle = "No Calories settings available"
    @State private var metTitle = "No MET settings available"
    @State private var ohrTitle = "No OHR settings available"
    @State private var ppiTitle = "No PPi settings available"
    @State private var sleepTitle = "No Sleep settings available"
    @State private var skinTempTitle = "No Skin temp settings available"
    
    @State private var isLoading = true

    @State private var isExportingDeviceLogs = false
    @State private var isDeviceLogsSharePresented = false
    @State private var deviceLogsZipURL: URL?
    @State private var exportDeviceLogsError: String?
    @State private var showExportDeviceLogsAlert = false

    var body: some View {
        if case .connected = bleSdkManager.deviceConnectionState,
           bleSdkManager.deviceInfoFeature.isSupported {
            VStack {
                if isLoading {
                    Spacer()
                    ProgressView("Loading log settings…")
                    Spacer()
                } else {
                    Button(accTitle,
                       action: {
                    Task{
                        isAccLoggingEnabled = !isAccLoggingEnabled
                        accTitle = getButtonText(measurement: "acc")
                        guard var logConfig = bleSdkManager.logConfig else { return }
                        logConfig.accelerationLogEnabled = isAccLoggingEnabled
                        bleSdkManager.setLogConfig(logConfig: logConfig)
                    }
                }).task {
                    if ((bleSdkManager.logConfig?.accelerationLogEnabled) != nil) {
                        isAccLoggingEnabled = bleSdkManager.logConfig!.accelerationLogEnabled!
                        accTitle = getButtonText(measurement: "acc")
                    }
                }
                .buttonStyle(getButtonStyle(toggle: isAccLoggingEnabled))
                
                Button(calTitle,
                       action: {
                    Task{
                        isCaloriesLoggingEnabled = !isCaloriesLoggingEnabled
                        calTitle = getButtonText(measurement: "cal")
                        guard var logConfig = bleSdkManager.logConfig else { return }
                        logConfig.caloriesLogEnabled = isCaloriesLoggingEnabled
                        bleSdkManager.setLogConfig(logConfig: logConfig)
                    }
                }).task {
                    if (bleSdkManager.logConfig?.caloriesLogEnabled) != nil {
                        isCaloriesLoggingEnabled = bleSdkManager.logConfig!.caloriesLogEnabled!
                        calTitle = getButtonText(measurement: "cal")
                    }
                }
                .buttonStyle(getButtonStyle(toggle: isCaloriesLoggingEnabled))
                
                Button(metTitle,
                       action: {
                    Task{
                        isMetLoggingEnabled = !isMetLoggingEnabled
                        metTitle = getButtonText(measurement: "met")
                        guard var logConfig = bleSdkManager.logConfig else { return }
                        logConfig.metLogEnabled = isMetLoggingEnabled
                        bleSdkManager.setLogConfig(logConfig: logConfig)
                    }
                }).task {
                    if (bleSdkManager.logConfig?.metLogEnabled) != nil {
                        isMetLoggingEnabled = bleSdkManager.logConfig!.metLogEnabled!
                        metTitle = getButtonText(measurement: "met")
                    }
                }
                .buttonStyle(getButtonStyle(toggle: isMetLoggingEnabled))
                
                Button(ohrTitle,
                       action: {
                    Task{
                        isOhrLoggingEnabled = !isOhrLoggingEnabled
                        ohrTitle = getButtonText(measurement: "ohr")
                        guard var logConfig = bleSdkManager.logConfig else { return }
                        logConfig.ohrLogEnabled = isOhrLoggingEnabled
                        bleSdkManager.setLogConfig(logConfig: logConfig)
                    }
                }).task {
                    if (bleSdkManager.logConfig?.ohrLogEnabled) != nil {
                        isOhrLoggingEnabled = bleSdkManager.logConfig!.ohrLogEnabled!
                        ohrTitle = getButtonText(measurement: "ohr")
                    }
                }
                .buttonStyle(getButtonStyle(toggle: isOhrLoggingEnabled))
                
                Button(ppiTitle,
                       action: {
                    Task{
                        isPPiLoggingEnabled = !isPPiLoggingEnabled
                        ppiTitle = getButtonText(measurement: "ppi")
                        guard var logConfig = bleSdkManager.logConfig else { return }
                        logConfig.ppiLogEnabled = isPPiLoggingEnabled
                        bleSdkManager.setLogConfig(logConfig: logConfig)
                    }
                }).task {
                    if (bleSdkManager.logConfig?.ppiLogEnabled) != nil {
                        isPPiLoggingEnabled = bleSdkManager.logConfig!.ppiLogEnabled!
                        ppiTitle = getButtonText(measurement: "ppi")
                    }
                }
                .buttonStyle(getButtonStyle(toggle: isPPiLoggingEnabled))
                
                Button(sleepTitle,
                       action: {
                    Task{
                        isSleepLoggingEnabled = !isSleepLoggingEnabled
                        sleepTitle = getButtonText(measurement: "sleep")
                        guard var logConfig = bleSdkManager.logConfig else { return }
                        logConfig.sleepLogEnabled = isSleepLoggingEnabled
                        bleSdkManager.setLogConfig(logConfig: logConfig)
                    }
                }).task {
                    if (bleSdkManager.logConfig?.sleepLogEnabled) != nil {
                        isSleepLoggingEnabled = bleSdkManager.logConfig!.sleepLogEnabled!
                        sleepTitle = getButtonText(measurement: "sleep")
                    }
                }
                .buttonStyle(getButtonStyle(toggle: isSleepLoggingEnabled))
                
                Button(skinTempTitle,
                       action: {
                    Task{
                        isSkinTempLoggingEnabled = !isSkinTempLoggingEnabled
                        skinTempTitle = getButtonText(measurement: "skinTemp")
                        guard var logConfig = bleSdkManager.logConfig else { return }
                        logConfig.skinTemperatureLogEnabled = isSkinTempLoggingEnabled
                        bleSdkManager.setLogConfig(logConfig: logConfig)
                    }
                }).task {
                    if (bleSdkManager.logConfig?.skinTemperatureLogEnabled) != nil {
                        isSkinTempLoggingEnabled = bleSdkManager.logConfig!.skinTemperatureLogEnabled!
                        skinTempTitle = getButtonText(measurement: "skinTemp")
                    }
                }
                .buttonStyle(getButtonStyle(toggle: isSkinTempLoggingEnabled))
                
                Spacer()

                ZStack {
                    Button("Export device logs and telemetry") {
                        Task {
                            await exportDeviceLogs()
                        }
                    }
                    .buttonStyle(SecondaryButtonStyle(buttonState: .pressedDown))
                    .disabled(isExportingDeviceLogs)
                    .opacity(isExportingDeviceLogs ? 0 : 1)

                    if isExportingDeviceLogs {
                        ProgressView()
                    }
                }
                .padding(.bottom)
                .alert("Export Device Logs", isPresented: $showExportDeviceLogsAlert) {
                    Button("OK", role: .cancel) {}
                } message: {
                    if let error = exportDeviceLogsError {
                        Text("Export failed: \(error)")
                    } else {
                        Text("No log files found on device.")
                    }
                }
                .sheet(isPresented: $isDeviceLogsSharePresented) {
                    if let url = deviceLogsZipURL {
                        ExportLogsView(text: "Export Device Logs", fileURL: url)
                    }
                }
                } // end else
            }
            .onAppear() {
                Task {
                    await bleSdkManager.getLogConfig()
                    if let config = bleSdkManager.logConfig {
                        if let val = config.accelerationLogEnabled {
                            isAccLoggingEnabled = val
                            accTitle = getButtonText(measurement: "acc")
                        }
                        if let val = config.caloriesLogEnabled {
                            isCaloriesLoggingEnabled = val
                            calTitle = getButtonText(measurement: "cal")
                        }
                        if let val = config.metLogEnabled {
                            isMetLoggingEnabled = val
                            metTitle = getButtonText(measurement: "met")
                        }
                        if let val = config.ohrLogEnabled {
                            isOhrLoggingEnabled = val
                            ohrTitle = getButtonText(measurement: "ohr")
                        }
                        if let val = config.ppiLogEnabled {
                            isPPiLoggingEnabled = val
                            ppiTitle = getButtonText(measurement: "ppi")
                        }
                        if let val = config.sleepLogEnabled {
                            isSleepLoggingEnabled = val
                            sleepTitle = getButtonText(measurement: "sleep")
                        }
                        if let val = config.skinTemperatureLogEnabled {
                            isSkinTempLoggingEnabled = val
                            skinTempTitle = getButtonText(measurement: "skinTemp")
                        }
                    }
                    isLoading = false
                }
            }
        }
    }

    private func exportDeviceLogs() async {
        isExportingDeviceLogs = true
        exportDeviceLogsError = nil
        defer { isExportingDeviceLogs = false }

        do {
            let logs = try await bleSdkManager.exportDeviceLogs()
            if logs.isEmpty {
                showExportDeviceLogsAlert = true
                return
            }

            let tempDir = FileManager.default.temporaryDirectory
                .appendingPathComponent("device_logs_\(Int(Date().timeIntervalSince1970))", isDirectory: true)
            try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)

            var fileURLs: [URL] = []
            for log in logs {
                let filename = (log.path as NSString).lastPathComponent
                let fileURL = tempDir.appendingPathComponent(filename)
                try log.data.write(to: fileURL)
                fileURLs.append(fileURL)
            }

            let deviceId = bleSdkManager.deviceId ?? "device"
            let zipFileName = "device_logs_\(deviceId)_\(Int(Date().timeIntervalSince1970)).zip"
            let zipURL = FileManager.default.temporaryDirectory.appendingPathComponent(zipFileName)

            try Zip.zipFiles(paths: fileURLs, zipFilePath: zipURL, password: nil, progress: nil)

            deviceLogsZipURL = zipURL
            isDeviceLogsSharePresented = true
        } catch {
            AppLogger.log("Export device logs failed: \(error)")
            exportDeviceLogsError = error.localizedDescription
            showExportDeviceLogsAlert = true
        }
    }

    private func getButtonText(measurement: String) -> String {

        switch measurement {
        case "acc":
            if isAccLoggingEnabled {
                return "Disable Acc logging"
            } else {
                return "Enable Acc logging"
            }
        case "cal":
            if isCaloriesLoggingEnabled {
                return "Disable Calories logging"
            } else {
                return "Enable Calories logging"
            }
        case "met":
            if isMetLoggingEnabled {
                return "Disable MET logging"
            } else {
                return "Enable MET logging"
            }
        case "ohr":
            if isOhrLoggingEnabled {
                return "Disable OHR logging"
            } else {
                return "Enable OHR logging"
            }
        case "ppi":
            if isPPiLoggingEnabled {
                return "Disable PPi logging"
            } else {
                return "Enable PPi logging"
            }
        case "sleep":
            if isSleepLoggingEnabled {
                return "Disable Sleep logging"
            } else {
                return "Enable Sleep logging"
            }
        case "skinTemp":
            if isSkinTempLoggingEnabled{
                return "Disable SkinTemp logging"
            } else {
                return "Enable SkinTemp logging"
            }
        default:
            return "Unknown measurement type"
        }
    }
    
    private func getButtonStyle(toggle: Bool) -> SecondaryButtonStyle {
        
        switch toggle {
        case true:
            return SecondaryButtonStyle(buttonState: ButtonState.released)
        case false:
            return SecondaryButtonStyle(buttonState: ButtonState.pressedDown)
        }
    }

}

struct ExportLogsView: UIViewControllerRepresentable {
    let text: String
    let fileURL: URL
    
    func makeUIViewController(context: Context) -> UIActivityViewController {
        AppLogger.log("Creating share sheet with file: \(fileURL)")

        let activityController = UIActivityViewController(activityItems: [text, fileURL], applicationActivities: nil)

        activityController.completionWithItemsHandler = { activityType, completed, returnedItems, error in
            if completed {
                AppLogger.log("Share completed successfully")
            } else if let error = error {
                AppLogger.log("Error during share: \(error.localizedDescription)")
            } else {
                AppLogger.log("Share was canceled")
            }
        }

        return activityController
    }

    // To conform to protocol UIViewControllerRepresentable
    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {
    }
}
