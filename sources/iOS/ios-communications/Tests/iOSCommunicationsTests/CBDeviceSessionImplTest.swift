// Copyright © 2026 Polar. All rights reserved.

import XCTest
import CoreBluetooth
@testable import iOSCommunications

/// Tests for the BLE disconnect classification extension defined in
/// `CBDeviceSessionImpl.swift`.
final class CBDeviceSessionImplTest: XCTestCase {

    private let writer = PeripheralWriteSpy()
    private var logs: [String] = []
    private var acknowledgements = 0
    private let packet = Data(repeating: 0xAB, count: 495)
    private let writeUuid = CBUUID(string: "FB005C51-02E7-F387-1CAD-8ACD2D8DF0C8")

    private func send(properties: CBCharacteristicProperties = [.write, .writeWithoutResponse], withResponse: Bool = false) throws -> Bool {
        let characteristic = CBMutableCharacteristic(type: writeUuid, properties: properties, value: nil, permissions: [.writeable])
        return try CBDeviceSessionImpl.transmitPacket(
            packet, for: characteristic, withResponse: withResponse,
            canSendWriteWithoutResponse: writer.canSendWriteWithoutResponse,
            maximumWriteWithResponseLength: writer.maximumWriteValueLength(for: .withResponse),
            writeValue: writer.writeValue,
            acknowledge: {
                self.acknowledgements += 1
                self.writer.events.append("ack")
            },
            log: { self.logs.append($0) }
        )
    }

    func testTransmit_readyUsesWithoutResponseAndAcknowledgesAfterWrite() throws {
        XCTAssertTrue(try send())
        XCTAssertEqual(writer.writes.map { $0.type }, [.withoutResponse])
        XCTAssertEqual(writer.events, ["write", "ack"])
        XCTAssertEqual(acknowledgements, 1)
        XCTAssertEqual(writer.maximumLengthQueries, 0)
        XCTAssertTrue(logs.isEmpty, "Do not log fallback messages on the normal fast path")
    }

    func testTransmit_blockedFallsBackWithoutWaitingForReadinessCallback() throws {
        writer.canSendWriteWithoutResponse = false
        XCTAssertTrue(try send(), "Fallback was submitted and must not be parked for replay")
        XCTAssertEqual(writer.writes.map { $0.type }, [.withResponse])
        XCTAssertEqual(writer.writes.first?.data, packet)
        XCTAssertEqual(writer.writes.first?.uuid, writeUuid)
        XCTAssertEqual(writer.events, ["write"])
        XCTAssertEqual(acknowledgements, 0, "Only didWriteValueFor may acknowledge the fallback")
    }

    func testTransmit_fallbackLogsReasonAndMetadataWithoutPayload() throws {
        writer.canSendWriteWithoutResponse = false
        _ = try send()
        let message = try XCTUnwrap(logs.first)
        XCTAssertEqual(logs.count, 1)
        XCTAssertTrue(message.contains("BLE write fallback"))
        XCTAssertTrue(message.contains("chr=\(writeUuid.uuidString)"))
        XCTAssertTrue(message.contains("bytes=495"))
        XCTAssertTrue(message.contains("canSendWriteWithoutResponse=false"))
        XCTAssertTrue(message.contains("withResponseLimit=512"))
        XCTAssertTrue(message.contains("awaiting didWriteValueFor"))
        XCTAssertFalse(message.lowercased().contains("abab"))
    }

    func testTransmit_fallbackAllowsPacketExactlyAtResponseLimit() throws {
        writer.canSendWriteWithoutResponse = false
        writer.responseLimit = packet.count
        XCTAssertTrue(try send())
        XCTAssertEqual(writer.writes.map { $0.type }, [.withResponse])
        XCTAssertEqual(acknowledgements, 0)
    }

    func testTransmit_oversizedFallbackIsParkedNotTruncatedOrAcknowledged() throws {
        writer.canSendWriteWithoutResponse = false
        writer.responseLimit = packet.count - 1
        XCTAssertFalse(try send())
        XCTAssertTrue(writer.writes.isEmpty)
        XCTAssertEqual(acknowledgements, 0)
        XCTAssertTrue(logs.first?.contains("exceeds withResponseLimit=494") == true)
    }

    func testTransmit_zeroResponseLimitDoesNotForceFallback() throws {
        writer.canSendWriteWithoutResponse = false
        writer.responseLimit = 0
        XCTAssertFalse(try send())
        XCTAssertTrue(writer.writes.isEmpty)
        XCTAssertEqual(acknowledgements, 0)
    }

    func testTransmit_withoutResponseOnlyCharacteristicStaysParkedWhenBlocked() throws {
        writer.canSendWriteWithoutResponse = false
        XCTAssertFalse(try send(properties: [.writeWithoutResponse]))
        XCTAssertTrue(writer.writes.isEmpty)
        XCTAssertEqual(acknowledgements, 0)
        XCTAssertEqual(writer.maximumLengthQueries, 0)
        XCTAssertTrue(logs.first?.contains("write-with-response unsupported") == true)
        XCTAssertTrue(logs.first?.contains("waiting for peripheralIsReady") == true)
    }

    func testTransmit_explicitResponseStillWaitsForDelegateAcknowledgement() throws {
        writer.canSendWriteWithoutResponse = false
        XCTAssertTrue(try send(properties: [.write], withResponse: true))
        XCTAssertEqual(writer.writes.map { $0.type }, [.withResponse])
        XCTAssertEqual(acknowledgements, 0)
        XCTAssertEqual(writer.maximumLengthQueries, 0)
        XCTAssertTrue(logs.isEmpty)
    }

    func testTransmit_requestedResponseOnCommandOnlyCharacteristicPreservesExistingBehavior() throws {
        XCTAssertTrue(try send(properties: [.writeWithoutResponse], withResponse: true))
        XCTAssertEqual(writer.writes.map { $0.type }, [.withoutResponse])
        XCTAssertEqual(acknowledgements, 1)
    }

    func testTransmit_unwritableCharacteristicThrowsWithoutWriteOrAck() {
        XCTAssertThrowsError(try send(properties: [.read])) { error in
            guard case BleGattException.gattCharacteristicError = error else {
                return XCTFail("Unexpected error: \(error)")
            }
        }
        XCTAssertTrue(writer.writes.isEmpty)
        XCTAssertEqual(acknowledgements, 0)
    }

    func testTransmit_returnsToFastPathWhenReadinessRecovers() throws {
        writer.canSendWriteWithoutResponse = false
        XCTAssertTrue(try send())
        writer.canSendWriteWithoutResponse = true
        XCTAssertTrue(try send())
        XCTAssertEqual(writer.writes.map { $0.type }, [.withResponse, .withoutResponse])
        XCTAssertEqual(writer.events, ["write", "write", "ack"])
        XCTAssertEqual(acknowledgements, 1)
        XCTAssertEqual(logs.count, 1)
    }

    // MARK: - CBATTError cases

    func testIndicatesBLEPairingProblem_CBATTError_insufficientEncryption_returnsTrue() {
        let error = makeCBATTError(.insufficientEncryption)
        XCTAssertEqual(error.bleDisconnectReason, .insufficientEncryption)
        XCTAssertFalse(error.indicatesBLEPairingProblem)
    }

    func testIndicatesBLEPairingProblem_CBATTError_insufficientAuthentication_returnsFalse() {
        let error = makeCBATTError(.insufficientAuthentication)
        XCTAssertFalse(error.indicatesBLEPairingProblem)
    }

    func testIndicatesBLEPairingProblem_CBATTError_attributeNotFound_returnsFalse() {
        let error = makeCBATTError(.attributeNotFound)
        XCTAssertFalse(error.indicatesBLEPairingProblem)
    }

    func testIndicatesBLEPairingProblem_CBATTError_requestNotSupported_returnsFalse() {
        let error = makeCBATTError(.requestNotSupported)
        XCTAssertFalse(error.indicatesBLEPairingProblem)
    }

    func testIndicatesBLEPairingProblem_CBATTError_readNotPermitted_returnsFalse() {
        let error = makeCBATTError(.readNotPermitted)
        XCTAssertFalse(error.indicatesBLEPairingProblem)
    }

    // MARK: - CBError cases

    func testIndicatesBLEPairingProblem_CBError_encryptionTimedOut_returnsFalse() {
        let error = makeCBError(.encryptionTimedOut)
        XCTAssertEqual(error.bleDisconnectReason, .encryptionTimedOut)
        XCTAssertFalse(error.indicatesBLEPairingProblem)
    }

    func testIndicatesBLEPairingProblem_CBError_peerRemovedPairingInformation_returnsTrue() {
        let error = makeCBError(.peerRemovedPairingInformation)
        XCTAssertEqual(error.bleDisconnectReason, .pairingInformationRemoved)
        XCTAssertTrue(error.indicatesBLEPairingProblem)
    }

    func testIndicatesBLEPairingProblem_CBError_uuidNotAllowed_returnsFalse() {
        let error = makeCBError(.uuidNotAllowed)
        XCTAssertEqual(error.bleDisconnectReason, .uuidNotAllowed)
        XCTAssertFalse(error.indicatesBLEPairingProblem)
    }

    func testIndicatesBLEPairingProblem_CBError_connectionTimeout_returnsFalse() {
        let error = makeCBError(.connectionTimeout)
        XCTAssertFalse(error.indicatesBLEPairingProblem)
    }

    func testIndicatesBLEPairingProblem_CBError_peripheralDisconnected_returnsFalse() {
        let error = makeCBError(.peripheralDisconnected)
        XCTAssertFalse(error.indicatesBLEPairingProblem)
    }

    func testIndicatesBLEPairingProblem_CBError_unknown_returnsFalse() {
        let error = makeCBError(.unknown)
        XCTAssertFalse(error.indicatesBLEPairingProblem)
    }

    func testIndicatesBLEPairingProblem_CBError_operationNotSupported_returnsFalse() {
        let error = makeCBError(.operationNotSupported)
        XCTAssertFalse(error.indicatesBLEPairingProblem)
    }

    // MARK: - Unrelated error domains

    func testIndicatesBLEPairingProblem_genericNSError_returnsFalse() {
        let error = NSError(domain: NSURLErrorDomain, code: NSURLErrorNotConnectedToInternet)
        XCTAssertFalse(error.indicatesBLEPairingProblem)
    }

    func testIndicatesBLEPairingProblem_customError_returnsFalse() {
        let error = CustomTestError.someError
        XCTAssertFalse(error.indicatesBLEPairingProblem)
    }

    func testIndicatesBLEPairingProblem_posixError_returnsFalse() {
        let error = NSError(domain: NSPOSIXErrorDomain, code: Int(ECONNREFUSED))
        XCTAssertFalse(error.indicatesBLEPairingProblem)
    }

    // MARK: - Boundary: codes from CBATTError domain that are not BLE pairing issues

    func testIndicatesBLEPairingProblem_CBATTErrorDomain_unknownCode_returnsFalse() {
        // Use a raw code that does not map to any known CBATTError pairing case
        let error = NSError(domain: CBATTError.errorDomain, code: 9999)
        XCTAssertFalse(error.indicatesBLEPairingProblem)
    }

    func testIndicatesBLEPairingProblem_CBErrorDomain_unknownCode_returnsFalse() {
        let error = NSError(domain: CBError.errorDomain, code: 9999)
        XCTAssertFalse(error.indicatesBLEPairingProblem)
    }
}

// MARK: - Helpers

private func makeCBATTError(_ code: CBATTError.Code) -> Error {
    return NSError(domain: CBATTError.errorDomain, code: code.rawValue)
}

private func makeCBError(_ code: CBError.Code) -> Error {
    return NSError(domain: CBError.errorDomain, code: code.rawValue)
}

private enum CustomTestError: Error {
    case someError
}

private final class PeripheralWriteSpy {
    var canSendWriteWithoutResponse = true
    var responseLimit = 512
    var maximumLengthQueries = 0
    var writes: [(data: Data, uuid: CBUUID, type: CBCharacteristicWriteType)] = []
    var events: [String] = []

    func maximumWriteValueLength(for type: CBCharacteristicWriteType) -> Int {
        XCTAssertEqual(type, .withResponse)
        maximumLengthQueries += 1
        return responseLimit
    }

    func writeValue(_ data: Data, for characteristic: CBCharacteristic, type: CBCharacteristicWriteType) {
        writes.append((data, characteristic.uuid, type))
        events.append("write")
    }
}
