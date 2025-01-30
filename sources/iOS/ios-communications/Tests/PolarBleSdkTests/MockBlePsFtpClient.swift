import Foundation
import RxSwift


public struct PsFtpNotification {
    // notification ID, @see PbPFtpDevToHostNotification
    public var id: Int32 = 0
    // notification parameters if any, @see pftp_notification.proto
    public var parameters = NSMutableData()
    
    public func description() -> String {
        return "Notification with ID: \(id)"
    }
}

public protocol BlePsFtpClient {
    func request(_ data: Data) -> Single<Data>
    func write(_ header: NSData, data: InputStream) -> Completable
    func sendNotification(_ id: Int, parameters: Data?) -> Completable
    func waitNotification() -> Observable<PsFtpNotification>
    func receiveRestApiEventData(identifier: String) -> Observable<[Data]>
    func receiveRestApiEvents<T:Decodable>(identifier: String) -> Observable<[T]>
}

class MockBlePsFtpClient: BlePsFtpClient {
    var requestCalls: [Data] = []
    var requestReturnValues: [Single<Data>] = []
    var requestReturnValueClosure: ((Data) -> Single<Data>)?
    var requestReturnValue: Single<Data>?
    var directoryContentReturnValue: Single<Data>?
    
    var writeCalls: [(header: NSData, data: InputStream)] = []
    var writeReturnValue: Completable?
    
    var sendNotificationCalls: [(notification: Int, parameters: Data?)] = []
    var sendNotificationReturnValue: Completable?
    
    var receiveNotificationCalls: [(notification: Int, parameters: [Data], compressed: Bool)] = []
    var receiveNotificationReturnValue: Completable?

    func request(_ data: Data) -> Single<Data> {
        requestCalls.append(data)

        if !requestReturnValues.isEmpty {
            return requestReturnValues.removeFirst()
        }
        
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
    
    func waitNotification() -> Observable<PsFtpNotification> {
        return Observable.from(receiveNotificationCalls)
            .map { (id, arrayOfData, compressed) in
                var event: Protocol_PbPftpDHRestApiEvent = Protocol_PbPftpDHRestApiEvent()
                event.uncompressed = compressed == false
                event.event = arrayOfData
                return PsFtpNotification( id: Int32(id), parameters: NSMutableData(data: try! event.serializedData()))
            }
    }
}
