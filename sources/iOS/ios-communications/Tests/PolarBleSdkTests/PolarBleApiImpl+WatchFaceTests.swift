// Copyright © 2026 Polar. All rights reserved.

import Foundation
import XCTest

@testable import PolarBleSdk

final class PolarBleApiImplWatchFaceTests: XCTestCase {

    private let deviceId = "ABCDEF01"
    private var mockClient: MockBlePsFtpClient!
    private var mockSession: MockBleDeviceSession!
    private var api: PolarBleApiImplWithMockSession!

    override func setUpWithError() throws {
        BlePolarDeviceCapabilitiesUtility.resetAndInitializeForTesting(
            deviceFileSystemTypes: ["h10": .h10FileSystem],
            defaultFileSystemType: .polarFileSystemV2,
            defaultRecordingSupported: false
        )
        mockClient = MockBlePsFtpClient(gattServiceTransmitter: MockPolarGattServiceTransmitter())
        mockSession = MockBleDeviceSession(mockFtpClient: mockClient)
        api = PolarBleApiImplWithMockSession(mockDeviceSession: mockSession)
    }

    override func tearDownWithError() throws {
        api = nil
        mockSession = nil
        mockClient = nil
    }

    func test_getWatchFaceConfig_returnsMappedComplicationsAndSkipsUnknownIds() async throws {
        var fields = WatchfaceConfigFields()
        fields.timeStyleId = 2
        fields.complicationLayoutId = 1
        fields.backgroundStyleId = 4
        fields.accentColor = 0xAABBCCDD
        fields.fontfaceId = 3
        fields.complicationIds = [
            PolarWatchFaceComplication.heartRate.id,
            Int32.min + 7,
            PolarWatchFaceComplication.spo2.id
        ]

        let script = PolarWatchFaceUtils.buildKvtxScript(fields: fields)
        mockClient.requestReturnValue = .success(Data(script))

        let config = try await api.getWatchFaceConfig(deviceId)

        XCTAssertEqual(config.enabledComplications, [.heartRate, .spo2])
        XCTAssertEqual(mockClient.requestCalls.count, 1)

        let operation = try Protocol_PbPFtpOperation(serializedBytes: mockClient.requestCalls[0])
        XCTAssertEqual(operation.command, .get)
        XCTAssertEqual(operation.path, "/SYS/KVTX")
    }

    func test_getWatchFaceConfig_whenKvtxKeyMissing_returnsEmptyComplications() async throws {
        mockClient.requestReturnValue = .success(Data())

        let config = try await api.getWatchFaceConfig(deviceId)

        XCTAssertTrue(config.enabledComplications.isEmpty)
        XCTAssertEqual(mockClient.requestCalls.count, 1)
    }

    func test_getWatchFaceConfig_whenPsFtpServiceUnavailable_throwsServiceNotFound() async {
        let noFtpSession = MockNoFtpClientBleDeviceSession()
        let noFtpApi = PolarBleApiImplWithNoFtpSession(mockDeviceSession: noFtpSession)

        do {
            _ = try await noFtpApi.getWatchFaceConfig(deviceId)
            XCTFail("Expected serviceNotFound")
        } catch let error as PolarErrors {
            guard case .serviceNotFound = error else {
                XCTFail("Expected PolarErrors.serviceNotFound, got \(error)")
                return
            }
        } catch {
            XCTFail("Unexpected error type: \(error)")
        }
    }

    func test_setWatchFaceConfig_writesComplicationsAndPreservesExistingScalars() async throws {
        var existing = WatchfaceConfigFields()
        existing.timeStyleId = 9
        existing.complicationLayoutId = 2
        existing.backgroundStyleId = 6
        existing.accentColor = 0x01020304
        existing.fontfaceId = 5
        existing.complicationIds = [PolarWatchFaceComplication.date.id]

        mockClient.requestReturnValue = .success(Data(PolarWatchFaceUtils.buildKvtxScript(fields: existing)))

        let inputConfig = PolarWatchFaceConfig(enabledComplications: [.battery, .heartRate, .weather])
        try await api.setWatchFaceConfig(deviceId, config: inputConfig)

        XCTAssertEqual(mockClient.requestCalls.count, 1)
        XCTAssertEqual(mockClient.writeCalls.count, 1)

        let writeHeader = mockClient.writeCalls[0].header as Data
        let putOperation = try Protocol_PbPFtpOperation(serializedBytes: writeHeader)
        XCTAssertEqual(putOperation.command, .put)
        XCTAssertEqual(putOperation.path, "/SYS/KVTX")

        let scriptBytes = [UInt8](readAll(from: mockClient.writeCalls[0].data))
        let writtenConfigBytes = try XCTUnwrap(PolarWatchFaceUtils.extractWatchFaceConfigFromKvtxScript(script: scriptBytes))
        let parsed = PolarWatchFaceUtils.parseWatchFaceConfigFlatBuffer(raw: writtenConfigBytes)

        XCTAssertEqual(parsed.timeStyleId, existing.timeStyleId)
        XCTAssertEqual(parsed.complicationLayoutId, existing.complicationLayoutId)
        XCTAssertEqual(parsed.backgroundStyleId, existing.backgroundStyleId)
        XCTAssertEqual(parsed.accentColor, existing.accentColor)
        XCTAssertEqual(parsed.fontfaceId, existing.fontfaceId)
        XCTAssertEqual(parsed.complicationIds, inputConfig.enabledComplications.map { $0.id })
    }

    func test_setWatchFaceConfig_whenInitialReadFails_propagatesErrorAndDoesNotWrite() async {
        let expected = NSError(domain: "watchface.read", code: 13)
        mockClient.requestReturnValue = .failure(expected)

        do {
            try await api.setWatchFaceConfig(deviceId, config: PolarWatchFaceConfig(enabledComplications: [.heartRate]))
            XCTFail("Expected read failure")
        } catch {
            let nsError = error as NSError
            XCTAssertEqual(nsError.domain, expected.domain)
            XCTAssertEqual(nsError.code, expected.code)
        }

        XCTAssertEqual(mockClient.writeCalls.count, 0)
    }

    func test_setWatchFaceConfig_whenPsFtpServiceUnavailable_throwsServiceNotFound() async {
        let noFtpSession = MockNoFtpClientBleDeviceSession()
        let noFtpApi = PolarBleApiImplWithNoFtpSession(mockDeviceSession: noFtpSession)

        do {
            try await noFtpApi.setWatchFaceConfig(deviceId, config: PolarWatchFaceConfig(enabledComplications: [.spo2]))
            XCTFail("Expected serviceNotFound")
        } catch let error as PolarErrors {
            guard case .serviceNotFound = error else {
                XCTFail("Expected PolarErrors.serviceNotFound, got \(error)")
                return
            }
        } catch {
            XCTFail("Unexpected error type: \(error)")
        }
    }

    private func readAll(from stream: InputStream) -> Data {
        stream.open()
        defer { stream.close() }

        var data = Data()
        var buffer = [UInt8](repeating: 0, count: 512)
        while stream.hasBytesAvailable {
            let count = stream.read(&buffer, maxLength: buffer.count)
            if count <= 0 { break }
            data.append(buffer, count: count)
        }
        return data
    }
}
