
import Foundation

open class BlePolarDeviceCapabilitiesUtility {
    public enum FileSystemType {
        case unknownFileSystem
        case h10FileSystem
        case sagRfc2FileSystem
    }
    
    /// Get type of filesystem the device supports
    /// - Parameter deviceType:  device type
    /// - Returns: type of the file system supported or unknown file system type
   public static func fileSystemType(_ deviceType: String) -> FileSystemType {
        switch deviceType.lowercased() {
        case "h10":
            return FileSystemType.h10FileSystem
        case "oh1": fallthrough
        case "sense":
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
}
