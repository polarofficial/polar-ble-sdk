// Copyright 2026 Polar Electro Oy. All rights reserved.

import XCTest
import CoreBluetooth
import Combine
@testable import iOSCommunications

final class BleMdsClientTest: XCTestCase {

    private var transmitter: MockMdsGattTransmitter!
    private var client: BleMdsClient!

    override func setUpWithError() throws {
        transmitter = MockMdsGattTransmitter()
        client = BleMdsClient(gattServiceTransmitter: transmitter)
        client.setServiceDiscovered(true)
    }

    override func tearDownWithError() throws {
        client.disconnected()
        client = nil
        transmitter = nil
    }

    // MARK: - processServiceData routing
    // Note: Internal continuations are tested indirectly through the async read methods

    func testProcessServiceData_dataExport_yieldsToChunkStream() {
        let exp = expectation(description: "chunk received")
        let notificationData = Data([0x01, 0x02, 0x03, 0x04])
        let expectedPayload = Data([0x02, 0x03, 0x04])
        var received: Data?

        Task {
            for try await chunk in client.observeMemfaultChunks(checkConnection: false) {
                received = chunk
                exp.fulfill()
                break
            }
        }

        // Give the stream task a moment to subscribe
        DispatchQueue.global().asyncAfter(deadline: .now() + 0.05) { [weak self] in
            self?.client.processServiceData(BleMdsClient.MDS_DATA_EXPORT, data: notificationData, err: 0)
        }

        wait(for: [exp], timeout: 2.0)
        XCTAssertEqual(received, expectedPayload)
    }

    func testProcessServiceData_dataExport_error_finishesStreamWithGattCharacteristicNotifyError() {
        let exp = expectation(description: "stream errors")
        var streamError: Error?

        Task {
            do {
                for try await _ in client.observeMemfaultChunks(checkConnection: false) { }
            } catch {
                streamError = error
                exp.fulfill()
            }
        }

        DispatchQueue.global().asyncAfter(deadline: .now() + 0.05) { [weak self] in
            self?.client.processServiceData(BleMdsClient.MDS_DATA_EXPORT, data: Data(), err: 133)
        }

        wait(for: [exp], timeout: 2.0)
        XCTAssertNotNil(streamError)
        if let e = streamError as? BleGattException, case .gattCharacteristicNotifyError = e {
            // expected
        } else {
            XCTFail("Expected gattCharacteristicNotifyError, got \(String(describing: streamError))")
        }
    }

    func testProcessServiceData_unknownCharacteristic_ignored() {
        let unknownUUID = CBUUID(string: "00001800-0000-1000-8000-00805f9b34fb")
        // Should not throw or crash - unknown characteristics are simply ignored
        client.processServiceData(unknownUUID, data: Data([0x01]), err: 0)
    }

    // MARK: - disconnected()

    func testDisconnected_cancelsPendingReadsAndFinishesStreamWithGattDisconnected() async {
        let exp = expectation(description: "stream disconnected")
        var streamError: Error?
        var readError: Error?

        // Start a stream
        Task {
            do {
                for try await _ in client.observeMemfaultChunks(checkConnection: false) { }
            } catch {
                streamError = error
            }
        }

        // Start a pending read
        Task {
            do {
                _ = try await client.readSupportedFeatures()
            } catch {
                readError = error
                exp.fulfill()
            }
        }

        // Give tasks time to start
        try? await Task.sleep(nanoseconds: 50_000_000)
        
        // Disconnect
        client.disconnected()

        await fulfillment(of: [exp], timeout: 2.0)

        // Verify both pending read and stream were cancelled with gattDisconnected
        if let e = readError as? BleGattException, case .gattDisconnected = e {
            // expected
        } else {
            XCTFail("Expected readSupportedFeatures to throw gattDisconnected, got \(String(describing: readError))")
        }
        
        if let e = streamError as? BleGattException, case .gattDisconnected = e {
            // expected
        } else {
            XCTFail("Expected stream to finish with gattDisconnected, got \(String(describing: streamError))")
        }
    }

    // MARK: - readSupportedFeatures

    func testReadSupportedFeatures_fourBytes_returnsCorrectBitmask() async throws {
        transmitter.readValueHandler = { [weak self] chr in
            guard let self = self else { return }
            if chr == BleMdsClient.MDS_SUPPORTED_FEATURES {
                // bit0 = streaming, bit1 = export
                let bitmask: UInt32 = 0x03
                var data = Data(count: 4)
                data.withUnsafeMutableBytes { $0.storeBytes(of: bitmask.littleEndian, as: UInt32.self) }
                self.client.processServiceData(BleMdsClient.MDS_SUPPORTED_FEATURES, data: data, err: 0)
            }
        }

        let result = try await client.readSupportedFeatures()

        XCTAssertEqual(result, 0x03)
        XCTAssertTrue((result & 0x01) != 0, "streaming should be supported")
        XCTAssertTrue((result & 0x02) != 0, "export should be supported")
    }

    func testReadSupportedFeatures_singleByte_zeroPaddedCorrectly() async throws {
        transmitter.readValueHandler = { [weak self] chr in
            guard let self = self else { return }
            if chr == BleMdsClient.MDS_SUPPORTED_FEATURES {
                self.client.processServiceData(BleMdsClient.MDS_SUPPORTED_FEATURES,
                                               data: Data([0x01]), err: 0)
            }
        }

        let result = try await client.readSupportedFeatures()
        XCTAssertEqual(result, 0x01)
    }

    func testReadSupportedFeatures_fiveBytes_throwsGattOperationModeChange() async {
        transmitter.readValueHandler = { [weak self] chr in
            guard let self = self else { return }
            if chr == BleMdsClient.MDS_SUPPORTED_FEATURES {
                // 5 bytes exceeds maximum allowed length of 4
                self.client.processServiceData(BleMdsClient.MDS_SUPPORTED_FEATURES,
                                               data: Data([0x01, 0x02, 0x03, 0x04, 0x05]), err: 0)
            }
        }

        do {
            _ = try await client.readSupportedFeatures()
            XCTFail("Expected gattOperationModeChange to be thrown")
        } catch let e as BleGattException {
            if case .gattOperationModeChange = e { /* expected */ }
            else { XCTFail("Unexpected BleGattException: \(e)") }
        } catch {
            XCTFail("Unexpected error: \(error)")
        }
    }

    func testReadSupportedFeatures_attError_throwsGattCharacteristicError() async {
        transmitter.readValueHandler = { [weak self] chr in
            guard let self = self else { return }
            if chr == BleMdsClient.MDS_SUPPORTED_FEATURES {
                self.client.processServiceData(BleMdsClient.MDS_SUPPORTED_FEATURES, data: Data(), err: 5)
            }
        }

        do {
            _ = try await client.readSupportedFeatures()
            XCTFail("Expected gattCharacteristicError to be thrown")
        } catch let e as BleGattException {
            if case .gattCharacteristicError = e { /* expected */ }
            else { XCTFail("Unexpected BleGattException: \(e)") }
        } catch {
            XCTFail("Unexpected error: \(error)")
        }
    }

    // MARK: - readDeviceIdentifier

    func testReadDeviceIdentifier_returnsUtf8String() async throws {
        transmitter.readValueHandler = { [weak self] chr in
            guard let self = self else { return }
            if chr == BleMdsClient.MDS_DEVICE_IDENTIFIER {
                self.client.processServiceData(BleMdsClient.MDS_DEVICE_IDENTIFIER,
                                               data: "POLAR-ABCDEF".data(using: .utf8)!, err: 0)
            }
        }
        let result = try await client.readDeviceIdentifier()
        XCTAssertEqual(result, "POLAR-ABCDEF")
    }

    func testReadDeviceIdentifier_attError_throwsGattCharacteristicError() async {
        transmitter.readValueHandler = { [weak self] chr in
            guard let self = self else { return }
            if chr == BleMdsClient.MDS_DEVICE_IDENTIFIER {
                self.client.processServiceData(BleMdsClient.MDS_DEVICE_IDENTIFIER, data: Data(), err: 3)
            }
        }

        do {
            _ = try await client.readDeviceIdentifier()
            XCTFail("Expected gattCharacteristicError to be thrown")
        } catch let e as BleGattException {
            if case .gattCharacteristicError = e { /* expected */ }
            else { XCTFail("Unexpected BleGattException: \(e)") }
        } catch {
            XCTFail("Unexpected error: \(error)")
        }
    }

    // MARK: - readDataUri

    func testReadDataUri_returnsUtf8String() async throws {
        transmitter.readValueHandler = { [weak self] chr in
            guard let self = self else { return }
            if chr == BleMdsClient.MDS_DATA_URI {
                self.client.processServiceData(BleMdsClient.MDS_DATA_URI,
                                               data: "https://nrf-chunks.memfault.com/api/v0/chunks/".data(using: .utf8)!, err: 0)
            }
        }
        let result = try await client.readDataUri()
        XCTAssertEqual(result, "https://nrf-chunks.memfault.com/api/v0/chunks/")
    }

    func testReadDataUri_attError_throwsGattCharacteristicError() async {
        transmitter.readValueHandler = { [weak self] chr in
            guard let self = self else { return }
            if chr == BleMdsClient.MDS_DATA_URI {
                self.client.processServiceData(BleMdsClient.MDS_DATA_URI, data: Data(), err: 7)
            }
        }

        do {
            _ = try await client.readDataUri()
            XCTFail("Expected gattCharacteristicError to be thrown")
        } catch let e as BleGattException {
            if case .gattCharacteristicError = e { /* expected */ }
            else { XCTFail("Unexpected BleGattException: \(e)") }
        } catch {
            XCTFail("Unexpected error: \(error)")
        }
    }

    // MARK: - readAuthorization

    func testReadAuthorization_returnsUtf8String() async throws {
        transmitter.readValueHandler = { [weak self] chr in
            guard let self = self else { return }
            if chr == BleMdsClient.MDS_AUTHORIZATION {
                self.client.processServiceData(BleMdsClient.MDS_AUTHORIZATION,
                                               data: "my-secret-token".data(using: .utf8)!, err: 0)
            }
        }
        let result = try await client.readAuthorization()
        XCTAssertEqual(result, "my-secret-token")
    }

    func testReadAuthorization_emptyData_returnsEmptyString() async throws {
        transmitter.readValueHandler = { [weak self] chr in
            guard let self = self else { return }
            if chr == BleMdsClient.MDS_AUTHORIZATION {
                self.client.processServiceData(BleMdsClient.MDS_AUTHORIZATION, data: Data(), err: 0)
            }
        }
        let result = try await client.readAuthorization()
        XCTAssertEqual(result, "")
    }

    func testReadAuthorization_attError_throwsGattCharacteristicError() async {
        transmitter.readValueHandler = { [weak self] chr in
            guard let self = self else { return }
            if chr == BleMdsClient.MDS_AUTHORIZATION {
                self.client.processServiceData(BleMdsClient.MDS_AUTHORIZATION, data: Data(), err: 11)
            }
        }

        do {
            _ = try await client.readAuthorization()
            XCTFail("Expected gattCharacteristicError to be thrown")
        } catch let e as BleGattException {
            if case .gattCharacteristicError = e { /* expected */ }
            else { XCTFail("Unexpected BleGattException: \(e)") }
        } catch {
            XCTFail("Unexpected error: \(error)")
        }
    }

    // MARK: - observeMemfaultChunks

    func testObserveMemfaultChunks_yieldsMultipleChunksInOrder() async throws {
        let notifications = [Data([0x01, 0x02]), Data([0x03, 0x04]), Data([0x05, 0x06])]
        let expectedPayloads = [Data([0x02]), Data([0x04]), Data([0x06])]
        var received: [Data] = []
        let exp = expectation(description: "three chunks received")

        Task {
            for try await chunk in client.observeMemfaultChunks(checkConnection: false) {
                received.append(chunk)
                if received.count == notifications.count { exp.fulfill(); break }
            }
        }

        DispatchQueue.global().asyncAfter(deadline: .now() + 0.05) { [weak self] in
            for notification in notifications {
                self?.client.processServiceData(BleMdsClient.MDS_DATA_EXPORT, data: notification, err: 0)
            }
        }

        await fulfillment(of: [exp], timeout: 2.0)
        XCTAssertEqual(received, expectedPayloads)
    }

    // MARK: - enableDataExportNotification

    func testEnableDataExportNotification_writesModeByte1() async throws {
        var writtenPackets: [(chr: CBUUID, data: Data)] = []
        transmitter.transmitMessageHandler = { [weak self] chr, data, withResponse in
            guard let self = self else { return }
            writtenPackets.append((chr: chr, data: data))
            if chr == BleMdsClient.MDS_DATA_EXPORT && withResponse {
                self.client.serviceDataWritten(chr, err: 0)
            }
        }

        try await client.enableDataExportNotification()

        let dataExportWrites = writtenPackets.filter { $0.chr == BleMdsClient.MDS_DATA_EXPORT }
        XCTAssertEqual(dataExportWrites.count, 1, "Exactly one write to DATA_EXPORT expected")
        XCTAssertEqual(dataExportWrites.first?.data, Data([0x01]),
                       "Mode byte 0x01 (streaming enabled) must be written")
    }

    func testEnableDataExportNotification_attWriteFailure_throwsGattCharacteristicError() async {
        transmitter.transmitMessageHandler = { [weak self] chr, _, withResponse in
            guard let self = self else { return }
            if chr == BleMdsClient.MDS_DATA_EXPORT && withResponse {
                self.client.serviceDataWritten(chr, err: 3)
            }
        }

        do {
            try await client.enableDataExportNotification()
            XCTFail("Expected gattCharacteristicError to be thrown")
        } catch let e as BleGattException {
            if case .gattCharacteristicError = e { /* expected */ }
            else { XCTFail("Unexpected BleGattException: \(e)") }
        } catch {
            XCTFail("Unexpected error: \(error)")
        }
    }

    // MARK: - disableDataExportNotification

    func testDisableDataExportNotification_writesModeByte0() async throws {
        var writtenPackets: [(chr: CBUUID, data: Data)] = []
        transmitter.transmitMessageHandler = { [weak self] chr, data, withResponse in
            guard let self = self else { return }
            writtenPackets.append((chr: chr, data: data))
            if chr == BleMdsClient.MDS_DATA_EXPORT && withResponse {
                self.client.serviceDataWritten(chr, err: 0)
            }
        }

        try await client.disableDataExportNotification()

        let dataExportWrites = writtenPackets.filter { $0.chr == BleMdsClient.MDS_DATA_EXPORT }
        XCTAssertEqual(dataExportWrites.count, 1, "Exactly one write to DATA_EXPORT expected")
        XCTAssertEqual(dataExportWrites.first?.data, Data([0x00]),
                       "Mode byte 0x00 (streaming disabled) must be written")
    }

    func testDisableDataExportNotification_attWriteFailure_throwsGattCharacteristicError() async {
        transmitter.transmitMessageHandler = { [weak self] chr, _, withResponse in
            guard let self = self else { return }
            if chr == BleMdsClient.MDS_DATA_EXPORT && withResponse {
                self.client.serviceDataWritten(chr, err: 5)
            }
        }

        do {
            try await client.disableDataExportNotification()
            XCTFail("Expected gattCharacteristicError to be thrown")
        } catch let e as BleGattException {
            if case .gattCharacteristicError = e { /* expected */ }
            else { XCTFail("Unexpected BleGattException: \(e)") }
        } catch {
            XCTFail("Unexpected error: \(error)")
        }
    }

    // MARK: - serviceDataWritten

    func testServiceDataWritten_unknownCharacteristic_doesNotCrash() {
        let unknownChr = CBUUID(string: "00001801-0000-1000-8000-00805f9b34fb")
        client.serviceDataWritten(unknownChr, err: 0)
    }

    func testServiceDataWritten_dataExport_noPendingContinuation_doesNotCrash() {
        client.serviceDataWritten(BleMdsClient.MDS_DATA_EXPORT, err: 0)
        client.serviceDataWritten(BleMdsClient.MDS_DATA_EXPORT, err: 3)
    }

    // MARK: - clientReady

    func testClientReady_serviceDiscovered_completesImmediately() {
        let exp = expectation(description: "clientReady completes")
        var cancellable: AnyCancellable?
        var completed = false

        cancellable = client.clientReady(false)
            .sink(
                receiveCompletion: { completion in
                    if case .finished = completion { completed = true }
                    exp.fulfill()
                    _ = cancellable
                },
                receiveValue: { _ in }
            )

        wait(for: [exp], timeout: 2.0)
        XCTAssertTrue(completed)
    }

    func testClientReady_serviceNotDiscovered_pendingUntilDiscovered() {
        client.setServiceDiscovered(false)
        let exp = expectation(description: "clientReady completes after discovery")
        var cancellable: AnyCancellable?
        var completed = false

        cancellable = client.clientReady(false)
            .sink(
                receiveCompletion: { completion in
                    if case .finished = completion { completed = true }
                    exp.fulfill()
                    _ = cancellable
                },
                receiveValue: { _ in }
            )

        DispatchQueue.global().asyncAfter(deadline: .now() + 0.05) { [weak self] in
            self?.client.setServiceDiscovered(true)
        }

        wait(for: [exp], timeout: 2.0)
        XCTAssertTrue(completed)
    }
    
    // MARK: - Sequence Number Validation
    
    func testSequenceValidation_normalSequence_allChunksYielded() async throws {
        let exp = expectation(description: "sequential chunks received")
        var receivedChunks: [Data] = []
        
        Task {
            for try await chunk in client.observeMemfaultChunks(checkConnection: false) {
                receivedChunks.append(chunk)
                if receivedChunks.count == 5 { exp.fulfill(); break }
            }
        }
        
        // Give the stream task time to subscribe
        try? await Task.sleep(nanoseconds: 50_000_000)
        
        // Send chunks with sequential sequence numbers: 0→1→2→3→4
        for seqNum in 0..<5 {
            let chunk = Data([UInt8(seqNum), 0xAA, 0xBB])
            client.processServiceData(BleMdsClient.MDS_DATA_EXPORT, data: chunk, err: 0)
        }
        
        await fulfillment(of: [exp], timeout: 2.0)
        XCTAssertEqual(receivedChunks.count, 5, "All 5 sequential chunks should be yielded")
        XCTAssertEqual(receivedChunks, Array(repeating: Data([0xAA, 0xBB]), count: 5))
    }
    
    func testSequenceValidation_modulo32Wraparound_handlesCorrectly() async throws {
        let exp = expectation(description: "wraparound chunks received")
        var receivedChunks: [Data] = []
        
        Task {
            for try await chunk in client.observeMemfaultChunks(checkConnection: false) {
                receivedChunks.append(chunk)
                if receivedChunks.count == 4 { exp.fulfill(); break }
            }
        }
        
        try? await Task.sleep(nanoseconds: 50_000_000)
        
        // Send chunks wrapping around: 30→31→0→1
        let seqNumbers: [UInt8] = [30, 31, 0, 1]
        for seqNum in seqNumbers {
            let chunk = Data([seqNum, 0xCC, 0xDD])
            client.processServiceData(BleMdsClient.MDS_DATA_EXPORT, data: chunk, err: 0)
        }
        
        await fulfillment(of: [exp], timeout: 2.0)
        XCTAssertEqual(receivedChunks.count, 4, "All wraparound chunks should be yielded")
        XCTAssertEqual(receivedChunks, Array(repeating: Data([0xCC, 0xDD]), count: 4))
    }
    
    func testSequenceValidation_duplicate_droppedSilently() async throws {
        let exp = expectation(description: "non-duplicate chunks received")
        var receivedChunks: [Data] = []
        
        Task {
            for try await chunk in client.observeMemfaultChunks(checkConnection: false) {
                receivedChunks.append(chunk)
                if receivedChunks.count == 3 { exp.fulfill(); break }
            }
        }
        
        try? await Task.sleep(nanoseconds: 50_000_000)
        
        // Send: 0→1→1(duplicate)→2
        let chunks = [
            Data([0, 0x11, 0x22]),  // seq 0
            Data([1, 0x33, 0x44]),  // seq 1
            Data([1, 0x55, 0x66]),  // seq 1 DUPLICATE - should be dropped
            Data([2, 0x77, 0x88])   // seq 2
        ]
        for chunk in chunks {
            client.processServiceData(BleMdsClient.MDS_DATA_EXPORT, data: chunk, err: 0)
        }
        
        await fulfillment(of: [exp], timeout: 2.0)
        XCTAssertEqual(receivedChunks.count, 3, "Duplicate should be dropped, only 3 yielded")
        XCTAssertEqual(receivedChunks, [
            Data([0x11, 0x22]),
            Data([0x33, 0x44]),
            Data([0x77, 0x88])
        ])
    }
    
    func testSequenceValidation_gap_yieldsChunkAndLogsError() async throws {
        let exp = expectation(description: "chunks with gap received")
        var receivedChunks: [Data] = []
        
        Task {
            for try await chunk in client.observeMemfaultChunks(checkConnection: false) {
                receivedChunks.append(chunk)
                if receivedChunks.count == 3 { exp.fulfill(); break }
            }
        }
        
        try? await Task.sleep(nanoseconds: 50_000_000)
        
        // Send: 5→6→9 (missing 7, 8)
        let chunks = [
            Data([5, 0xAA]),
            Data([6, 0xBB]),
            Data([9, 0xCC])  // Gap: expected 7, got 9
        ]
        for chunk in chunks {
            client.processServiceData(BleMdsClient.MDS_DATA_EXPORT, data: chunk, err: 0)
        }
        
        await fulfillment(of: [exp], timeout: 2.0)
        XCTAssertEqual(receivedChunks.count, 3, "All chunks yielded despite gap")
        XCTAssertEqual(receivedChunks, [Data([0xAA]), Data([0xBB]), Data([0xCC])])
    }
    
    func testSequenceValidation_firstChunk_anySequenceAccepted() async throws {
        let exp = expectation(description: "first chunk received")
        var receivedChunk: Data?
        
        Task {
            for try await chunk in client.observeMemfaultChunks(checkConnection: false) {
                receivedChunk = chunk
                exp.fulfill()
                break
            }
        }
        
        try? await Task.sleep(nanoseconds: 50_000_000)
        
        // First chunk with arbitrary sequence number 17
        let chunk = Data([17, 0xFF, 0xEE])
        client.processServiceData(BleMdsClient.MDS_DATA_EXPORT, data: chunk, err: 0)
        
        await fulfillment(of: [exp], timeout: 2.0)
        XCTAssertEqual(receivedChunk, Data([0xFF, 0xEE]), "First chunk always accepted")
    }
    
    func testSequenceValidation_emptyChunk_dropped() async throws {
        let exp = expectation(description: "valid chunk received after empty")
        var receivedChunks: [Data] = []
        
        Task {
            for try await chunk in client.observeMemfaultChunks(checkConnection: false) {
                receivedChunks.append(chunk)
                if receivedChunks.count == 1 { exp.fulfill(); break }
            }
        }
        
        try? await Task.sleep(nanoseconds: 50_000_000)
        
        // Send empty chunk (too short) followed by valid chunk
        client.processServiceData(BleMdsClient.MDS_DATA_EXPORT, data: Data(), err: 0)
        client.processServiceData(BleMdsClient.MDS_DATA_EXPORT, data: Data([5, 0xAA]), err: 0)
        
        await fulfillment(of: [exp], timeout: 2.0)
        XCTAssertEqual(receivedChunks.count, 1, "Empty chunk dropped")
        XCTAssertEqual(receivedChunks[0], Data([0xAA]))
    }
    
    func testSequenceValidation_resetOnEnableNotification() async throws {
        // First stream: receive chunk with seq 10
        let exp1 = expectation(description: "first stream chunk")
        var firstChunk: Data?
        
        Task {
            for try await chunk in client.observeMemfaultChunks(checkConnection: false) {
                firstChunk = chunk
                exp1.fulfill()
                break
            }
        }
        
        try? await Task.sleep(nanoseconds: 50_000_000)
        client.processServiceData(BleMdsClient.MDS_DATA_EXPORT, data: Data([10, 0xAA]), err: 0)
        await fulfillment(of: [exp1], timeout: 2.0)
        XCTAssertEqual(firstChunk, Data([0xAA]))
        
        // Simulate enable notification (resets sequence state)
        transmitter.transmitMessageHandler = { [weak self] chr, _, _ in
            self?.client.serviceDataWritten(chr, err: 0)
        }
        try await client.enableDataExportNotification()
        
        // Second stream: should accept chunk with seq 5 (different from 10)
        let exp2 = expectation(description: "second stream chunk")
        var secondChunk: Data?
        
        Task {
            for try await chunk in client.observeMemfaultChunks(checkConnection: false) {
                secondChunk = chunk
                exp2.fulfill()
                break
            }
        }
        
        try? await Task.sleep(nanoseconds: 50_000_000)
        client.processServiceData(BleMdsClient.MDS_DATA_EXPORT, data: Data([5, 0xBB]), err: 0)
        await fulfillment(of: [exp2], timeout: 2.0)
        XCTAssertEqual(secondChunk, Data([0xBB]), "After reset, any seq number accepted")
    }
    
    func testSequenceValidation_resetOnDisconnect() async throws {
        // First stream: receive chunk with seq 20
        let exp1 = expectation(description: "first stream chunk")
        var firstChunk: Data?
        
        Task {
            for try await chunk in client.observeMemfaultChunks(checkConnection: false) {
                firstChunk = chunk
                exp1.fulfill()
                break
            }
        }
        
        try? await Task.sleep(nanoseconds: 50_000_000)
        client.processServiceData(BleMdsClient.MDS_DATA_EXPORT, data: Data([20, 0xCC]), err: 0)
        await fulfillment(of: [exp1], timeout: 2.0)
        XCTAssertEqual(firstChunk, Data([0xCC]))
        
        // Disconnect (resets sequence state)
        client.disconnected()
        
        // Recreate client for second stream
        client = BleMdsClient(gattServiceTransmitter: transmitter)
        client.setServiceDiscovered(true)
        
        // Second stream: should accept chunk with seq 0 (different from 20)
        let exp2 = expectation(description: "second stream chunk")
        var secondChunk: Data?
        
        Task {
            for try await chunk in client.observeMemfaultChunks(checkConnection: false) {
                secondChunk = chunk
                exp2.fulfill()
                break
            }
        }
        
        try? await Task.sleep(nanoseconds: 50_000_000)
        client.processServiceData(BleMdsClient.MDS_DATA_EXPORT, data: Data([0, 0xDD]), err: 0)
        await fulfillment(of: [exp2], timeout: 2.0)
        XCTAssertEqual(secondChunk, Data([0xDD]), "After disconnect, any seq number accepted")
    }
}

// MARK: - MockMdsGattTransmitter

private class MockMdsGattTransmitter: BleAttributeTransportProtocol {
    var readValueHandler: ((CBUUID) -> Void)?
    /// Called for every `transmitMessage`. Arguments: (characteristicUUID, packet, withResponse)
    var transmitMessageHandler: ((CBUUID, Data, Bool) -> Void)?

    func isConnected() -> Bool { return true }

    func transmitMessage(_ parent: BleGattClientBase, serviceUuid: CBUUID,
                         characteristicUuid: CBUUID, packet: Data, withResponse: Bool) throws {
        transmitMessageHandler?(characteristicUuid, packet, withResponse)
    }

    func readValue(_ parent: BleGattClientBase, serviceUuid: CBUUID,
                   characteristicUuid: CBUUID) throws {
        readValueHandler?(characteristicUuid)
    }

    func setCharacteristicNotify(_ parent: BleGattClientBase, serviceUuid: CBUUID,
                                  characteristicUuid: CBUUID, notify: Bool) throws {
        // Immediately simulate a successful CCCD write response
        parent.notifyDescriptorWritten(characteristicUuid, enabled: notify, err: 0)
    }

    func attributeOperationStarted() {}
    func attributeOperationFinished() {}
}
