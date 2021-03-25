//
//  Mock.swift
//  iOSCommunicationsTests
//
//  Created by Jukka Oikarinen on 3.3.2021.
//  Copyright © 2021 Polar. All rights reserved.
//

import Foundation
import iOSCommunications
import CoreBluetooth

class MockGattServiceTransmitterImpl: BleAttributeTransportProtocol {
    func isConnected() -> Bool {
        return true
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
        // Do nothing
    }
    
    func attributeOperationStarted(){
        // Do nothing
    }
    
    func attributeOperationFinished(){
        // Do nothing
    }
}
