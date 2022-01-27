
import Foundation

/// general purpose "java" style atomic boolean class
public class AtomicBoolean {
    
    fileprivate var val: Bool
    let lock = NSCondition()

    init(initialValue: Bool) {
        self.val = initialValue
    }

    /// get current boolean value, may wait if lock is aquired by another thread
    public func get() -> Bool {
        lock.lock()
        defer {
            lock.unlock()
        }
        return val
    }

    /// set new boolean value, may wait if lock is aquired by another thread
    public func set(_ value: Bool){
        lock.lock()
        val = value
        lock.signal()
        lock.unlock()
    }
    
    /// wait infinite time for value update
    func wait() {
        lock.lock()
        lock.wait()
        lock.unlock()
    }
    
    /// check waited value or wait for value update
    func checkAndWait(_ waited: Bool){
        lock.lock()
        if val != waited {
            lock.wait()
        }
        lock.unlock()
    }
}
