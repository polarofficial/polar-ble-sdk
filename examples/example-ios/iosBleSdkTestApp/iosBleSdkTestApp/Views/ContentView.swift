/// Copyright © 2019 Polar Electro Oy. All rights reserved.

import SwiftUI

struct ContentView: View {
    @ObservedObject var viewModel: PolarBleSdkManager
    
    var body: some View {
        VStack {
            Text("Polar BLE SDK example app")
                .bold()
            if viewModel.bluetoothPowerOn {
                ScrollView(.vertical) {
                    VStack(spacing: 20) {
                        Group {
                            if viewModel.broadcastEnabled {
                                Button("Listening broadcast", action: {viewModel.broadcastToggle()})
                            } else {
                                Button("Listen broadcast", action: {viewModel.broadcastToggle()})
                            }
                        }
                        Group {
                            switch viewModel.deviceConnectionState {
                            case .disconnected:
                                Button("Connect", action: {viewModel.connectToDevice()})
                                Button("Auto Connect", action: {viewModel.autoConnect()})
                            case .connecting(let deviceId):
                                Button("Connecting \(deviceId)", action: {})
                                    .disabled(true)
                                Button("Auto Connect", action: {})
                                    .disabled(true)
                            case .connected(let deviceId):
                                Button("Disconnect \(deviceId)", action: {viewModel.disconnectFromDevice()})
                                Button("Auto Connect", action: {})
                                    .disabled(true)
                            }
                            
                            if viewModel.seachEnabled {
                                Button("Stop device scan", action: {viewModel.searchToggle()})
                            } else {
                                Button("Scan devices", action: {viewModel.searchToggle()})
                            }
                        }
                        Group {
                            if viewModel.ecgEnabled {
                                Button("Stop ECG Stream", action: {viewModel.ecgToggle()})
                            } else {
                                Button("Start ECG Stream", action: {viewModel.ecgToggle()})
                            }
                            
                            if viewModel.accEnabled {
                                Button("Stop ACC Stream", action: {viewModel.accToggle()})
                            } else {
                                Button("Start ACC Stream", action: {viewModel.accToggle()})
                            }
                            
                            if viewModel.gyroEnabled {
                                Button("Stop GYRO Stream", action: {viewModel.gyroToggle()})
                            } else {
                                Button("Start GYRO Stream", action: {viewModel.gyroToggle()})
                            }
                            
                            if viewModel.magnetometerEnabled {
                                Button("Stop MAGNETOMETER Stream", action: {viewModel.magnetometerToggle()})
                            } else {
                                Button("Start MAGNETOMETER Stream", action: {viewModel.magnetometerToggle()})
                            }
                            
                            if viewModel.ppgEnabled {
                                Button("Stop PPG Stream", action: { viewModel.ppgToggle()})
                            } else {
                                Button("Start PPG Stream", action: {viewModel.ppgToggle()})
                            }
                            
                            if viewModel.ppiEnabled {
                                Button("Stop PPI Stream", action: {viewModel.ppiToggle()})
                            } else {
                                Button("Start PPI Stream", action: {viewModel.ppiToggle()})
                            }
                            
                            if viewModel.sdkModeEnabled {
                                Button("Disable SDK mode", action: { viewModel.sdkModeDisable()})
                            } else {
                                Button("Enable SDK mode", action: { viewModel.sdkModeEnable()})
                            }
                        }
                        
                        Group {
                            Button("List exercises", action: { viewModel.listExercises()})
                            Button("Read exercise", action: {viewModel.readExercise()})
                            Button("Remove exercise", action: { viewModel.removeExercise()})
                        }
                        
                        Group {
                            Button("Start H10 recording", action: { viewModel.startH10Recording()})
                            Button("Stop H10 recording", action: {viewModel.stopH10Recording()})
                            Button("H10 recording status", action: { viewModel.getH10RecordingStatus()})
                        }
                        
                        Group {
                            Button("Set time", action: { viewModel.setTime()})
                        }
                    }.frame(maxWidth: .infinity)
                }
            } else {
                Text("Bluetooth OFF")
                    .bold()
                    .foregroundColor(.red)
                Spacer()
            }
        }.alert(item: $viewModel.error) { message in
            Alert(
                title: Text(message.text),
                dismissButton: .cancel()
            )
        }
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ForEach(["iPhone 8", "iPAD Pro (12.9-inch)"], id: \.self) { deviceName in
            ContentView(viewModel: PolarBleSdkManager())
                .previewDevice(PreviewDevice(rawValue: deviceName))
                .previewDisplayName(deviceName)
        }
        
    }
}
