// Copyright © 2026 Polar Electro Oy. All rights reserved.

import Foundation

/// Implementation of `PolarLoggingApi`.
///
/// Handles fetching of device diagnostic/trace log files and log configuration
/// management via the PFTP file transfer protocol.
extension PolarBleApiImpl: PolarLoggingApi {

    func exportDeviceLogs(_ identifier: String) async throws -> [PolarDeviceLog] {
        logApiCall("exportDeviceLogs", ("identifier", identifier))
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
            throw PolarErrors.serviceNotFound
        }
        guard .polarFileSystemV2 == BlePolarDeviceCapabilitiesUtility.fileSystemType(session.advertisementContent.polarDeviceType) else {
            throw PolarErrors.operationNotSupported
        }

        var results = [PolarDeviceLog]()

        /// Fetches `path` via `fileUtils.tryFetchFile`; appends a `PolarDeviceLog` on success.
        /// Returns `true` if the file was present, `false` if it was missing or any error occurred,
        /// so sequential-index loops (TRC, DBGTRC) can stop at the first gap.
        func tryFetch(_ path: String) async -> Bool {
            guard let data = await fileUtils.tryFetchFile(client: client, path: path) else {
                BleLogger.trace("exportDeviceLogs: \(path) not found on device, skipping")
                return false
            }
            results.append(PolarDeviceLog(path: path, data: data))
            BleLogger.trace("exportDeviceLogs: fetched \(path) from \(identifier)")
            return true
        }

        // Static log files
        for path in ["/ERRORLOG.BPB", "/ERRORLO2.BPB", "/SYSLOG.TXT"] {
            _ = await tryFetch(path)
        }

        // Telemetry trace files: TRC1.BIN, TRC2.BIN, … stop at first missing index
        var index = 1
        while await tryFetch("/TRC\(index).BIN") { index += 1 }

        // Debug trace files: DBGTRC1.BIN, DBGTRC2.BIN, … stop at first missing index
        index = 1
        while await tryFetch("/DBGTRC\(index).BIN") { index += 1 }

        BleLogger.trace("exportDeviceLogs: completed, fetched \(results.count) file(s) from \(identifier)")
        return results
    }

    func getLogConfig(_ identifier: String) async throws -> LogConfig {
        logApiCall("getLogConfig", ("identifier", identifier))
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
            throw PolarErrors.serviceNotFound
        }
        guard .polarFileSystemV2 == BlePolarDeviceCapabilitiesUtility.fileSystemType(session.advertisementContent.polarDeviceType) else {
            throw PolarErrors.operationNotSupported
        }
        var operation = Protocol_PbPFtpOperation()
        operation.command = .get
        operation.path = LOG_CONFIG_PATH
        let request = try operation.serializedData()
        BleLogger.trace("getLogConfig: device=\(identifier) path=\(operation.path)")
        try await client.sendNotification(Protocol_PbPFtpHostToDevNotification.initializeSession.rawValue, parameters: nil)
        let data = try await client.request(request)
        let sensorDataLog: Data_PbSensorDataLog
        let logConfig: LogConfig
        do {
            sensorDataLog = try Data_PbSensorDataLog(serializedBytes: data as Data)
            logConfig = LogConfig.fromProto(proto: sensorDataLog)
        } catch {
            BleLogger.error("getLogConfig: Failed to get LogConfig: \(error)")
            throw error
        }
        try await client.sendNotification(Protocol_PbPFtpHostToDevNotification.terminateSession.rawValue, parameters: nil)
        return logConfig
    }

    func setLogConfig(_ identifier: String, logConfig: LogConfig) async throws {
        logApiCall("setLogConfig", ("identifier", identifier), ("logConfig", logConfig))
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
            throw PolarErrors.serviceNotFound
        }
        let sdLogConfigProto = try LogConfig.toProto(logConfig: logConfig).serializedData()
        var operation = Protocol_PbPFtpOperation()
        operation.command = .put
        operation.path = LOG_CONFIG_PATH
        let proto = try operation.serializedData()
        BleLogger.trace("setLogConfig: device=\(identifier) path=\(operation.path)")
        let inputStream = InputStream(data: Data(sdLogConfigProto))
        for try await _ in client.write(proto as NSData, data: inputStream) {}
    }
}
