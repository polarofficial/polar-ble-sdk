
import Foundation

public protocol BleLoggerProtocol {
    func logMessage(_ message: String)
}

public class BleLogger {
    public static let sharedInstance = BleLogger()
    public static let LOG_LEVEL_ALL   = 0xFF
    public static let LOG_LEVEL_ERROR = 0x01
    public static let LOG_LEVEL_TRACE = 0x02
    public static let LOG_LEVEL_HEX   = 0x04
    
    fileprivate var logger: BleLoggerProtocol?
    fileprivate var queue = DispatchQueue(label: "BleLoggerQueue")
    fileprivate var logLevel = 0
    
    public static func setLogLevel(_ level: Int){
        sharedInstance.queue.sync(execute: {
            sharedInstance.logLevel = level
        })
    }
    
    public static func setLogger(_ logger: BleLoggerProtocol?){
        sharedInstance.queue.sync(execute: {
            sharedInstance.logger = logger
        })
    }
    
    public static func trace(_ strings: String...){
        sharedInstance.queue.async(execute: {
            if( sharedInstance.logger != nil && (sharedInstance.logLevel & BleLogger.LOG_LEVEL_TRACE) != 0 ){
                let fullLog = "[BLE] " + strings.joined(separator: " ")
                sharedInstance.logger?.logMessage(fullLog)
            }
        })
    }
    
    public static func error(_ strings: String...){
        sharedInstance.queue.async(execute: {
            if( sharedInstance.logger != nil && (sharedInstance.logLevel & BleLogger.LOG_LEVEL_ERROR) != 0 ){
                let fullLog = "[BLE][ERROR] " + strings.joined(separator: " ")
                sharedInstance.logger?.logMessage(fullLog)
            }
        })
    }
    
    public static func trace_if_error(_ message: String, error: Error?){
        if error != nil {
            sharedInstance.queue.async(execute: {
                if( sharedInstance.logger != nil && (sharedInstance.logLevel & BleLogger.LOG_LEVEL_ERROR) != 0 ){
                    sharedInstance.logger?.logMessage("[BLE][ERROR] " + message + " err: " + (error?.localizedDescription)!)
                }
            })
        }
    }

    public static func trace_hex(_ message: String, data: Data) {
        sharedInstance.queue.async(execute: {
            if( sharedInstance.logger != nil && (sharedInstance.logLevel & BleLogger.LOG_LEVEL_HEX) != 0 ){
                let logStr = (message + " HEX " + data.compactMap { (byte) -> String in
                    return String(format: "%02X", byte)
                }.joined(separator: " "))
                sharedInstance.logger?.logMessage(logStr)
            }
        })
    }
    
    public static func trace_hex(_ message: String, data: [UInt8]) {
        sharedInstance.queue.async(execute: {
            if( sharedInstance.logger != nil && (sharedInstance.logLevel & BleLogger.LOG_LEVEL_HEX) != 0 ){
                let logStr = (message + " HEX " + data.compactMap { (byte) -> String in
                   return String(format: "%02X", byte)
               }.joined(separator: " "))
               sharedInstance.logger?.logMessage(logStr)
            }
        })
    }
}
