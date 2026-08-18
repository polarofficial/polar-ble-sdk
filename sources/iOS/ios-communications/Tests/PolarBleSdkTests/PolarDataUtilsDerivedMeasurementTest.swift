// Copyright © 2026 Polar Electro Oy. All rights reserved.

import XCTest
@testable import PolarBleSdk

final class PolarDataUtilsDerivedMeasurementTests: XCTestCase {

    func testGroupId_takenFromResponsePayload_whenPresent() throws {
        // Arrange
        let bytes = Data([
            0x0C, 0x01, 0x01,
            0x0B, 0x01, 0xE8, 0x03, 0x00, 0x00,
            0x07, 0x01, 0x02,
            0x08, 0x01, 0x02,
            0x09, 0x01, 0x32, 0x00
        ])

        // Act
        let group = PolarDataUtils.mapPmdSettingsToPolarDerivedMeasurementSettingsGroup(
            try PmdSetting(bytes), requestedGroupId: 99)

        // Assert
        XCTAssertEqual(group.groupId, 1)
    }

    func testGroupId_fallsBackToRequestedId_whenNotInResponsePayload() throws {
        // Arrange
        let bytes = Data([
            0x0B, 0x01, 0xE8, 0x03, 0x00, 0x00,
            0x07, 0x03, 0x00, 0x01, 0x02,
            0x08, 0x01, 0x02,
            0x09, 0x01, 0x32, 0x00
        ])

        // Act
        let group = PolarDataUtils.mapPmdSettingsToPolarDerivedMeasurementSettingsGroup(
            try PmdSetting(bytes), requestedGroupId: 1)

        // Assert
        XCTAssertEqual(group.groupId, 1)
    }

    func testGroupId_defaultRequestedGroupIdIsZero_forBackwardCompatibility() throws {
        // Arrange
        let bytes = Data([
            0x0C, 0x01, 0x00,
            0x0B, 0x01, 0xE8, 0x03, 0x00, 0x00,
            0x07, 0x01, 0x00,
            0x08, 0x01, 0x02,
            0x09, 0x01, 0x32, 0x00
        ])

        // Act
        let group = PolarDataUtils.mapPmdSettingsToPolarDerivedMeasurementSettingsGroup(try PmdSetting(bytes))

        // Assert
        XCTAssertEqual(group.groupId, 0)
    }
}
