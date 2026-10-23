/// Copyright © 2026 Polar Electro Oy. All rights reserved.

import Foundation

/// Single-flight guard that prevents overlapping device-side API calls to the same device.
/// Calls targeting different devices do not block each other. Calls are rejected, not queued.
/// Streams take the lock when consumption starts (not when the function returns the stream) and hold it until the
/// producer has finished. Long-lived observers (e.g. exercise status) and the telemetry APIs, which use a separate
/// BLE client, are intentionally not guarded.
final class PolarApiCallGuard: @unchecked Sendable {

    struct Token {
        fileprivate let id = UUID()
        fileprivate let callName: String
        fileprivate let identifier: String
    }

    private struct ActiveCall {
        let id: UUID
        let callName: String
        let identifier: String

        var description: String { "\(callName)(\(identifier))" }
    }

    private let lock = NSLock()
    private var activeCalls: [String: ActiveCall] = [:]

    /// Acquires the lock of the device. Must be paired with `release(_:)`.
    ///
    /// - Parameters:
    ///   - callName: Logical name of the API being invoked, used in the error message.
    ///   - identifier: Target device identifier.
    ///   - allowConcurrent: True when the API intentionally permits overlapping read/observe calls.
    /// - Returns: Token to release, or nil when the call bypasses the lock.
    /// - Throws: `PolarErrors.concurrentApiCall` if another guarded call is active for the device.
    func acquire(_ callName: String, _ identifier: String, allowConcurrent: Bool = false) throws -> Token? {
        if allowConcurrent {
            return nil
        }
        let token = Token(callName: callName, identifier: identifier)
        lock.lock()
        defer { lock.unlock() }
        if let current = activeCalls[identifier] {
            throw PolarErrors.concurrentApiCall(
                description: "Cannot start \(callName) while \(current.description) is still in progress"
            )
        }
        activeCalls[identifier] = ActiveCall(id: token.id, callName: callName, identifier: identifier)
        return token
    }

    /// Releases a lock acquired with `acquire`. Releasing twice or with a nil token is a no-op.
    func release(_ token: Token?) {
        guard let token else { return }
        lock.lock()
        defer { lock.unlock() }
        if activeCalls[token.identifier]?.id == token.id {
            activeCalls[token.identifier] = nil
        }
    }

    /// Runs an async operation while holding the device lock.
    func guardAsync<T>(
        _ callName: String,
        _ identifier: String,
        allowConcurrent: Bool = false,
        _ block: () async throws -> T
    ) async throws -> T {
        let token = try acquire(callName, identifier, allowConcurrent: allowConcurrent)
        defer { release(token) }
        try Task.checkCancellation()
        return try await block()
    }

    /// Runs a synchronous operation while holding the device lock.
    func guardSync<T>(
        _ callName: String,
        _ identifier: String,
        allowConcurrent: Bool = false,
        _ block: () throws -> T
    ) throws -> T {
        let token = try acquire(callName, identifier, allowConcurrent: allowConcurrent)
        defer { release(token) }
        return try block()
    }

    /// Runs the producer of an `AsyncThrowingStream` while holding the device lock. Use in place of `Task { }`
    /// inside the stream builder. The `body` must finish the continuation itself.
    ///
    /// The lock is taken when the stream starts being consumed. It is released once the body has completed, or as
    /// soon as the body finishes the stream. When the consumer cancels, the body is cancelled and the lock is held
    /// until the body has actually returned, so device work can never overlap with the next call.
    /// If the lock cannot be acquired the stream fails immediately and the body is not run.
    func streamTask<T>(
        _ callName: String,
        _ identifier: String,
        _ continuation: AsyncThrowingStream<T, Error>.Continuation,
        allowConcurrent: Bool = false,
        _ body: @escaping @Sendable () async -> Void
    ) {
        let token: Token?
        do {
            token = try acquire(callName, identifier, allowConcurrent: allowConcurrent)
        } catch {
            continuation.finish(throwing: error)
            return
        }
        let task = Task {
            defer { self.release(token) }
            await body()
            continuation.finish()
        }
        continuation.onTermination = { [weak self] termination in
            if case .cancelled = termination {
                task.cancel()
            } else {
                self?.release(token)
            }
        }
    }
}
