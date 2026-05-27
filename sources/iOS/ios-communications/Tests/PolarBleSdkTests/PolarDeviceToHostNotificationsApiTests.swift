//  Copyright © 2026 Polar. All rights reserved.

import XCTest

@testable import PolarBleSdk

class PolarDeviceToHostNotificationsApiTests: XCTestCase {
    
    var mockClient: MockBlePsFtpClient!
    var mockSession: MockBleDeviceSession!
    var api: PolarBleApiImplWithMockSession!
    let deviceId = "123456"
    
    override func setUpWithError() throws {
        mockClient = MockBlePsFtpClient(gattServiceTransmitter: MockPolarGattServiceTransmitter())
        mockSession = MockBleDeviceSession(mockFtpClient: mockClient)
        api = PolarBleApiImplWithMockSession(mockDeviceSession: mockSession)
    }
    
    override func tearDownWithError() throws {
        mockClient = nil
        mockSession = nil
        api = nil
    }
    
    func testReceivesSyncRequiredNotification() async throws {
        // Arrange
        let syncRequiredNotificationId = Protocol_PbPFtpDevToHostNotification.syncRequired.rawValue
        var syncRequiredNotificationParameter = Protocol_PbPFtpSyncRequiredParams()
        var syncTrigger = Protocol_PbPFtpSyncTrigger()
        syncTrigger.source = .timed
        syncRequiredNotificationParameter.syncTriggers = [syncTrigger]
        let syncRequiredNotificationParamsData = try syncRequiredNotificationParameter.serializedData()
        
        let keepAliveNotificationId = Protocol_PbPFtpDevToHostNotification.keepBackgroundAlive.rawValue
        
        mockClient.receiveNotificationCalls.append(contentsOf: [
            (syncRequiredNotificationId, [syncRequiredNotificationParamsData], false),
            (keepAliveNotificationId, [Data()], false)
        ])
        
        // Act
        let results = try await collectStream(api.observeDeviceToHostNotifications(identifier: deviceId))
        
        // Assert
        XCTAssertEqual(results.count, 2)
        
        XCTAssertEqual(results[0].notificationType, PolarDeviceToHostNotification.syncRequired)
        XCTAssertEqual(results[0].parameters, syncRequiredNotificationParamsData)
        XCTAssertNotNil(results[0].parsedParameters)
        XCTAssertTrue(results[0].parsedParameters is Protocol_PbPFtpSyncRequiredParams)
        let parsedParams = results[0].parsedParameters as! Protocol_PbPFtpSyncRequiredParams
        XCTAssertEqual(parsedParams, syncRequiredNotificationParameter)
        
        XCTAssertEqual(results[1].notificationType, PolarDeviceToHostNotification.keepBackgroundAlive)
        XCTAssertEqual(results[1].parameters.count, 0)
    }
    
    func testReceivesFilesystemModifiedNotification() async throws {
        // Arrange
        let notificationId = Protocol_PbPFtpDevToHostNotification.filesystemModified.rawValue
        var fileSystemModifiedParams = Protocol_PbPFtpFilesystemModifiedParams()
        fileSystemModifiedParams.action = .created
        fileSystemModifiedParams.path = "/U/0/"
        let serializedData = try fileSystemModifiedParams.serializedData()
        
        mockClient.receiveNotificationCalls.append(contentsOf: [(notificationId, [serializedData], false)])
        
        // Act
        let result = try await collectStream(api.observeDeviceToHostNotifications(identifier: deviceId)).last
        
        // Assert
        XCTAssertNotNil(result)
        XCTAssertEqual(result!.notificationType, PolarDeviceToHostNotification.filesystemModified)
        XCTAssertEqual(result!.parameters, serializedData)
        XCTAssertNotNil(result!.parsedParameters)
        XCTAssertTrue(result!.parsedParameters is Protocol_PbPFtpFilesystemModifiedParams)
        let parsedParams = result!.parsedParameters as! Protocol_PbPFtpFilesystemModifiedParams
        XCTAssertEqual(parsedParams, fileSystemModifiedParams)
    }
    
    func testReceivesInactivityAlertNotification() async throws {
        // Arrange
        let notificationId = Protocol_PbPFtpDevToHostNotification.inactivityAlert.rawValue
        var inactivityAlertParams = Protocol_PbPFtpInactivityAlert()
        inactivityAlertParams.countdown = 5
        let serializedData = try inactivityAlertParams.serializedData()
        
        mockClient.receiveNotificationCalls.append(contentsOf: [(notificationId, [serializedData], false)])
        
        // Act
        let result = try await collectStream(api.observeDeviceToHostNotifications(identifier: deviceId)).first
        
        // Assert
        XCTAssertNotNil(result)
        XCTAssertEqual(result!.notificationType, PolarDeviceToHostNotification.inactivityAlert)
        XCTAssertEqual(result!.parameters, serializedData)
        XCTAssertNotNil(result!.parsedParameters)
        XCTAssertTrue(result!.parsedParameters is Protocol_PbPFtpInactivityAlert)
        let parsedParams = result!.parsedParameters as! Protocol_PbPFtpInactivityAlert
        XCTAssertEqual(parsedParams.countdown, 5)
    }
    
    func testReceivesTrainingSessionStatusNotification() async throws {
        // Arrange
        let notificationId = Protocol_PbPFtpDevToHostNotification.trainingSessionStatus.rawValue
        var trainingSessionStatus = Protocol_PbPFtpTrainingSessionStatus()
        trainingSessionStatus.inprogress = true
        let serializedData = try trainingSessionStatus.serializedData()
        
        mockClient.receiveNotificationCalls.append(contentsOf: [(notificationId, [serializedData], false)])
        
        // Act
        let result = try await collectStream(api.observeDeviceToHostNotifications(identifier: deviceId)).first
        
        // Assert
        XCTAssertNotNil(result)
        XCTAssertEqual(result!.notificationType, PolarDeviceToHostNotification.trainingSessionStatus)
        XCTAssertEqual(result!.parameters, serializedData)
        XCTAssertNotNil(result!.parsedParameters)
        XCTAssertTrue(result!.parsedParameters is Protocol_PbPFtpTrainingSessionStatus)
        let parsedParams = result!.parsedParameters as! Protocol_PbPFtpTrainingSessionStatus
        XCTAssertTrue(parsedParams.inprogress)
    }
    
    func testReceivesAutosyncStatusNotification() async throws {
        // Arrange
        let notificationId = Protocol_PbPFtpDevToHostNotification.autosyncStatus.rawValue
        var autoSyncStatus = Protocol_PbPFtpAutoSyncStatusParams()
        autoSyncStatus.succeeded = true
        autoSyncStatus.description_p = "Sync completed successfully"
        let serializedData = try autoSyncStatus.serializedData()
        
        mockClient.receiveNotificationCalls.append(contentsOf: [(notificationId, [serializedData], false)])
        
        // Act
        let result = try await collectStream(api.observeDeviceToHostNotifications(identifier: deviceId)).first
        
        // Assert
        XCTAssertNotNil(result)
        XCTAssertEqual(result!.notificationType, PolarDeviceToHostNotification.autosyncStatus)
        XCTAssertEqual(result!.parameters, serializedData)
        XCTAssertNotNil(result!.parsedParameters)
        XCTAssertTrue(result!.parsedParameters is Protocol_PbPFtpAutoSyncStatusParams)
        let parsedParams = result!.parsedParameters as! Protocol_PbPFtpAutoSyncStatusParams
        XCTAssertTrue(parsedParams.succeeded)
        XCTAssertEqual(parsedParams.description_p, "Sync completed successfully")
    }
    
    func testReceivesNotificationWithoutParameters() async throws {
        // Arrange
        let notificationId = Protocol_PbPFtpDevToHostNotification.stopGpsMeasurement.rawValue
        mockClient.receiveNotificationCalls.append(contentsOf: [(notificationId, [Data()], false)])
        
        // Act
        let result = try await collectStream(api.observeDeviceToHostNotifications(identifier: deviceId)).first
        
        // Assert
        XCTAssertNotNil(result)
        XCTAssertEqual(result!.notificationType, PolarDeviceToHostNotification.stopGpsMeasurement)
        XCTAssertEqual(result!.parameters.count, 0)
        XCTAssertNil(result!.parsedParameters)
    }
    
    func testFiltersUnknownNotificationTypes() async throws {
        // Arrange
        mockClient.receiveNotificationCalls.append(contentsOf: [
            (999, [Data()], false),
            (Protocol_PbPFtpDevToHostNotification.idling.rawValue, [Data()], false)
        ])
        
        // Act
        let results = try await collectStream(api.observeDeviceToHostNotifications(identifier: deviceId))
        
        // Assert
        XCTAssertEqual(results.count, 1)
        XCTAssertEqual(results[0].notificationType, PolarDeviceToHostNotification.idling)
    }
    
    func testHandlesInvalidProtobufDataGracefully() async throws {
        // Arrange
        let notificationId = Protocol_PbPFtpDevToHostNotification.syncRequired.rawValue
        let invalidData = "invalid protobuf data".data(using: .utf8)!
        mockClient.receiveNotificationCalls.append(contentsOf: [(notificationId, [invalidData], false)])
        
        // Act
        let result = try await collectStream(api.observeDeviceToHostNotifications(identifier: deviceId)).first
        
        // Assert
        XCTAssertNotNil(result)
        XCTAssertEqual(result!.notificationType, PolarDeviceToHostNotification.syncRequired)
        XCTAssertEqual(result!.parameters, invalidData)
        XCTAssertNil(result!.parsedParameters)
    }
    
    func testReceivesMediaControlRequestNotification() async throws {
        // Arrange
        let notificationId = Protocol_PbPFtpDevToHostNotification.mediaControlRequestDh.rawValue
        var mediaControlRequest = Protocol_PbPftpDHMediaControlRequest()
        mediaControlRequest.request = .getMediaData
        let serializedData = try mediaControlRequest.serializedData()
        mockClient.receiveNotificationCalls.append(contentsOf: [(notificationId, [serializedData], false)])
        
        // Act
        let result = try await collectStream(api.observeDeviceToHostNotifications(identifier: deviceId)).first
        
        // Assert
        XCTAssertNotNil(result)
        XCTAssertEqual(result!.notificationType, PolarDeviceToHostNotification.mediaControlRequestDh)
        XCTAssertEqual(result!.parameters, serializedData)
        XCTAssertNotNil(result!.parsedParameters)
        XCTAssertTrue(result!.parsedParameters is Protocol_PbPftpDHMediaControlRequest)
        let parsedParams = result!.parsedParameters as! Protocol_PbPftpDHMediaControlRequest
        XCTAssertEqual(parsedParams.request, .getMediaData)
    }
    
    func testReceivesMediaControlCommandNotification() async throws {
        // Arrange
        let notificationId = Protocol_PbPFtpDevToHostNotification.mediaControlCommandDh.rawValue
        var mediaControlCommand = Protocol_PbPftpDHMediaControlCommand()
        mediaControlCommand.command = .play
        let serializedData = try mediaControlCommand.serializedData()
        mockClient.receiveNotificationCalls.append(contentsOf: [(notificationId, [serializedData], false)])
        
        // Act
        let result = try await collectStream(api.observeDeviceToHostNotifications(identifier: deviceId)).first
        
        // Assert
        XCTAssertNotNil(result)
        XCTAssertEqual(result!.notificationType, PolarDeviceToHostNotification.mediaControlCommandDh)
        XCTAssertEqual(result!.parameters, serializedData)
        XCTAssertNotNil(result!.parsedParameters)
        XCTAssertTrue(result!.parsedParameters is Protocol_PbPftpDHMediaControlCommand)
        let parsedParams = result!.parsedParameters as! Protocol_PbPftpDHMediaControlCommand
        XCTAssertEqual(parsedParams.command, .play)
    }
    
    func testReceivesStartGpsMeasurementNotification() async throws {
        // Arrange
        let notificationId = Protocol_PbPFtpDevToHostNotification.startGpsMeasurement.rawValue
        var startGpsMeasurement = Protocol_PbPftpStartGPSMeasurement()
        startGpsMeasurement.minimumInterval = 1000
        startGpsMeasurement.accuracy = 2
        startGpsMeasurement.latitude = 60.1695
        startGpsMeasurement.longitude = 24.9354
        let serializedData = try startGpsMeasurement.serializedData()
        mockClient.receiveNotificationCalls.append(contentsOf: [(notificationId, [serializedData], false)])
        
        // Act
        let result = try await collectStream(api.observeDeviceToHostNotifications(identifier: deviceId)).first
        
        // Assert
        XCTAssertNotNil(result)
        XCTAssertEqual(result!.notificationType, PolarDeviceToHostNotification.startGpsMeasurement)
        XCTAssertEqual(result!.parameters, serializedData)
        XCTAssertNotNil(result!.parsedParameters)
        XCTAssertTrue(result!.parsedParameters is Protocol_PbPftpStartGPSMeasurement)
        let parsedParams = result!.parsedParameters as! Protocol_PbPftpStartGPSMeasurement
        XCTAssertEqual(parsedParams.minimumInterval, 1000)
        XCTAssertEqual(parsedParams.accuracy, 2)
        XCTAssertEqual(parsedParams.latitude, 60.1695, accuracy: 0.0001)
        XCTAssertEqual(parsedParams.longitude, 24.9354, accuracy: 0.0001)
    }

    // MARK: - Helpers

    private func collectStream<T>(_ stream: AsyncThrowingStream<T, Error>) async throws -> [T] {
        var results: [T] = []
        for try await value in stream { results.append(value) }
        return results
    }
}
