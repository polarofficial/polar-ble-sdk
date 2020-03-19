
import Foundation

public enum AtomicIntegerException: Error{
    case waitTimeout
    case canceledSignal
}

/// general purpose "java" style atomic integer class
class AtomicInteger {
    
    var val: Int
    let lock = NSCondition()
    
    internal init(initialValue: Int) {
        self.val = initialValue
    }
    
    /// set value and signal condition
    ///
    /// - Parameters:
    ///     - value: value to be set
    internal func set(_ value: Int) {
        lock.lock()
        val = value
        lock.signal()
        lock.unlock()
    }
    
    /// increment by 1 current value, signal condition and return value
    ///
    /// - Returns: current value
    internal func incrementAndGet() -> Int{
        lock.lock()
        val += 1
        lock.signal()
        lock.unlock()
        return get()
    }

    /// increment by 1 current value and signal condition
    ///
    /// - Returns: current value
    internal func increment() {
        lock.lock()
        val += 1
        lock.signal()
        lock.unlock()
    }
    
    /// reduce by 1 current value, signal condition and return
    ///
    /// - Returns: current value
    internal func decrementAndGet() -> Int{
        lock.lock()
        val -= 1
        lock.signal()
        lock.unlock()
        return get()
    }
    
    /// reset set value to 0 and signal condition
    func reset() {
        lock.lock()
        val = 0
        lock.signal()
        lock.unlock()
    }
    
    /// get current value
    ///
    /// - Returns: current value
    func get() -> Int {
        lock.lock()
        defer {
            lock.unlock()
        }
        return val
    }
    
    /// wait endlessly
    func wait() {
        lock.lock()
        lock.wait()
        lock.unlock()
    }
    
    /// signal condition
    func signal(){
        lock.lock()
        lock.signal()
        lock.unlock()
    }
    
    /// checkAndWait endlessly
    ///
    /// - Parameters:
    ///     - waited: value to be checked
    func checkAndWait(_ waited: Int){
        lock.lock()
        if val != waited {
            lock.wait()
        }
        lock.unlock()
    }
    
    /// checkAndWait check atomically value stored or wait if precondition is not met
    ///
    /// - Parameters:
    ///     - waited: value to be checked
    ///     - secs: max to be waited
    /// - Throws:
    ///     - waitTimeout: secs timeout passed without value been updated
    func checkAndWait(_ waited: Int, secs: TimeInterval) throws {
        try self.checkAndWait(waited, secs: secs) {
            // do nothing
        }
    }
    
    /// checkAndWait check atomically value stored or wait if precondition is not met
    ///
    /// - Parameters:
    ///     - waited: value to be checked
    ///     - secs: max time to be waited
    ///     - canceled: block that can be cancelled
    ///     - canceledError: error to be thrown if cancel block is invoked
    ///     - timeoutcall: informal lambda to be invoked when timeout has been triggered
    /// - Throws:
    ///     - waitTimeout: secs timeout passed without value been updated
    func checkAndWait(_ waited: Int, secs: TimeInterval, canceled: BlockOperation, canceledError: Error, timeoutCall: () -> Void) throws {
        lock.lock()
        defer {
            lock.unlock()
        }
        if !canceled.isCancelled {
            if val != waited {
                if !lock.wait(until: Date (timeIntervalSinceNow: secs)){
                    timeoutCall()
                    throw AtomicIntegerException.waitTimeout
                }
                if canceled.isCancelled {
                    throw canceledError
                }
            }
        } else {
            throw canceledError
        }
    }
    
    /// checkAndWait check atomically value stored or wait if precondition is not met
    ///
    /// - Parameters:
    ///     - waited: value to be checked
    ///     - secs: max to be waited
    ///     - timeoutcall: informal lambda to be invoked when timeout has been triggered
    /// - Throws:
    ///     - waitTimeout: secs timeout passed without value been updated
    func checkAndWait(_ waited: Int, secs: TimeInterval, timeoutCall: () -> Void) throws {
        lock.lock()
        defer {
            lock.unlock()
        }
        if val != waited {
            if !lock.wait(until: Date (timeIntervalSinceNow: secs)){
                timeoutCall()
                throw AtomicIntegerException.waitTimeout
            }
        }
    }
}

/// ++ operator
prefix func ++( x: inout AtomicInteger) {
    x.increment()
}

/// -- operator
prefix func --( x: inout AtomicInteger) {
    _ = x.decrementAndGet()
}
