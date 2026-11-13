
import Foundation
import CoreBluetooth

/// Note anyone of these callbacks may come from different thread
public protocol BleAttributeTransportProtocol: AnyObject {
    func transmitMessage(_ parent: BleGattClientBase, serviceUuid: CBUUID, characteristicUuid: CBUUID, packet: Data, withResponse: Bool) throws
    
    func readValue(_ parent: BleGattClientBase, serviceUuid: CBUUID, characteristicUuid: CBUUID ) throws
    
    func setCharacteristicNotify(_ parent: BleGattClientBase, serviceUuid: CBUUID, characteristicUuid: CBUUID, notify: Bool) throws
    
    /// Drops the write-without-response of the given client and characteristic that is parked until the peripheral
    /// is ready to send, without acknowledging it. A parked write of any other client or characteristic is left untouched.
    /// Once this returns, the dropped packet is never transmitted and never acknowledged.
    func discardPendingWriteWithoutResponse(_ parent: BleGattClientBase, characteristicUuid: CBUUID)

    /// Asks the transport to drop the connection. Used when write acknowledgements can no longer be attributed to
    /// the write they belong to; the client is notified through its disconnected() callback.
    /// Every transport must implement this: without it, PS-FTP could not recover from unattributable acknowledgements.
    func requestDisconnect()

    func isConnected() -> Bool
    
    // callback to inform of large attribute operation started. Client should stop scanning etc...
    func attributeOperationStarted()
    
    // callback to inform of large attribute operation stopped. Client can continue scanning etc...
    func attributeOperationFinished()
}

public extension BleAttributeTransportProtocol {
    func discardPendingWriteWithoutResponse(_ parent: BleGattClientBase, characteristicUuid: CBUUID) {}
}
