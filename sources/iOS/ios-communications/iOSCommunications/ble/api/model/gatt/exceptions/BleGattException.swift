
import Foundation

/// gatt transport layer exceptions
public enum BleGattException: Error{
    case gattDisconnected
    case gattServiceNotFound
    case gattServicesNotFound
    case gattCharacteristicNotFound
    case gattCharacteristicNotifyNotEnabled
    case gattCharacteristicError
    case gattUndefinedDeviceError
    case gattAttributeError(errorCode: Int, errorDescription: String = "")
    case gattOperationNotSupported
    case gattTransportNotAvailable
}
