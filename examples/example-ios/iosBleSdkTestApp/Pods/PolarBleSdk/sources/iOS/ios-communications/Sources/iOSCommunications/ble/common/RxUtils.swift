
import Foundation
import RxSwift

/// general purpose rx helper utils
class RxUtils {
    
    /// helper to emit specific error and clear list
    static func postErrorAndClearList<T>(_ list: AtomicList<RxObserver<T>>, error: Error ) {
        list.list().forEach { (object) in
            object.obs.onError(error)
        }
        list.removeAll()
    }
    
    static func postErrorOnSingleAndClearList<T>(_ list: AtomicList<RxObserverSingle<T>>, error: Error) {
        list.list().forEach { (object) in
            object.obs(.failure(error))
        }
        list.removeAll()
    }
    
    static func postErrorOnCompletableAndClearList(_ list: AtomicList<RxObserverCompletable>, error: Error) {
        list.list().forEach { (object) in
            object.obs(.error(error))
        }
        list.removeAll()
    }
    
    /// helper to emit next object
    static func emitNext<T>(_ list: AtomicList<T>, emitter: (_ item: T) -> Void ) {
        let objects = list.list()
        for object in objects {
            emitter(object)
        }
    }
    
    static func emitNext<T>(_ list: Set<T>, emitter: (_ item: T) -> Void ) {
        let objects = Array(list)
        for object in objects {
            emitter(object)
        }
    }
    
    static func monitor<T>(_ list: AtomicList<RxObserver<T>>, transport: BleAttributeTransportProtocol?, checkConnection: Bool) -> Observable<T> {
        var object: RxObserver<T>!
        return Observable<T>.create { observer in
            object = RxObserver<T>.init(obs: observer)
            if !checkConnection || transport?.isConnected() ?? false {
                list.append(object)
            } else {
                observer.onError(BleGattException.gattDisconnected)
            }
            return Disposables.create {
                list.remove({ (item) -> Bool in
                    return item === object
                })
            }
        }
    }
}
