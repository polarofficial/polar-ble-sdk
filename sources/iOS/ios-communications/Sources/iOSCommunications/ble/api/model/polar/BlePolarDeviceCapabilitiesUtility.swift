
import Foundation

open class BlePolarDeviceCapabilitiesUtility {
    public enum FileSystemType {
        case unknownFileSystem
        case h10FileSystem
        case sagRfc2FileSystem
    }
    
    public static let H10 = "h10"
    public static let OH1 = "oh1"
    public static let SENSE = "sense"
    public static let INW4J = "inw4j"
    public static let INW5T = "inw5t"
    public static let POLAR_360 = "360"
    public static let IGNITE_3 = "ignite 3"
    public static let GRIT_X2_PRO = "grit x2 pro"
    public static let VANTAGE_V3 = "vantage v3"

    /// Get type of filesystem the device supports
    /// - Parameter deviceType:  device type
    /// - Returns: type of the file system supported or unknown file system type
   public static func fileSystemType(_ deviceType: String) -> FileSystemType {
        switch deviceType.lowercased() {
        case "h10":
            return FileSystemType.h10FileSystem
        case OH1: fallthrough
        case SENSE: fallthrough
        case INW5T: fallthrough
        case POLAR_360: fallthrough
        case IGNITE_3: fallthrough
        case GRIT_X2_PRO: fallthrough
        case VANTAGE_V3:
            return FileSystemType.sagRfc2FileSystem
        default:
            return FileSystemType.unknownFileSystem
        }
    }

    /// Check if device is supporting recording start and stop over BLE
    /// - Parameter deviceType: device type
    /// - Returns: true if device supports recoding
    public static func isRecordingSupported(_ deviceType: String) -> Bool {
        return deviceType == "H10"
    }
    
    /// Check if device is supporting firmware update
    /// - Parameter deviceType: device type
    /// - Returns: true if device firmware update
    public static func isFirmwareUpdateSupported(_ deviceType: String) -> Bool {
        let lowercasedDeviceType = deviceType.lowercased()
        return lowercasedDeviceType != OH1
    }

    /// Check if device is supporting activity data
    /// - Parameter deviceType: device type
    /// - Returns: true if device supports activity data
    public static func isActivityDataSupported(_ deviceType: String) -> Bool {
        let lowercasedDeviceType = deviceType.lowercased()
        return lowercasedDeviceType == POLAR_360
            || lowercasedDeviceType == IGNITE_3
            || lowercasedDeviceType == GRIT_X2_PRO
            || lowercasedDeviceType == VANTAGE_V3
    }
    
    public static func isDeviceSensor(_ deviceType: String) -> Bool {
        let lowercasedDeviceType = deviceType.lowercased()
        return lowercasedDeviceType == OH1 ||
               lowercasedDeviceType == H10 ||
               lowercasedDeviceType == SENSE ||
               lowercasedDeviceType == INW4J ||
               lowercasedDeviceType == POLAR_360
    }
}
