
import Foundation

public class BleRssiFilter{
    var rssiValues = Array<Int32>()
    public private(set) var rssi: Int32 = (-100)
    public private(set) var medianRssi:Int32 = (-100)
    var sortedRssis = Array<Int32>()
    let RSSI_MEDIAN_LIMIT = 7
    
    func processRssiValueUpdated(_ rssi: Int32) {
        if rssi < 0 {
            self.rssiValues.append(rssi)
            self.rssi = rssi
            if rssiValues.count >= RSSI_MEDIAN_LIMIT {
                sortedRssis = self.rssiValues.sorted().reversed()
                self.medianRssi = sortedRssis[3]
                self.rssiValues.removeFirst()
            } else {
                self.medianRssi = rssi
            }
        }
    }
}
