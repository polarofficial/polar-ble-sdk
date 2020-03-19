
import Foundation
import RxSwift

class RxObserver<T>: Hashable {
    internal var obs: RxSwift.AnyObserver<T>
    init(obs: RxSwift.AnyObserver<T>){
        self.obs = obs
    }
    public func hash(into hasher: inout Hasher) {
        hasher.combine(ObjectIdentifier(self).hashValue)
    }
    static func == (lhs: RxObserver<T>, rhs: RxObserver<T>) -> Bool {
        return ObjectIdentifier(lhs) == ObjectIdentifier(rhs)
    }
}

class RxObserverCompletable {
    internal var obs: RxSwift.PrimitiveSequenceType.CompletableObserver
    init(obs: @escaping RxSwift.PrimitiveSequenceType.CompletableObserver){
        self.obs = obs
    }
}

class RxObserverSingle<T> {
    internal var obs: (RxSwift.SingleEvent<T>) -> ()
    init(obs: @escaping (RxSwift.SingleEvent<T>) -> ()){
        self.obs = obs
    }
}
