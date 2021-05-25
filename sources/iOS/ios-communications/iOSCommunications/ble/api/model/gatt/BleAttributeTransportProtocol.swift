
import Foundation
import CoreBluetooth

/// Note anyone of these callbacks may come from different thread
public protocol BleAttributeTransportProtocol: AnyObject {
    func transmitMessage(_ parent: BleGattClientBase, serviceUuid: CBUUID, characteristicUuid: CBUUID, packet: Data, withResponse: Bool) throws
    
    func readValue(_ parent: BleGattClientBase, serviceUuid: CBUUID, characteristicUuid: CBUUID ) throws
    
    func setCharacteristicNotify(_ parent: BleGattClientBase, serviceUuid: CBUUID, characteristicUuid: CBUUID, notify: Bool) throws
    
    func isConnected() -> Bool
    
    // callback to inform of large attribute operation started. Client should stop scanning etc...
    func attributeOperationStarted()
    
    // callback to inform of large attribute operation stopped. Client can continue scanning etc...
    func attributeOperationFinished()
}
