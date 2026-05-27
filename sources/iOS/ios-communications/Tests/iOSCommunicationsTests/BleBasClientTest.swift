//  Copyright © 2021 Polar. All rights reserved.

import XCTest
@testable import iOSCommunications
import CoreBluetooth

class BleBasClientTest: XCTestCase {
    var bleBasClient: BleBasClient!
    var mockGattServiceTransmitterImpl: MockPolarGattServiceTransmitter!

    override func setUpWithError() throws {
        mockGattServiceTransmitterImpl = MockGattServiceTransmitterImpl()
        bleBasClient = BleBasClient(gattServiceTransmitter: mockGattServiceTransmitterImpl)
    }

    override func tearDownWithError() throws {
        mockGattServiceTransmitterImpl = nil
        bleBasClient = nil
    }

    // MARK: - Helpers

    private func collect<T>(_ count: Int, from stream: AsyncThrowingStream<T, Error>) async throws -> [T] {
        var results: [T] = []
        for try await item in stream {
            results.append(item)
            if results.count == count { break }
        }
        return results
    }

    private func collectUntilError<T>(_ stream: AsyncThrowingStream<T, Error>) async -> (values: [T], error: Error?) {
        var values: [T] = []
        do {
            for try await item in stream { values.append(item) }
        } catch {
            return (values, error)
        }
        return (values, nil)
    }

    // MARK: - Battery Level Tests

    // GIVEN that BLE Battery Service client receives battery data updates
    // WHEN battery level observable is subscribed
    // THEN the latest cached battery value is emitted
    func testTheCachedValue() async throws {
        // Arrange
        let characteristic: CBUUID = CBUUID(string: "2A19")
        let deviceNotifyingBatteryData1 = Data([100])
        let deviceNotifyingBatteryData2 = Data([80])
        let error = 0

        // Act
        bleBasClient.processServiceData(characteristic, data: deviceNotifyingBatteryData1, err: error)
        bleBasClient.processServiceData(characteristic, data: deviceNotifyingBatteryData2, err: error)

        let stream = bleBasClient.monitorBatteryStatus(true)
        let results = try await collect(1, from: stream)

        // Assert
        XCTAssertEqual(results, [Int(deviceNotifyingBatteryData2[0])])
    }

    // GIVEN that BLE Battery Service client receives battery data updates
    // WHEN battery level observable is subscribed
    // THEN the correct values are received by observer
    func testBatteryValuesStreamEmitsCorrectValues() async throws {
        // Arrange
        let characteristic: CBUUID = CBUUID(string: "2A19")
        let deviceNotifyingValidBatteryData1 = Data([100])
        let deviceNotifyingInvalidBatteryData1 = Data([250])
        let deviceNotifyingValidBatteryData2 = Data([80])
        let deviceNotifyingInvalidBatteryData2 = Data([0xFF])
        let deviceNotifyingValidBatteryData3 = Data([00])
        let error = 0

        // Act
        bleBasClient.processServiceData(characteristic, data: deviceNotifyingValidBatteryData1, err: error)
        let stream = bleBasClient.monitorBatteryStatus(true)
        let task = Task { try await self.collect(3, from: stream) }
        try await Task.sleep(nanoseconds: 20_000_000)

        bleBasClient.processServiceData(characteristic, data: deviceNotifyingInvalidBatteryData1, err: error)
        bleBasClient.processServiceData(characteristic, data: deviceNotifyingValidBatteryData2, err: error)
        bleBasClient.processServiceData(characteristic, data: deviceNotifyingInvalidBatteryData2, err: error)
        bleBasClient.processServiceData(characteristic, data: deviceNotifyingValidBatteryData3, err: error)

        let results = try await task.value

        // Assert
        XCTAssertEqual(results, [
            Int(deviceNotifyingValidBatteryData1[0]),
            Int(deviceNotifyingValidBatteryData2[0]),
            Int(deviceNotifyingValidBatteryData3[0])
        ])
    }

    // GIVEN that BLE Battery Service client receives battery data updates
    // WHEN battery level observable is subscribed
    // THEN at some point device connection is lost
    func testDeviceDisconnectsWhileStreaming() async throws {
        // Arrange
        let characteristic: CBUUID = CBUUID(string: "2A19")
        let deviceNotifyingBatteryData1 = Data([100])
        let deviceNotifyingBatteryData2 = Data([90])
        let deviceNotifyingBatteryData3 = Data([80])
        let error = 0

        // Act
        let stream = bleBasClient.monitorBatteryStatus(true)
        let task = Task { await self.collectUntilError(stream) }
        try await Task.sleep(nanoseconds: 20_000_000)

        bleBasClient.processServiceData(characteristic, data: deviceNotifyingBatteryData1, err: error)
        bleBasClient.processServiceData(characteristic, data: deviceNotifyingBatteryData2, err: error)
        bleBasClient.processServiceData(characteristic, data: deviceNotifyingBatteryData3, err: error)
        bleBasClient.disconnected()

        let result = await task.value

        // Assert
        XCTAssertEqual(result.values, [
            Int(deviceNotifyingBatteryData1[0]),
            Int(deviceNotifyingBatteryData2[0]),
            Int(deviceNotifyingBatteryData3[0])
        ])
        XCTAssertNotNil(result.error)
    }

    // MARK: - Charging Status Tests

    func testProcessServiceData_ShouldEmitUnknownChargeState() async throws {
        // Arrange
        let characteristic: CBUUID = BleBasClient.BATTERY_STATUS_CHARACTERISTIC
        let status = 0
        let batteryStatusData = Data([0b00000000, 0b00000000])

        // Act
        let stream = bleBasClient.monitorChargingStatus(true)
        // stream yields cached (.unknown) immediately, then new value after processServiceData
        let task = Task { try await self.collect(2, from: stream) }
        try await Task.sleep(nanoseconds: 20_000_000)
        bleBasClient.processServiceData(characteristic, data: batteryStatusData, err: status)
        let results = try await task.value

        // Assert
        XCTAssertEqual(results[1], BleBasClient.ChargeState.unknown)
    }

    func testProcessServiceData_ShouldEmitDischargingInactiveChargeState() async throws {
        // Arrange
        let characteristic: CBUUID = BleBasClient.BATTERY_STATUS_CHARACTERISTIC
        let status = 0
        let batteryStatusData = Data([0x00, 0b11100011])

        // Act
        let stream = bleBasClient.monitorChargingStatus(true)
        let task = Task { try await self.collect(2, from: stream) }
        try await Task.sleep(nanoseconds: 20_000_000)
        bleBasClient.processServiceData(characteristic, data: batteryStatusData, err: status)
        let results = try await task.value

        // Assert
        XCTAssertEqual(results[1], BleBasClient.ChargeState.dischargingInactive)
    }

    func testProcessServiceData_ShouldEmitDischargingActiveChargeState() async throws {
        // Arrange
        let characteristic: CBUUID = BleBasClient.BATTERY_STATUS_CHARACTERISTIC
        let status = 0
        let batteryStatusData = Data([0x00, 0b11000001])

        // Act
        let stream = bleBasClient.monitorChargingStatus(true)
        let task = Task { try await self.collect(2, from: stream) }
        try await Task.sleep(nanoseconds: 20_000_000)
        bleBasClient.processServiceData(characteristic, data: batteryStatusData, err: status)
        let results = try await task.value

        // Assert
        XCTAssertEqual(results[1], BleBasClient.ChargeState.dischargingActive)
    }

    func testProcessServiceData_ShouldEmitChargingChargeState() async throws {
        // Arrange
        let characteristic: CBUUID = BleBasClient.BATTERY_STATUS_CHARACTERISTIC
        let status = 0
        let batteryStatusData = Data([0x00, 0b10100011])

        // Act
        let stream = bleBasClient.monitorChargingStatus(true)
        let task = Task { try await self.collect(2, from: stream) }
        try await Task.sleep(nanoseconds: 20_000_000)
        bleBasClient.processServiceData(characteristic, data: batteryStatusData, err: status)
        let results = try await task.value

        // Assert
        XCTAssertEqual(results[1], BleBasClient.ChargeState.charging)
    }

    // MARK: - Power Sources State Tests

    // GIVEN that BLE Battery Service client receives battery data updates
    // WHEN battery power sources status observable is subscribed
    // THEN the latest cached power sources status value is emitted
    func testPowerSourcesCachedValue() async throws {
        // Arrange
        let characteristic: CBUUID = BleBasClient.BATTERY_STATUS_CHARACTERISTIC
        let batteryStatusDataWiredNotConnected = Data([0x00, 0b10100001])
        let batteryStatusDataWiredConnected    = Data([0x00, 0b10100011])
        let error = 0

        // Act
        bleBasClient.processServiceData(characteristic, data: batteryStatusDataWiredConnected, err: error)
        bleBasClient.processServiceData(characteristic, data: batteryStatusDataWiredNotConnected, err: error)

        let stream = bleBasClient.monitorPowerSourcesState(true)
        // cached value is emitted immediately as first item
        let results = try await collect(1, from: stream)

        // Assert
        XCTAssertEqual(results.count, 1)
        XCTAssertEqual(results[0].wiredExternalPowerConnected, BleBasClient.PowerSourceState.notConnected)
    }

    func testProcessServiceData_ShouldEmitAllPowerSourceStatesCorrectly() async throws {
        // Arrange
        let characteristic: CBUUID = BleBasClient.BATTERY_STATUS_CHARACTERISTIC
        let status = 0
        let batteryStatusDataBatteryNotPresent              = Data([0x00, 0b10100000])
        let batteryStatusDataBatteryPresent                 = Data([0x00, 0b10100001])
        let batteryStatusDataWiredNotConnected              = Data([0x00, 0b10100001])
        let batteryStatusDataWiredConnected                 = Data([0x00, 0b10100011])
        let batteryStatusDataWiredUnknown                   = Data([0x00, 0b10100101])
        let batteryStatusDataWiredReservedForFutureUse      = Data([0x00, 0b10100111])
        let batteryStatusDataWirelessNotConnected           = Data([0x00, 0b10100011])
        let batteryStatusDataWirelessConnected              = Data([0x00, 0b10101011])
        let batteryStatusDataWirelessUnknown                = Data([0x00, 0b10110011])
        let batteryStatusDataWirelessReservedForFutureUse   = Data([0x00, 0b10111011])

        // Act — 1 cached + 10 processServiceData = 11 items
        let stream = bleBasClient.monitorPowerSourcesState(true)
        let task = Task { try await self.collect(11, from: stream) }
        try await Task.sleep(nanoseconds: 20_000_000)

        bleBasClient.processServiceData(characteristic, data: batteryStatusDataBatteryNotPresent, err: status)
        bleBasClient.processServiceData(characteristic, data: batteryStatusDataBatteryPresent, err: status)
        bleBasClient.processServiceData(characteristic, data: batteryStatusDataWiredNotConnected, err: status)
        bleBasClient.processServiceData(characteristic, data: batteryStatusDataWiredConnected, err: status)
        bleBasClient.processServiceData(characteristic, data: batteryStatusDataWiredUnknown, err: status)
        bleBasClient.processServiceData(characteristic, data: batteryStatusDataWiredReservedForFutureUse, err: status)
        bleBasClient.processServiceData(characteristic, data: batteryStatusDataWirelessNotConnected, err: status)
        bleBasClient.processServiceData(characteristic, data: batteryStatusDataWirelessConnected, err: status)
        bleBasClient.processServiceData(characteristic, data: batteryStatusDataWirelessUnknown, err: status)
        bleBasClient.processServiceData(characteristic, data: batteryStatusDataWirelessReservedForFutureUse, err: status)

        let events = try await task.value

        // Assert
        XCTAssertEqual(events[1].batteryPresent, BleBasClient.BatteryPresentState.notPresent)
        XCTAssertEqual(events[2].batteryPresent, BleBasClient.BatteryPresentState.present)
        XCTAssertEqual(events[3].wiredExternalPowerConnected, BleBasClient.PowerSourceState.notConnected)
        XCTAssertEqual(events[4].wiredExternalPowerConnected, BleBasClient.PowerSourceState.connected)
        XCTAssertEqual(events[5].wiredExternalPowerConnected, BleBasClient.PowerSourceState.unknown)
        XCTAssertEqual(events[6].wiredExternalPowerConnected, BleBasClient.PowerSourceState.reservedForFutureUse)
        XCTAssertEqual(events[7].wirelessExternalPowerConnected, BleBasClient.PowerSourceState.notConnected)
        XCTAssertEqual(events[8].wirelessExternalPowerConnected, BleBasClient.PowerSourceState.connected)
        XCTAssertEqual(events[9].wirelessExternalPowerConnected, BleBasClient.PowerSourceState.unknown)
        XCTAssertEqual(events[10].wirelessExternalPowerConnected, BleBasClient.PowerSourceState.reservedForFutureUse)
    }

    // MARK: - Synchronous getter tests

    func testGetBatteryLevel_should_return_newest_battery_level_value() throws {
        // Arrange
        let characteristic: CBUUID = CBUUID(string: "2A19")
        let deviceNotifyingBatteryData = Data([50])
        let deviceNotifyingBatteryData2 = Data([100])
        let error = 0

        // Act
        bleBasClient.processServiceData(characteristic, data: deviceNotifyingBatteryData, err: error)
        bleBasClient.processServiceData(characteristic, data: deviceNotifyingBatteryData2, err: error)
        let result = bleBasClient.getBatteryLevel()

        // Assert
        XCTAssertEqual(result, Int(deviceNotifyingBatteryData2[0]))
    }

    func testGetBatteryLevel_should_return_undefined_battery_percentage() throws {
        // Arrange
        let characteristic: CBUUID = CBUUID(string: "91A2")

        // Act
        bleBasClient.processServiceData(characteristic, data: Data(), err: 0)
        let result = bleBasClient.getBatteryLevel()

        // Assert
        XCTAssertEqual(result, -1)
    }

    func testGetChargerStatus_should_return_newest_charger_status() throws {
        // Arrange
        let characteristic: CBUUID = BleBasClient.BATTERY_STATUS_CHARACTERISTIC
        let status = 0
        let batteryStatusDataWiredConnected = Data([0x00, 0b10100011])

        // Act
        bleBasClient.processServiceData(characteristic, data: batteryStatusDataWiredConnected, err: status)
        let result = bleBasClient.getChargeState()

        // Assert
        XCTAssertEqual(result, BleBasClient.ChargeState.charging)
    }

    func testGetChargerStatus_should_return_undefined_battery_percentage() throws {
        // Arrange
        let characteristic: CBUUID = CBUUID(string: "91A2")
        let batteryStatusDataWiredConnected = Data([0x00, 0b10100011])
        let status = 0

        // Act
        bleBasClient.processServiceData(characteristic, data: batteryStatusDataWiredConnected, err: status)
        let result = bleBasClient.getChargeState()

        // Assert
        XCTAssertEqual(result, BleBasClient.ChargeState.unknown)
    }
}
