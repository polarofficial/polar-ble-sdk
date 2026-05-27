// Copyright © 2026 Polar. All rights reserved.

import XCTest
@testable import PolarBleSdk

final class PolarDataUtilsTest: XCTestCase {

    // MARK: - mapToPmdClientMeasurementType

    func testMapToPmdClientMeasurementType_ecg() {
        XCTAssertEqual(.ecg, PolarDataUtils.mapToPmdClientMeasurementType(from: .ecg))
    }

    func testMapToPmdClientMeasurementType_acc() {
        XCTAssertEqual(.acc, PolarDataUtils.mapToPmdClientMeasurementType(from: .acc))
    }

    func testMapToPmdClientMeasurementType_ppg() {
        XCTAssertEqual(.ppg, PolarDataUtils.mapToPmdClientMeasurementType(from: .ppg))
    }

    func testMapToPmdClientMeasurementType_ppi() {
        XCTAssertEqual(.ppi, PolarDataUtils.mapToPmdClientMeasurementType(from: .ppi))
    }

    func testMapToPmdClientMeasurementType_gyro() {
        XCTAssertEqual(.gyro, PolarDataUtils.mapToPmdClientMeasurementType(from: .gyro))
    }

    func testMapToPmdClientMeasurementType_magnetometer() {
        XCTAssertEqual(.mgn, PolarDataUtils.mapToPmdClientMeasurementType(from: .magnetometer))
    }

    func testMapToPmdClientMeasurementType_hr() {
        XCTAssertEqual(.offline_hr, PolarDataUtils.mapToPmdClientMeasurementType(from: .hr))
    }

    func testMapToPmdClientMeasurementType_temperature() {
        XCTAssertEqual(.temperature, PolarDataUtils.mapToPmdClientMeasurementType(from: .temperature))
    }

    func testMapToPmdClientMeasurementType_skinTemperature() {
        XCTAssertEqual(.skinTemperature, PolarDataUtils.mapToPmdClientMeasurementType(from: .skinTemperature))
    }

    func testMapToPmdClientMeasurementType_pressure() {
        XCTAssertEqual(.pressure, PolarDataUtils.mapToPmdClientMeasurementType(from: .pressure))
    }

    // MARK: - mapToPolarFeature – all supported types

    func testMapToPolarFeature_ecg() throws {
        XCTAssertEqual(.ecg, try PolarDataUtils.mapToPolarFeature(from: .ecg))
    }

    func testMapToPolarFeature_acc() throws {
        XCTAssertEqual(.acc, try PolarDataUtils.mapToPolarFeature(from: .acc))
    }

    func testMapToPolarFeature_ppg() throws {
        XCTAssertEqual(.ppg, try PolarDataUtils.mapToPolarFeature(from: .ppg))
    }

    func testMapToPolarFeature_ppi() throws {
        XCTAssertEqual(.ppi, try PolarDataUtils.mapToPolarFeature(from: .ppi))
    }

    func testMapToPolarFeature_gyro() throws {
        XCTAssertEqual(.gyro, try PolarDataUtils.mapToPolarFeature(from: .gyro))
    }

    func testMapToPolarFeature_mgn() throws {
        XCTAssertEqual(.magnetometer, try PolarDataUtils.mapToPolarFeature(from: .mgn))
    }

    func testMapToPolarFeature_offline_hr() throws {
        XCTAssertEqual(.hr, try PolarDataUtils.mapToPolarFeature(from: .offline_hr))
    }

    func testMapToPolarFeature_temperature() throws {
        XCTAssertEqual(.temperature, try PolarDataUtils.mapToPolarFeature(from: .temperature))
    }

    func testMapToPolarFeature_pressure() throws {
        XCTAssertEqual(.pressure, try PolarDataUtils.mapToPolarFeature(from: .pressure))
    }

    func testMapToPolarFeature_skinTemperature() throws {
        XCTAssertEqual(.skinTemperature, try PolarDataUtils.mapToPolarFeature(from: .skinTemperature))
    }

    // MARK: - mapToPolarFeature – unsupported types throw

    func testMapToPolarFeature_sdkMode_throws() {
        XCTAssertThrowsError(try PolarDataUtils.mapToPolarFeature(from: .sdkMode)) { error in
            guard case PolarErrors.polarBleSdkInternalException = error else {
                return XCTFail("Expected polarBleSdkInternalException, got \(error)")
            }
        }
    }

    func testMapToPolarFeature_location_throws() {
        XCTAssertThrowsError(try PolarDataUtils.mapToPolarFeature(from: .location)) { error in
            guard case PolarErrors.polarBleSdkInternalException = error else {
                return XCTFail("Expected polarBleSdkInternalException, got \(error)")
            }
        }
    }

    // MARK: - mapToPmdClientMeasurementType / mapToPolarFeature round-trip

    func testRoundTrip_polarToPmdToPolar_allBidirectionalTypes() throws {
        let bidirectional: [PolarDeviceDataType] = [
            .ecg, .acc, .ppg, .ppi, .gyro, .magnetometer, .hr,
            .temperature, .pressure, .skinTemperature
        ]
        for original in bidirectional {
            let pmd = PolarDataUtils.mapToPmdClientMeasurementType(from: original)
            let back = try PolarDataUtils.mapToPolarFeature(from: pmd)
            XCTAssertEqual(original, back, "Round-trip failed for \(original)")
        }
    }

    // MARK: - mapToPmdSecret

    func testMapToPmdSecret_validKey_returnsAes128Secret() throws {
        let key = Data(repeating: 0xAB, count: 16)
        let polarSecret = try PolarRecordingSecret(key: key)

        let pmdSecret = try PolarDataUtils.mapToPmdSecret(from: polarSecret)

        XCTAssertEqual(key, pmdSecret.key)
        XCTAssertEqual(PmdSecret.SecurityStrategy.aes128, pmdSecret.strategy)
    }

    func testMapToPmdSecret_keyIsPreservedVerbatim() throws {
        let key = Data([0x00,0x01,0x02,0x03,0x04,0x05,0x06,0x07,
                        0x08,0x09,0x0A,0x0B,0x0C,0x0D,0x0E,0x0F])
        let polarSecret = try PolarRecordingSecret(key: key)

        let pmdSecret = try PolarDataUtils.mapToPmdSecret(from: polarSecret)

        XCTAssertEqual(key, pmdSecret.key)
    }

    // MARK: - mapToPmdOfflineTriggerMode (via mapToPmdOfflineTrigger)

    func testMapToPmdOfflineTrigger_triggerDisabled() throws {
        let trigger = PolarOfflineRecordingTrigger(
            triggerMode: .triggerDisabled, triggerFeatures: [:]
        )
        let pmdTrigger = try PolarDataUtils.mapToPmdOfflineTrigger(from: trigger)
        XCTAssertEqual(.disabled, pmdTrigger.triggerMode)
    }

    func testMapToPmdOfflineTrigger_triggerSystemStart() throws {
        let trigger = PolarOfflineRecordingTrigger(
            triggerMode: .triggerSystemStart, triggerFeatures: [:]
        )
        let pmdTrigger = try PolarDataUtils.mapToPmdOfflineTrigger(from: trigger)
        XCTAssertEqual(.systemStart, pmdTrigger.triggerMode)
    }

    func testMapToPmdOfflineTrigger_triggerExerciseStart() throws {
        let trigger = PolarOfflineRecordingTrigger(
            triggerMode: .triggerExerciseStart, triggerFeatures: [:]
        )
        let pmdTrigger = try PolarDataUtils.mapToPmdOfflineTrigger(from: trigger)
        XCTAssertEqual(.exerciseStart, pmdTrigger.triggerMode)
    }

    func testMapToPmdOfflineTrigger_featuresAreMapped() throws {
        let trigger = PolarOfflineRecordingTrigger(
            triggerMode: .triggerSystemStart,
            triggerFeatures: [.acc: nil, .ppg: nil]
        )
        let pmdTrigger = try PolarDataUtils.mapToPmdOfflineTrigger(from: trigger)
        XCTAssertTrue(pmdTrigger.triggers.keys.contains(.acc))
        XCTAssertTrue(pmdTrigger.triggers.keys.contains(.ppg))
        XCTAssertEqual(2, pmdTrigger.triggers.count)
    }

    func testMapToPmdOfflineTrigger_allFeaturesMarkedEnabled() throws {
        let trigger = PolarOfflineRecordingTrigger(
            triggerMode: .triggerSystemStart,
            triggerFeatures: [.ecg: nil, .gyro: nil]
        )
        let pmdTrigger = try PolarDataUtils.mapToPmdOfflineTrigger(from: trigger)
        for (_, value) in pmdTrigger.triggers {
            XCTAssertEqual(.enabled, value.status)
        }
    }

    func testMapToPmdOfflineTrigger_emptyFeatures_producesEmptyTriggers() throws {
        let trigger = PolarOfflineRecordingTrigger(
            triggerMode: .triggerDisabled, triggerFeatures: [:]
        )
        let pmdTrigger = try PolarDataUtils.mapToPmdOfflineTrigger(from: trigger)
        XCTAssertTrue(pmdTrigger.triggers.isEmpty)
    }

    // MARK: - mapToPolarOfflineTrigger (pmd → polar)

    func testMapToPolarOfflineTrigger_disabled() throws {
        let pmd = PmdOfflineTrigger(triggerMode: .disabled, triggers: [:])
        let polar = try PolarDataUtils.mapToPolarOfflineTrigger(from: pmd)
        XCTAssertEqual(.triggerDisabled, polar.triggerMode)
    }

    func testMapToPolarOfflineTrigger_systemStart() throws {
        let pmd = PmdOfflineTrigger(triggerMode: .systemStart, triggers: [:])
        let polar = try PolarDataUtils.mapToPolarOfflineTrigger(from: pmd)
        XCTAssertEqual(.triggerSystemStart, polar.triggerMode)
    }

    func testMapToPolarOfflineTrigger_exerciseStart() throws {
        let pmd = PmdOfflineTrigger(triggerMode: .exerciseStart, triggers: [:])
        let polar = try PolarDataUtils.mapToPolarOfflineTrigger(from: pmd)
        XCTAssertEqual(.triggerExerciseStart, polar.triggerMode)
    }

    func testMapToPolarOfflineTrigger_enabledTriggerIsMapped() throws {
        let triggers: [PmdMeasurementType: (status: PmdOfflineRecTriggerStatus, setting: PmdSetting?)] = [
            .acc: (.enabled, nil),
            .gyro: (.enabled, nil)
        ]
        let pmd = PmdOfflineTrigger(triggerMode: .systemStart, triggers: triggers)
        let polar = try PolarDataUtils.mapToPolarOfflineTrigger(from: pmd)
        XCTAssertTrue(polar.triggerFeatures.keys.contains(.acc))
        XCTAssertTrue(polar.triggerFeatures.keys.contains(.gyro))
    }

    func testMapToPolarOfflineTrigger_disabledTriggerIsNotIncluded() throws {
        let triggers: [PmdMeasurementType: (status: PmdOfflineRecTriggerStatus, setting: PmdSetting?)] = [
            .acc: (.enabled, nil),
            .gyro: (.disabled, nil)  // disabled → must not appear in result
        ]
        let pmd = PmdOfflineTrigger(triggerMode: .systemStart, triggers: triggers)
        let polar = try PolarDataUtils.mapToPolarOfflineTrigger(from: pmd)
        XCTAssertTrue(polar.triggerFeatures.keys.contains(.acc))
        XCTAssertFalse(polar.triggerFeatures.keys.contains(.gyro))
    }

    func testMapToPolarOfflineTrigger_unsupportedPmdType_throws() {
        let triggers: [PmdMeasurementType: (status: PmdOfflineRecTriggerStatus, setting: PmdSetting?)] = [
            .sdkMode: (.enabled, nil)
        ]
        let pmd = PmdOfflineTrigger(triggerMode: .systemStart, triggers: triggers)
        XCTAssertThrowsError(try PolarDataUtils.mapToPolarOfflineTrigger(from: pmd)) { error in
            guard case PolarErrors.polarBleSdkInternalException = error else {
                return XCTFail("Expected polarBleSdkInternalException, got \(error)")
            }
        }
    }

    // MARK: - mapToPmdOfflineTrigger / mapToPolarOfflineTrigger round-trip

    func testRoundTrip_polarTriggerToPmdToPolar() throws {
        let original = PolarOfflineRecordingTrigger(
            triggerMode: .triggerSystemStart,
            triggerFeatures: [.acc: nil, .ecg: nil]
        )
        let pmd = try PolarDataUtils.mapToPmdOfflineTrigger(from: original)
        let back = try PolarDataUtils.mapToPolarOfflineTrigger(from: pmd)

        XCTAssertEqual(original.triggerMode, back.triggerMode)
        XCTAssertTrue(back.triggerFeatures.keys.contains(.acc))
        XCTAssertTrue(back.triggerFeatures.keys.contains(.ecg))
    }

    // MARK: - PmdSetting.mapToPolarSettings

    func testMapToPolarSettings_sampleRateMapped() {
        let pmdSetting = PmdSetting([.sampleRate: UInt32(52)])
        let polar = pmdSetting.mapToPolarSettings()
        XCTAssertEqual(Set([UInt32(52)]), polar.settings[.sampleRate])
    }

    func testMapToPolarSettings_resolutionMapped() {
        let pmdSetting = PmdSetting([.resolution: UInt32(16)])
        let polar = pmdSetting.mapToPolarSettings()
        XCTAssertEqual(Set([UInt32(16)]), polar.settings[.resolution])
    }

    func testMapToPolarSettings_rangeMapped() {
        let pmdSetting = PmdSetting([.range: UInt32(4)])
        let polar = pmdSetting.mapToPolarSettings()
        XCTAssertEqual(Set([UInt32(4)]), polar.settings[.range])
    }

    func testMapToPolarSettings_rangeMilliUnitMapped() {
        let pmdSetting = PmdSetting([.rangeMilliUnit: UInt32(100)])
        let polar = pmdSetting.mapToPolarSettings()
        XCTAssertEqual(Set([UInt32(100)]), polar.settings[.rangeMilliunit])
    }

    func testMapToPolarSettings_channelsMapped() {
        let pmdSetting = PmdSetting([.channels: UInt32(3)])
        let polar = pmdSetting.mapToPolarSettings()
        XCTAssertEqual(Set([UInt32(3)]), polar.settings[.channels])
    }

    func testMapToPolarSettings_multipleSettingsMapped() {
        let pmdSetting = PmdSetting([
            .sampleRate: UInt32(130),
            .resolution: UInt32(24),
            .channels:   UInt32(4)
        ])
        let polar = pmdSetting.mapToPolarSettings()
        XCTAssertEqual(Set([UInt32(130)]), polar.settings[.sampleRate])
        XCTAssertEqual(Set([UInt32(24)]),  polar.settings[.resolution])
        XCTAssertEqual(Set([UInt32(4)]),   polar.settings[.channels])
    }

    func testMapToPolarSettings_unknownSettingTypeIsIgnored() {
        // .security is not mapped to any PolarSensorSetting type
        let pmdSetting = PmdSetting([.security: UInt32(1)])
        let polar = pmdSetting.mapToPolarSettings()
        XCTAssertTrue(polar.settings.isEmpty)
    }
}
