
import Foundation

open class BlePolarDeviceIdUtility {
    
    public static func isValidDeviceId(_ deviceId: String) -> Bool {
        switch deviceId.lengthOfBytes(using: String.Encoding.ascii) {
        case 8:
            if let deviceIdInt = UInt32(deviceId, radix: 16) {
                return self.checkSumForDeviceId(deviceIdInt, width: 8) == UInt8(deviceIdInt & 0x000000000000000F)
            }
            return false
        default:
            return self.checkSumForDeviceId(UInt32(strtouq(deviceId, nil, 16)), width: 8) != 0
        }
    }
    
    public static func checkSumForDeviceId(_ deviceId: UInt32, width: Int) -> UInt8 {
        var siftOffset: UInt32=0
        var a2: UInt8=0x01
        switch width {
            case 8:
                a2 = UInt8((deviceId >> 4) & 0x0F)
                siftOffset = 8
            case 7:
                a2 = UInt8((deviceId) & 0x0F)
                siftOffset = 4
            case 6:
                break
            default:
                return 0
        }
        let a3 = UInt8((deviceId >> siftOffset) & 0x0F)
        let a4 = UInt8((deviceId >> (siftOffset+4)) & 0x0F)
        let a5 = UInt8((deviceId >> (siftOffset+8)) & 0x0F)
        let a6 = UInt8((deviceId >> (siftOffset+12)) & 0x0F)
        let a7 = UInt8((deviceId >> (siftOffset+16)) & 0x0F)
        let a8 = UInt8((deviceId >> (siftOffset+20)) & 0x0F)
        let component = (3 * (a2+a4+a6+a8)+a3+a5+a7)
        return UInt8(component % 16)
    }
    
    public static func assemblyFullPolarDeviceId(_ deviceId: UInt32, width: Int) -> String {
        switch width {
            case 6:
                let checksum = checkSumForDeviceId(deviceId,width: width)
                return String(format: "%06X1%01X", deviceId,checksum)
            case 7:
                let checksum = checkSumForDeviceId(deviceId,width: width)
                let ret = NSMutableString()
                ret.append(String(format: "%07X%01X", deviceId,checksum))
                return ret as String
            case 8:
                return String(format: "%08X", deviceId)
            default:
                return ""
        }
    }
    
    public static func polarDeviceIdToInt(_ deviceId: String) -> UInt32 {
        if let value = UInt32(deviceId, radix: 16) {
            return value
        }
        return 0
    }
}
