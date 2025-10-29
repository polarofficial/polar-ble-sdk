
import Foundation
import os

public protocol BleLoggerProtocol {
    func logMessage(_ message: String, privacy: OSLogPrivacy)
}

public extension BleLoggerProtocol {
    func logMessage(_ message: String) {
        logMessage(message, privacy: .public)
    }
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
    private let unifiedLogger = Logger(subsystem: "Polar-BLE-SDK", category: "BleLogger")

    public static func setLogLevel(_ level: Int){
        sharedInstance.queue.sync {
            sharedInstance.logLevel = level
        }
    }
    
    public static func setLogger(_ logger: BleLoggerProtocol?){
        sharedInstance.queue.sync {
            sharedInstance.logger = logger
        }
    }

    private static func logToUnified(_ logger: Logger, level: Int, message: String, privacy: OSLogPrivacy) {
        let isPrivate = String(describing: privacy).contains("private")
        if isPrivate {
            if level == LOG_LEVEL_ERROR {
                logger.error("\(message, privacy: .private)")
            } else {
                logger.debug("\(message, privacy: .private)")
            }
        } else {
            if level == LOG_LEVEL_ERROR {
                logger.error("\(message, privacy: .public)")
            } else {
                logger.debug("\(message, privacy: .public)")
            }
        }
    }
    
    public static func trace(_ strings: String..., privacy: OSLogPrivacy = .public) {
        sharedInstance.queue.async {
            if (sharedInstance.logLevel & LOG_LEVEL_TRACE) != 0 {
                let fullLog = "[BLE] " + strings.joined(separator: " ")
                if let customLogger = sharedInstance.logger {
                    customLogger.logMessage(fullLog, privacy: privacy)
                } else {
                    logToUnified(sharedInstance.unifiedLogger, level: LOG_LEVEL_TRACE, message: fullLog, privacy: privacy)
                }
            }
        }
    }

    public static func error(_ strings: String..., privacy: OSLogPrivacy = .public) {
        sharedInstance.queue.async {
            if (sharedInstance.logLevel & LOG_LEVEL_ERROR) != 0 {
                let fullLog = "[BLE][ERROR] " + strings.joined(separator: " ")
                if let customLogger = sharedInstance.logger {
                    customLogger.logMessage(fullLog, privacy: privacy)
                } else {
                    logToUnified(sharedInstance.unifiedLogger, level: LOG_LEVEL_ERROR, message: fullLog, privacy: privacy)
                }
            }
        }
    }

    public static func trace_if_error(_ message: String, error: Error?, privacy: OSLogPrivacy = .public) {
        if let err = error {
            sharedInstance.queue.async {
                if (sharedInstance.logLevel & LOG_LEVEL_ERROR) != 0 {
                    let msg = "[BLE][ERROR] " + message + " err: " + err.localizedDescription
                    if let customLogger = sharedInstance.logger {
                        customLogger.logMessage(msg, privacy: privacy)
                    } else {
                        logToUnified(sharedInstance.unifiedLogger, level: LOG_LEVEL_ERROR, message: msg, privacy: privacy)
                    }
                }
            }
        }
    }

    public static func trace_hex(_ message: String, data: Data, privacy: OSLogPrivacy = .public) {
        sharedInstance.queue.async {
            if (sharedInstance.logLevel & LOG_LEVEL_HEX) != 0 {
                let logStr = message + " HEX " + data.map { String(format: "%02X", $0) }.joined(separator: " ")
                if let customLogger = sharedInstance.logger {
                    customLogger.logMessage(logStr, privacy: privacy)
                } else {
                    logToUnified(sharedInstance.unifiedLogger, level: LOG_LEVEL_HEX, message: logStr, privacy: privacy)
                }
            }
        }
    }

    public static func trace_hex(_ message: String, data: [UInt8], privacy: OSLogPrivacy = .public) {
        sharedInstance.queue.async {
            if (sharedInstance.logLevel & LOG_LEVEL_HEX) != 0 {
                let logStr = message + " HEX " + data.map { String(format: "%02X", $0) }.joined(separator: " ")
                if let customLogger = sharedInstance.logger {
                    customLogger.logMessage(logStr, privacy: privacy)
                } else {
                    logToUnified(sharedInstance.unifiedLogger, level: LOG_LEVEL_HEX, message: logStr, privacy: privacy)
                }
            }
        }
    }
}
