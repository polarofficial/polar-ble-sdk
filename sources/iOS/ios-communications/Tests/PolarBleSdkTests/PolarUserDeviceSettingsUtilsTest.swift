// Copyright © 2026 Polar. All rights reserved.

import XCTest
@testable import PolarBleSdk

final class PolarUserDeviceSettingsUtilsTest: XCTestCase {

    // MARK: - Properties

    var mockFtpClient: MockBlePsFtpClient!

    override func setUpWithError() throws {
        mockFtpClient = MockBlePsFtpClient(gattServiceTransmitter: MockPolarGattServiceTransmitter())
    }

    override func tearDownWithError() throws {
        mockFtpClient = nil
    }

    // MARK: - Path constants

    func testDeviceSettingsFilePathConstant() {
        XCTAssertEqual("/U/0/S/UDEVSET.BPB", DEVICE_SETTINGS_FILE_PATH)
    }

    func testSensorSettingsFilePathConstant() {
        XCTAssertEqual("/UDEVSET.BPB", SENSOR_SETTINGS_FILE_PATH)
    }

    // MARK: - Request encoding

    func testGetUserDeviceSettings_sendsRequestWithCorrectPath() async throws {
        mockFtpClient.requestReturnValue = .success(encodedProto())

        _ = try await PolarUserDeviceSettingsUtils
            .getUserDeviceSettings(client: mockFtpClient, deviceSettingsPath: DEVICE_SETTINGS_FILE_PATH)

        XCTAssertEqual(1, mockFtpClient.requestCalls.count)
        let sentOperation = try Protocol_PbPFtpOperation(serializedBytes: mockFtpClient.requestCalls[0])
        XCTAssertEqual(DEVICE_SETTINGS_FILE_PATH, sentOperation.path)
        XCTAssertEqual(.get, sentOperation.command)
    }

    func testGetUserDeviceSettings_sendsGetCommand() async throws {
        mockFtpClient.requestReturnValue = .success(encodedProto())

        _ = try await PolarUserDeviceSettingsUtils
            .getUserDeviceSettings(client: mockFtpClient, deviceSettingsPath: SENSOR_SETTINGS_FILE_PATH)

        let sentOperation = try Protocol_PbPFtpOperation(serializedBytes: mockFtpClient.requestCalls[0])
        XCTAssertEqual(.get, sentOperation.command)
        XCTAssertEqual(SENSOR_SETTINGS_FILE_PATH, sentOperation.path)
    }

    // MARK: - Successful response: deviceLocation

    func testGetUserDeviceSettings_wristLeft_parsedCorrectly() async throws {
        mockFtpClient.requestReturnValue = .success(encodedProto(makeProto(location: .deviceLocationWristLeft)))
        let result = try await awaitResult()
        XCTAssertEqual(PolarUserDeviceSettings.DeviceLocation.WRIST_LEFT, result?.deviceLocation)
    }

    func testGetUserDeviceSettings_wristRight_parsedCorrectly() async throws {
        mockFtpClient.requestReturnValue = .success(encodedProto(makeProto(location: .deviceLocationWristRight)))
        let result = try await awaitResult()
        XCTAssertEqual(PolarUserDeviceSettings.DeviceLocation.WRIST_RIGHT, result?.deviceLocation)
    }

    func testGetUserDeviceSettings_chest_parsedCorrectly() async throws {
        mockFtpClient.requestReturnValue = .success(encodedProto(makeProto(location: .deviceLocationChest)))
        let result = try await awaitResult()
        XCTAssertEqual(PolarUserDeviceSettings.DeviceLocation.CHEST, result?.deviceLocation)
    }

    // MARK: - Successful response: USB connection mode

    func testGetUserDeviceSettings_usbModeOn_parsedCorrectly() async throws {
        var proto = makeProto(location: .deviceLocationWristLeft)
        var usbSettings = Data_PbUsbConnectionSettings()
        usbSettings.mode = .on
        proto.usbConnectionSettings = usbSettings
        mockFtpClient.requestReturnValue = .success(encodedProto(proto))
        let result = try await awaitResult()
        XCTAssertEqual(.ON, result?.usbConnectionMode)
    }

    func testGetUserDeviceSettings_usbModeOff_parsedCorrectly() async throws {
        var proto = makeProto(location: .deviceLocationWristLeft)
        var usbSettings = Data_PbUsbConnectionSettings()
        usbSettings.mode = .off
        proto.usbConnectionSettings = usbSettings
        mockFtpClient.requestReturnValue = .success(encodedProto(proto))
        let result = try await awaitResult()
        XCTAssertEqual(.OFF, result?.usbConnectionMode)
    }

    func testGetUserDeviceSettings_noUsbSettings_usbModeIsNil() async throws {
        mockFtpClient.requestReturnValue = .success(encodedProto(makeProto(location: .deviceLocationWristLeft)))
        let result = try await awaitResult()
        XCTAssertNil(result?.usbConnectionMode)
    }

    // MARK: - Successful response: telemetry

    func testGetUserDeviceSettings_telemetryEnabled_parsedCorrectly() async throws {
        var proto = makeProto(location: .deviceLocationWristLeft)
        var telemetry = Data_PbUserDeviceTelemetrySettings()
        telemetry.telemetryEnabled = true
        proto.telemetrySettings = telemetry
        mockFtpClient.requestReturnValue = .success(encodedProto(proto))
        let result = try await awaitResult()
        XCTAssertEqual(true, result?.telemetryEnabled)
    }

    func testGetUserDeviceSettings_telemetryDisabled_parsedCorrectly() async throws {
        var proto = makeProto(location: .deviceLocationWristLeft)
        var telemetry = Data_PbUserDeviceTelemetrySettings()
        telemetry.telemetryEnabled = false
        proto.telemetrySettings = telemetry
        mockFtpClient.requestReturnValue = .success(encodedProto(proto))
        let result = try await awaitResult()
        XCTAssertEqual(false, result?.telemetryEnabled)
    }

    func testGetUserDeviceSettings_noTelemetrySettings_telemetryIsNil() async throws {
        mockFtpClient.requestReturnValue = .success(encodedProto(makeProto(location: .deviceLocationWristLeft)))
        let result = try await awaitResult()
        XCTAssertNil(result?.telemetryEnabled)
    }

    // MARK: - Successful response: automatic training detection

    func testGetUserDeviceSettings_trainingDetectionOn_parsedCorrectly() async throws {
        var proto = makeProto(location: .deviceLocationWristLeft)
        var atdSettings = Data_PbAutomaticTrainingDetectionSettings()
        atdSettings.state = .on
        atdSettings.sensitivity = 75
        atdSettings.minimumTrainingDurationSeconds = 300
        proto.automaticMeasurementSettings.automaticTrainingDetectionSettings = atdSettings
        mockFtpClient.requestReturnValue = .success(encodedProto(proto))
        let result = try await awaitResult()
        XCTAssertEqual(.ON, result?.automaticTrainingDetectionMode)
        XCTAssertEqual(75, result?.automaticTrainingDetectionSensitivity)
        XCTAssertEqual(300, result?.minimumTrainingDurationSeconds)
    }

    // MARK: - Error handling

    func testGetUserDeviceSettings_clientError_propagatesError() async throws {
        mockFtpClient.requestReturnValue = .failure(PolarErrors.serviceNotFound)
        do {
            _ = try await PolarUserDeviceSettingsUtils
                .getUserDeviceSettings(client: mockFtpClient, deviceSettingsPath: DEVICE_SETTINGS_FILE_PATH)
            XCTFail("Expected error to be thrown")
        } catch {
            XCTAssertNotNil(error)
        }
    }

    func testGetUserDeviceSettings_invalidProtoData_propagatesError() async throws {
        mockFtpClient.requestReturnValue = .success(Data([0xFF, 0xFF, 0xFF, 0xFF]))
        do {
            _ = try await PolarUserDeviceSettingsUtils
                .getUserDeviceSettings(client: mockFtpClient, deviceSettingsPath: DEVICE_SETTINGS_FILE_PATH)
            XCTFail("Expected error to be thrown")
        } catch {
            XCTAssertNotNil(error)
        }
    }
}

// MARK: - Helpers

extension PolarUserDeviceSettingsUtilsTest {

    private func makeValidProto() -> Data_PbUserDeviceSettings {
        var proto = Data_PbUserDeviceSettings()
        proto.generalSettings = Data_PbUserDeviceGeneralSettings()
        proto.lastModified = PolarTimeUtils.dateToPbSystemDateTime(date: Date())
        proto.automaticMeasurementSettings.automaticTrainingDetectionSettings.sensitivity = 50
        proto.automaticMeasurementSettings.automaticTrainingDetectionSettings.minimumTrainingDurationSeconds = 600
        return proto
    }

    private func makeProto(location: PbDeviceLocation) -> Data_PbUserDeviceSettings {
        var proto = makeValidProto()
        proto.generalSettings.deviceLocation = location
        return proto
    }

    private func encodedProto(_ proto: Data_PbUserDeviceSettings? = nil) -> Data {
        return try! (proto ?? makeValidProto()).serializedData()
    }

    private func awaitResult(
        path: String = DEVICE_SETTINGS_FILE_PATH
    ) async throws -> PolarUserDeviceSettings.PolarUserDeviceSettingsResult? {
        return try await PolarUserDeviceSettingsUtils
            .getUserDeviceSettings(client: mockFtpClient, deviceSettingsPath: path)
    }
}
