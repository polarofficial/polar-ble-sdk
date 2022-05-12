
import Foundation

public class BleRssiFilter{
    var rssiValues = NSMutableArray()
    public private(set) var rssi: Int32 = (-100)
    public private(set) var medianRssi:Int32 = (-100)
    var sortedRssis = NSMutableArray()
    let RSSI_MEDIAN_LIMIT = 7
    
    func processRssiValueUpdated(_ rssi: Int32) {
        if rssi < 0 {
            self.rssiValues.add(NSNumber(value: rssi as Int32))
            self.rssi = rssi
            if rssiValues.count >= RSSI_MEDIAN_LIMIT {
                sortedRssis.removeAllObjects()
                sortedRssis.addObjects(from: rssiValues as [AnyObject])
                let highestToLowest = NSSortDescriptor(key: "self", ascending: false)
                sortedRssis.sort(using: [highestToLowest])
                self.medianRssi = (sortedRssis.object(at: 3) as AnyObject).int32Value
                self.rssiValues.removeObject(at: 0)
            } else {
                self.medianRssi = rssi
            }
        }
    }
}
