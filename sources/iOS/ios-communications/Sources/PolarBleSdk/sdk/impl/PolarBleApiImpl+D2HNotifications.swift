import Foundation

extension PolarBleApiImpl: PolarDeviceToHostNotificationsApi {
    func observeDeviceToHostNotifications(identifier: String) -> AsyncThrowingStream<PolarD2HNotificationData, Error> {
        return AsyncThrowingStream { continuation in
            Task {
                do {
                    let session = try self.serviceClientUtils.sessionFtpClientReady(identifier)
                    guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                        continuation.finish(throwing: PolarErrors.serviceNotFound)
                        return
                    }
                    for try await notification in client.waitNotification() {
                        guard let mappedNotification = PolarDeviceToHostNotification(rawValue: Int(notification.id)) else {
                            BleLogger.trace("Unknown notification type: \(notification.id)")
                            continue
                        }
                        let parameters = Data(notification.parameters)
                        let parsedParameters = BlePsFtpClient.parseD2HNotificationParameters(mappedNotification, data: parameters)
                        let data = PolarD2HNotificationData(
                            notificationType: mappedNotification,
                            parameters: parameters,
                            parsedParameters: parsedParameters
                        )
                        BleLogger.trace("Received D2H notification for \(identifier): \(data.notificationType)")
                        continuation.yield(data)
                    }
                    continuation.finish()
                } catch {
                    BleLogger.error("D2H notification error for \(identifier): \(error.localizedDescription)")
                    continuation.finish(throwing: error)
                }
            }
        }
    }
}

extension BlePsFtpClient {
    static func parseD2HNotificationParameters(_ notification: PolarDeviceToHostNotification, data: Data) -> Any? {
        if data.isEmpty { return nil }
        do {
            switch notification {
            case .syncRequired:       return try Protocol_PbPFtpSyncRequiredParams(serializedBytes: data, extensions: nil)
            case .filesystemModified: return try Protocol_PbPFtpFilesystemModifiedParams(serializedBytes: data, extensions: nil)
            case .inactivityAlert:    return try Protocol_PbPFtpInactivityAlert(serializedBytes: data, extensions: nil)
            case .trainingSessionStatus: return try Protocol_PbPFtpTrainingSessionStatus(serializedBytes: data, extensions: nil)
            case .autosyncStatus:     return try Protocol_PbPFtpAutoSyncStatusParams(serializedBytes: data, extensions: nil)
            case .pnsDhNotificationResponse: return try Protocol_PbPftpPnsDHNotificationResponse(serializedBytes: data, extensions: nil)
            case .pnsSettings:        return try Protocol_PbPftpPnsState(serializedBytes: data, extensions: nil)
            case .startGpsMeasurement: return try Protocol_PbPftpStartGPSMeasurement(serializedBytes: data, extensions: nil)
            case .polarShellDhData:   return try Protocol_PbPFtpPolarShellMessageParams(serializedBytes: data, extensions: nil)
            case .mediaControlRequestDh: return try Protocol_PbPftpDHMediaControlRequest(serializedBytes: data, extensions: nil)
            case .mediaControlCommandDh: return try Protocol_PbPftpDHMediaControlCommand(serializedBytes: data, extensions: nil)
            case .mediaControlEnabled: return try Protocol_PbPftpDHMediaControlEnabled(serializedBytes: data, extensions: nil)
            case .restApiEvent:       return try Protocol_PbPftpDHRestApiEvent(serializedBytes: data, extensions: nil)
            case .exerciseStatus:     return try Protocol_PbPftpDHExerciseStatus(serializedBytes: data, extensions: nil)
            default:
                BleLogger.trace("No parameter parsing implemented for: \(notification)")
                return nil
            }
        } catch {
            BleLogger.error("Failed to parse D2H notification parameters for \(notification): \(error.localizedDescription)")
            return nil
        }
    }
}
