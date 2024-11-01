import Foundation
import RxSwift


public protocol BlePsFtpClient {
    func request(_ data: Data) -> Single<Data>
    func write(_ header: NSData, data: InputStream) -> Completable
    func sendNotification(_ id: Int, parameters: Data?) -> Completable
}

class MockBlePsFtpClient: BlePsFtpClient {
    var requestCalls: [Data] = []
    var requestReturnValueClosure: ((Data) -> Single<Data>)?
    var requestReturnValue: Single<Data>?
    var directoryContentReturnValue: Single<Data>?
    
    var writeCalls: [(header: NSData, data: InputStream)] = []
    var writeReturnValue: Completable?
    
    var sendNotificationCalls: [(notification: Int, parameters: Data?)] = []
    var sendNotificationReturnValue: Completable?

    func request(_ data: Data) -> Single<Data> {
        requestCalls.append(data)
        if let returnValue = requestReturnValueClosure {
            return returnValue(data)
        }
        return requestReturnValue ?? Single.just(Data())
    }

    func write(_ header: NSData, data: InputStream) -> Completable {
        writeCalls.append((header, data))
        return writeReturnValue ?? Completable.empty()
    }
    
    func sendNotification(_ notification: Int, parameters: Data?) -> Completable {
        sendNotificationCalls.append((notification, parameters))
        return sendNotificationReturnValue ?? Completable.empty()
    }
}
