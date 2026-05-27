// Copyright © 2026 Polar. All rights reserved.

import XCTest
import CoreBluetooth
@testable import iOSCommunications

final class StreamContinuationListTest: XCTestCase {

    func testMakeStream_checkConnectionFalse_streamIsCreated() async throws {
        // Arrange
        let list = StreamContinuationList<Int>()

        // Act
        let stream = list.makeStream(transport: nil, checkConnection: false)

        // Assert – stream exists and is not immediately finished
        list.finish(throwing: CancellationError())
        var count = 0
        do {
            for try await _ in stream { count += 1 }
        } catch { /* expected CancellationError */ }
        XCTAssertEqual(count, 0)
    }

    func testMakeStream_checkConnectionTrue_transportNotConnected_finishesWithGattDisconnected() async throws {
        // Arrange
        let list = StreamContinuationList<Int>()
        let disconnectedTransport = MockDisconnectedTransport()

        // Act
        let stream = list.makeStream(transport: disconnectedTransport, checkConnection: true)

        // Assert
        do {
            for try await _ in stream { XCTFail("Should not yield values") }
            XCTFail("Should have thrown")
        } catch let error as BleGattException {
            if case .gattDisconnected = error { /* expected */ } else {
                XCTFail("Expected gattDisconnected, got \(error)")
            }
        }
    }

    func testMakeStream_checkConnectionTrue_transportConnected_streamIsCreated() async throws {
        // Arrange
        let list = StreamContinuationList<Int>()
        let connectedTransport = MockConnectedTransport()

        // Act
        let stream = list.makeStream(transport: connectedTransport, checkConnection: true)

        // Assert – stream is live (not immediately finished); close it cleanly
        list.finish(throwing: CancellationError())
        do {
            for try await _ in stream {}
        } catch is CancellationError { /* expected */ }
    }

    func testMakeStream_checkConnectionTrue_transportNil_finishesWithGattDisconnected() async throws {
        // Arrange
        let list = StreamContinuationList<Int>()

        // Act
        let stream = list.makeStream(transport: nil, checkConnection: true)

        // Assert
        do {
            for try await _ in stream { XCTFail("Should not yield values") }
            XCTFail("Should have thrown")
        } catch let error as BleGattException {
            if case .gattDisconnected = error { /* expected */ } else {
                XCTFail("Expected gattDisconnected, got \(error)")
            }
        }
    }

    func testYield_singleStream_receivesValue() async throws {
        // Arrange
        let list = StreamContinuationList<Int>()
        let stream = list.makeStream(transport: nil, checkConnection: false)

        // Act
        list.yield(42)
        list.finish(throwing: CancellationError())

        // Assert
        var received: [Int] = []
        do {
            for try await value in stream { received.append(value) }
        } catch is CancellationError {}
        XCTAssertEqual(received, [42])
    }

    func testYield_multipleStreams_allReceiveValue() async throws {
        // Arrange
        let list = StreamContinuationList<Int>()
        let stream1 = list.makeStream(transport: nil, checkConnection: false)
        let stream2 = list.makeStream(transport: nil, checkConnection: false)

        // Act
        list.yield(7)
        list.finish(throwing: CancellationError())

        // Assert
        async let r1: [Int] = {
            var vals: [Int] = []
            do { for try await v in stream1 { vals.append(v) } } catch is CancellationError {}
            return vals
        }()
        async let r2: [Int] = {
            var vals: [Int] = []
            do { for try await v in stream2 { vals.append(v) } } catch is CancellationError {}
            return vals
        }()
        let (received1, received2) = try await (r1, r2)
        XCTAssertEqual(received1, [7])
        XCTAssertEqual(received2, [7])
    }

    func testYield_multipleValues_receivedInOrder() async throws {
        // Arrange
        let list = StreamContinuationList<Int>()
        let stream = list.makeStream(transport: nil, checkConnection: false)

        // Act
        list.yield(1)
        list.yield(2)
        list.yield(3)
        list.finish(throwing: CancellationError())

        // Assert
        var received: [Int] = []
        do {
            for try await value in stream { received.append(value) }
        } catch is CancellationError {}
        XCTAssertEqual(received, [1, 2, 3])
    }

    func testFinish_propagatesErrorToAllStreams() async throws {
        // Arrange
        let list = StreamContinuationList<Int>()
        let stream1 = list.makeStream(transport: nil, checkConnection: false)
        let stream2 = list.makeStream(transport: nil, checkConnection: false)

        struct TestError: Error, Equatable {}

        // Act
        list.finish(throwing: TestError())

        // Assert
        func collect(_ stream: AsyncThrowingStream<Int, Error>) async -> Error? {
            do {
                for try await _ in stream {}
                return nil
            } catch {
                return error
            }
        }
        let error1 = await collect(stream1)
        let error2 = await collect(stream2)
        XCTAssertTrue(error1 is TestError, "stream1 should receive TestError, got \(String(describing: error1))")
        XCTAssertTrue(error2 is TestError, "stream2 should receive TestError, got \(String(describing: error2))")
    }

    func testFinish_removesAllEntries() async throws {
        // Arrange
        let list = StreamContinuationList<Int>()
        _ = list.makeStream(transport: nil, checkConnection: false)
        _ = list.makeStream(transport: nil, checkConnection: false)

        // Act
        list.finish(throwing: CancellationError())

        // Assert – after finish the list is empty
        XCTAssertTrue(list.isEmpty)
    }

    func testIsEmpty_noStreams_returnsTrue() {
        let list = StreamContinuationList<Int>()
        XCTAssertTrue(list.isEmpty)
    }

    func testIsEmpty_withActiveStream_returnsFalse() {
        let list = StreamContinuationList<Int>()
        let stream = list.makeStream(transport: nil, checkConnection: false)
        XCTAssertFalse(list.isEmpty)
        // Keep stream alive until after the assertion
        _ = stream
    }

    func testIsEmpty_afterStreamTerminates_returnsTrue() async throws {
        // Arrange
        let list = StreamContinuationList<Int>()
        let stream = list.makeStream(transport: nil, checkConnection: false)

        // Act – finish the stream and drain it so onTermination fires
        list.finish(throwing: CancellationError())
        do { for try await _ in stream {} } catch is CancellationError {}

        // Assert – onTermination should have removed the entry
        // Give the onTermination callback a moment to execute
        try await Task.sleep(nanoseconds: 10_000_000)
        XCTAssertTrue(list.isEmpty)
    }

    func testYield_concurrentAccess_doesNotCrash() async throws {
        // Arrange
        let list = StreamContinuationList<Int>()
        let streamCount = 10
        var streams: [AsyncThrowingStream<Int, Error>] = []
        for _ in 0..<streamCount {
            streams.append(list.makeStream(transport: nil, checkConnection: false))
        }

        // Act – yield from multiple concurrent tasks
        await withTaskGroup(of: Void.self) { group in
            for i in 0..<50 {
                group.addTask { list.yield(i) }
            }
        }
        list.finish(throwing: CancellationError())

        // Assert – all streams complete without crashing
        await withTaskGroup(of: Void.self) { group in
            for stream in streams {
                group.addTask {
                    do { for try await _ in stream {} } catch {}
                }
            }
        }
    }
}

private class MockConnectedTransport: BleAttributeTransportProtocol {
    func isConnected() -> Bool { return true }
    func transmitMessage(_ parent: BleGattClientBase, serviceUuid: CBUUID, characteristicUuid: CBUUID, packet: Data, withResponse: Bool) throws {}
    func characteristicWith(uuid: CBUUID) throws -> CBCharacteristic? { return nil }
    func characteristicNameWith(uuid: CBUUID) -> String? { return nil }
    func readValue(_ parent: BleGattClientBase, serviceUuid: CBUUID, characteristicUuid: CBUUID) throws {}
    func setCharacteristicNotify(_ parent: BleGattClientBase, serviceUuid: CBUUID, characteristicUuid: CBUUID, notify: Bool) throws {}
    func attributeOperationStarted() {}
    func attributeOperationFinished() {}
}

private class MockDisconnectedTransport: BleAttributeTransportProtocol {
    func isConnected() -> Bool { return false }
    func transmitMessage(_ parent: BleGattClientBase, serviceUuid: CBUUID, characteristicUuid: CBUUID, packet: Data, withResponse: Bool) throws {}
    func characteristicWith(uuid: CBUUID) throws -> CBCharacteristic? { return nil }
    func characteristicNameWith(uuid: CBUUID) -> String? { return nil }
    func readValue(_ parent: BleGattClientBase, serviceUuid: CBUUID, characteristicUuid: CBUUID) throws {}
    func setCharacteristicNotify(_ parent: BleGattClientBase, serviceUuid: CBUUID, characteristicUuid: CBUUID, notify: Bool) throws {}
    func attributeOperationStarted() {}
    func attributeOperationFinished() {}
}
