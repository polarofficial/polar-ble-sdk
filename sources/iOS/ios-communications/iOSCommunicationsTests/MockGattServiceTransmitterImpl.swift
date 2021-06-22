//  Copyright © 2021 Polar. All rights reserved.

import Foundation
import iOSCommunications
import CoreBluetooth

class MockGattServiceTransmitterImpl: BleAttributeTransportProtocol {
    var mockConnectionStatus: Bool = true
    var setCharacteristicsNotifyCache: [(characteristicUuid: CBUUID, notify: Bool)] = []
    
    func isConnected() -> Bool {
        return mockConnectionStatus
    }
    
    func transmitMessage(_ parent: BleGattClientBase, serviceUuid: CBUUID , characteristicUuid: CBUUID , packet: Data, withResponse: Bool) throws {
        // Do nothing
    }
    
    func characteristicWith(uuid: CBUUID) throws -> CBCharacteristic? {
        return nil
    }
    
    func characteristicNameWith(uuid: CBUUID) -> String? {
        return nil
    }
    
    func readValue(_ parent: BleGattClientBase, serviceUuid: CBUUID , characteristicUuid: CBUUID ) throws {
        // Do nothing
    }
    
    func setCharacteristicNotify(_ parent: BleGattClientBase, serviceUuid: CBUUID, characteristicUuid: CBUUID, notify: Bool) throws {
        setCharacteristicsNotifyCache.append((characteristicUuid, notify))
        parent.notifyDescriptorWritten(characteristicUuid, enabled: notify, err: 0)
    }
    
    func attributeOperationStarted(){
        // Do nothing
    }
    
    func attributeOperationFinished(){
        // Do nothing
    }
}
