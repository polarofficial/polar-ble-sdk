
import Foundation
import CoreBluetooth

class BleGattClientFactory{
    let gattClients: [(_ gattServiceTransmitter: BleAttributeTransportProtocol) -> BleGattClientBase]
    
    init(_ clients: [(_ gattServiceTransmitter: BleAttributeTransportProtocol) -> BleGattClientBase]) {
        gattClients = clients
    }
    
    func loadClients(_ transport: BleAttributeTransportProtocol) -> [BleGattClientBase] {
        return gattClients.compactMap { client in
            return client(transport) 
        } 
    }
}
