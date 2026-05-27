//  Copyright © 2023 Polar. All rights reserved.

import XCTest
@testable import iOSCommunications

class BlePmdClientTest: XCTestCase {

    var mockGattServiceTransmitterImpl: MockPolarGattServiceTransmitter!
    var blePmdClient: BlePmdClient!

    override func setUpWithError() throws {
        mockGattServiceTransmitterImpl = MockPolarGattServiceTransmitter()
        blePmdClient = BlePmdClient(gattServiceTransmitter: mockGattServiceTransmitterImpl)
    }

    override func tearDownWithError() throws {
        mockGattServiceTransmitterImpl = nil
        blePmdClient = nil
    }

    func testProcessControlPointResponseWhenStatusIsSuccess() throws {
        // Arrange
        // HEX: F0 01 00 00 00 00 00 00 70 FF
        // index    type                                data
        // 0:      Response code                        F0
        // 1...:   Data                                 01 00 00 00 00 00 00 70 FF
        let controlPointResponse = Data([
            0xF0,
            0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x70, 0xFF
        ])
        let successErrCode = 0x00

        // Act
        blePmdClient.processServiceData(BlePmdClient.PMD_CP, data: controlPointResponse, err: successErrCode)

        // Assert
        let data = try blePmdClient.pmdCpResponseQueue.pop()
        XCTAssertEqual(controlPointResponse, data)
    }

    func testProcessMeasurementStopControlPointCommand() async throws {
        // Arrange
        // HEX: 01 01 02
        // index    type                                data
        // 0:      Online Measurement Stopped           01
        // 1...:   Measurement types                    01 (PPG), 02 (ACC)
        let controlPointResponse = Data([0x01, 0x01, 0x02])
        let successErrCode = 0x00

        let ppgStream = blePmdClient.observePpg()
        let accStream = blePmdClient.observeAcc()
        let ppiStream = blePmdClient.observePpi()

        // Start tasks that consume the streams — they will unblock when streams close
        let ppgTask = Task<Error?, Never> {
            do { for try await _ in ppgStream {} } catch { return error }
            return nil
        }
        let accTask = Task<Error?, Never> {
            do { for try await _ in accStream {} } catch { return error }
            return nil
        }

        // Give tasks a moment to start iterating before the stop command arrives
        try await Task.sleep(nanoseconds: 20_000_000) // 20ms

        // Act
        blePmdClient.processServiceData(BlePmdClient.PMD_CP, data: controlPointResponse, err: successErrCode)

        // Await the stream close errors
        let ppgError = await ppgTask.value
        let accError = await accTask.value

        // Assert PPG stream closed with bleOnlineStreamClosed
        XCTAssertNotNil(ppgError, "PPG stream should have closed with an error")
        guard case BlePmdError.bleOnlineStreamClosed = ppgError! else {
            return XCTFail("Expected bleOnlineStreamClosed for PPG, got \(ppgError!)")
        }

        // Assert ACC stream closed with bleOnlineStreamClosed
        XCTAssertNotNil(accError, "ACC stream should have closed with an error")
        guard case BlePmdError.bleOnlineStreamClosed = accError! else {
            return XCTFail("Expected bleOnlineStreamClosed for ACC, got \(accError!)")
        }

        // Assert PPI stream was NOT closed (type 0x03 was not in the stop command)
        // Race the stream against a short timeout — timeout should win (stream still open)
        let ppiClosedWithError = await withTaskGroup(of: Bool.self) { group in
            group.addTask {
                do { for try await _ in ppiStream {} } catch { return true }
                return false
            }
            group.addTask {
                try? await Task.sleep(nanoseconds: 50_000_000) // 50ms timeout
                return false
            }
            let result = await group.next() ?? false
            group.cancelAll()
            return result
        }
        XCTAssertFalse(ppiClosedWithError, "PPI stream should NOT have been closed by the stop command")
    }
}
