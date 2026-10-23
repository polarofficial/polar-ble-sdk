
import Foundation

public enum AtomicListException: Error{
    case waitTimeout
    case emptyQueueSignal
    case canceledSignal
}

class AtomicList<T> {
    var items:[T] = []
    let lock = NSCondition()
    
    func pushItems( _ items: [T]) {
        lock.lock()
        self.items += items
        lock.signal()
        lock.unlock()
    }

    func append(_ item: T) {
        lock.lock()
        self.items.append(item)
        lock.unlock()
    }

    func push(_ item: T) {
        lock.lock()
        self.items.append(item)
        lock.signal()
        lock.unlock()
    }
    
    func removeAll() {
        lock.lock()
        self.items.removeAll()
        lock.signal()
        lock.unlock()
    }
    
    func size() -> Int {
        lock.lock()
        defer {
            lock.unlock()
        }
        return items.count
    }
    
    func pop() throws -> T {
        lock.lock()
        defer { lock.unlock() }
        guard let item = items.first else {
            throw AtomicListException.emptyQueueSignal
        }
        items.removeFirst()
        return item
    }
    
    func remove(_ f: (_ item: T) -> Bool ) {
        lock.lock()
        for i in 0..<self.items.count {
            if f(items[i]) {
                items.remove(at: i)
                lock.unlock()
                return
            }
        }
        lock.unlock()
    }
    
    func fetch(_ f: (_ item: T) -> Bool ) -> T? {
        lock.lock()
        let object = items.filter { (object: T) -> Bool in
            return f(object)
        }.first
        lock.unlock()
        return object
    }
    
    func removeIf(_ f: (_ item: T) -> Bool ) -> Int {
        lock.lock()
        defer {
            lock.unlock()
        }
        let count = items.count
        items = items.filter({ (_ object: T) -> Bool in
            return !f(object)
        })
        return count - items.count
    }
    
    func list() -> [T] {
        var ret=[T]()
        lock.lock()
        ret.append(contentsOf: items)
        lock.unlock()
        return ret
    }

    /// Atomically removes all items from the list and returns them.
    /// This guarantees no item can be appended between the snapshot and the clear.
    func drain() -> [T] {
        lock.lock()
        let snapshot = items
        items.removeAll()
        lock.unlock()
        return snapshot
    }
    
    /// Removes and returns the first item. Caller must already hold `lock`.
    private func popLocked() throws -> T {
        guard let item = items.first else {
            throw AtomicListException.emptyQueueSignal
        }
        items.removeFirst()
        return item
    }

    /// `lock` is held continuously across the check-and-wait, per the
    /// standard condition-variable pattern ("check the predicate in a loop while holding the
    /// lock"), so no push can be missed between the check and the wait.
    func poll( _ secs: TimeInterval, canceled: BlockOperation, cancelError: Error ) throws -> T {
        lock.lock()
        defer { lock.unlock() }

        let deadline = Date(timeIntervalSinceNow: secs)
        while items.isEmpty {
            if canceled.isCancelled {
                throw cancelError
            }
            guard lock.wait(until: deadline) else {
                throw AtomicListException.waitTimeout
            }
        }
        if canceled.isCancelled {
            throw cancelError
        }
        return try popLocked()
    }
    
    func poll( _ secs: TimeInterval ) throws -> T {
        lock.lock()
        defer { lock.unlock() }

        let deadline = Date(timeIntervalSinceNow: secs)
        while items.isEmpty {
            guard lock.wait(until: deadline) else {
                throw AtomicListException.waitTimeout
            }
        }
        return try popLocked()
    }
    
    func poll() throws -> T {
        if(size() != 0){
            return try pop()
        } else {
            throw AtomicListException.emptyQueueSignal
        }
    }
    
    func pollUntilSignaled() throws -> T {
        lock.lock()
        defer { lock.unlock() }

        while items.isEmpty {
            lock.wait()
        }
        return try popLocked()
    }
    
    func pollUntilSignaled(canceled: BlockOperation, cancelError: Error) throws -> T {
        lock.lock()
        defer { lock.unlock() }

        while items.isEmpty {
            if canceled.isCancelled {
                throw cancelError
            }
            lock.wait()
        }
        if canceled.isCancelled {
            throw cancelError
        }
        return try popLocked()
    }
}
