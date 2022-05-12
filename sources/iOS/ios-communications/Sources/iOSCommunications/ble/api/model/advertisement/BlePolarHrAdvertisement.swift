
import Foundation

public class BlePolarHrAdvertisement {
    var currentData = Data()
    
    public var batteryStatus : Bool {
        get {
            if(currentData.isEmpty) {
                return false
            } else {
                return (currentData[0] & 0x01) == 1
            }
        }
    }
    
    public var sensorContact : Bool {
        get {
            if(currentData.isEmpty) {
                return false
            } else {
                return ((currentData[0] & 0x02) >> 1) == 1
            }
        }
    }
    
    public var advFrameCounter: UInt8 {
        get {
            if(currentData.isEmpty) {
                return 0xFF
            } else {
                return (currentData[0] & 0x1C) >> 2
            }
        }
    }
    private var previousAdvFrameCounter: UInt8 = 0
    
    public var broadcastBit: UInt8 {
        get {
            if(currentData.isEmpty) {
                return 0
            } else {
                return (currentData[0] & 0x20) >> 5
            }
        }
    }
    
    public var sensorDataType: UInt8 {
        get {
            if(currentData.isEmpty) {
                return 0
            } else {
                return (currentData[0] & 0x40) >> 6
            }
        }
    }
    
    public var statusFlags: UInt8 {
        get {
            if(currentData.isEmpty) {
                return 0
            } else {
                return (currentData[0] & 0x80) >> 7
            }
        }
    }
    
    public var khzCode: UInt8 {
        get {
            if(currentData.isEmpty) {
                return 0
            } else {
                return currentData[1]
            }
        }
    }
    
    public var fastAverageHr: UInt8 {
        get {
            if(currentData.isEmpty) {
                return 0
            } else {
                return (currentData[2] & 0x000000FF)
            }
        }
    }
    
    public var slowAverageHr: UInt8 {
        get {
            if(currentData.isEmpty) {
                return 0
            } else {
                if( currentData.count == 4 ){
                    return (currentData[3] & 0x000000FF)
                } else {
                    return (currentData[2] & 0x000000FF)
                }
            }
        }
    }
    
    public var isHrDataUpdated: Bool {
        get { return previousAdvFrameCounter != advFrameCounter }
    }
    
    public var isPresent: Bool {
        get {
            return currentData.count != 0
        }
    }
    
    public var hrValueForDisplay: UInt8 {
        get {
            return slowAverageHr
        }
    }
    
    public func processPolarManufacturerData(_ data: Data) {
        previousAdvFrameCounter = advFrameCounter
        currentData = data
    }
    
    public func resetToDefault() {
        currentData = Data()
    }
    
    public func description() -> String {
        return "HR: \(hrValueForDisplay) UC: \(advFrameCounter)"
    }
}
