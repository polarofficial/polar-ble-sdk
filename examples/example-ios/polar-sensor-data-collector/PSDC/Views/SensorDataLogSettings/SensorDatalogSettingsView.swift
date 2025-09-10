/// Copyright © 2022 Polar Electro Oy. All rights reserved.

import Foundation
import SwiftUI
import PolarBleSdk

struct SensorDatalogSettingsView: View {
    
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
    
    @State private var isShareSheetPresented = false
    @State private var logFileURL: URL?
    
    var body: some View {
        if case .connected = bleSdkManager.deviceConnectionState,
           bleSdkManager.deviceInfoFeature.isSupported {
            VStack {
                Button(accTitle,
                       action: {
                    Task{
                        isAccLoggingEnabled = !isAccLoggingEnabled
                        accTitle = getButtonText(measurement: "acc")
                        guard var sdLogConfig = bleSdkManager.sdLogConfig else { return }
                        sdLogConfig.accelerationLogEnabled = isAccLoggingEnabled
                        bleSdkManager.setSDLogSettings(logConfig: sdLogConfig)
                    }
                }).task {
                    if ((bleSdkManager.sdLogConfig?.accelerationLogEnabled) != nil) {
                        isAccLoggingEnabled = bleSdkManager.sdLogConfig!.accelerationLogEnabled!
                        accTitle = getButtonText(measurement: "acc")
                    }
                }
                .buttonStyle(getButtonStyle(toggle: isAccLoggingEnabled))
                
                Button(calTitle,
                       action: {
                    Task{
                        isCaloriesLoggingEnabled = !isCaloriesLoggingEnabled
                        calTitle = getButtonText(measurement: "cal")
                        guard var sdLogConfig = bleSdkManager.sdLogConfig else { return }
                        sdLogConfig.caloriesLogEnabled = isCaloriesLoggingEnabled
                        bleSdkManager.setSDLogSettings(logConfig: sdLogConfig)
                    }
                }).task {
                    if (bleSdkManager.sdLogConfig?.caloriesLogEnabled) != nil {
                        isCaloriesLoggingEnabled = bleSdkManager.sdLogConfig!.caloriesLogEnabled!
                        calTitle = getButtonText(measurement: "cal")
                    }
                }
                .buttonStyle(getButtonStyle(toggle: isCaloriesLoggingEnabled))
                
                Button(metTitle,
                       action: {
                    Task{
                        isMetLoggingEnabled = !isMetLoggingEnabled
                        metTitle = getButtonText(measurement: "met")
                        guard var sdLogConfig = bleSdkManager.sdLogConfig else { return }
                        sdLogConfig.metLogEnabled = isMetLoggingEnabled
                        bleSdkManager.setSDLogSettings(logConfig: sdLogConfig)
                    }
                }).task {
                    if (bleSdkManager.sdLogConfig?.metLogEnabled) != nil {
                        isMetLoggingEnabled = bleSdkManager.sdLogConfig!.metLogEnabled!
                        metTitle = getButtonText(measurement: "met")
                    }
                }
                .buttonStyle(getButtonStyle(toggle: isMetLoggingEnabled))
                
                Button(ohrTitle,
                       action: {
                    Task{
                        isOhrLoggingEnabled = !isOhrLoggingEnabled
                        ohrTitle = getButtonText(measurement: "ohr")
                        guard var sdLogConfig = bleSdkManager.sdLogConfig else { return }
                        sdLogConfig.ohrLogEnabled = isOhrLoggingEnabled
                        bleSdkManager.setSDLogSettings(logConfig: sdLogConfig)
                    }
                }).task {
                    if (bleSdkManager.sdLogConfig?.ohrLogEnabled) != nil {
                        isOhrLoggingEnabled = bleSdkManager.sdLogConfig!.ohrLogEnabled!
                        ohrTitle = getButtonText(measurement: "ohr")
                    }
                }
                .buttonStyle(getButtonStyle(toggle: isOhrLoggingEnabled))
                
                Button(ppiTitle,
                       action: {
                    Task{
                        isPPiLoggingEnabled = !isPPiLoggingEnabled
                        ppiTitle = getButtonText(measurement: "ppi")
                        guard var sdLogConfig = bleSdkManager.sdLogConfig else { return }
                        sdLogConfig.ppiLogEnabled = isPPiLoggingEnabled
                        bleSdkManager.setSDLogSettings(logConfig: sdLogConfig)
                    }
                }).task {
                    if (bleSdkManager.sdLogConfig?.ppiLogEnabled) != nil {
                        isPPiLoggingEnabled = bleSdkManager.sdLogConfig!.ppiLogEnabled!
                        ppiTitle = getButtonText(measurement: "ppi")
                    }
                }
                .buttonStyle(getButtonStyle(toggle: isPPiLoggingEnabled))
                
                Button(sleepTitle,
                       action: {
                    Task{
                        isSleepLoggingEnabled = !isSleepLoggingEnabled
                        sleepTitle = getButtonText(measurement: "sleep")
                        guard var sdLogConfig = bleSdkManager.sdLogConfig else { return }
                        sdLogConfig.sleepLogEnabled = isSleepLoggingEnabled
                        bleSdkManager.setSDLogSettings(logConfig: sdLogConfig)
                    }
                }).task {
                    if (bleSdkManager.sdLogConfig?.sleepLogEnabled) != nil {
                        isSleepLoggingEnabled = bleSdkManager.sdLogConfig!.sleepLogEnabled!
                        sleepTitle = getButtonText(measurement: "sleep")
                    }
                }
                .buttonStyle(getButtonStyle(toggle: isSleepLoggingEnabled))
                
                Button(skinTempTitle,
                       action: {
                    Task{
                        isSkinTempLoggingEnabled = !isSkinTempLoggingEnabled
                        skinTempTitle = getButtonText(measurement: "skinTemp")
                        guard var sdLogConfig = bleSdkManager.sdLogConfig else { return }
                        sdLogConfig.skinTemperatureLogEnabled = isSkinTempLoggingEnabled
                        bleSdkManager.setSDLogSettings(logConfig: sdLogConfig)
                    }
                }).task {
                    if (bleSdkManager.sdLogConfig?.skinTemperatureLogEnabled) != nil {
                        isSkinTempLoggingEnabled = bleSdkManager.sdLogConfig!.skinTemperatureLogEnabled!
                        skinTempTitle = getButtonText(measurement: "skinTemp")
                    }
                }
                .buttonStyle(getButtonStyle(toggle: isSkinTempLoggingEnabled))
                
                Spacer()
                
                Button("Export PSDC app logs") {
                    if logFileURL == nil {
                        logFileURL = getLogFile()
                    }
                    if logFileURL != nil {
                        isShareSheetPresented = true
                    }
                }
                .padding(.bottom)
                .sheet(isPresented: $isShareSheetPresented) {
                    if let url = logFileURL {
                        ExportLogsView(text: "Export PSDC app logs", fileURL: url)
                    }
                }.onAppear() {
                    Task {
                        await bleSdkManager.getSDLogSettings()
                        logFileURL = getLogFile()
                    }
                }
            }
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

    private func getLogFile() -> URL? {
        let documentsDirectory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        let logFileURL = documentsDirectory.appendingPathComponent("PSDCAppLogs.txt")

        if FileManager.default.fileExists(atPath: logFileURL.path) {
            NSLog("Reading from existing log file at: \(logFileURL)")
        } else {
            NSLog("Sample log file not found at: \(logFileURL)")
        }

        return logFileURL
    }
}

struct ExportLogsView: UIViewControllerRepresentable {
    let text: String
    let fileURL: URL
    
    func makeUIViewController(context: Context) -> UIActivityViewController {
        NSLog("Creating share sheet with file: \(fileURL)")

        let activityController = UIActivityViewController(activityItems: [text, fileURL], applicationActivities: nil)

        activityController.completionWithItemsHandler = { activityType, completed, returnedItems, error in
            if completed {
                NSLog("Share completed successfully")
            } else if let error = error {
                NSLog("Error during share: \(error.localizedDescription)")
            } else {
                NSLog("Share was canceled")
            }
        }

        return activityController
    }

    // To conform to protocol UIViewControllerRepresentable
    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {
    }
}
