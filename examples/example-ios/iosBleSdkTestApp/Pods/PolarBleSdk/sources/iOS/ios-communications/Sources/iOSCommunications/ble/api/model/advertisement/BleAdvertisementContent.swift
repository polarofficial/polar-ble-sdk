
import Foundation
import CoreBluetooth
import RxSwift

public class BleAdvertisementContent {
    public private(set) var polarHrAdvertisementData = BlePolarHrAdvertisement()
    public private(set) var rssiFilter = BleRssiFilter()
    private let POLAR_OLD_H7_MANUFACTURER_DATA_LENGTH = 3
    private let POLAR_H7_UPDATE_MANUFACTURER_DATA_LENGTH = 4
    
    // public types
    public private(set) var advertisementTimestamp: TimeInterval = 0
    public private(set) var advertisementDelta: TimeInterval = 0
    public private(set) var polarDeviceIdInt: UInt32 = 0
    public private(set) var polarDeviceIdIntUntouched: UInt32 = 0
    public private(set) var isConnectable = true
    public private(set) var medianRssi: Int32 = -100
    public private(set) var advData = [String : Any]()
    public private(set) var name = ""
    public private(set) var polarDeviceId = ""
    public private(set) var polarDeviceIdUntouched = ""
    public private(set) var polarDeviceType = ""
    
    
    func processAdvertisementData(_ rssi: Int32, advertisementData: [String : Any]){
        advData = advertisementData
        advertisementDelta = TimeUtility.timeDeltaSeconds(advertisementTimestamp)
        advertisementTimestamp = TimeUtility.currentTime()
        rssiFilter.processRssiValueUpdated(rssi)
        medianRssi = rssiFilter.medianRssi
        
        processAdvName(advertisementData)
        
        if let connectable = advertisementData[CBAdvertisementDataIsConnectable] as? NSNumber{
            isConnectable = connectable == 1
        }
        processManufacturerData(advertisementData)
    }
    
    fileprivate func processAdvName(_ advertisementData: [String : Any]) {
        if let localName = advertisementData[CBAdvertisementDataLocalNameKey] as? String{
            if !(localName == name) {
                name = localName
                if PolarAdvDataUtility.isPolarDevice(advLocalName: name) {
                    polarDeviceType = PolarAdvDataUtility.getPolarModelNameFromAdvLocalName(advLocalName: name)
                    if let devId = name.split(separator: " ").map(String.init).last {
                        polarDeviceIdUntouched = devId
                        if let deviceId = UInt32(devId, radix: 16) {
                            polarDeviceId = BlePolarDeviceIdUtility.assemblyFullPolarDeviceId(deviceId, width: devId.count)
                            polarDeviceIdInt = BlePolarDeviceIdUtility.polarDeviceIdToInt(polarDeviceId)
                            polarDeviceIdIntUntouched = BlePolarDeviceIdUtility.polarDeviceIdToInt(polarDeviceIdUntouched)
                        }
                    }
                }
            }
        }
    }
    
    func processManufacturerData(_ advertisementData: [String : Any]) {
        var didContainHrData = false
        if let manData = advertisementData[CBAdvertisementDataManufacturerDataKey] as? Data, manData.count >= 2 {
            var manufacturer: Int16 = 0
            memcpy(&manufacturer, (manData as NSData).bytes, 2)
            if manufacturer == 0x006B {
                var offset = 0
                // polar manufacturer data example from OH1: 6b 00 72 06 7f 44 37 00 00 00 33 00 3c
                let content = manData.subdata(in: 2..<(manData.count))
                switch content.count {
                case POLAR_OLD_H7_MANUFACTURER_DATA_LENGTH: fallthrough
                case POLAR_H7_UPDATE_MANUFACTURER_DATA_LENGTH:
                    polarHrAdvertisementData.processPolarManufacturerData(content)
                    didContainHrData = true
                    break
                default:
                    while offset < content.count {
                        let gpbDataBit = content[offset] & 0x40
                        switch gpbDataBit {
                        case 0:
                            if ( (offset+3) <= content.count ){
                                polarHrAdvertisementData.processPolarManufacturerData(content.subdata(in: offset..<content.count))
                                didContainHrData = true
                            }
                            offset += 3
                        default:
                            offset += (Int(content[offset+1]) + 2)
                        }
                    }
                }
            }
        }
        if(!didContainHrData) {
            polarHrAdvertisementData.resetToDefault()
        }
    }
    
    public func containsService(_ service: CBUUID) -> Bool {
        if let services = advData[CBAdvertisementDataServiceUUIDsKey] as? [CBUUID] {
            return services.contains(service)
        }
        return false
    }
    
    func reset() {
        advData.removeAll()
    }
}
