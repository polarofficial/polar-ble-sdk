import CoreBluetooth
import XCTest
@testable import iOSCommunications

final class BleSonovaClientTest: XCTestCase {
    private var transmitter: RecordingGattTransmitter!
    private var client: BleSonovaClient!

    override func setUpWithError() throws {
        transmitter = RecordingGattTransmitter()
        client = BleSonovaClient(gattServiceTransmitter: transmitter)
    }

    override func tearDownWithError() throws {
        client = nil
        transmitter = nil
    }

    func testWriteSportId_sendsOneByteSportId() throws {
        try client.writeSportId(sportId: 123)

        XCTAssertEqual(transmitter.transmissions.count, 1)
        XCTAssertEqual(transmitter.transmissions[0].serviceUuid, BleSonovaClient.SPORT_ID_SERVICE)
        XCTAssertEqual(transmitter.transmissions[0].characteristicUuid, BleSonovaClient.SPORT_ID_CHARACTERISTIC)
        XCTAssertEqual(transmitter.transmissions[0].packet, Data([123]))
        XCTAssertTrue(transmitter.transmissions[0].withResponse)
    }

    func testWriteSportId_rejectsValueOutsideSupportedRange() {
        XCTAssertThrowsError(try client.writeSportId(sportId: 201)) { error in
            guard case let BleGattException.gattDataError(description) = error else {
                return XCTFail("Expected gattDataError, got \(error)")
            }
            XCTAssertEqual(description, "Sport id must be between 0 and 200, was: 201")
        }
    }

    func testDisconnected_sendsZeroSportId() {
        client.disconnected()

        XCTAssertEqual(transmitter.transmissions.count, 1)
        XCTAssertEqual(transmitter.transmissions[0].packet, Data([0]))
    }
}

private final class RecordingGattTransmitter: BleAttributeTransportProtocol {
    struct Transmission {
        let serviceUuid: CBUUID
        let characteristicUuid: CBUUID
        let packet: Data
        let withResponse: Bool
    }

    var transmissions: [Transmission] = []

    func isConnected() -> Bool { true }

    func transmitMessage(
        _ parent: BleGattClientBase,
        serviceUuid: CBUUID,
        characteristicUuid: CBUUID,
        packet: Data,
        withResponse: Bool
    ) throws {
        transmissions.append(
            Transmission(
                serviceUuid: serviceUuid,
                characteristicUuid: characteristicUuid,
                packet: packet,
                withResponse: withResponse
            )
        )
    }

    func characteristicWith(uuid: CBUUID) throws -> CBCharacteristic? { nil }
    func characteristicNameWith(uuid: CBUUID) -> String? { nil }
    func readValue(_ parent: BleGattClientBase, serviceUuid: CBUUID, characteristicUuid: CBUUID) throws {}
    func setCharacteristicNotify(_ parent: BleGattClientBase, serviceUuid: CBUUID, characteristicUuid: CBUUID, notify: Bool) throws {}
    func attributeOperationStarted() {}
    func attributeOperationFinished() {}
}
