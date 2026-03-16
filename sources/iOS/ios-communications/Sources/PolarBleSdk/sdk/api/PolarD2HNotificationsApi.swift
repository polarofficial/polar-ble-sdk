import Foundation
import RxSwift

public enum PolarDeviceToHostNotification: Int {
  
  public typealias RawValue = Int

  /// (Not used currently for anything. Reserved for future use.)
  case filesystemModified // = 0

  /// Used to inform host about internal test data.
  case internalTestEvent // = 1

  /// Used to inform host when the device is ready to communicate again after reporting WAIT_FOR_IDLING error.
  case idling // = 2

  /// Used to inform host about device's battery status.
  case batteryStatus // = 3

  /// Used to inform host about user's inactivity.
  case inactivityAlert // = 4

  /// Used to inform host about training session status.
  case trainingSessionStatus // = 5

  /// Used by device to request host to sync. This happens for example when user presses "Sync" button in the device.
  case syncRequired // = 7

  /// Used by device to inform result of START_AUTOSYNC synchronization.
  case autosyncStatus // = 8

  /// Used to send responses to Polar Notification Service notifications.
  case pnsDhNotificationResponse // = 9

  /// Used for Polar Notification Service settings
  case pnsSettings // = 10

  /// Used to request mobile device to start GPS measurement. Parameter PbPftpStartGPSMeasurement
  case startGpsMeasurement // = 11

  /// Used to request mobile device to stop GPS measurement. No parameters
  case stopGpsMeasurement // = 12

  /// Used to keep mobile running in background. No parameters
  case keepBackgroundAlive // = 13

  /// Polar shell is to transfer any test related data from device to host
  case polarShellDhData // = 14

  /// Request information from media player
  case mediaControlRequestDh // = 15

  /// Send command for media player
  case mediaControlCommandDh // = 16

  /// Used for informing host when device wants to receive media control data
  case mediaControlEnabled // = 17

  /// Generic REST API event
  case restApiEvent // = 18

  /// Used to inform host about exercise status
  case exerciseStatus // = 19

  init() {
    self = .filesystemModified
  }

  public init?(rawValue: Int) {
    switch rawValue {
    case 0: self = .filesystemModified
    case 1: self = .internalTestEvent
    case 2: self = .idling
    case 3: self = .batteryStatus
    case 4: self = .inactivityAlert
    case 5: self = .trainingSessionStatus
    case 7: self = .syncRequired
    case 8: self = .autosyncStatus
    case 9: self = .pnsDhNotificationResponse
    case 10: self = .pnsSettings
    case 11: self = .startGpsMeasurement
    case 12: self = .stopGpsMeasurement
    case 13: self = .keepBackgroundAlive
    case 14: self = .polarShellDhData
    case 15: self = .mediaControlRequestDh
    case 16: self = .mediaControlCommandDh
    case 17: self = .mediaControlEnabled
    case 18: self = .restApiEvent
    case 19: self = .exerciseStatus
    default: return nil
    }
  }

  public var rawValue: Int {
    switch self {
    case .filesystemModified: return 0
    case .internalTestEvent: return 1
    case .idling: return 2
    case .batteryStatus: return 3
    case .inactivityAlert: return 4
    case .trainingSessionStatus: return 5
    case .syncRequired: return 7
    case .autosyncStatus: return 8
    case .pnsDhNotificationResponse: return 9
    case .pnsSettings: return 10
    case .startGpsMeasurement: return 11
    case .stopGpsMeasurement: return 12
    case .keepBackgroundAlive: return 13
    case .polarShellDhData: return 14
    case .mediaControlRequestDh: return 15
    case .mediaControlCommandDh: return 16
    case .mediaControlEnabled: return 17
    case .restApiEvent: return 18
    case .exerciseStatus: return 19
    }
  }
}

/// Data class representing a received device-to-host notification.
public struct PolarD2HNotificationData: Equatable {
    /// The type of notification
    public let notificationType: PolarDeviceToHostNotification
    
    /// Raw parameter data as Data
    public let parameters: Data
    
    /// Optional parsed parameter object (if parsing was successful)
    public let parsedParameters: Any?
    
    public init(notificationType: PolarDeviceToHostNotification,
                parameters: Data,
                parsedParameters: Any?) {
        self.notificationType = notificationType
        self.parameters = parameters
        self.parsedParameters = parsedParameters
    }
    
    public static func == (lhs: PolarD2HNotificationData, rhs: PolarD2HNotificationData) -> Bool {
        return lhs.notificationType == rhs.notificationType &&
               lhs.parameters == rhs.parameters
        // Note: parsedParameters comparison is omitted since Any? cannot be compared generically
    }
}

/// Device to host notifications. Device notifications are used to inform client apps of important device state
/// changes, such as negotiating data syncing.
public protocol PolarDeviceToHostNotificationsApi {
    /// Streams for received device to host notifications endlessly.
    /// Only dispose, take(1) etc... stops stream.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_device_control`
    ///
    /// - parameters:
    ///   - identifier: Polar device ID or BT address
    /// - Returns:
    ///     Observable stream of PolarD2HNotificationData
    ///     Produces   onNext after successfully received notification.
    ///             onCompleted not produced unless stream is further configured.
    ///             onError, see `BlePsFtpException, BleGattException`
    ///
    func observeDeviceToHostNotifications(identifier: String) -> Observable<PolarD2HNotificationData>
}
