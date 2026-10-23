//  Copyright © 2021 Polar. All rights reserved.

import XCTest
@testable import PolarBleSdk

class BlePsFtpUtilityTest: XCTestCase {

    func test_pftpResponseErrorsResolveAllDeclaredErrorCodes() {
        let expectedErrors: [(Int, Protocol_PbPFtpError)] = [
            (0, .operationSucceeded),
            (1, .rebooting),
            (2, .tryAgain),
            (100, .unidentifiedHostError),
            (101, .invalidCommand),
            (102, .invalidParameter),
            (103, .noSuchFileOrDirectory),
            (104, .directoryExists),
            (105, .fileExists),
            (106, .operationNotPermitted),
            (107, .noSuchUser),
            (108, .timeout),
            (200, .unidentifiedDeviceError),
            (201, .notImplemented),
            (202, .systemBusy),
            (203, .invalidContent),
            (204, .checksumFailure),
            (205, .diskFull),
            (206, .prerequisiteNotMet),
            (207, .insufficientBuffer),
            (208, .waitForIdling),
            (209, .batteryTooLow),
            (210, .waitForPairing)
        ]

        XCTAssertEqual(expectedErrors.map { $0.1 }, Array(Protocol_PbPFtpError.allCases))

        for (expectedCode, expectedError) in expectedErrors {
            let responseError = BlePsFtpException.responseError(errorCode: expectedCode)

            XCTAssertEqual(expectedError.rawValue, expectedCode)
            XCTAssertEqual(Protocol_PbPFtpError(rawValue: expectedCode), expectedError)
            XCTAssertEqual(responseError._code, expectedCode)
            XCTAssertEqual(responseError.errorName, "ResponseError.\(expectedError)")
        }
    }

    func test_processSingleFrame() throws {
        // Arrange
        // HEX 02 FF 00
        // index    type                                            data:
        // 0        header                                          0x02
        //      bit0 :       next                                     0b (first frame)
        //      bit1..2 :    status                                  01b (last frame)
        //      bit3 :       RFU
        //      bit4..7:     sequence number                       0000b (first frame)
        // 1..MAX   payload                                         0xFF 0x00
        let expectedNext = 0
        let expectedStatus = BlePsFtpUtility.RFC76_STATUS_LAST
        let expectedSequenceNumber = 0
        let expectedPayload = Data([0xFF,0x00])
        let expectedError : Int? = nil

        // Act
        let deviceNotifyingData = Data([0x02,0xFF,0x00])
        let resultFrame = try BlePsFtpUtility.processRfc76MessageFrame(deviceNotifyingData)
    
        // Assert
        XCTAssertEqual(resultFrame.next, expectedNext)
        XCTAssertEqual(resultFrame.status, expectedStatus)
        XCTAssertEqual(resultFrame.sequenceNumber, expectedSequenceNumber)
        XCTAssertEqual(resultFrame.payload.count, expectedPayload.count)
        XCTAssertEqual(resultFrame.payload, expectedPayload)
        XCTAssertEqual(resultFrame.error, expectedError)
    }

    func test_processFirstFrameInLongSequence() throws {
        // Arrange
        // HEX 06 FF 00
        // index    type                                            data:
        // 0        header                                          0x06
        //      bit0 :       next                                     0b (first frame)
        //      bit1..2 :    status                                  11b (more frames to come)
        //      bit3 :       RFU
        //      bit4..7:     sequence number                       0000b (first frame)
        // 1..MAX   payload                                         0xFF 0x00
        let expectedNext = 0
        let expectedStatus = BlePsFtpUtility.RFC76_STATUS_MORE
        let expectedSequenceNumber = 0
        let expectedPayload = Data([0xFF,0x00])
        let expectedError : Int? = nil

        // Act
        let deviceNotifyingData = Data([0x06,0xFF,0x00])
        let resultFrame = try BlePsFtpUtility.processRfc76MessageFrame(deviceNotifyingData)
    
        // Assert
        XCTAssertEqual(resultFrame.next, expectedNext)
        XCTAssertEqual(resultFrame.status, expectedStatus)
        XCTAssertEqual(resultFrame.sequenceNumber, expectedSequenceNumber)
        XCTAssertEqual(resultFrame.payload.count, expectedPayload.count)
        XCTAssertEqual(resultFrame.payload, expectedPayload)
        XCTAssertEqual(resultFrame.error, expectedError)
    }
    
    func test_processSecondFrameInLongSequence() throws {
        // Arrange
        // HEX 17 FF 00
        // index    type                                            data:
        // 0        header                                          0x17
        //      bit0 :       next                                     1b (frame inside sequence)
        //      bit1..2 :    status                                  11b (more frames to come)
        //      bit3 :       RFU
        //      bit4..7:     sequence number                       0001b (second frame)
        // 1..MAX   payload                                         0xFF 0x00
        let expectedNext = 1
        let expectedStatus = BlePsFtpUtility.RFC76_STATUS_MORE
        let expectedSequenceNumber = 1
        let expectedPayload = Data([0xFF,0x00])
        let expectedError : Int? = nil

        // Act
        let deviceNotifyingData = Data([0x17,0xFF,0x00])
        let resultFrame = try BlePsFtpUtility.processRfc76MessageFrame(deviceNotifyingData)
    
        // Assert
        XCTAssertEqual(resultFrame.next, expectedNext)
        XCTAssertEqual(resultFrame.status, expectedStatus)
        XCTAssertEqual(resultFrame.sequenceNumber, expectedSequenceNumber)
        XCTAssertEqual(resultFrame.payload.count, expectedPayload.count)
        XCTAssertEqual(resultFrame.payload, expectedPayload)
        XCTAssertEqual(resultFrame.error, expectedError)
    }
    
    func test_processLastFrameInLongSequence() throws {
        // Arrange
        // HEX 23 FF 00
        // index    type                                            data:
        // 0        header                                          0x23
        //      bit0 :       next                                     1b (frame inside sequence)
        //      bit1..2 :    status                                  01b (last frame in sequence)
        //      bit3 :       RFU
        //      bit4..7:     sequence number                       0010b (third frame)
        // 1..MAX   payload                                         0xFF 0x00
        let expectedNext = 1
        let expectedStatus = BlePsFtpUtility.RFC76_STATUS_LAST
        let expectedSequenceNumber = 2
        let expectedPayload = Data([0xFF,0x00])
        let expectedError : Int? = nil

        // Act
        let deviceNotifyingData = Data([0x23,0xFF,0x00])
        let resultFrame = try BlePsFtpUtility.processRfc76MessageFrame(deviceNotifyingData)
    
        // Assert
        XCTAssertEqual(resultFrame.next, expectedNext)
        XCTAssertEqual(resultFrame.status, expectedStatus)
        XCTAssertEqual(resultFrame.sequenceNumber, expectedSequenceNumber)
        XCTAssertEqual(resultFrame.payload.count, expectedPayload.count)
        XCTAssertEqual(resultFrame.payload, expectedPayload)
        XCTAssertEqual(resultFrame.error, expectedError)
    }
    
    func test_processErrorFrame() throws {
        // Arrange
        // HEX 00 FE FF
        // index    type                                            data:
        // 0        header                                          0x00
        //      bit0 :       next                                     0b (first frame)
        //      bit1..2 :    status                                  00b (error frame)
        //      bit3 :       RFU
        //      bit4..7:     sequence number                       0000b (first frame)
        // 1..MAX   payload                                         0xFF, 0xFF
        let expectedNext = 0
        let expectedStatus = BlePsFtpUtility.RFC76_STATUS_ERROR_OR_RESPONSE
        let expectedSequenceNumber = 0
        let expectedPayload = Data()
        let expectedError = 0xFFFE

        // Act
        let deviceNotifyingData = Data([0x00, 0xFE, 0xFF])
        let resultFrame = try BlePsFtpUtility.processRfc76MessageFrame(deviceNotifyingData)
    
        // Assert
        XCTAssertEqual(resultFrame.next, expectedNext)
        XCTAssertEqual(resultFrame.status, expectedStatus)
        XCTAssertEqual(resultFrame.sequenceNumber, expectedSequenceNumber)
        XCTAssertTrue(resultFrame.payload == expectedPayload)
        XCTAssertEqual(resultFrame.error, expectedError)
    }
    
    func test_processHeaderOnlyMoreFrame() throws {
        // Arrange
        // HEX 16
        // index    type                                            data:
        // 0        header                                          0x16
        //      bit0 :       next                                     0b (first frame)
        //      bit1..2 :    status                                  11b (MORE - more frames to come)
        //      bit3 :       RFU
        //      bit4..7:     sequence number                       0001b (sequence 1)
        // 1..MAX   payload                                         N/A (0 bytes)
        let expectedNext = 0
        let expectedStatus = BlePsFtpUtility.RFC76_STATUS_MORE
        let expectedSequenceNumber = 1
        let expectedPayload = Data()
        let expectedError: Int? = nil

        // Act
        let deviceNotifyingData = Data([0x16])
        let resultFrame = try BlePsFtpUtility.processRfc76MessageFrame(deviceNotifyingData)

        // Assert
        XCTAssertEqual(resultFrame.next, expectedNext)
        XCTAssertEqual(resultFrame.status, expectedStatus)
        XCTAssertEqual(resultFrame.sequenceNumber, expectedSequenceNumber)
        XCTAssertEqual(resultFrame.payload, expectedPayload)
        XCTAssertEqual(resultFrame.error, expectedError)
    }

    func test_processHeaderOnlyLastFrame() throws {
        // Arrange
        // A header-only LAST frame (status=01, no payload) — valid EOF signal from device.
        //
        // HEX 02
        // index    type                                            data:
        // 0        header                                          0x02
        //      bit0 :       next                                     0b (first frame)
        //      bit1..2 :    status                                  01b (LAST)
        //      bit3 :       RFU
        //      bit4..7:     sequence number                       0000b
        // 1..MAX   payload                                         N/A (0 bytes)
        let expectedNext = 0
        let expectedStatus = BlePsFtpUtility.RFC76_STATUS_LAST
        let expectedSequenceNumber = 0
        let expectedPayload = Data()
        let expectedError: Int? = nil

        // Act
        let deviceNotifyingData = Data([0x02])
        let resultFrame = try BlePsFtpUtility.processRfc76MessageFrame(deviceNotifyingData)

        // Assert
        XCTAssertEqual(resultFrame.next, expectedNext)
        XCTAssertEqual(resultFrame.status, expectedStatus)
        XCTAssertEqual(resultFrame.sequenceNumber, expectedSequenceNumber)
        XCTAssertEqual(resultFrame.payload, expectedPayload)
        XCTAssertEqual(resultFrame.error, expectedError)
    }

    func test_processInteruptFrame() throws {
        // Arrange
        // HEX 00
        // index    type                                            data:
        // 0        header                                          0x00
        //      bit0 :       next                                     0b (first frame)
        //      bit1..2 :    status                                  00b (error frame)
        //      bit3 :       RFU
        //      bit4..7:     sequence number                       0000b (first frame)
        // 1..MAX   payload                                         N/A
        let expectedNext = 0
        let expectedStatus = BlePsFtpUtility.RFC76_STATUS_ERROR_OR_RESPONSE
        let expectedSequenceNumber = 0
        let expectedPayload = Data()
        let expectedError : Int? = nil

        // Act
        let deviceNotifyingData = Data([0x00])
        let resultFrame = try BlePsFtpUtility.processRfc76MessageFrame(deviceNotifyingData)
    
        // Assert
        XCTAssertEqual(resultFrame.next, expectedNext)
        XCTAssertEqual(resultFrame.status, expectedStatus)
        XCTAssertEqual(resultFrame.sequenceNumber, expectedSequenceNumber)
        XCTAssertTrue(resultFrame.payload == expectedPayload)
        XCTAssertEqual(resultFrame.error, expectedError)
    }
}

