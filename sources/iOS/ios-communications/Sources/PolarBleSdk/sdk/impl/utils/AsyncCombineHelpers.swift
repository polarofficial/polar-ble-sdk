// Copyright © 2024 Polar Electro Oy. All rights reserved.
// Helpers for bridging async/await to Combine publishers.

import Foundation
import Combine

private final class AsyncPublisherTask: @unchecked Sendable {
    private let lock = NSLock()
    private var task: Task<Void, Never>?
    private var cancelled = false

    func setTask(_ task: Task<Void, Never>) {
        lock.lock()
        let shouldCancel = cancelled
        if !shouldCancel {
            self.task = task
        }
        lock.unlock()
        if shouldCancel {
            task.cancel()
        }
    }

    func cancel() {
        lock.lock()
        cancelled = true
        let task = self.task
        self.task = nil
        lock.unlock()
        task?.cancel()
    }
}

/// Convert an async throwing function that returns a value into an AnyPublisher.
func asyncPublisher<T>(_ operation: @escaping () async throws -> T) -> AnyPublisher<T, Error> {
    Deferred {
        let taskHolder = AsyncPublisherTask()
        return Future<T, Error> { promise in
            let task = Task {
                do {
                    let result = try await operation()
                    promise(.success(result))
                } catch {
                    promise(.failure(error))
                }
            }
            taskHolder.setTask(task)
        }
        .handleEvents(receiveCancel: { taskHolder.cancel() })
        .eraseToAnyPublisher()
    }.eraseToAnyPublisher()
}

/// Convert an async throwing void function into an AnyPublisher<Never, Error>.
func asyncVoidPublisher(_ operation: @escaping () async throws -> Void) -> AnyPublisher<Never, Error> {
    asyncPublisher(operation)
        .flatMap { _ in Empty<Never, Error>() }
        .eraseToAnyPublisher()
}

/// Convert an AsyncThrowingStream into an AnyPublisher.
func streamPublisher<T>(_ stream: AsyncThrowingStream<T, Error>) -> AnyPublisher<T, Error> {
    let subject = PassthroughSubject<T, Error>()
    let task = Task {
        do {
            for try await value in stream {
                subject.send(value)
            }
            subject.send(completion: .finished)
        } catch {
            subject.send(completion: .failure(error))
        }
    }
    return subject
        .handleEvents(receiveCancel: { task.cancel() })
        .eraseToAnyPublisher()
}

/// Coordinates a Combine subscription with an awaiting task's continuation.
///
/// Combine publishers can complete synchronously while `sink` is being installed, and task
/// cancellation can race with either subscription installation or completion. All state changes
/// therefore pass through this lock-protected holder, which resumes the continuation exactly once.
private final class AsyncCombineSubscription: @unchecked Sendable {
    private let lock = NSLock()
    private var continuation: CheckedContinuation<Void, Error>?
    private var cancellable: AnyCancellable?
    private var result: Result<Void, Error>?

    /// Stores the continuation and reports whether the publisher should be subscribed to.
    func setContinuation(_ continuation: CheckedContinuation<Void, Error>) -> Bool {
        lock.lock()
        if let result {
            lock.unlock()
            continuation.resume(with: result)
            return false
        }
        self.continuation = continuation
        lock.unlock()
        return true
    }

    /// Retains a subscription unless an earlier completion or cancellation has already won.
    func setCancellable(_ cancellable: AnyCancellable) {
        lock.lock()
        let shouldCancel = result != nil
        if !shouldCancel {
            self.cancellable = cancellable
        }
        lock.unlock()

        if shouldCancel {
            cancellable.cancel()
        }
    }

    func receiveValue<Output>(_ value: Output, body: (Output) -> Void) {
        lock.lock()
        let isActive = result == nil
        lock.unlock()

        // A callback that passed this check is already in flight if cancellation races with it.
        if isActive {
            body(value)
        } else {
            BleLogger.error("AsyncCombineHelpers: publisher delivered a value after the async bridge had terminated")
        }
    }

    @discardableResult
    func finish(_ result: Result<Void, Error>) -> Bool {
        lock.lock()
        guard self.result == nil else {
            lock.unlock()
            return false
        }

        self.result = result
        let cancellable = self.cancellable
        self.cancellable = nil
        let continuation = self.continuation
        self.continuation = nil
        lock.unlock()

        if case .failure(let error) = result, !(error is CancellationError) {
            BleLogger.error("AsyncCombineHelpers: publisher completed with error: \(error)")
        }
        cancellable?.cancel()
        continuation?.resume(with: result)
        return true
    }

    func cancel() {
        if finish(.failure(CancellationError())) {
            BleLogger.trace("AsyncCombineHelpers: propagated task cancellation to the Combine subscription")
        }
    }
}

extension Publisher {
    /// Iterate over all values asynchronously, calling `body` for each one, then await
    /// completion. Compatible with iOS 14+.
    func asyncForEach(_ body: @escaping (Output) -> Void) async throws {
        let subscription = AsyncCombineSubscription()
        try await withTaskCancellationHandler(operation: {
            try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
                guard subscription.setContinuation(continuation) else { return }
                guard !Task.isCancelled else {
                    subscription.cancel()
                    return
                }

                let cancellable = self.sink(
                    receiveCompletion: { result in
                        switch result {
                        case .finished:
                            subscription.finish(.success(()))
                        case .failure(let error):
                            subscription.finish(.failure(error))
                        }
                    },
                    receiveValue: { subscription.receiveValue($0, body: body) }
                )
                subscription.setCancellable(cancellable)
            }
        }, onCancel: {
            subscription.cancel()
        })
    }
}

extension Publisher where Output == Never {
    /// Await the publisher's completion. Compatible with iOS 14+.
    func awaitCompletion() async throws {
        let subscription = AsyncCombineSubscription()
        try await withTaskCancellationHandler(operation: {
            try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
                guard subscription.setContinuation(continuation) else { return }
                guard !Task.isCancelled else {
                    subscription.cancel()
                    return
                }

                let cancellable = self.sink(
                    receiveCompletion: { result in
                        switch result {
                        case .finished:
                            subscription.finish(.success(()))
                        case .failure(let error):
                            subscription.finish(.failure(error))
                        }
                    },
                    receiveValue: { _ in }
                )
                subscription.setCancellable(cancellable)
            }
        }, onCancel: {
            subscription.cancel()
        })
    }
}
