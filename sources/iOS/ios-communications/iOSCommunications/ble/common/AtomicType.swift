
import Foundation

/// template atomic 
public class AtomicType<T> : NSObject{
    var item: T
    let lock = NSCondition()

    init(initialValue: T) {
        item = initialValue
        super.init()
    }
    
    /// copy of item stored(note only for primitive types or copyable, for object use accessItem) to AtomicType
    public func get() -> T {
        // sorry no proper RAII support
        lock.lock()
        defer {
            lock.unlock()
        }
        return item
    }

    /// f function for atomic access for the stored item
    public func accessItem(_ f: (_ item: inout T) -> Void) {
        self.lock.lock()
        f(&item)
        self.lock.unlock()
    }
    
    /// @param value set new value for item
    public func set(_ value: T) {
        lock.lock()
        defer {
            lock.unlock()
        }
        item = value
        lock.signal()
    }
    
    /// wait's item to be set to new value
    public func wait() {
        lock.lock()
        lock.wait()
        lock.unlock()
    }
}
