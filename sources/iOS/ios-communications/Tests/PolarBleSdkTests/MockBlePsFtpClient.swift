import Foundation
import RxSwift
@testable import PolarBleSdk

class MockBlePsFtpClient: BlePsFtpClient {
    var requestCalls: [Data] = []
    var requestReturnValues: [Single<Data>] = []
    var requestReturnValueClosure: ((Data) -> Single<Data>)?
    var requestReturnValue: Single<Data>?
    var directoryContentReturnValue: Single<Data>?
    
    var writeCalls: [(header: NSData, data: InputStream)] = []
    var writeReturnValue: Observable<UInt>?
    
    var sendNotificationCalls: [(notification: Int, parameters: NSData?)] = []
    var sendNotificationReturnValue: Completable?
    
    var receiveNotificationCalls: [(notification: Int, parameters: [Data], compressed: Bool)] = []
    var receiveNotificationReturnValue: Completable?

    public override func request(_ header: Data) -> Single<NSData> {
        requestCalls.append(header)

        if !requestReturnValues.isEmpty {
            return requestReturnValues.removeFirst().map { NSData(data: $0) }
        }
        
        if let returnValue = requestReturnValueClosure {
            return returnValue(header).map { NSData(data: $0) }
        }
        return (requestReturnValue ?? Single.just(Data())).map { NSData(data: $0) }
    }
    
    public override func write(_ header: NSData, data: InputStream) -> Observable<UInt> {
        writeCalls.append((header, data))
        return writeReturnValue ?? Observable.empty()
    }
    
    public override func sendNotification(_ id: Int, parameters: NSData?) -> Completable {
        sendNotificationCalls.append((id, parameters))
        return sendNotificationReturnValue ?? Completable.empty()
    }
    
    override func waitNotification() -> Observable<PsFtpNotification> {
        return Observable.from(receiveNotificationCalls)
            .map { (id, arrayOfData, compressed) in
                var event: Protocol_PbPftpDHRestApiEvent = Protocol_PbPftpDHRestApiEvent()
                event.uncompressed = compressed == false
                event.event = arrayOfData
                let notification = PsFtpNotification()
                notification.id = Int32(id)
                notification.parameters = NSMutableData(data: try! event.serializedData())
                return notification
            }
    }
}
