//  Copyright © 2022 Polar. All rights reserved.

import Foundation

class TypeUtils {
    
    static func convertArrayToSignedInt(_ data: Data, offset: Int, size: Int) -> Int32 {
        return convertArrayToSignedInt(data.subdata(in: offset..<(offset+size)))
    }
    
    static func convertArrayToSignedInt(_ data: Data) -> Int32 {
        assert(data.count <= 4)
        var value: Int32 = 0
        memcpy(&value, (data as NSData).bytes, data.count)
        let mask = (Int32.max << ((data.count * 8) - 1))
        if (value & mask) != 0 {
            value |= mask
        }
        return value
    }
    
    static func convertArrayToUnsignedInt(_ data: Data, offset: Int, size: Int) -> UInt {
        return convertArrayToUnsignedInt(data.subdata(in: offset..<(offset+size)))
    }
    
    static func convertArrayToUnsignedInt(_ data: Data) -> UInt {
        assert(data.count <= 4)
        var value: UInt = 0
        memcpy(&value, (data as NSData).bytes, data.count)
        let mask = (UInt.max << ((data.count * 8) - 1))
        if (value & mask) != 0 {
            value |= mask
        }
        return value
    }
}
