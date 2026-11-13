import Foundation

/// The SDK rejects a guarded API call with `PolarErrors.concurrentApiCall` when another guarded call for the same
/// device is active. This actor makes such calls wait in turn (FIFO) per device. Calls for different devices run
/// in parallel.
///
/// The lock is not reentrant. Do not start a serialized call from inside another serialized call for the same device.
actor DeviceApiCallSerializer {
    static let shared = DeviceApiCallSerializer()

    private struct Waiter {
        let id: UUID
        let continuation: CheckedContinuation<Void, Error>
    }

    private var busy: Set<String> = []
    private var waiters: [String: [Waiter]] = [:]

    nonisolated func serialize<T>(_ identifier: String, _ block: () async throws -> T) async throws -> T {
        try await acquire(identifier)
        do {
            let result = try await block()
            await release(identifier)
            return result
        } catch {
            await release(identifier)
            throw error
        }
    }

    /// True when a serialized call is running or queued for the device.
    func isBusy(_ identifier: String) -> Bool {
        busy.contains(identifier)
    }

    private func acquire(_ identifier: String) async throws {
        try Task.checkCancellation()
        if !busy.contains(identifier) {
            busy.insert(identifier)
            return
        }
        let id = UUID()
        try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
                waiters[identifier, default: []].append(Waiter(id: id, continuation: continuation))
            }
        } onCancel: {
            Task { await self.cancelWaiter(identifier, id) }
        }
    }

    private func cancelWaiter(_ identifier: String, _ id: UUID) {
        guard let index = waiters[identifier]?.firstIndex(where: { $0.id == id }) else { return }
        let waiter = waiters[identifier]!.remove(at: index)
        if waiters[identifier]?.isEmpty == true { waiters[identifier] = nil }
        waiter.continuation.resume(throwing: CancellationError())
    }

    private func release(_ identifier: String) {
        // Ownership passes directly to the next waiter, so busy stays set.
        if var queue = waiters[identifier], !queue.isEmpty {
            let next = queue.removeFirst()
            waiters[identifier] = queue.isEmpty ? nil : queue
            next.continuation.resume()
        } else {
            busy.remove(identifier)
        }
    }
}
