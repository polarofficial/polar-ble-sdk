import SwiftUI
import PolarBleSdk

struct UserDeviceSettingsView: View {
    @Environment(\.presentationMode) var presentationMode
    @EnvironmentObject private var bleSdkManager: PolarBleSdkManager

    @ObservedObject private var formValidation = FormValidation()

    @State private var selectedUserDeviceLocation: String = String(describing: PolarUserDeviceSettings.DeviceLocation.UNDEFINED)
    @State private var isUSBConnectionEnabled: Bool = false
    @State private var isAutomaticTrainingDetectionEnabled: Bool = false
    @State private var automaticTrainingDetectionSensitity: String = "" //UInt32 = 0
    @State private var automaticTrainingDetectionMinimumDuration: String = ""//UInt32 = 0
    @State private var settings = PolarUserDeviceSettingsData()

    var deviceId: String

    init(deviceId: String) {
        self.deviceId = deviceId
    }

    var body: some View {
        VStack {
            Text("User Device Settings")
                .font(.subheadline)
                .padding()

            Form {
                Section(header: Text("Device location settings")) {
                    HStack {
                        Text("User device location")
                        Menu(String(describing: selectedUserDeviceLocation)) {
                            ForEach(PolarUserDeviceSettings.getAllAsString(), id: \.self) { item in
                                Button(item, action: {
                                    self.selectedUserDeviceLocation = PolarUserDeviceSettings.DeviceLocation(rawValue: item)!.rawValue
                                    self.settings.polarUserDeviceSettings.deviceLocation = PolarUserDeviceSettings.getDeviceLocation(deviceLocation: item)
                                })
                            }
                        }
                    }
                }.task {
                    let settings = await bleSdkManager.getUserDeviceSettings()
                    selectedUserDeviceLocation = (settings?.deviceLocation.rawValue)!
                    isUSBConnectionEnabled = settings?.usbConnectionMode ==  PolarUserDeviceSettings.UsbConnectionMode.ON ? true : false
                    isAutomaticTrainingDetectionEnabled = settings?.automaticTrainingDetectionMode ==  PolarUserDeviceSettings.AutomaticTrainingDetectionMode.ON ? true : false
                    automaticTrainingDetectionMinimumDuration = String(describing: settings?.minimumTrainingDurationSeconds ?? 0)
                    automaticTrainingDetectionSensitity = String(describing: settings?.automaticTrainingDetectionSensitivity ?? 0)
                }

                Section(header: Text("USB settings")) {
                    Toggle("Enable USB connection", isOn: $isUSBConnectionEnabled)
                        .toggleStyle(SwitchToggleStyle(tint: .blue))
                }

                Section(header: Text("Automatic training detection settings (ATD)")) {
                    Toggle("Enable automatic training detection", isOn: $isAutomaticTrainingDetectionEnabled)
                        .toggleStyle(SwitchToggleStyle(tint: .blue))

                    HStack {
                        Text("ATD sensitivity")
                            .font(.subheadline)
                            .padding()
                        TextField("ATD sensitivity)", text: $automaticTrainingDetectionSensitity)
                            .keyboardType(.numberPad)
                            .validation((0...100).contains (Int(automaticTrainingDetectionSensitity) ?? 0),
                                        guide: "0...100",
                                        formValidation: formValidation)
                    }

                    HStack {
                        Text("ATD minimum duration (seconds)")
                            .font(.subheadline)
                            .padding()
                    
                    TextField("ATD minimum duration (seconds)", text: $automaticTrainingDetectionMinimumDuration)
                        .keyboardType(.numberPad)
                    }
                }
            }

            Button(action: {
                Task {
                    await submitData()
                }
            }) {
                Text("Submit")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color.blue)
                    .foregroundColor(.white)
                    .cornerRadius(10)
                    .padding(.horizontal)
            }
            .disabled(formValidation.isOK == false)
            .opacity(formValidation.isOK ? 1 : 0.5)

            Button(action: {
                Task {
                    await cancel()
                }
            }) {
                Text("Cancel")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color.red)
                    .foregroundColor(.white)
                    .cornerRadius(10)
                    .padding(.horizontal)
            }
            .disabled(formValidation.isOK == false)
            .opacity(formValidation.isOK ? 1 : 0.5)

        }.navigationBarTitle("User device settings", displayMode: .inline)
    }

    func submitData() async {
        
        await bleSdkManager.setUserDeviceLocation(location: PolarUserDeviceSettings.DeviceLocation(
            rawValue: selectedUserDeviceLocation)?.toInt() ?? 0)

        await bleSdkManager.setUsbConnectionMode(enabled: isUSBConnectionEnabled)

        await bleSdkManager.setAutomaticTrainingDetectionSettings(
            mode: isAutomaticTrainingDetectionEnabled,
            sensitivity: Int(automaticTrainingDetectionSensitity) ?? 0,
            minimumDuration: Int(automaticTrainingDetectionMinimumDuration) ?? 0
        )

        await MainActor.run {
            presentationMode.wrappedValue.dismiss()
        }
    }

    func cancel() async {
        await MainActor.run {
            presentationMode.wrappedValue.dismiss()
        }
    }
}

fileprivate extension View {
    func validation(_ valid: Bool, guide: String, formValidation: FormValidation) -> some View {
        Task { @MainActor in
            if !valid {
                formValidation.add(guide)
            } else {
                formValidation.remove(guide)
            }
        }
        return ZStack {
            self
                .frame(maxWidth: .infinity)
            Text(valid ? "✔" : guide)
                .font(.footnote)
                .opacity(valid ? 1.0 : 0.25)
                .frame(maxWidth: .infinity, alignment: .trailing)
        }
        .frame(maxWidth: .infinity)
    }
}
