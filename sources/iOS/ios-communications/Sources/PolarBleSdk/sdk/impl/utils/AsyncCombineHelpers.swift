// Copyright © 2024 Polar Electro Oy. All rights reserved.
// Helpers for bridging async/await to Combine publishers.

import Foundation
import Combine

/// Convert an async throwing function that returns a value into an AnyPublisher.
func asyncPublisher<T>(_ operation: @escaping () async throws -> T) -> AnyPublisher<T, Error> {
    Deferred {
        Future { promise in
            Task {
                do {
                    let result = try await operation()
                    promise(.success(result))
                } catch {
                    promise(.failure(error))
                }
            }
        }
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

extension Publisher {
    /// Iterate over all values asynchronously, calling `body` for each one, then await
    /// completion. Compatible with iOS 14+.
    func asyncForEach(_ body: @escaping (Output) -> Void) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            var c: AnyCancellable?
            c = self.sink(
                receiveCompletion: { result in
                    switch result {
                    case .finished: continuation.resume()
                    case .failure(let error): continuation.resume(throwing: error)
                    }
                    c?.cancel()
                },
                receiveValue: { body($0) }
            )
        }
    }
}

extension Publisher where Output == Never {
    /// Await the publisher's completion. Compatible with iOS 14+.
    func awaitCompletion() async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            var c: AnyCancellable?
            c = self.sink(
                receiveCompletion: { result in
                    switch result {
                    case .finished: continuation.resume()
                    case .failure(let error): continuation.resume(throwing: error)
                    }
                    c?.cancel()
                },
                receiveValue: { _ in }
            )
        }
    }
}
