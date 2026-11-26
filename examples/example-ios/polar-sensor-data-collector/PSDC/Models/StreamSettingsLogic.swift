/// Copyright © 2026 Polar Electro Oy. All rights reserved.

import Foundation
import PolarBleSdk

/// Pure logic behind the stream settings dialog.
enum StreamSettingsLogic {
    /// Picker indices into each setting's sorted values.
    struct Indices: Equatable {
        var sampleRate = 0
        var range = 0
        var resolution = 0
        var channels = 0
    }

    /// Builds the chosen settings. An index outside the option list is ignored instead of crashing.
    static func chosen(from available: RecordingSettings, feature: PolarDeviceDataType, indices: Indices) -> RecordingSettings {
        let pairs: [(PolarSensorSetting.SettingType, Int)] = [
            (.sampleRate, indices.sampleRate), (.range, indices.range),
            (.resolution, indices.resolution), (.channels, indices.channels),
        ]
        var values: [TypeSetting] = []
        for (type, index) in pairs {
            guard let options = available.sortedSettings.first(where: { $0.type == type })?.sortedValues,
                  options.indices.contains(index) else { continue }
            values.append(TypeSetting(type: type, values: [options[index]]))
        }
        return RecordingSettings(feature: feature, settings: values)
    }

    /// Indices of previously saved values within the currently available options.
    static func restoredIndices(saved: RecordingSettings, available: RecordingSettings) -> Indices {
        func index(_ type: PolarSensorSetting.SettingType) -> Int? {
            guard let value = saved.settings.first(where: { $0.type == type })?.values.first,
                  let options = available.sortedSettings.first(where: { $0.type == type }) else { return nil }
            return options.sortedValues.firstIndex(of: value)
        }
        var result = Indices()
        if let i = index(.sampleRate) { result.sampleRate = i }
        if let i = index(.range) { result.range = i }
        if let i = index(.resolution) { result.resolution = i }
        if let i = index(.channels) { result.channels = i }
        return result
    }

    // MARK: Derived data

    static func derivedSourceRates(_ group: PolarDerivedMeasurementSettingsGroup?) -> [Int] {
        guard let rates = group?.sourceSampleRates else { return [] }
        return rates.contains(50) ? [50] : rates.sorted()
    }

    static func derivedTimeWindows(_ group: PolarDerivedMeasurementSettingsGroup?) -> [Int] {
        group?.timeWindowOptions.sorted() ?? []
    }

    /// The last remaining method can not be deselected.
    static func toggled(
        _ method: PolarDerivedMeasurementMethod,
        in methods: Set<PolarDerivedMeasurementMethod>
    ) -> Set<PolarDerivedMeasurementMethod> {
        var result = methods
        if result.contains(method) {
            if result.count > 1 { result.remove(method) }
        } else {
            result.insert(method)
        }
        return result
    }

    static func derivedSettings(
        group: PolarDerivedMeasurementSettingsGroup,
        sourceRateIndex: Int,
        timeWindowIndex: Int,
        methods: Set<PolarDerivedMeasurementMethod>
    ) -> PolarDerivedMeasurementSettings? {
        let rates = derivedSourceRates(group)
        let windows = derivedTimeWindows(group)
        guard rates.indices.contains(sourceRateIndex), windows.indices.contains(timeWindowIndex),
              rates[sourceRateIndex] > 0, windows[timeWindowIndex] > 0 else { return nil }
        return PolarDerivedMeasurementSettings(
            groupId: group.groupId,
            sourceMeasurementType: .acc,
            sourceSampleRate: rates[sourceRateIndex],
            timeWindowMs: windows[timeWindowIndex],
            selectedMethods: methods.isEmpty ? [.downsample] : methods)
    }

    struct RestoredDerived: Equatable {
        var enabled = false
        var sourceRateIndex = 0
        var timeWindowIndex = 0
        var methods: Set<PolarDerivedMeasurementMethod> = [.downsample]
    }

    /// Previously saved derived settings re-enable the section so they are kept on the next start.
    static func restoredDerived(
        saved: PolarDerivedMeasurementSettings?,
        group: PolarDerivedMeasurementSettingsGroup?
    ) -> RestoredDerived {
        var result = RestoredDerived()
        guard let saved else { return result }
        result.enabled = true
        result.methods = saved.selectedMethods
        if let i = derivedSourceRates(group).firstIndex(of: saved.sourceSampleRate) { result.sourceRateIndex = i }
        if let i = derivedTimeWindows(group).firstIndex(of: saved.timeWindowMs) { result.timeWindowIndex = i }
        return result
    }

    static func formatTimeWindow(_ ms: Int) -> String {
        let duration: String
        switch ms {
        case ..<1_000: duration = "\(ms) ms"
        case ..<60_000: duration = "\(ms / 1_000) s"
        case ..<3_600_000: duration = "\(ms / 60_000) min"
        default: duration = "\(ms / 3_600_000) h"
        }
        let output: String
        switch ms {
        case ...0: output = ""
        case ...1_000: output = " — \(1000 / ms) Hz output"
        case ..<60_000: output = " — 1 sample / \(ms / 1_000) s"
        case ..<3_600_000: output = " — 1 sample / \(ms / 60_000) min"
        default: output = " — 1 sample / \(ms / 3_600_000) h"
        }
        return "\(duration)\(output)"
    }
}
