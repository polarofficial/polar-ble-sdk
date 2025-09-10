/// Copyright © 2022 Polar Electro Oy. All rights reserved.

import Foundation
import SwiftUI
import PolarBleSdk

struct OnlineStreamsView: View {
    @EnvironmentObject private var bleSdkManager: PolarBleSdkManager
    @State private var urlToShare: IdentifiableURL?
    
    func shareURL(url: URL) {
        urlToShare = IdentifiableURL(url: url)
    }
    
    var body: some View {

        if case .connected = bleSdkManager.deviceConnectionState,
           bleSdkManager.onlineStreamingFeature.isSupported {
            VStack {
                ForEach(PolarDeviceDataType.allCases) { dataType in
                    HStack {
                        OnlineStreamingButton(dataType: dataType)
                        Spacer()
                        if case let .success(urlOptional) = bleSdkManager.onlineStreamingFeature.isStreaming[dataType],
                           let url = urlOptional {
                            
                            ShareButton() { shareURL(url: url) }
                                .padding(.trailing)
                        }
                    }
                }
                OnlineStreamValues()
            }
            .fullScreenCover(item: $bleSdkManager.onlineStreamSettings) { streamSettings in
                let settings = streamSettings
                SettingsView(streamedFeature: settings.feature, streamSettings: settings)
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
        }
    }
}

struct OnlineStreamingButton: View {
    let dataType: PolarDeviceDataType
    @EnvironmentObject private var bleSdkManager: PolarBleSdkManager
    
    var body: some View {
        Button(getStreamButtonText(dataType, bleSdkManager.onlineStreamingFeature.isStreaming[dataType]),
               action: { streamButtonToggle(dataType) })
        .buttonStyle(SecondaryButtonStyle(buttonState: getStreamButtonState(dataType)))
        .disabled(getEnableStreamingButton(buttonState: getStreamButtonState(dataType)))
    }
    
    private func getEnableStreamingButton(buttonState: ButtonState) -> Bool {

        if (buttonState == ButtonState.disabled) {
            return true
        } else {
            return false
        }
    }

    private func getStreamButtonText(_ feature:PolarDeviceDataType, _ isStreaming: OnlineStreamingState?) -> String {
        let text = getShortNameForDataType(feature)
        let buttonText:String
        switch(isStreaming!) {
        case .inProgress:
            buttonText = "Stop \(text) Stream"
        case .success(url: _):
            buttonText = "Start \(text) Stream"
        case .failed(error: _):
            buttonText = "Start \(text) Stream"
        }
        return buttonText
    }
    
    private func streamButtonToggle(_ feature:PolarDeviceDataType) {
        NSLog("Stream toggle for feature \(feature)")
        if(bleSdkManager.isStreamOn(feature: feature)) {
            bleSdkManager.onlineStreamStop(feature: feature)
        } else {
            if(feature == PolarDeviceDataType.ppi || feature == PolarDeviceDataType.hr) {
                bleSdkManager.onlineStreamStart(feature: feature)
            } else {
                bleSdkManager.getOnlineStreamSettings(feature: feature)
            }
        }
    }
    
    private func getStreamButtonState(_ feature: PolarDeviceDataType) -> ButtonState {
        if(bleSdkManager.onlineStreamingFeature.availableOnlineDataTypes[feature] ?? false) {
            if bleSdkManager.isStreamOn(feature: feature) {
                return ButtonState.pressedDown
            } else {
                return ButtonState.released
            }
        } else {
            return ButtonState.disabled
        }
    }
}

struct OnlineStreamValues: View {
    @EnvironmentObject private var bleSdkManager: PolarBleSdkManager
    var body: some View {
        
        VStack(alignment: .leading, spacing: 6) {
            ForEach(bleSdkManager.onlineRecordingDataTypes) { (dataType: PolarDeviceDataType) in
                    
                switch dataType {
                case .ecg:
                    VStack(alignment: .leading, spacing: 6) {
                        if ((bleSdkManager.onlineStreamingFeature.isStreaming[PolarDeviceDataType.ecg]) != nil) {
                            Text("ECG values:")
                                .font(.system(size: 16)).bold()
                            Text("Voltage: \(bleSdkManager.ecgRecordingData.voltage)µV ")
                                .font(.system(size: 14))
                        }
                    }
                case .acc:
                    VStack(alignment: .leading, spacing: 6) {
                        if ((bleSdkManager.onlineStreamingFeature.isStreaming[PolarDeviceDataType.acc]) != nil) {
                            Text("Accelerometer values:")
                                .font(.system(size: 16)).bold()
                            Text("Accelerometer: x: \(bleSdkManager.accRecordingData.x), y: \(bleSdkManager.accRecordingData.y), z: \(bleSdkManager.accRecordingData.z)")
                                .font(.system(size: 14))
                        }
                    }
                case .ppg:
                    VStack(alignment: .leading, spacing: 6) {
                        if ((bleSdkManager.onlineStreamingFeature.isStreaming[PolarDeviceDataType.ppg]) != nil) {
                            Text("PPG values:")
                                .font(.system(size: 16)).bold()
                            Text("ppg0: \(bleSdkManager.ppgRecordingData.ppg0)").font(.system(size: 14))
                            Text("ppg1: \(bleSdkManager.ppgRecordingData.ppg1)").font(.system(size: 14))
                            if (bleSdkManager.ppgRecordingData.ppg2 != 0) {
                                Text("ppg2: \(bleSdkManager.ppgRecordingData.ppg2)").font(.system(size: 14))
                            }
                            
                            if (bleSdkManager.ppgRecordingData.ppg3 != 0) {
                                Text("ppg3: \(bleSdkManager.ppgRecordingData.ppg2)").font(.system(size: 14))
                            }
                            
                            if (bleSdkManager.ppgRecordingData.ambient != 0) {
                                Text("Ambient channel: \(bleSdkManager.ppgRecordingData.ambient)")
                                    .font(.system(size: 14))
                            }
                            
                            if (bleSdkManager.ppgRecordingData.sportId != 0) {
                                Text("Sport id: \(String(describing: bleSdkManager.ppgRecordingData.sportId))")
                                    .font(.system(size: 14))
                            }
                            
                            if (bleSdkManager.ppgRecordingData.status != nil) {
                                Text("Status: \(String(describing: bleSdkManager.ppgRecordingData.status ?? 0))")
                                    .font(.system(size: 14))
                            }
                        }
                    }
                case .ppi:
                    VStack(alignment: .leading, spacing: 6) {
                        if ((bleSdkManager.onlineStreamingFeature.isStreaming[PolarDeviceDataType.ppi]) != nil) {
                            Text("Peak-peak interval values:")
                                .font(.system(size: 16)).bold()
                            Text("pp: \(bleSdkManager.ppiRecordingData.ppInMs)ms")
                                .font(.system(size: 14))
                            Text("Blocker bit: \(bleSdkManager.ppiRecordingData.blockerBit)")
                                .font(.system(size: 14))
                            Text("PP error estimate: \(bleSdkManager.ppiRecordingData.ppErrorEstimate)")
                                .font(.system(size: 14))
                        }
                    }
                case .gyro:
                    VStack(alignment: .leading, spacing: 6) {
                        if ((bleSdkManager.onlineStreamingFeature.isStreaming[PolarDeviceDataType.gyro]) != nil) {
                            Text("Gyroscope values:")
                                .font(.system(size: 16)).bold()
                            Text("Gyroscope: x: \(String(format: "%.2f", bleSdkManager.gyroRecordingData.x)), y: \(String(format: "%.2f", bleSdkManager.gyroRecordingData.y)), z: \(String(format: "%.2f", bleSdkManager.gyroRecordingData.z))")
                                .font(.system(size: 14))
                        }
                    }
                case .magnetometer:
                    VStack(alignment: .leading, spacing: 6) {
                        if ((bleSdkManager.onlineStreamingFeature.isStreaming[PolarDeviceDataType.magnetometer]) != nil) {
                            Text("Magnetometer values:")
                                .font(.system(size: 16)).bold()
                            Text("Magnetometer: x: \(String(format: "%.2f", bleSdkManager.magnetometerRecordingData.x)), y: \(String(format: "%.2f", bleSdkManager.magnetometerRecordingData.y)), z: \(String(format: "%.2f", bleSdkManager.magnetometerRecordingData.z))")
                                .font(.system(size: 14))
                        }
                    }
                case .hr:
                    VStack(alignment: .leading, spacing: 6) {
                        if ((bleSdkManager.onlineStreamingFeature.isStreaming[PolarDeviceDataType.hr]) != nil) {
                            Text("Heart rate values:")
                                .font(.system(size: 16)).bold()
                            Text("Heart rate: \(bleSdkManager.hrRecordingData.hr)")
                                .font(.system(size: 14))
                            Text("RR available: \(bleSdkManager.hrRecordingData.rrAvailable)")
                                .font(.system(size: 14))
                            Text("RRs: \(bleSdkManager.hrRecordingData.rrs)")
                                .font(.system(size: 14))
                            Text("Contact status supported: \(bleSdkManager.hrRecordingData.contactStatusSupported)")
                                .font(.system(size: 14))
                            Text("Contact status: \(bleSdkManager.hrRecordingData.contactStatus)")
                                .font(.system(size: 14))
                        }
                    }
                case .temperature:
                    VStack(alignment: .leading, spacing: 6) {
                        if ((bleSdkManager.onlineStreamingFeature.isStreaming[PolarDeviceDataType.temperature]) != nil) {
                            Text("Temperature values:")
                                .font(.system(size: 16)).bold()
                            Text("Temperature \(String(format: "%.2f", bleSdkManager.temperatureRecordingData.temperature))")
                                .font(.system(size: 14))
                        }
                    }
                case .pressure:
                    VStack(alignment: .leading, spacing: 6) {
                        if ((bleSdkManager.onlineStreamingFeature.isStreaming[PolarDeviceDataType.pressure]) != nil) {
                            Text("Pressure values:")
                                .font(.system(size: 16)).bold()
                            Text("Pressure \(String(format: "%.2f", bleSdkManager.pressureRecordingData.pressure))")
                                .font(.system(size: 14))
                        }
                    }
                case .skinTemperature:
                    VStack(alignment: .leading, spacing: 6) {
                        if ((bleSdkManager.onlineStreamingFeature.isStreaming[PolarDeviceDataType.skinTemperature]) != nil) {
                            Text("Skin temperature values:")
                                .font(.system(size: 16)).bold()
                            Text("Skin temperature \(String(format: "%.2f", bleSdkManager.skinTemperatureRecordingData.temperature))")
                                .font(.system(size: 14))
                        }
                    }
                }
            }
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
