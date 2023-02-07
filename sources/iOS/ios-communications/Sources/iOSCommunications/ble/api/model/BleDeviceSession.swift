
import Foundation
import RxSwift
import CoreBluetooth

@objc open class BleDeviceSession: NSObject {
    
    public enum DeviceSessionState{
        case
        /// Disconnected state
        sessionClosed,
        /// Connection attempting/connecting at the moment
        sessionOpening,
        /// Device is disconnected, but is waiting for advertisement head for reconnection, or power on event
        sessionOpenPark,
        /// Device is connected
        sessionOpen,
        /// Disconnecting at the moment
        sessionClosing
        
        public func description() -> String {
            switch self {
            case .sessionClosed:
                return "sessionClosed"
            case .sessionOpening:
                return "sessionOpening"
            case .sessionOpenPark:
                return "sessionOpenPark"
            case .sessionOpen:
                return "sessionOpen"
            case .sessionClosing:
                return "sessionClosing"
            }
        }
    }
    
    public enum ConnectionType {
        /// connection attempt is directly requested after disconnection
        case directConnection
        /// connection attempt is requested after first advertisement head, precondition is that device is connectable prior to connection attempt
        case connectFromAdvertisementHead
    }
    
    // apis to access
    public let address:UUID
    public let advertisementContent = BleAdvertisementContent()
    public var state = DeviceSessionState.sessionClosed
    public var previousState = DeviceSessionState.sessionClosed

    /// by default connect only from adv head
    public var connectionType = ConnectionType.connectFromAdvertisementHead
    var gattClients=[BleGattClientBase]()
    
    public init(_ addr: UUID){
        self.address=addr
    }
    
    /// helper to return BleGattClientBase instane based on service uuid
    ///
    /// - Parameter serviceUuid: service uuid to look for
    /// - Returns: instance of client implementation
    public func fetchGattClient(_ serviceUuid: CBUUID) -> BleGattClientBase? {
        return gattClients.first(where: { (client: BleGattClientBase) -> Bool in
            client.serviceBelongsToClient(serviceUuid)
        })
    }
    
    /// helper to check is the advertisement type connectable
    ///
    /// - Returns: Bool
    public func isConnectable() -> Bool {
        fatalError("not implemented")
    }
    
    /// Helper observable to asynchronously wait all services discovered
    ///
    /// - Parameter checkConnection: check current connection
    /// - Returns: Observable<CBUUID>
    public func monitorServicesDiscovered(_ checkConnection: Bool) -> Observable<CBUUID> {
        fatalError("not implemented")
    }
    
    /// Helper observable to asynchronously wait all services discovered
    ///
    /// - Parameter checkConnection: check current connection
    /// - Returns: Observable<CBUUID>
    /*public func monitorServicesDiscovered(_ checkConnection: Bool) -> Single<[CBUUID]> {
        return monitorServicesDiscovered(true)
            .toArray()
    }*/
    
    /// Helper observable to asynchronously wait all available/desired clients to be ready for use
    ///
    /// - Returns: Observable
    public func clientsReady() -> Observable<Never> {
        // improvement change to completable
        return monitorServicesDiscovered(true)
            .concatMap { (uid) -> Observable<Never> in
                if let client = self.fetchGattClient(uid) {
                    return client.clientReady(true).asObservable()
                } else {
                    // if client not found produce empty
                    return Observable.empty()
                }
            }
    }
}
