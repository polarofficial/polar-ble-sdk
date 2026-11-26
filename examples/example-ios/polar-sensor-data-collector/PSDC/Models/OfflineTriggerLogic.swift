/// Copyright © 2026 Polar Electro Oy. All rights reserved.

import Foundation
import PolarBleSdk

/// Pure logic behind the offline recording trigger screens, kept free of SwiftUI state so it can be unit tested.
enum OfflineTriggerLogic {

    // MARK: Trigger mode

    static func modeText(_ mode: PolarOfflineRecordingTriggerMode) -> String {
        switch mode {
        case .triggerDisabled: return "DISABLED"
        case .triggerSystemStart: return "SYSTEM START"
        case .triggerExerciseStart: return "EXERCISE START"
        }
    }

    static func mode(fromText text: String) -> PolarOfflineRecordingTriggerMode? {
        switch text {
        case "DISABLED": return .triggerDisabled
        case "SYSTEM START": return .triggerSystemStart
        case "EXERCISE START": return .triggerExerciseStart
        default: return nil
        }
    }

    // MARK: Settings

    /// Builds the selected values and available options for one data type. Applied trigger settings override defaults.
    static func settings(
        for dataType: PolarDeviceDataType,
        available: RecordingSettings?,
        applied: PolarSensorSetting?
    ) -> (selected: SelectedSettingsForType, options: SettingsOptions) {
        func options(_ type: PolarSensorSetting.SettingType) -> [UInt32] {
            guard let setting = available?.settings.first(where: { $0.type == type }) else { return [] }
            return setting.sortedValues.compactMap { UInt32(exactly: $0) }
        }
        let sampleRates = options(.sampleRate)
        let resolutions = options(.resolution)
        let ranges = options(.range)
        let channels = options(.channels)

        var sampleRate = sampleRates.first ?? 0
        var resolution = resolutions.first ?? 0
        var range = ranges.first ?? 0
        var channel = channels.first ?? 0

        if let applied = applied {
            if let v = applied.settings[.sampleRate]?.first { sampleRate = v }
            if let v = applied.settings[.resolution]?.first { resolution = v }
            if let v = applied.settings[.range]?.first { range = v }
            if let v = applied.settings[.channels]?.first { channel = v }
        }

        let selected = SelectedSettingsForType(
            selectedSampleRate: sampleRate > 0 ? sampleRate : nil,
            selectedResolution: resolution > 0 ? resolution : nil,
            selectedRange: range > 0 ? range : nil,
            selectedChannels: channel > 0 ? channel : nil,
            selectedDataType: dataType
        )
        let available = SettingsOptions(
            sampleRateOptions: sampleRates,
            resolutionOptions: resolutions,
            rangeOptions: ranges,
            channelOptions: channels,
            selectedDataType: dataType
        )
        return (selected, available)
    }

    static func hasOptions(for dataType: PolarDeviceDataType, in options: [SettingsOptions]?) -> Bool {
        guard let entry = options?.first(where: { $0.selectedDataType == dataType }) else { return false }
        return !entry.sampleRateOptions.isEmpty
            || !entry.resolutionOptions.isEmpty
            || !entry.rangeOptions.isEmpty
            || !entry.channelOptions.isEmpty
    }

    /// Replaces the stored settings of `selection.selectedDataType`, or appends them when absent.
    static func upserting(_ selection: SelectedSettingsForType, into list: [SelectedSettingsForType]?) -> [SelectedSettingsForType] {
        var result = list ?? []
        if let index = result.firstIndex(where: { $0.selectedDataType == selection.selectedDataType }) {
            result[index] = selection
        } else {
            result.append(selection)
        }
        return result
    }

    static func byDataType(_ list: [SelectedSettingsForType]?) -> [PolarDeviceDataType: SelectedSettingsForType] {
        var result: [PolarDeviceDataType: SelectedSettingsForType] = [:]
        for item in list ?? [] {
            if let type = item.selectedDataType { result[type] = item }
        }
        return result
    }

    static func enabledTriggers(from selections: [DatatypeSelection]) -> [String: Bool] {
        var result: [String: Bool] = [:]
        for item in selections { result[getShortNameForDataType(item.datatype)] = item.isSelected }
        return result
    }

    // MARK: Unsaved edits

    static func isSame(_ lhs: SelectedSettingsForType?, _ rhs: SelectedSettingsForType?) -> Bool {
        guard let lhs = lhs, let rhs = rhs else { return lhs == nil && rhs == nil }
        return lhs.selectedSampleRate == rhs.selectedSampleRate
            && lhs.selectedResolution == rhs.selectedResolution
            && lhs.selectedRange == rhs.selectedRange
            && lhs.selectedChannels == rhs.selectedChannels
    }

    static func hasUnsavedChanges(
        triggerMode: String,
        fetchedTriggerMode: String,
        selections: [DatatypeSelection],
        fetchedEnabled: Set<PolarDeviceDataType>,
        selectedSettings: [SelectedSettingsForType]?,
        fetchedSettings: [PolarDeviceDataType: SelectedSettingsForType]
    ) -> Bool {
        if triggerMode != fetchedTriggerMode { return true }
        let currentEnabled = Set(selections.filter { $0.isSelected }.map { $0.datatype })
        if currentEnabled != fetchedEnabled { return true }
        let current = byDataType(selectedSettings)
        return currentEnabled.contains { !isSame(current[$0], fetchedSettings[$0]) }
    }

    /// State to restore when the user discards trigger edits.
    static func resetToFetched(
        selections: [DatatypeSelection],
        fetchedEnabled: Set<PolarDeviceDataType>,
        fetchedSettings: [PolarDeviceDataType: SelectedSettingsForType],
        current: [SelectedSettingsForType]?
    ) -> (selections: [DatatypeSelection], enabledTriggers: [String: Bool], settings: [SelectedSettingsForType]) {
        let updated = selections.map { DatatypeSelection(datatype: $0.datatype, isSelected: fetchedEnabled.contains($0.datatype)) }
        let settings: [SelectedSettingsForType] = updated.map { item in
            if let fetched = fetchedSettings[item.datatype] { return fetched }
            if let existing = (current ?? []).first(where: { $0.selectedDataType == item.datatype }) { return existing }
            return SelectedSettingsForType(
                selectedSampleRate: nil, selectedResolution: nil, selectedRange: nil,
                selectedChannels: nil, selectedDataType: item.datatype)
        }
        return (updated, enabledTriggers(from: updated), settings)
    }

    // MARK: Status tab

    /// Recordable data types sorted by name, flagged when the device has a trigger configured for them.
    static func statusSelections(
        recordable: [PolarDeviceDataType: Bool],
        triggerFeatures: [PolarDeviceDataType: PolarSensorSetting?]
    ) -> (settings: [PolarDeviceDataType: PolarSensorSetting], selections: [DatatypeSelection]) {
        var settings: [PolarDeviceDataType: PolarSensorSetting] = [:]
        for (type, setting) in triggerFeatures {
            if let setting = setting { settings[type] = setting }
        }
        let selections = recordable.keys
            .map { DatatypeSelection(datatype: $0, isSelected: triggerFeatures.keys.contains($0)) }
            .sorted { $0.datatype.displayName < $1.datatype.displayName }
        return (settings, selections)
    }

    // MARK: Applying

    /// Disabling sends an empty feature map so the device clears every trigger. Returns nil for an unknown mode text.
    static func makeTrigger(
        modeText: String,
        selections: [DatatypeSelection],
        selectedSettings: [SelectedSettingsForType]?
    ) -> PolarOfflineRecordingTrigger? {
        guard let mode = mode(fromText: modeText) else { return nil }
        var features: [PolarDeviceDataType: PolarSensorSetting?] = [:]
        if mode != .triggerDisabled {
            for dataType in selections.filter({ $0.isSelected }).map({ $0.datatype }) {
                var values: [PolarSensorSetting.SettingType: UInt32] = [:]
                if let current = selectedSettings?.first(where: { $0.selectedDataType == dataType }) {
                    values[.range] = current.selectedRange
                    values[.channels] = current.selectedChannels
                    values[.sampleRate] = current.selectedSampleRate
                    values[.resolution] = current.selectedResolution
                }
                do {
                    features[dataType] = try PolarSensorSetting(values)
                } catch {
                    BleLogger.trace("Skipping invalid trigger settings for \(dataType): \(error)")
                }
            }
        }
        return PolarOfflineRecordingTrigger(triggerMode: mode, triggerFeatures: features)
    }

    /// Merges the device's trigger setup into the local edit state.
    static func synced(
        features: [PolarDeviceDataType: PolarSensorSetting?],
        selections: [DatatypeSelection],
        selectedSettings: [SelectedSettingsForType]?
    ) -> (selections: [DatatypeSelection], enabledTriggers: [String: Bool], settings: [SelectedSettingsForType]) {
        let enabled = Set(features.keys)
        var updated = selections
        for index in updated.indices { updated[index].isSelected = enabled.contains(updated[index].datatype) }

        var settings = selectedSettings ?? []
        for (dataType, maybeSetting) in features {
            guard let setting = maybeSetting else { continue }
            settings = upserting(SelectedSettingsForType(
                selectedSampleRate: setting.settings[.sampleRate]?.first,
                selectedResolution: setting.settings[.resolution]?.first,
                selectedRange: setting.settings[.range]?.first,
                selectedChannels: setting.settings[.channels]?.first,
                selectedDataType: dataType), into: settings)
        }
        return (updated, enabledTriggers(from: updated), settings)
    }

    // MARK: Start/stop buttons

    static func recordButtonText(_ feature: PolarDeviceDataType, isRecording: Bool?) -> String {
        let name = getShortNameForDataType(feature)
        return isRecording == true ? "Stop \(name) Recording" : "Start \(name) Recording"
    }

    static func buttonState(isRecording: Bool?) -> ButtonState {
        guard let recording = isRecording else { return .disabled }
        return recording ? .pressedDown : .released
    }

    /// PPI and HR have no settings to pick, so they start immediately; everything else asks for settings first.
    static func startsWithoutSettings(_ feature: PolarDeviceDataType) -> Bool {
        feature == .ppi || feature == .hr
    }
}
