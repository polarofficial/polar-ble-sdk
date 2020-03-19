/// Copyright © 2019 Polar Electro Oy. All rights reserved.

import Foundation

/// GATT characteristic notification not enabled
public class NotificationNotEnabled: Error {
}

/// GATT service not found
public class ServiceNotFound: Error {
}

/// Device state != Connected
public class DeviceNotConnected: Error {
}

/// Device not found
public class DeviceNotFound: Error {
}

/// Requested operation is not supported
public class OperationNotSupported: Error {
}

/// Google protocol buffers encode failed
public class MessageEncodeFailed: Error {
}

/// Google protocol buffers decode failed
public class MessageDecodeFailed: Error {
}

/// String to date time formatting failed
public class DateTimeFormatFailed: Error {
}

/// Failed to start streaming
public class UnableToStartStreaming: Error {
}

/// invalid argument
public class InvalidArgument: Error {
}

/// Unknown error
public enum UndefinedError: LocalizedError {
    case DeviceError(localizedDescription: String)
    public var localizedDescription: String {
        switch self {
            case .DeviceError(let localizedDescription): return localizedDescription
        }
    }
}
