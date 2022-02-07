
import Foundation

public class BlePolarHrAdvertisement{
 
    public private(set) var batteryStatus = false
    public private(set) var sensorContact = false
    public private(set) var ucAdvFrameCounter: UInt8=0
    public private(set) var broadcastBit: UInt8=0
    public private(set) var sensorDataType: UInt8=0
    public private(set) var statusFlags: UInt8=0
    public private(set) var khzCode: UInt8=0
    public private(set) var fastAverageHr: UInt8=0
    public private(set) var slowAverageHr: UInt8=0
    var currentData=Data()
    
    public var isPresent: Bool {
        get {
            return currentData.count != 0
        }
    }
    
    public var isOldH7H6: Bool {
        get {
            return currentData.count == 3
        }
    }
    
    public var isH7Update: Bool {
        get {
            return !isOldH7H6
        }
    }
    
    public var hrValueForDisplay: UInt8 {
        get {
            return slowAverageHr
        }
    }
    
    public func processPolarManufacturerData(_ data: Data){
        currentData = data
        batteryStatus = (data[0] & 0x01) == 1
        sensorContact = ((data[0] & 0x02) >> 1) == 1
        ucAdvFrameCounter = (data[0] & 0x1C) >> 2
        broadcastBit = (data[0] & 0x20) >> 5
        sensorDataType = (data[0] & 0x40) >> 6
        statusFlags = (data[0] & 0x80) >> 7
        khzCode = data[1]
        fastAverageHr = (data[2] & 0x000000FF)
        if( data.count == 4 ){
            slowAverageHr = (data[3] & 0x000000FF)
        } else {
            slowAverageHr = (data[2] & 0x000000FF)
        }
    }
    
    public func description() -> String {
        return "HR: \(hrValueForDisplay) UC: \(ucAdvFrameCounter)"
    }
}
