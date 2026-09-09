// Copyright © 2026 Polar. All rights reserved.

import XCTest
@testable import iOSCommunications

/// Tests for `BleDeviceSession`'s pending-device-command and security-recovery state tracking.
final class BleDeviceSessionTest: XCTestCase {

    private var session: BleDeviceSession!

    override func setUpWithError() throws {
        session = BleDeviceSession(UUID())
    }

    override func tearDownWithError() throws {
        session = nil
    }

    // MARK: - Expected device-command disconnect

    func testMarkExpectedDeviceCommandDisconnect_reportsCommandWhileValid() {
        session.markExpectedDeviceCommandDisconnect(.restart)

        XCTAssertEqual(session.expectedDeviceCommandIfStillValid(), .restart)
        XCTAssertEqual(session.pendingDeviceCommand, .restart)
    }

    func testMarkExpectedDeviceCommandDisconnect_expiresAfterValidFor() {
        session.markExpectedDeviceCommandDisconnect(.restart, validFor: -1)

        XCTAssertNil(session.expectedDeviceCommandIfStillValid())
    }

    func testMarkExpectedDeviceCommandDisconnect_keepsFirstValidCommand() {
        session.markExpectedDeviceCommandDisconnect(.restart)
        session.markExpectedDeviceCommandDisconnect(.factoryReset)

        XCTAssertEqual(session.expectedDeviceCommandIfStillValid(), .restart)
    }

    func testMarkExpectedDeviceCommandDisconnect_acceptsNewCommandAfterPreviousExpired() {
        session.markExpectedDeviceCommandDisconnect(.restart, validFor: -1)
        session.markExpectedDeviceCommandDisconnect(.factoryReset)

        XCTAssertEqual(session.expectedDeviceCommandIfStillValid(), .factoryReset)
    }

    func testClearExpectedDeviceCommandDisconnect_removesPendingCommand() {
        session.markExpectedDeviceCommandDisconnect(.restart)
        session.clearExpectedDeviceCommandDisconnect()

        XCTAssertNil(session.pendingDeviceCommand)
        XCTAssertNil(session.expectedDeviceCommandIfStillValid())
    }

    func testMarkConnectionEstablished_clearsPendingDeviceCommand() {
        session.markExpectedDeviceCommandDisconnect(.restart)

        session.markConnectionEstablished()

        XCTAssertNil(session.pendingDeviceCommand)
        XCTAssertNil(session.expectedDeviceCommandIfStillValid())
    }

    // MARK: - hasEstablishedConnection

    func testHasEstablishedConnection_startsFalse() {
        XCTAssertFalse(session.hasEstablishedConnection)
    }

    func testMarkConnectionEstablished_setsHasEstablishedConnection() {
        session.markConnectionEstablished()

        XCTAssertTrue(session.hasEstablishedConnection)
    }

    // MARK: - Security recovery attempts

    func testRecordSecurityRecoveryAttempt_notExhaustedBeforeMaxAttempts() {
        XCTAssertFalse(session.recordSecurityRecoveryAttempt(maxAttempts: 3))
        XCTAssertFalse(session.securityRecoveryExhausted)

        XCTAssertFalse(session.recordSecurityRecoveryAttempt(maxAttempts: 3))
        XCTAssertFalse(session.securityRecoveryExhausted)
    }

    func testRecordSecurityRecoveryAttempt_exhaustedAtMaxAttempts() {
        XCTAssertFalse(session.recordSecurityRecoveryAttempt(maxAttempts: 3))
        XCTAssertFalse(session.recordSecurityRecoveryAttempt(maxAttempts: 3))

        XCTAssertTrue(session.recordSecurityRecoveryAttempt(maxAttempts: 3))
        XCTAssertTrue(session.securityRecoveryExhausted)
    }

    func testRecordSecurityRecoveryAttempt_respectsCustomMaxAttempts() {
        XCTAssertTrue(session.recordSecurityRecoveryAttempt(maxAttempts: 1))
        XCTAssertTrue(session.securityRecoveryExhausted)
    }

    func testMarkConnectionEstablished_resetsSecurityRecoveryState() {
        _ = session.recordSecurityRecoveryAttempt(maxAttempts: 1)
        XCTAssertTrue(session.securityRecoveryExhausted)

        session.markConnectionEstablished()

        XCTAssertFalse(session.securityRecoveryExhausted)
        XCTAssertFalse(session.recordSecurityRecoveryAttempt(maxAttempts: 3))
    }
}
