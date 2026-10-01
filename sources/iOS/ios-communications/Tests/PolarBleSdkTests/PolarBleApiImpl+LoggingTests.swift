import Foundation
import XCTest
@testable import PolarBleSdk

final class PolarBleApiImplLoggingTests: XCTestCase {

    private let identifier = "TEST_DEVICE"
    private var mockClient: MockBlePsFtpClient!
    private var api: PolarBleApiImplWithMockSession!

    override func setUpWithError() throws {
        BlePolarDeviceCapabilitiesUtility.resetAndInitializeForTesting(
            deviceFileSystemTypes: [
                "360": .polarFileSystemV2,
                "h10": .h10FileSystem
            ]
        )
        mockClient = MockBlePsFtpClient(gattServiceTransmitter: MockPolarGattServiceTransmitter())
        let session = MockBleDeviceSession(mockFtpClient: mockClient)
        api = PolarBleApiImplWithMockSession(mockDeviceSession: session)
    }

    override func tearDownWithError() throws {
        api = nil
        mockClient = nil
    }

    // MARK: - exportDeviceLogs

    func testExportDeviceLogs_fetchesStaticAndIndexedFilesUntilFirstGap() async throws {
        let files: [String: Data] = [
            "/ERRORLOG.BPB": Data("err1".utf8),
            "/SYSLOG.TXT": Data("sys".utf8),
            "/TRC1.BIN": Data([0x01]),
            "/TRC2.BIN": Data([0x02]),
            "/DBGTRC1.BIN": Data([0xA1])
        ]
        mockClient.requestReturnValueClosure = { header in
            let op = try Protocol_PbPFtpOperation(serializedBytes: header)
            if let data = files[op.path] { return data }
            throw BlePsFtpException.responseError(errorCode: 103)
        }

        let result = try await api.exportDeviceLogs(identifier)

        XCTAssertEqual(result.map(\.path), [
            "/ERRORLOG.BPB",
            "/SYSLOG.TXT",
            "/TRC1.BIN",
            "/TRC2.BIN",
            "/DBGTRC1.BIN"
        ])

        let requested = try mockClient.requestCalls.map { try Protocol_PbPFtpOperation(serializedBytes: $0).path }
        XCTAssertEqual(requested, [
            "/ERRORLOG.BPB",
            "/ERRORLO2.BPB",
            "/SYSLOG.TXT",
            "/TRC1.BIN",
            "/TRC2.BIN",
            "/TRC3.BIN",
            "/DBGTRC1.BIN",
            "/DBGTRC2.BIN"
        ])
    }

    func testExportDeviceLogs_throwsServiceNotFoundWhenFtpClientMissing() async {
        let noFtpApi = PolarBleApiImplWithNoFtpSession(mockDeviceSession: MockNoFtpClientBleDeviceSession())

        do {
            _ = try await noFtpApi.exportDeviceLogs(identifier)
            XCTFail("Expected serviceNotFound")
        } catch let error as PolarErrors {
            if case .serviceNotFound = error { }
            else { XCTFail("Expected serviceNotFound, got \(error)") }
        } catch {
            XCTFail("Expected PolarErrors.serviceNotFound, got \(error)")
        }
    }

    func testExportDeviceLogs_throwsOperationNotSupportedForNonV2Device() async {
        let h10Client = MockBlePsFtpClient(gattServiceTransmitter: MockPolarGattServiceTransmitter())
        let h10Api = PolarBleApiImplWithMockH10Session(mockDeviceSession: MockH10BleDeviceSession(mockFtpClient: h10Client))

        do {
            _ = try await h10Api.exportDeviceLogs(identifier)
            XCTFail("Expected operationNotSupported")
        } catch let error as PolarErrors {
            if case .operationNotSupported = error { }
            else { XCTFail("Expected operationNotSupported, got \(error)") }
        } catch {
            XCTFail("Expected PolarErrors.operationNotSupported, got \(error)")
        }
    }

    // MARK: - getLogConfig

    func testGetLogConfig_returnsDecodedConfig_andWrapsSessionNotifications() async throws {
        let proto = makeSensorDataLogProto(
            ohrEnabled: true,
            ppiEnabled: false,
            hrDeviceInformation: "POLAR-HR-TEST",
            hrWaitUntilConnected: true
        )
        mockClient.requestReturnValue = .success(try proto.serializedData())

        let result = try await api.getLogConfig(identifier)

        XCTAssertEqual(result.ohrLogEnabled, true)
        XCTAssertEqual(result.ppiLogEnabled, false)
        XCTAssertEqual(result.hrSensorConfig?.deviceInformation, "POLAR-HR-TEST")
        XCTAssertEqual(result.hrSensorConfig?.waitUntilConnected, true)

        XCTAssertEqual(mockClient.sendNotificationCalls.count, 2)
        XCTAssertEqual(mockClient.sendNotificationCalls[0].notification, Protocol_PbPFtpHostToDevNotification.initializeSession.rawValue)
        XCTAssertEqual(mockClient.sendNotificationCalls[1].notification, Protocol_PbPFtpHostToDevNotification.terminateSession.rawValue)

        XCTAssertEqual(mockClient.requestCalls.count, 1)
        let request = try Protocol_PbPFtpOperation(serializedBytes: mockClient.requestCalls[0])
        XCTAssertEqual(request.command, .get)
        XCTAssertEqual(request.path, LOG_CONFIG_PATH)
    }

    func testGetLogConfig_throwsServiceNotFoundWhenFtpClientMissing() async {
        let noFtpApi = PolarBleApiImplWithNoFtpSession(mockDeviceSession: MockNoFtpClientBleDeviceSession())

        do {
            _ = try await noFtpApi.getLogConfig(identifier)
            XCTFail("Expected serviceNotFound")
        } catch let error as PolarErrors {
            if case .serviceNotFound = error { }
            else { XCTFail("Expected serviceNotFound, got \(error)") }
        } catch {
            XCTFail("Expected PolarErrors.serviceNotFound, got \(error)")
        }
    }

    func testGetLogConfig_throwsOperationNotSupportedForNonV2Device() async {
        let h10Client = MockBlePsFtpClient(gattServiceTransmitter: MockPolarGattServiceTransmitter())
        let h10Api = PolarBleApiImplWithMockH10Session(mockDeviceSession: MockH10BleDeviceSession(mockFtpClient: h10Client))

        do {
            _ = try await h10Api.getLogConfig(identifier)
            XCTFail("Expected operationNotSupported")
        } catch let error as PolarErrors {
            if case .operationNotSupported = error { }
            else { XCTFail("Expected operationNotSupported, got \(error)") }
        } catch {
            XCTFail("Expected PolarErrors.operationNotSupported, got \(error)")
        }
    }

    func testGetLogConfig_whenPayloadIsInvalid_throwsDecodingError() async {
        mockClient.requestReturnValue = .success(Data([0x00, 0x01]))

        do {
            _ = try await api.getLogConfig(identifier)
            XCTFail("Expected decoding error")
        } catch {
            XCTAssertNotNil(error)
        }

        // Current implementation terminates session only after successful decoding.
        XCTAssertEqual(mockClient.sendNotificationCalls.count, 1)
        XCTAssertEqual(mockClient.sendNotificationCalls.first?.notification, Protocol_PbPFtpHostToDevNotification.initializeSession.rawValue)
    }

    // MARK: - setLogConfig

    func testSetLogConfig_writesPutRequestAndSerializedLogConfig() async throws {
        let config = makeLogConfig(ohrEnabled: true, ppiEnabled: false)

        try await api.setLogConfig(identifier, logConfig: config)

        XCTAssertEqual(mockClient.writeCalls.count, 1)
        let writeCall = mockClient.writeCalls[0]
        let request = try Protocol_PbPFtpOperation(serializedBytes: writeCall.header as Data)
        XCTAssertEqual(request.command, .put)
        XCTAssertEqual(request.path, LOG_CONFIG_PATH)

        let payload = readAll(from: writeCall.data)
        let writtenProto = try Data_PbSensorDataLog(serializedBytes: payload)
        XCTAssertEqual(writtenProto.ohrLogEnabled, true)
        XCTAssertEqual(writtenProto.ppiLogEnabled, false)

        XCTAssertTrue(writtenProto.hasBleHrSensorConfig)
        XCTAssertEqual(writtenProto.bleHrSensorConfig.deviceName.name, "POLAR-HR-TEST")
        XCTAssertEqual(writtenProto.bleHrSensorConfig.waitForConnect, true)
    }

    func testSetLogConfig_throwsServiceNotFoundWhenFtpClientMissing() async {
        let noFtpApi = PolarBleApiImplWithNoFtpSession(mockDeviceSession: MockNoFtpClientBleDeviceSession())
        let config = makeLogConfig(ohrEnabled: true, ppiEnabled: true)

        do {
            try await noFtpApi.setLogConfig(identifier, logConfig: config)
            XCTFail("Expected serviceNotFound")
        } catch let error as PolarErrors {
            if case .serviceNotFound = error { }
            else { XCTFail("Expected serviceNotFound, got \(error)") }
        } catch {
            XCTFail("Expected PolarErrors.serviceNotFound, got \(error)")
        }
    }

    // MARK: - Helpers

    private func makeSensorDataLogProto(
        ohrEnabled: Bool,
        ppiEnabled: Bool,
        hrDeviceInformation: String? = nil,
        hrWaitUntilConnected: Bool? = nil
    ) -> Data_PbSensorDataLog {
        Data_PbSensorDataLog.with {
            $0.ohrLogEnabled = ohrEnabled
            $0.ppiLogEnabled = ppiEnabled

            if hrDeviceInformation != nil || hrWaitUntilConnected != nil {
                $0.bleHrSensorConfig = Data_PbBleHrSensorConfig.with {
                    if let hrDeviceInformation {
                        $0.deviceName = PbBleDeviceName.with { $0.name = hrDeviceInformation }
                    }
                    if let hrWaitUntilConnected {
                        $0.waitForConnect = hrWaitUntilConnected
                    }
                }
            }
        }
    }

    private func makeLogConfig(ohrEnabled: Bool, ppiEnabled: Bool) -> LogConfig {
        LogConfig(
            ppiLogEnabled: ppiEnabled,
            accelerationLogEnabled: nil,
            caloriesLogEnabled: nil,
            gpsLogEnabled: nil,
            gpsNmeaLogEnabled: nil,
            magnetometerLogEnabled: nil,
            tapLogEnabled: nil,
            barometerLogEnabled: nil,
            gyroscopeLogEnabled: nil,
            sleepLogEnabled: nil,
            slopeLogEnabled: nil,
            ambientLightLogEnabled: nil,
            tlrLogEnabled: nil,
            ondemandLogEnabled: nil,
            capsenseLogEnabled: nil,
            fusionLogEnabled: nil,
            metLogEnabled: nil,
            ohrLogEnabled: ohrEnabled,
            verticalAccLogEnabled: nil,
            amdLogEnabled: nil,
            skinTemperatureLogEnabled: nil,
            compassLogEnabled: nil,
            speed3DLogEnabled: nil,
            logTrigger: nil,
            magnetometerFrequency: nil,
            hrSensorConfig: HrSensorConfig(
                deviceInformation: "POLAR-HR-TEST",
                waitUntilConnected: true
            )
        )
    }

    private func readAll(from input: InputStream) -> Data {
        input.open()
        defer { input.close() }
        var collected = Data()
        var buffer = [UInt8](repeating: 0, count: 512)

        while input.hasBytesAvailable {
            let bytesRead = input.read(&buffer, maxLength: buffer.count)
            if bytesRead <= 0 { break }
            collected.append(buffer, count: bytesRead)
        }

        return collected
    }
}
