///  Copyright © 2025 Polar. All rights reserved.

import Foundation
import zlib

extension Data {
    
    /// Return compressed data, using deflate algorithm
    /// - parameters:
    ///  - bufferSize: size of buffer used in compression. Defaults to 1024
    ///
    func deflated(_ bufferSize: Int = 1024) -> Data? {
        guard !self.isEmpty else { return nil }
        
        var stream = z_stream()
        stream.next_in = UnsafeMutablePointer<Bytef>(mutating: (self as NSData).bytes.bindMemory(to: Bytef.self, capacity: self.count))
        stream.avail_in = uint(self.count)
        
        let buffer = UnsafeMutablePointer<Bytef>.allocate(capacity: bufferSize)
        
        var deflatedData = Data()
        
        let initStatus = deflateInit2_(&stream, Z_DEFAULT_COMPRESSION, Z_DEFLATED, MAX_WBITS, MAX_MEM_LEVEL, Z_DEFAULT_STRATEGY, ZLIB_VERSION, Int32(MemoryLayout<z_stream>.size))
        guard initStatus == Z_OK else {
            buffer.deallocate()
            return nil
        }
        
        repeat {
            stream.next_out = buffer
            stream.avail_out = uInt(bufferSize)
            
            let deflateStatus = deflate(&stream, Z_FINISH)
            guard deflateStatus == Z_OK || deflateStatus == Z_STREAM_END else {
                deflateEnd(&stream)
                buffer.deallocate()
                return nil
            }
            
            let deflatedBytes = bufferSize - Int(stream.avail_out)
            deflatedData.append(buffer, count: deflatedBytes)
        } while stream.avail_out == 0
        
        deflateEnd(&stream)
        buffer.deallocate()
        
        return deflatedData
    }
}

extension Data {
    
    /// Return decompressed data, using inflate algorithm
    /// - parameters:
    ///  - bufferSize: size of buffer used in compression. Defaults to 1024
    ///
    func inflated(_ bufferSize: Int = 1024) -> Data? {
        
        guard !self.isEmpty else { return nil }
        
        var stream = z_stream()
        stream.next_in = UnsafeMutablePointer<Bytef>(mutating: (self as NSData).bytes.bindMemory(to: Bytef.self, capacity: self.count))
        stream.avail_in = uint(self.count)
        
        let buffer = UnsafeMutablePointer<Bytef>.allocate(capacity: bufferSize)
        
        var inflatedData = Data()
        
        let initStatus = inflateInit2_(&stream, MAX_WBITS + 32, ZLIB_VERSION, Int32(MemoryLayout<z_stream>.size))
        guard initStatus == Z_OK else {
            buffer.deallocate()
            return nil
        }
        
        repeat {
            stream.next_out = buffer
            stream.avail_out = uInt(bufferSize)
            
            let inflateStatus = inflate(&stream, Z_NO_FLUSH)
            guard inflateStatus == Z_OK || inflateStatus == Z_STREAM_END else {
                inflateEnd(&stream)
                buffer.deallocate()
                return nil
            }
            
            let inflatedBytes = bufferSize - Int(stream.avail_out)
            inflatedData.append(buffer, count: inflatedBytes)
        } while stream.avail_out == 0
        
        inflateEnd(&stream)
        buffer.deallocate()
        
        return inflatedData
    }
}
