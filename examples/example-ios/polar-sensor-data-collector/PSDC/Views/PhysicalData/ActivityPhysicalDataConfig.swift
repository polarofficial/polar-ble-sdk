import SwiftUI
import PolarBleSdk

struct PhysicalDataConfigView: View {
    @Environment(\.presentationMode) var presentationMode
    @EnvironmentObject private var bleSdkManager: PolarBleSdkManager
    
    @State private var gender: PolarFirstTimeUseConfig.Gender = .male
    @State private var height: String = "185"
    @State private var weight: String = "85"
    @State private var maxHeartRate: String = "180"
    @State private var restingHeartRate: String = "50"
    @State private var vo2Max: String = "50"
    @State private var trainingBackground: PolarFirstTimeUseConfig.TrainingBackground = .frequent
    @State private var birthDate: Date = DateComponents(calendar: Calendar.current, year: 2000, month: 1, day: 1).date ?? Date()
    @State private var typicalDay: PolarFirstTimeUseConfig.TypicalDay = .mostlyStanding
    @State private var sleepGoalMinutes: String = "480"
    @State private var loading = true

    @ObservedObject private var formValidation = FormValidation()
    
    var deviceId: String
    
    init(deviceId: String) {
        self.deviceId = deviceId
    }
    
    private let genders: [PolarFirstTimeUseConfig.Gender] = [.female, .male]
    
    private let trainingBackgroundLevels: [PolarFirstTimeUseConfig.TrainingBackground] = [
        .occasional,
        .regular,
        .frequent,
        .heavy,
        .semiPro,
        .pro
    ]
    
    private let typicalDays: [PolarFirstTimeUseConfig.TypicalDay] = [
        .mostlySitting,
        .mostlyStanding,
        .mostlyMoving
    ]
    
    var body: some View {
        Group {
            if loading {
                ProgressView("Loading…")
            } else {
                VStack {
                    Text("Device ID: \(deviceId)")
                        .font(.subheadline)
                        .padding()

            Form {
                Section(header: Text("Personal Information")) {
                    Picker("Gender", selection: $gender) {
                        ForEach(genders, id: \.self) { gender in
                            Text(gender == .male ? "Male" : "Female")
                        }
                    }
                    DatePicker("Birth Date", selection: $birthDate, displayedComponents: [.date])
                    TextField("Height (cm)", text: $height)
                        .keyboardType(.numberPad)
                        .validation((90...240).contains(Int(height) ?? 0),
                                    guide: "90...240 cm",
                                    formValidation: formValidation)
                    TextField("Weight (kg)", text: $weight)
                        .keyboardType(.numberPad)
                        .validation((15...300).contains(Int(weight) ?? 0),
                                    guide: "15...300 kg",
                                    formValidation: formValidation)
                }
                
                Section(header: Text("Heart Rates")) {
                    TextField("Max Heart Rate (bpm)", text: $maxHeartRate)
                        .keyboardType(.numberPad)
                        .validation((100...240).contains(Int(maxHeartRate) ?? 0),
                                    guide: "100...240 bpm",
                                    formValidation: formValidation)
                    TextField("Resting Heart Rate (bpm)", text: $restingHeartRate)
                        .keyboardType(.numberPad)
                        .validation((20...120).contains(Int(restingHeartRate) ?? 0),
                                    guide: "20...120 bpm",
                                    formValidation: formValidation)
                }
                
                Section(header: Text("Fitness")) {
                    TextField("VO2 Max", text: $vo2Max)
                        .keyboardType(.numberPad)
                        .validation((10...95).contains(Int(vo2Max) ?? 0),
                                    guide: "10...95",
                                    formValidation: formValidation)
                    Picker("Training Background", selection: $trainingBackground) {
                        ForEach(trainingBackgroundLevels, id: \.self) { level in
                            Text("\(level)")
                        }
                    }
                }
                
                Section(header: Text("Daily Activity")) {
                    Picker("Typical Day", selection: $typicalDay) {
                        ForEach(typicalDays, id: \.self) { day in
                            Text(day.description)
                        }
                    }
                    TextField("Sleep Goal (minutes)", text: $sleepGoalMinutes)
                        .keyboardType(.numberPad)
                        .validation((300...650).contains (Int(sleepGoalMinutes) ?? 0),
                                    guide: "300...650 min",
                                    formValidation: formValidation)
                }
            }

                    Button(action: { Task { await submitData() } }) {
                        Text("Submit")
                            .font(.headline)
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(Color.blue)
                            .foregroundColor(.white)
                            .cornerRadius(10)
                            .padding(.horizontal)
                    }
                    .disabled(!formValidation.isOK)
                    .opacity(formValidation.isOK ? 1 : 0.5)
                }
            }
        }
        .navigationBarTitle("Physical Data Config", displayMode: .inline)
        .task { await loadPhysicalInfo() }
    }

    private func loadPhysicalInfo() async {
        if let info = await bleSdkManager.getUserPhysicalConfiguration() {
            await MainActor.run {
                switch info.gender {
                    case .male: gender = .male
                    case .female: gender = .female
                    @unknown default: gender = .male
                }
                height = String(Int(info.height))
                weight = String(Int(info.weight))
                maxHeartRate = String(info.maxHeartRate)
                restingHeartRate = String(info.restingHeartRate)
                vo2Max = String(info.vo2Max)
                trainingBackground = PolarFirstTimeUseConfig.TrainingBackground(rawValue: info.trainingBackground) ?? .frequent
                birthDate = info.birthDate
                typicalDay = PolarFirstTimeUseConfig.TypicalDay(rawValue: info.typicalDay.rawValue) ?? .mostlyStanding
                sleepGoalMinutes = String(info.sleepGoalMinutes)
            }
        }
        await MainActor.run { loading = false }
    }

    private func submitData() async {
        let ftuConfig = PolarFirstTimeUseConfig(
            gender: gender,
            birthDate: birthDate,
            height: Float(height) ?? 0,
            weight: Float(weight) ?? 0,
            maxHeartRate: Int(maxHeartRate) ?? 0,
            vo2Max: Int(vo2Max) ?? 0,
            restingHeartRate: Int(restingHeartRate) ?? 0,
            trainingBackground: trainingBackground,
            deviceTime: ISO8601DateFormatter().string(from: Date()),
            typicalDay: typicalDay,
            sleepGoalMinutes: Int(sleepGoalMinutes) ?? 0
        )
        await bleSdkManager.sendPhysicalConfig(ftuConfig: ftuConfig)
        await MainActor.run { presentationMode.wrappedValue.dismiss() }
    }
}

class FormValidation: ObservableObject {
    private var shownGuides = Set<String>()
    func add (_ validation: String) {
        shownGuides.insert(validation)
        if (isOK) { isOK = false }
    }
    func remove(_ validation: String) {
        shownGuides.remove(validation)
        if shownGuides.isEmpty && isOK == false {
            isOK = true
        }
    }
    @Published var isOK: Bool = true
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
