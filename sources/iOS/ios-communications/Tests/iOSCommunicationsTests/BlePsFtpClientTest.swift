//  Copyright © 2021 Polar. All rights reserved.

import XCTest
import iOSCommunications
import CoreBluetooth
import RxBlocking
import RxSwift

class BlePsFtpClientTest: XCTestCase {
    var blePsFtpClient:BlePsFtpClient!
    var mockGattServiceTransmitterImpl:MockGattServiceTransmitterImpl!
    
    override func setUpWithError() throws {
        mockGattServiceTransmitterImpl = MockGattServiceTransmitterImpl()
        blePsFtpClient = BlePsFtpClient(gattServiceTransmitter: mockGattServiceTransmitterImpl)
    }
    
    override func tearDownWithError() throws {
        mockGattServiceTransmitterImpl = nil
        blePsFtpClient = nil
    }
    
    // GIVEN that BLE PSFTP Service client is listening rfc76 notifications
    // WHEN BLE PSFTP service sends rfc76 single frame notification
    // THEN BLE PSFTP Service client emits the rfc76 payload to subscriber
    func testWaitNotificationSingleFrame() throws {
        // Arrange
        // rfc76 header:
        //      bit0 :       next                                     0b (first frame)
        //      bit1..2 :    status                                  01b (last frame)
        //      bit3 :       RFU
        //      bit4..7:     sequence number                       0000b (first frame)
        let rfc76Header = Data([0x02])
        var rfc76Payload = Data()
        let psftpNotifcationId = Data([0x01])
        let psftpNotificationParams = Data([0xFF,0x00])
        rfc76Payload.append(psftpNotifcationId)
        rfc76Payload.append(psftpNotificationParams)
        var notificationFromDevice = Data()
        notificationFromDevice.append(rfc76Header)
        notificationFromDevice.append(rfc76Payload)
        
        let characteristic: CBUUID = BlePsFtpClient.PSFTP_D2H_NOTIFICATION_CHARACTERISTIC
        let error = 0
        
        // Act
        blePsFtpClient.processServiceData(characteristic, data: notificationFromDevice, err: error)
        let event = try blePsFtpClient.waitNotification().toBlocking().first()
        
        // Assert
        XCTAssertEqual(Int32(psftpNotifcationId[0]), event!.id)
        XCTAssertTrue(event!.parameters.isEqual(to: psftpNotificationParams))
    }
    
    // GIVEN that BLE PSFTP Service client is listening rfc76 notifications
    // WHEN BLE PSFTP service sends rfc76 multi frame notification
    // THEN BLE PSFTP Service client emits the combined payload from rfc76 notifications to subscriber
    func testWaitNotificationMultiFrame() throws {
        // Arrange
        let psftpNotifcationId = Data([0x01])
        let psftpNotificationParams1 = Data([0xFF,0x00])
        let psftpNotificationParams2 = Data([0xEF,0xFE])
        
        // rfc76 header 1:
        //      bit0 :       next                                     0b (first frame)
        //      bit1..2 :    status                                  11b (more to come)
        //      bit3 :       RFU
        //      bit4..7:     sequence number                       0000b (first frame)
        let rfc76Header1 = Data([0x06])
        var rfc76Payload1 = Data()
        rfc76Payload1.append(psftpNotifcationId)
        rfc76Payload1.append(psftpNotificationParams1)
        
        // rfc76 header 2:
        //      bit0 :       next                                     1b (subsequent frame)
        //      bit1..2 :    status                                  01b (last frame)
        //      bit3 :       RFU
        //      bit4..7:     sequence number                       0001b (second frame)
        let rfc76Header2 = Data([0x13])
        var rfc76Payload2 = Data()
        rfc76Payload2.append(psftpNotificationParams2)
        
        var notificationFromDeviceData1 = Data()
        notificationFromDeviceData1.append(rfc76Header1)
        notificationFromDeviceData1.append(rfc76Payload1)
        
        var notificationFromDeviceData2 = Data()
        notificationFromDeviceData2.append(rfc76Header2)
        notificationFromDeviceData2.append(rfc76Payload2)
        
        let characteristic: CBUUID = BlePsFtpClient.PSFTP_D2H_NOTIFICATION_CHARACTERISTIC
        let error = 0
        
        // Act
        blePsFtpClient.processServiceData(characteristic, data: notificationFromDeviceData1, err: error)
        blePsFtpClient.processServiceData(characteristic, data: notificationFromDeviceData2, err: error)
        let event = try blePsFtpClient.waitNotification().toBlocking().first()
        
        // Assert
        XCTAssertEqual(Int32(psftpNotifcationId[0]), event!.id)
        var expectedParameters = Data()
        expectedParameters.append(psftpNotificationParams1)
        expectedParameters.append(psftpNotificationParams2)
        
        XCTAssertTrue(event!.parameters.isEqual(to: expectedParameters))
    }
    
    // GIVEN that BLE PSFTP Service client is listening rfc76 notifications
    // WHEN BLE PSFTP service sends rfc76 error notification in first frame
    // THEN BLE PSFTP Service client shall ignore the received rfc76 error frame, emit nothing (not even error) and continue waiting more frames (i.e. test should timeout)
    func testWaitNotificationErrorInFirstFrame() throws {
        // Arrange
        let psftpNotificationErrorParams = Data([0xFF,0x00])
        
        // rfc76 header:
        //      bit0 :       next                                     0b (first frame)
        //      bit1..2 :    status                                  00b (error)
        //      bit3 :       RFU
        //      bit4..7:     sequence number                       0000b (first frame)
        let rfc76Header = Data([0x00])
        var rfc76Payload = Data()
        rfc76Payload.append(psftpNotificationErrorParams)
        var notificationFromDevice = Data()
        notificationFromDevice.append(rfc76Header)
        notificationFromDevice.append(rfc76Payload)
        
        let characteristic: CBUUID = BlePsFtpClient.PSFTP_D2H_NOTIFICATION_CHARACTERISTIC
        let error = 0
        
        // Act && Assert
        blePsFtpClient.processServiceData(characteristic, data: notificationFromDevice, err: error)
        XCTAssertThrowsError( try blePsFtpClient.waitNotification().toBlocking(timeout: 1).first()) { error in
            guard case RxError.timeout = error else {
                return XCTFail()
            }
        }
    }
    
    // GIVEN that BLE PSFTP Service client is listening rfc76 notifications
    // WHEN BLE PSFTP service sends rfc76 error notification in second frame
    // THEN BLE PSFTP Service client shall ignore the received rfc76 error frame, emit nothing (not even error) and continue waiting more frames
    func testWaitNotificationErrorInSecondFrame() throws {
        // Arrange
        let psftpNotifcationId = Data([0x01])
        let psftpNotificationParams1 = Data([0xFF,0x00])
        let psftpNotificationParams2 = Data([0xEF,0xFE])
        
        // rfc76 header 1:
        //      bit0 :       next                                     0b (first frame)
        //      bit1..2 :    status                                  11b (more to come)
        //      bit3 :       RFU
        //      bit4..7:     sequence number                       0000b (first frame)
        let rfc76Header1 = Data([0x06])
        var rfc76Payload1 = Data()
        rfc76Payload1.append(psftpNotifcationId)
        rfc76Payload1.append(psftpNotificationParams1)
        
        // rfc76 header 2:
        //      bit0 :       next                                     1b (subsequent frame)
        //      bit1..2 :    status                                  00b (error)
        //      bit3 :       RFU
        //      bit4..7:     sequence number                       0001b (second frame)
        let rfc76Header2 = Data([0x11])
        var rfc76Payload2 = Data()
        rfc76Payload2.append(psftpNotificationParams2)
        
        var notificationFromDeviceData1 = Data()
        notificationFromDeviceData1.append(rfc76Header1)
        notificationFromDeviceData1.append(rfc76Payload1)
        
        var notificationFromDeviceData2 = Data()
        notificationFromDeviceData2.append(rfc76Header2)
        notificationFromDeviceData2.append(rfc76Payload2)
        
        let characteristic: CBUUID = BlePsFtpClient.PSFTP_D2H_NOTIFICATION_CHARACTERISTIC
        let error = 0
        
        // Act & Assert
        blePsFtpClient.processServiceData(characteristic, data: notificationFromDeviceData1, err: error)
        blePsFtpClient.processServiceData(characteristic, data: notificationFromDeviceData2, err: error)
        
        XCTAssertThrowsError( try blePsFtpClient.waitNotification().toBlocking(timeout: 1).first()) { error in
            guard case RxError.timeout = error else {
                return XCTFail()
            }
        }
    }
    
    // GIVEN that BLE PSFTP Service client is listening rfc76 notifications
    // WHEN BLE PSFTP service client receives error packet
    // THEN BLE PSFTP Service client shall stop observable onError
    func testWaitNotificationErrorInFirstPackage() throws {
        // Arrange
        let psftpNotificationErrorParams = Data([0xFF,0x00])
        
        // rfc76 header:
        //      bit0 :       next                                     0b (first frame)
        //      bit1..2 :    status                                  00b (error)
        //      bit3 :       RFU
        //      bit4..7:     sequence number                       0000b (first frame)
        let rfc76Header = Data([0x00])
        var rfc76Payload = Data()
        rfc76Payload.append(psftpNotificationErrorParams)
        var notificationFromDevice = Data()
        notificationFromDevice.append(rfc76Header)
        notificationFromDevice.append(rfc76Payload)
        
        let characteristic: CBUUID = BlePsFtpClient.PSFTP_D2H_NOTIFICATION_CHARACTERISTIC
        let expectedError:Int = 1
        
        // Act
        blePsFtpClient.processServiceData(characteristic, data: notificationFromDevice, err: expectedError)
        let result = blePsFtpClient.waitNotification().toBlocking().materialize()
        
        // Assert
        switch result {
        case .completed(_):
            XCTFail("Observable should fail instead of complete")
            
        case .failed(_, let error):
            guard case BlePsFtpException.responseError(errorCode:expectedError) = error else {
                return XCTFail()
            }
        }
    }
    
    // GIVEN that BLE PSFTP Service client is listening rfc76 notifications
    // WHEN BLE PSFTP service client receives error in second packet
    // THEN BLE PSFTP Service client shall stop observable onError
    func testWaitNotificationErrorInSecondPackage() throws {
        // Arrange
        let psftpNotifcationId = Data([0x01])
        let psftpNotificationParams1 = Data([0xFF,0x00])
        let psftpNotificationParams2 = Data([0xEF,0xFE])
        
        // rfc76 header 1:
        //      bit0 :       next                                     0b (first frame)
        //      bit1..2 :    status                                  11b (more to come)
        //      bit3 :       RFU
        //      bit4..7:     sequence number                       0000b (first frame)
        let rfc76Header1 = Data([0x06])
        var rfc76Payload1 = Data()
        rfc76Payload1.append(psftpNotifcationId)
        rfc76Payload1.append(psftpNotificationParams1)
        
        // rfc76 header 2:
        //      bit0 :       next                                     1b (subsequent frame)
        //      bit1..2 :    status                                  01b (last frame)
        //      bit3 :       RFU
        //      bit4..7:     sequence number                       0001b (second frame)
        let rfc76Header2 = Data([0x13])
        var rfc76Payload2 = Data()
        rfc76Payload2.append(psftpNotificationParams2)
        
        var notificationFromDeviceData1 = Data()
        notificationFromDeviceData1.append(rfc76Header1)
        notificationFromDeviceData1.append(rfc76Payload1)
        
        var notificationFromDeviceData2 = Data()
        notificationFromDeviceData2.append(rfc76Header2)
        notificationFromDeviceData2.append(rfc76Payload2)
        
        let characteristic: CBUUID = BlePsFtpClient.PSFTP_D2H_NOTIFICATION_CHARACTERISTIC
        let expectedError = 255
        
        // Act
        blePsFtpClient.processServiceData(characteristic, data: notificationFromDeviceData1, err: 0)
        blePsFtpClient.processServiceData(characteristic, data: notificationFromDeviceData2, err: expectedError)
        let result = blePsFtpClient.waitNotification().toBlocking().materialize()
        
        // Assert
        switch result {
        case .completed(_):
            XCTFail("Observable should fail instead of complete")
            
        case .failed(_, let error):
            guard case BlePsFtpException.responseError(errorCode:expectedError) = error else {
                return XCTFail()
            }
        }
    }
}
