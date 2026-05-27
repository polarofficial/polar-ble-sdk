//  Copyright © 2021 Polar. All rights reserved.

import XCTest
@testable import iOSCommunications
import CoreBluetooth

class BleHrClientTest: XCTestCase {

    var bleHrClient: BleHrClient!
    var mockGattServiceTransmitterImpl: MockPolarGattServiceTransmitter!

    override func setUpWithError() throws {
        mockGattServiceTransmitterImpl = MockPolarGattServiceTransmitter()
        bleHrClient = BleHrClient(gattServiceTransmitter: mockGattServiceTransmitterImpl)
    }

    override func tearDownWithError() throws {
        mockGattServiceTransmitterImpl = nil
        bleHrClient = nil
    }

    // MARK: - Helpers

    /// Collects up to `count` items from a stream, then cancels it.
    private func collect(_ count: Int, from stream: AsyncThrowingStream<BleHrClient.BleHrNotification, Error>) async throws -> [BleHrClient.BleHrNotification] {
        var results: [BleHrClient.BleHrNotification] = []
        for try await item in stream {
            results.append(item)
            if results.count == count { break }
        }
        return results
    }

    // GIVEN that BLE HR Service client observable is subscribed
    // WHEN the connection to device is missing
    // THEN stream should immediately throw gattDisconnected
    func testConnectionToDeviceIsLost() async throws {
        // Arrange
        mockGattServiceTransmitterImpl.mockConnectionStatus = false

        // Act & Assert
        let stream = bleHrClient.observeHrNotifications(true)
        do {
            for try await _ in stream { XCTFail("Should not receive data") }
            XCTFail("Should have thrown an error")
        } catch {
            guard case BleGattException.gattDisconnected = error else {
                return XCTFail("Expected gattDisconnected, got \(error)")
            }
        }
    }

    // GIVEN that BLE HR Service client receives heart rate data updates
    // WHEN hr data stream is being consumed
    // THEN at some point device connection is lost
    func testDeviceDisconnectsWhileStreaming() async throws {
        // Arrange
        let characteristic = CBUUID(string: "2a37")
        let stream = bleHrClient.observeHrNotifications(true)

        // Use a Task that returns its collected results and error so Swift value-
        // semantics don't hide mutations made inside the @Sendable closure.
        let task = Task<(items: [BleHrClient.BleHrNotification], error: Error?), Never> {
            var collected: [BleHrClient.BleHrNotification] = []
            do {
                for try await item in stream { collected.append(item) }
            } catch {
                return (collected, error)
            }
            return (collected, nil)
        }

        try await Task.sleep(nanoseconds: 20_000_000)

        // Act
        bleHrClient.processServiceData(characteristic, data: Data([0x00, 1]), err: 0)
        bleHrClient.processServiceData(characteristic, data: Data([0x00, 2]), err: 0)
        bleHrClient.processServiceData(characteristic, data: Data([0x00, 3]), err: 0)
        bleHrClient.disconnected()

        let (results, thrownError) = await task.value

        // Assert — 3 HR values then a disconnection error
        XCTAssertEqual(3, results.count)
        for item in results { XCTAssertTrue((1...3).contains(item.hr)) }
        XCTAssertNotNil(thrownError)
        guard case BleGattException.gattDisconnected = thrownError! else {
            return XCTFail("Expected gattDisconnected, got \(thrownError!)")
        }
    }

    // GIVEN that BLE HR Service client observable is receiving hr values
    // WHEN stream is subscribed
    // THEN no values in cache
    func testNoCachedValues() async throws {
        // Arrange
        let characteristic = CBUUID(string: "2a37")

        // Emit first value BEFORE subscribing — should NOT be cached
        bleHrClient.processServiceData(characteristic, data: Data([0x00, 101]), err: 0)

        let stream = bleHrClient.observeHrNotifications(true)
        let task = Task { try await self.collect(1, from: stream) }

        try await Task.sleep(nanoseconds: 20_000_000)

        // Emit second value AFTER subscribing — should be received
        bleHrClient.processServiceData(characteristic, data: Data([0x00, 102]), err: 0)

        let results = try await task.value

        // Assert — only the second value should be received
        XCTAssertEqual(1, results.count)
        XCTAssertEqual(102, results[0].hr)
    }

    // GIVEN BLE HR Service client receives heart rate value in uint8 data format
    // WHEN heart rate stream is consumed
    // THEN the values are emitted
    func testHrFormatUint8() async throws {
        // Arrange
        let characteristic = CBUUID(string: "2a37")
        let stream = bleHrClient.observeHrNotifications(true)
        let task = Task { try await self.collect(2, from: stream) }

        try await Task.sleep(nanoseconds: 20_000_000)

        // Act
        bleHrClient.processServiceData(characteristic, data: Data([0x00, 0xFF]), err: 0)
        bleHrClient.processServiceData(characteristic, data: Data([0x00, 0x7F]), err: 0)

        let results = try await task.value

        // Assert
        XCTAssertEqual(2, results.count)
        XCTAssertEqual(255, results[0].hr)
        XCTAssertFalse(results[0].sensorContact)
        XCTAssertFalse(results[0].sensorContactSupported)
        XCTAssertEqual(0, results[0].energy)
        XCTAssertEqual(0, results[0].rrs.count)
        XCTAssertEqual(127, results[1].hr)
        XCTAssertFalse(results[1].sensorContact)
        XCTAssertFalse(results[1].sensorContactSupported)
        XCTAssertEqual(0, results[1].energy)
        XCTAssertEqual(0, results[1].rrs.count)
    }

    // GIVEN BLE HR Service client receives heart rate value in uint16 data format
    // WHEN heart rate stream is consumed
    // THEN the values are emitted
    func testHrFormatUint16() async throws {
        let characteristic = CBUUID(string: "2a37")
        let stream = bleHrClient.observeHrNotifications(true)
        let task = Task { try await self.collect(2, from: stream) }

        try await Task.sleep(nanoseconds: 20_000_000)

        bleHrClient.processServiceData(characteristic, data: Data([0x01, 0x80, 0x80]), err: 0)
        bleHrClient.processServiceData(characteristic, data: Data([0x01, 0x7F, 0x7F]), err: 0)

        let results = try await task.value

        XCTAssertEqual(32896, results[0].hr)
        XCTAssertFalse(results[0].sensorContact)
        XCTAssertFalse(results[0].sensorContactSupported)
        XCTAssertEqual(0, results[0].energy)
        XCTAssertEqual(0, results[0].rrs.count)
        XCTAssertEqual(32639, results[1].hr)
        XCTAssertFalse(results[1].sensorContact)
        XCTAssertFalse(results[1].sensorContactSupported)
        XCTAssertEqual(0, results[1].energy)
        XCTAssertEqual(0, results[1].rrs.count)
    }

    // GIVEN BLE HR Service client receives data
    // WHEN sensor contact is supported
    // THEN sensor contact corresponds the support flag
    func testSensorContactSupported() async throws {
        let characteristic = CBUUID(string: "2a37")
        let stream = bleHrClient.observeHrNotifications(true)
        let task = Task { try await self.collect(2, from: stream) }

        try await Task.sleep(nanoseconds: 20_000_000)

        bleHrClient.processServiceData(characteristic, data: Data([0x06, 0xFF]), err: 0)
        bleHrClient.processServiceData(characteristic, data: Data([0x04, 0xFF]), err: 0)

        let results = try await task.value

        XCTAssertTrue(results[0].sensorContact)
        XCTAssertTrue(results[0].sensorContactSupported)
        XCTAssertFalse(results[1].sensorContact)
        XCTAssertTrue(results[1].sensorContactSupported)
    }

    // GIVEN BLE HR Service client receives data
    // WHEN sensor contact is not supported
    // THEN sensor contact shall be always false
    func testSensorContactNotSupported() async throws {
        let characteristic = CBUUID(string: "2a37")
        let stream = bleHrClient.observeHrNotifications(true)
        let task = Task { try await self.collect(2, from: stream) }

        try await Task.sleep(nanoseconds: 20_000_000)

        bleHrClient.processServiceData(characteristic, data: Data([0x02, 0x00]), err: 0)
        bleHrClient.processServiceData(characteristic, data: Data([0x00, 0x00]), err: 0)

        let results = try await task.value

        XCTAssertFalse(results[0].sensorContact)
        XCTAssertFalse(results[0].sensorContactSupported)
        XCTAssertFalse(results[1].sensorContact)
        XCTAssertFalse(results[1].sensorContactSupported)
    }

    // GIVEN BLE HR Service client receives data
    // WHEN Energy Expended field is present
    // THEN correct energy value is emitted
    func testEnergyExpended() async throws {
        let characteristic = CBUUID(string: "2a37")
        let stream = bleHrClient.observeHrNotifications(true)
        let task = Task { try await self.collect(2, from: stream) }

        try await Task.sleep(nanoseconds: 20_000_000)

        bleHrClient.processServiceData(characteristic, data: Data([0x09, 0x00, 0x00, 0xFF, 0xFF]), err: 0)
        bleHrClient.processServiceData(characteristic, data: Data([0x08, 0x00, 0x7F, 0x80]), err: 0)

        let results = try await task.value

        XCTAssertEqual(65535, results[0].energy)
        XCTAssertEqual(32895, results[1].energy)
    }

    // GIVEN BLE HR Service client receives data
    // WHEN RR-Interval field is present
    // THEN correct rr values are emitted
    func testRRinterval() async throws {
        let characteristic = CBUUID(string: "2a37")
        let stream = bleHrClient.observeHrNotifications(true)
        let task = Task { try await self.collect(3, from: stream) }

        try await Task.sleep(nanoseconds: 20_000_000)

        bleHrClient.processServiceData(characteristic, data: Data([0x10, 0x00, 0xFF, 0xFF]), err: 0)
        bleHrClient.processServiceData(characteristic, data: Data([0x11, 0x00, 0x00, 0xFF, 0xFF, 0x7F, 0x80]), err: 0)
        bleHrClient.processServiceData(characteristic, data: Data([0x10, 0x00, 0x00, 0x00]), err: 0)

        let results = try await task.value

        XCTAssertEqual(3, results.count)

        XCTAssertEqual(0, results[0].hr)
        XCTAssertEqual(1, results[0].rrs.count)
        XCTAssertEqual(1, results[0].rrsMs.count)
        XCTAssertTrue(results[0].rrPresent)
        XCTAssertEqual(65535, results[0].rrs[0])
        XCTAssertEqual(63999, results[0].rrsMs[0])

        XCTAssertEqual(0, results[1].hr)
        XCTAssertEqual(2, results[1].rrs.count)
        XCTAssertEqual(2, results[1].rrsMs.count)
        XCTAssertTrue(results[1].rrPresent)
        XCTAssertEqual(65535, results[1].rrs[0])
        XCTAssertEqual(63999, results[1].rrsMs[0])
        XCTAssertEqual(32895, results[1].rrs[1])
        XCTAssertEqual(32124, results[1].rrsMs[1])

        XCTAssertEqual(0, results[2].hr)
        XCTAssertEqual(1, results[2].rrs.count)
        XCTAssertEqual(1, results[2].rrsMs.count)
        XCTAssertTrue(results[2].rrPresent)
        XCTAssertEqual(0, results[2].rrs[0])
        XCTAssertEqual(0, results[2].rrsMs[0])
    }
}
