
import Foundation

/// gatt transport layer exceptions
public enum BleGattException: Error {
    case gattDisconnected
    case gattServiceNotFound
    case gattServicesNotFound
    case gattCharacteristicNotFound
    case gattCharacteristicNotifyNotEnabled
    case gattCharacteristicNotifyNotDisabled
    case gattCharacteristicNotifyError(errorCode: Int, errorDescription: String = "")
    case gattCharacteristicError
    case gattUndefinedDeviceError
    case gattAttributeError(errorCode: Int, errorDescription: String = "")
    case gattOperationNotSupported
    case gattTransportNotAvailable
    case gattOperationModeChange(description: String = "")
    case gattDataError(description: String = "")
    case gattSecurityError(description: String = "")
}

/// Errors caused by the client's own lifecycle, separate from transport or protocol level errors
public enum BleClientError: Error {
    /// The client was deallocated before a queued operation could run. Not caused by GATT disconnection alone.
    case clientDeallocated
}

