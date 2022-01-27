
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
    
    func pop() -> T {
        var item: T
        lock.lock()
        item = items.removeFirst()
        lock.unlock()
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
    
    func poll( _ secs: TimeInterval, canceled: BlockOperation, cancelError: Error ) throws -> T {
        if !canceled.isCancelled {
            if( size() != 0 ){
                return pop()
            }else{
                lock.lock()
                if lock.wait(until: Date (timeIntervalSinceNow: secs)) {
                    lock.unlock()
                    if canceled.isCancelled {
                        throw cancelError
                    } else if( size() != 0 ){
                        return pop()
                    } else {
                        throw AtomicListException.emptyQueueSignal
                    }
                } else {
                    lock.unlock()
                    throw AtomicListException.waitTimeout
                }
            }
        } else {
            throw cancelError
        }
    }
    
    func poll( _ secs: TimeInterval ) throws -> T {
        if( size() != 0 ){
            return pop()
        }else{
            lock.lock()
            if lock.wait(until: Date (timeIntervalSinceNow: secs)) {
                lock.unlock()
                if( size() != 0 ){
                    return pop()
                }else{
                    throw AtomicListException.emptyQueueSignal
                }
            } else {
                lock.unlock()
                throw AtomicListException.waitTimeout
            }
        }
    }
    
    func poll() throws -> T {
        if(size() != 0){
            return pop()
        } else {
            throw AtomicListException.emptyQueueSignal
        }
    }
    
    func pollUntilSignaled() throws -> T {
        if(size() != 0){
            return pop()
        } else {
            self.lock.lock()
            self.lock.wait()
            self.lock.unlock()
            return try poll()
        }
    }
    
    func pollUntilSignaled(canceled: BlockOperation, cancelError: Error) throws -> T {
        if !canceled.isCancelled {
            if(size() != 0){
                return pop()
            } else {
                self.lock.lock()
                self.lock.wait()
                self.lock.unlock()
                if canceled.isCancelled {
                    throw cancelError
                } else if( size() != 0 ){
                    return pop()
                } else {
                    throw AtomicListException.emptyQueueSignal
                }
            }
        } else {
            throw cancelError
        }
    }
}
