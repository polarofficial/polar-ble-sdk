// Copyright © 2026 Polar. All rights reserved.

import XCTest
import CoreBluetooth
@testable import iOSCommunications

/// Tests for the `Error.indicatesBLEPairingProblem` extension defined in
/// `CBDeviceSessionImpl.swift`.
final class CBDeviceSessionImplTest: XCTestCase {

    // MARK: - CBATTError cases

    func testIndicatesBLEPairingProblem_CBATTError_insufficientEncryption_returnsTrue() {
        let error = makeCBATTError(.insufficientEncryption)
        XCTAssertTrue(error.indicatesBLEPairingProblem)
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

    func testIndicatesBLEPairingProblem_CBError_encryptionTimedOut_returnsTrue() {
        let error = makeCBError(.encryptionTimedOut)
        XCTAssertTrue(error.indicatesBLEPairingProblem)
    }

    func testIndicatesBLEPairingProblem_CBError_peerRemovedPairingInformation_returnsTrue() {
        let error = makeCBError(.peerRemovedPairingInformation)
        XCTAssertTrue(error.indicatesBLEPairingProblem)
    }

    func testIndicatesBLEPairingProblem_CBError_uuidNotAllowed_returnsTrue() {
        let error = makeCBError(.uuidNotAllowed)
        XCTAssertTrue(error.indicatesBLEPairingProblem)
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
