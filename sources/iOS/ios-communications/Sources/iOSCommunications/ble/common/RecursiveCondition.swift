
import Foundation

public class RecursiveCondition {

    var condition = pthread_cond_t()
    var mutex = pthread_mutex_t()
    
    public init() {
        var attrs = pthread_mutexattr_t()
        pthread_mutexattr_init( &attrs )
        pthread_mutexattr_settype( &attrs, PTHREAD_MUTEX_RECURSIVE )
        pthread_mutex_init( &mutex, &attrs )
        pthread_mutexattr_destroy( &attrs )
        pthread_cond_init( &condition, nil )
    }

    deinit {
        pthread_cond_destroy( &condition )
        pthread_mutex_destroy( &mutex )
    }
    
    public func lock(){
        pthread_mutex_lock( &mutex )
    }

    public func unlock(){
        pthread_mutex_unlock( &mutex )
    }

    public func signal() {
        pthread_mutex_lock( &mutex )
        pthread_cond_signal( &condition )
        pthread_mutex_unlock( &mutex )
    }

    public func wait(){
        pthread_mutex_lock( &mutex )
        pthread_cond_wait( &condition, &mutex )
        pthread_mutex_unlock( &mutex )
    }

    public func wait(until: Date) -> Bool {
        pthread_mutex_lock( &mutex )
        var expireTime = timespec()
        expireTime.tv_sec = __darwin_time_t(until.timeIntervalSince1970)
        expireTime.tv_nsec = 0
        let result = pthread_cond_timedwait( &condition, &mutex, &expireTime )
        pthread_mutex_unlock( &mutex )
        return result != ETIMEDOUT
    }
}
