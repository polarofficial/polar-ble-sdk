//  Copyright © 2023 Polar. All rights reserved.

import XCTest
import iOSCommunications
import CoreBluetooth

class BleHtsClientTest: XCTestCase {

    var bleHtsClient: BleHtsClient!
    var mockGattServiceTransmitterImpl: MockPolarGattServiceTransmitter!

    override func setUpWithError() throws {
        mockGattServiceTransmitterImpl = MockGattServiceTransmitterImpl()
        bleHtsClient = BleHtsClient(gattServiceTransmitter: mockGattServiceTransmitterImpl)
    }

    override func tearDownWithError() throws {
        mockGattServiceTransmitterImpl = nil
        bleHtsClient = nil
    }

    func testTemperatureMeasurement() async throws {
        // Arrange
        let characteristic: CBUUID = HealthThermometer.TEMPERATURE_MEASUREMENT
        let status = 0

        let expectedCelsius1 = 27.20
        let measurementFrame1: [UInt8] = [0x00, 0xa0, 0x0a, 0x00, 0xfe]

        let expectedCelsius2 = 27.21
        let measurementFrame2: [UInt8] = [0x00, 0xa1, 0x0a, 0x00, 0xfe]

        // Act
        let stream = bleHtsClient.observeHtsNotifications(checkConnection: true)
        let task = Task { () -> [BleHtsClient.TemperatureMeasurement] in
            var results: [BleHtsClient.TemperatureMeasurement] = []
            for try await item in stream {
                results.append(item)
                if results.count == 2 { break }
            }
            return results
        }
        try await Task.sleep(nanoseconds: 20_000_000)

        bleHtsClient.processServiceData(characteristic, data: Data(measurementFrame1), err: status)
        bleHtsClient.processServiceData(characteristic, data: Data(measurementFrame2), err: status)

        let recordedEvents = try await task.value

        // Assert
        XCTAssertEqual(recordedEvents.count, 2)
        XCTAssertEqual(recordedEvents[0].temperatureCelsius, Float(expectedCelsius1))
        XCTAssertEqual(recordedEvents[1].temperatureCelsius, Float(expectedCelsius2))
    }
}
