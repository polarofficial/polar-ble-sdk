// Copyright © 2026 Polar Electro Oy. All rights reserved.

import Foundation

struct FetchTimeoutError: Error {}
struct FetchBackgroundTimeExpiredError: Error {}

/// Keeps completed and exhausted types out of reconnect continuations.
struct SyncFetchPlan {
    private let maxRetries: Int
    private(set) var isActive = false
    private(set) var pendingTypes: [SyncableDataType] = []
    private(set) var exhaustedTypes: Set<SyncableDataType> = []
    private(set) var postponedTypes: [SyncableDataType] = []
    private(set) var completedTypeCount = 0
    private var failureCounts: [SyncableDataType: Int] = [:]

    init(maxRetries: Int) {
        self.maxRetries = maxRetries
    }

    var next: SyncableDataType? { pendingTypes.first }
    var nextIsRetry: Bool {
        guard let next else { return false }
        return failureCounts[next, default: 0] > 0
    }

    @discardableResult
    mutating func beginCycle(dataTypes: [SyncableDataType]) -> Bool {
        guard !isActive else { return false }
        isActive = true
        pendingTypes = dataTypes
        exhaustedTypes = []
        postponedTypes = []
        completedTypeCount = 0
        failureCounts = [:]
        return true
    }

    mutating func complete(_ dataType: SyncableDataType) {
        pendingTypes.removeAll { $0 == dataType }
        completedTypeCount += 1
    }

    /// Failed types move behind unattempted work; exhausted types wait for a new cycle.
    @discardableResult
    mutating func recordFailure(_ dataType: SyncableDataType) -> Bool {
        pendingTypes.removeAll { $0 == dataType }
        failureCounts[dataType, default: 0] += 1
        if failureCounts[dataType, default: 0] <= maxRetries {
            pendingTypes.append(dataType)
            return true
        }
        exhaustedTypes.insert(dataType)
        return false
    }

    mutating func finishCycle() {
        isActive = false
    }

    mutating func postponeRemaining() {
        postponedTypes = pendingTypes
        pendingTypes = []
        finishCycle()
    }
}

/// Reconnection timers run off the main queue, while fetch completion runs on it.
final class SyncFetchRegistry<Value> {
    private let lock = NSLock()
    private var operations: [String: SyncFetchOperation<Value>] = [:]
    private var awaitingDisconnect: Set<String> = []

    subscript(deviceId: String) -> SyncFetchOperation<Value>? {
        get {
            lock.lock()
            defer { lock.unlock() }
            return operations[deviceId]
        }
        set {
            lock.lock()
            defer { lock.unlock() }
            operations[deviceId] = newValue
        }
    }

    func isBlocked(_ deviceId: String) -> Bool {
        lock.lock()
        defer { lock.unlock() }
        return operations[deviceId] != nil || awaitingDisconnect.contains(deviceId)
    }

    func requestDisconnect(_ deviceId: String) {
        lock.lock()
        defer { lock.unlock() }
        awaitingDisconnect.insert(deviceId)
    }

    @discardableResult
    func didDisconnect(_ deviceId: String) -> Bool {
        lock.lock()
        defer { lock.unlock() }
        return awaitingDisconnect.remove(deviceId) != nil
    }
}

/// The deadline releases the caller, but the request remains tracked until the SDK returns.
@MainActor
final class SyncFetchOperation<Value> {
    private var operationTask: Task<Void, Never>?
    private var timeoutTask: Task<Void, Never>?
    private var continuation: CheckedContinuation<Value, Error>?

    func run(
        seconds: TimeInterval,
        operation: @escaping @MainActor () async throws -> Value,
        onTimeout: @escaping @MainActor () -> Void,
        onDrained: @escaping @MainActor () -> Void
    ) async throws -> Value {
        if Task.isCancelled {
            onDrained()
            throw CancellationError()
        }
        return try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { continuation in
                self.continuation = continuation
                operationTask = Task {
                    let result: Result<Value, Error>
                    do {
                        try Task.checkCancellation()
                        let value = try await operation()
                        try Task.checkCancellation()
                        result = .success(value)
                    } catch {
                        result = .failure(error)
                    }
                    operationTask = nil
                    resolve(result)
                    onDrained()
                }
                timeoutTask = Task {
                    do {
                        try await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
                    } catch {
                        return
                    }
                    guard self.continuation != nil else { return }
                    operationTask?.cancel()
                    resolve(.failure(FetchTimeoutError()))
                    onTimeout()
                }
            }
        } onCancel: {
            Task { @MainActor in self.cancel() }
        }
    }

    func cancel() {
        operationTask?.cancel()
        resolve(.failure(CancellationError()))
    }

    private func resolve(_ result: Result<Value, Error>) {
        timeoutTask?.cancel()
        timeoutTask = nil
        let pending = continuation
        continuation = nil
        pending?.resume(with: result)
    }
}

enum CalendarDayFetcher {
    @MainActor
    static func fetch<Value>(
        from: Date,
        to: Date,
        calendar: Calendar = .current,
        onDayCompleted: (Date) -> Void = { _ in },
        readDay: @MainActor (Date) async throws -> [Value]
    ) async throws -> [Value] {
        var day = calendar.startOfDay(for: from)
        let lastDay = calendar.startOfDay(for: to)
        var results: [Value] = []
        while day <= lastDay {
            try Task.checkCancellation()
            let values = try await readDay(day)
            try Task.checkCancellation()
            results.append(contentsOf: values)
            onDayCompleted(day)
            guard let next = calendar.date(byAdding: .day, value: 1, to: day) else { break }
            day = next
        }
        return results
    }
}
