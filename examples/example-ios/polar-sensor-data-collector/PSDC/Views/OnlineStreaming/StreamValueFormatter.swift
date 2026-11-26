/// Copyright © 2026 Polar Electro Oy. All rights reserved.

import Foundation
import PolarBleSdk

/// A titled block of live values for one data type.
struct StreamValueSummary: Equatable {
    enum Graph { case ecg, acc, hr }
    let title: String
    let lines: [String]
    let graph: Graph?
}

/// Builds the text shown for live values. Reads from the manager, so it lives with the view rather than with the pure logic.
enum StreamValueFormatter {
    @MainActor
    static func summary(for dataType: PolarDeviceDataType, manager m: PolarBleSdkManager) -> StreamValueSummary? {
        guard m.onlineStreamingFeature.isStreaming[dataType] != nil else { return nil }
        func f(_ v: Float) -> String { String(format: "%.2f", v) }

        switch dataType {
        case .ecg:
            return .init(title: "ECG values:", lines: ["Voltage: \(m.ecgRecordingData.voltage)µV "], graph: .ecg)
        case .acc:
            let d = m.accRecordingData
            return .init(title: "Accelerometer values:", lines: ["Accelerometer: x: \(d.x), y: \(d.y), z: \(d.z)"], graph: .acc)
        case .ppg:
            let d = m.ppgRecordingData
            var lines = ["ppg0: \(d.ppg0)", "ppg1: \(d.ppg1)"]
            if d.ppg2 != 0 { lines.append("ppg2: \(d.ppg2)") }
            if d.ppg3 != 0 { lines.append("ppg3: \(d.ppg3)") }
            if d.ambient != 0 { lines.append("Ambient channel: \(d.ambient)") }
            if d.sportId != 0 { lines.append("Sport id: \(d.sportId)") }
            for (index, status) in (d.status ?? []).enumerated() { lines.append("Status\(index): \(status)") }
            return .init(title: "PPG values:", lines: lines, graph: nil)
        case .ppi:
            let d = m.ppiRecordingData
            return .init(title: "Peak-peak interval values:",
                         lines: ["pp: \(d.ppInMs)ms", "Blocker bit: \(d.blockerBit)", "PP error estimate: \(d.ppErrorEstimate)"],
                         graph: nil)
        case .gyro:
            let d = m.gyroRecordingData
            return .init(title: "Gyroscope values:", lines: ["Gyroscope: x: \(f(d.x)), y: \(f(d.y)), z: \(f(d.z))"], graph: nil)
        case .magnetometer:
            let d = m.magnetometerRecordingData
            return .init(title: "Magnetometer values:", lines: ["Magnetometer: x: \(f(d.x)), y: \(f(d.y)), z: \(f(d.z))"], graph: nil)
        case .hr:
            let d = m.hrRecordingData
            return .init(title: "Heart rate values:", lines: [
                "Heart rate: \(d.hr)",
                "RR available: \(d.rrAvailable)",
                "RRs (ms): \(d.rrs.map(String.init).joined(separator: ", "))",
                "Contact status supported: \(d.contactStatusSupported)",
                "Contact status: \(d.contactStatus)",
            ], graph: .hr)
        case .temperature:
            return .init(title: "Temperature values:", lines: ["Temperature \(f(m.temperatureRecordingData.temperature))"], graph: nil)
        case .pressure:
            return .init(title: "Pressure values:", lines: ["Pressure \(f(m.pressureRecordingData.pressure))"], graph: nil)
        case .skinTemperature:
            return .init(title: "Skin temperature values:", lines: ["Skin temperature \(f(m.skinTemperatureRecordingData.temperature))"], graph: nil)
        }
    }
}
