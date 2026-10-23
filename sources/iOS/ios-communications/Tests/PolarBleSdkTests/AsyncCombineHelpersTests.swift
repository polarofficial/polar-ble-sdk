// Copyright © 2026 Polar Electro Oy. All rights reserved.

import XCTest
import Combine
@testable import PolarBleSdk

final class AsyncCombineHelpersTests: XCTestCase {

    private enum TestError: Error, Equatable {
        case expected
    }

    private func awaitSingleValue<T>(_ publisher: AnyPublisher<T, Error>, timeout: TimeInterval = 1) throws -> T {
        var receivedValue: T?
        var receivedError: Error?
        let expectation = expectation(description: "awaitSingleValue")

        let cancellable = publisher
            .first()
            .sink(
                receiveCompletion: { completion in
                    if case .failure(let error) = completion { receivedError = error }
                    expectation.fulfill()
                },
                receiveValue: { value in
                    receivedValue = value
                }
            )

        wait(for: [expectation], timeout: timeout)
        cancellable.cancel()

        if let receivedError { throw receivedError }
        return try XCTUnwrap(receivedValue)
    }

    private func awaitPublisherError<T>(_ publisher: AnyPublisher<T, Error>, timeout: TimeInterval = 1) -> Error? {
        var receivedError: Error?
        let expectation = expectation(description: "awaitPublisherError")

        let cancellable = publisher
            .sink(
                receiveCompletion: { completion in
                    if case .failure(let error) = completion { receivedError = error }
                    expectation.fulfill()
                },
                receiveValue: { _ in }
            )

        wait(for: [expectation], timeout: timeout)
        cancellable.cancel()
        return receivedError
    }

    func test_asyncPublisher_success_emitsValue() throws {
        let value = try awaitSingleValue(asyncPublisher { 42 })
        XCTAssertEqual(value, 42)
    }

    func test_asyncPublisher_failure_propagatesError() {
        let error = awaitPublisherError(asyncPublisher { throw TestError.expected })
        XCTAssertEqual(error as? TestError, .expected)
    }

    func test_asyncPublisher_cancellationCancelsBackingTask() async {
        let started = expectation(description: "operation started")
        let cancelled = expectation(description: "operation cancelled")
        let cancellable = asyncPublisher { () async throws -> Int in
            started.fulfill()
            do {
                try await Task.sleep(nanoseconds: 60_000_000_000)
                return 1
            } catch {
                cancelled.fulfill()
                throw error
            }
        }.sink(receiveCompletion: { _ in }, receiveValue: { _ in })

        await fulfillment(of: [started], timeout: 1)
        cancellable.cancel()
        await fulfillment(of: [cancelled], timeout: 1)
    }

    func test_asyncVoidPublisher_success_completesWithoutValues() {
        let completionExpectation = expectation(description: "asyncVoidPublisher completion")
        let valueExpectation = expectation(description: "asyncVoidPublisher should not emit value")
        valueExpectation.isInverted = true

        let cancellable = asyncVoidPublisher { }
            .sink(
                receiveCompletion: { completion in
                    if case .failure(let error) = completion {
                        XCTFail("Unexpected failure: \(error)")
                    }
                    completionExpectation.fulfill()
                },
                receiveValue: { _ in
                    valueExpectation.fulfill()
                }
            )

        wait(for: [completionExpectation, valueExpectation], timeout: 1)
        cancellable.cancel()
    }

    func test_asyncVoidPublisher_failure_propagatesError() {
        let completionExpectation = expectation(description: "asyncVoidPublisher failure")

        let cancellable = asyncVoidPublisher { throw TestError.expected }
            .sink(
                receiveCompletion: { completion in
                    switch completion {
                    case .finished:
                        XCTFail("Expected failure")
                    case .failure(let error):
                        XCTAssertEqual(error as? TestError, .expected)
                    }
                    completionExpectation.fulfill()
                },
                receiveValue: { _ in
                    XCTFail("Unexpected value")
                }
            )

        wait(for: [completionExpectation], timeout: 1)
        cancellable.cancel()
    }

    func test_streamPublisher_success_emitsAllValuesAndFinishes() {
        let stream = AsyncThrowingStream<Int, Error> { continuation in
            Task {
                try? await Task.sleep(nanoseconds: 20_000_000)
                continuation.yield(1)
                continuation.yield(2)
                continuation.yield(3)
                continuation.finish()
            }
        }

        var receivedValues: [Int] = []
        let completionExpectation = expectation(description: "streamPublisher finished")

        let cancellable = streamPublisher(stream)
            .sink(
                receiveCompletion: { completion in
                    if case .failure(let error) = completion {
                        XCTFail("Unexpected failure: \(error)")
                    }
                    completionExpectation.fulfill()
                },
                receiveValue: { value in
                    receivedValues.append(value)
                }
            )

        wait(for: [completionExpectation], timeout: 1)
        XCTAssertEqual(receivedValues, [1, 2, 3])
        cancellable.cancel()
    }

    func test_streamPublisher_failure_propagatesError() {
        let stream = AsyncThrowingStream<Int, Error> { continuation in
            Task {
                try? await Task.sleep(nanoseconds: 20_000_000)
                continuation.yield(1)
                continuation.finish(throwing: TestError.expected)
            }
        }

        var receivedValues: [Int] = []
        let completionExpectation = expectation(description: "streamPublisher failure")

        let cancellable = streamPublisher(stream)
            .sink(
                receiveCompletion: { completion in
                    switch completion {
                    case .finished:
                        XCTFail("Expected failure")
                    case .failure(let error):
                        XCTAssertEqual(error as? TestError, .expected)
                    }
                    completionExpectation.fulfill()
                },
                receiveValue: { value in
                    receivedValues.append(value)
                }
            )

        wait(for: [completionExpectation], timeout: 1)
        XCTAssertEqual(receivedValues, [1])
        cancellable.cancel()
    }

    func test_asyncForEach_success_callsBodyForEachElement() async throws {
        let publisher = [10, 20, 30].publisher
            .setFailureType(to: Error.self)
            .eraseToAnyPublisher()

        var receivedValues: [Int] = []
        try await publisher.asyncForEach { receivedValues.append($0) }

        XCTAssertEqual(receivedValues, [10, 20, 30])
    }

    func test_asyncForEach_failure_throwsPublisherError() async {
        let publisher = Fail<Int, Error>(error: TestError.expected)
            .eraseToAnyPublisher()

        do {
            try await publisher.asyncForEach { _ in }
            XCTFail("Expected asyncForEach to throw")
        } catch {
            XCTAssertEqual(error as? TestError, .expected)
        }
    }

    func test_asyncForEach_cancellationCancelsSubscriptionAndThrowsCancellationError() async {
        let subject = PassthroughSubject<Int, Error>()
        let subscribed = expectation(description: "subscription installed")
        let cancelled = expectation(description: "subscription cancelled")
        var receivedValues: [Int] = []
        let publisher = subject.handleEvents(
            receiveSubscription: { _ in subscribed.fulfill() },
            receiveCancel: { cancelled.fulfill() }
        )

        let task = Task { () -> Error? in
            do {
                try await publisher.asyncForEach { receivedValues.append($0) }
                return nil
            } catch {
                return error
            }
        }

        await fulfillment(of: [subscribed], timeout: 1)
        task.cancel()
        await fulfillment(of: [cancelled], timeout: 1)
        subject.send(123)

        let error = await task.value
        XCTAssertTrue(error is CancellationError)
        XCTAssertTrue(receivedValues.isEmpty)
    }

    func test_asyncForEach_cancelledBeforeSubscriptionDoesNotSubscribe() async {
        let subject = PassthroughSubject<Int, Error>()
        let subscription = expectation(description: "publisher should not be subscribed")
        subscription.isInverted = true
        let publisher = subject.handleEvents(receiveSubscription: { _ in subscription.fulfill() })

        let task = Task { () -> Error? in
            withUnsafeCurrentTask { $0?.cancel() }
            do {
                try await publisher.asyncForEach { _ in }
                return nil
            } catch {
                return error
            }
        }

        let error = await task.value
        XCTAssertTrue(error is CancellationError)
        await fulfillment(of: [subscription], timeout: 0.1)
    }

    func test_awaitCompletion_success_returnsWhenFinished() async throws {
        let publisher = Empty<Never, Error>(completeImmediately: true)
            .eraseToAnyPublisher()

        try await publisher.awaitCompletion()
    }

    func test_awaitCompletion_failure_throwsPublisherError() async {
        let publisher = Fail<Never, Error>(error: TestError.expected)
            .eraseToAnyPublisher()

        do {
            try await publisher.awaitCompletion()
            XCTFail("Expected awaitCompletion to throw")
        } catch {
            XCTAssertEqual(error as? TestError, .expected)
        }
    }

    func test_awaitCompletion_cancellationCancelsSubscriptionAndThrowsCancellationError() async {
        let subject = PassthroughSubject<Never, Error>()
        let subscribed = expectation(description: "subscription installed")
        let cancelled = expectation(description: "subscription cancelled")
        let publisher = subject.handleEvents(
            receiveSubscription: { _ in subscribed.fulfill() },
            receiveCancel: { cancelled.fulfill() }
        )

        let task = Task { () -> Error? in
            do {
                try await publisher.awaitCompletion()
                return nil
            } catch {
                return error
            }
        }

        await fulfillment(of: [subscribed], timeout: 1)
        task.cancel()
        await fulfillment(of: [cancelled], timeout: 1)

        let error = await task.value
        XCTAssertTrue(error is CancellationError)
    }
}
