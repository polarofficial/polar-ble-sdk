
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
    public private(set) var manufacturerType: Int16 = 0
    public private(set) var advData = [String : Any]()
    //private
    public private(set) var name=""
    public private(set) var polarDeviceId=""
    public private(set) var polarDeviceIdUntouched=""
    public private(set) var polarDeviceType=""
    
    func processAdvertisementData(_ rssi: Int32, advertisementData: [String : Any]){
        advData = advertisementData
        advertisementDelta = TimeUtility.timeDeltaSeconds(advertisementTimestamp)
        advertisementTimestamp = TimeUtility.currentTime()
        rssiFilter.processRssiValueUpdated(rssi)
        medianRssi = rssiFilter.medianRssi
        if let localName = advertisementData[CBAdvertisementDataLocalNameKey] as? String{
            if !(localName == name) {
                name = localName
                let items=name.split(separator: " ").map(String.init)
                if items.count > 2 {
                    polarDeviceType = items[1]
                    if let devId = items.last {
                        polarDeviceIdUntouched = devId
                        if let deviceId = UInt32(devId, radix: 16) {
                            polarDeviceId = BlePolarDeviceIdUtility.assemblyFullPolarDeviceId(deviceId, width: devId.count)
                            polarDeviceIdInt =
                                BlePolarDeviceIdUtility.polarDeviceIdToInt(polarDeviceId)
                            polarDeviceIdIntUntouched =
                                BlePolarDeviceIdUtility.polarDeviceIdToInt(polarDeviceIdUntouched)
                        }
                    }
                    if items.count == 4 {
                        polarDeviceType = polarDeviceType + " " + items[2]
                    }
                }
            }
        }
        
        if let connectable = advertisementData[CBAdvertisementDataIsConnectable] as? NSNumber{
            isConnectable = connectable == 1
        }
        if (advertisementData[CBAdvertisementDataManufacturerDataKey] as? Data) != nil {
            processManufacturerData(advertisementData)
        }
    }
    
    func processManufacturerData(_ advertisementData: [String : Any]) {
        if let manData = advertisementData[CBAdvertisementDataManufacturerDataKey] as? Data {
            var manufacturer: Int16=0
            memcpy(&manufacturer, (manData as NSData).bytes, 2)
            manufacturerType = manufacturer
            if manufacturer == 0x006B {
                var offset = 0
                // polar manufacturer data example from OH1: 6b 00 72 06 7f 44 37 00 00 00 33 00 3c
                let content = manData.subdata(in: 2..<(manData.count))
                switch content.count {
                case POLAR_OLD_H7_MANUFACTURER_DATA_LENGTH: fallthrough
                case POLAR_H7_UPDATE_MANUFACTURER_DATA_LENGTH:
                    polarHrAdvertisementData.processPolarManufacturerData(content)
                    break
                default:
                    while offset < content.count {
                        let gpbDataBit = content[offset] & 0x40
                        switch gpbDataBit {
                        case 0:
                            if ( (offset+3) <= content.count ){
                                polarHrAdvertisementData.processPolarManufacturerData(content.subdata(in: offset..<content.count))
                            }
                            offset += 3
                        default:
                            offset += (Int(content[offset+1]) + 2)
                        }
                    }
                }
            }
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
