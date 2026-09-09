/// Copyright  2026 Polar Electro Oy. All rights reserved.

import Foundation
import CoreBluetooth

/// Implementation of offline recording API methods.
extension PolarBleApiImpl {

    func requestOfflineRecordingSettings(_ identifier: String, feature: PolarDeviceDataType) async throws -> PolarSensorSetting {
        logApiCall("requestOfflineRecordingSettings", ("identifier", identifier), ("feature", feature))
        BleLogger.trace("Request offline stream settings. Feature: \(feature) Device: \(identifier)")
        switch feature {
        case .ecg: return try await querySettings(identifier, type: .ecg, recordingType: .offline)
        case .acc: return try await querySettings(identifier, type: .acc, recordingType: .offline)
        case .ppg: return try await querySettings(identifier, type: .ppg, recordingType: .offline)
        case .magnetometer: return try await querySettings(identifier, type: .mgn, recordingType: .offline)
        case .gyro: return try await querySettings(identifier, type: .gyro, recordingType: .offline)
        case .ppi, .hr: throw PolarErrors.operationNotSupported
        case .temperature: return try await querySettings(identifier, type: .temperature, recordingType: .offline)
        case .skinTemperature: return try await querySettings(identifier, type: .skinTemperature, recordingType: .offline)
        case .pressure: return try await querySettings(identifier, type: .pressure, recordingType: .offline)
        }
    }

    func requestFullOfflineRecordingSettings(_ identifier: String, feature: PolarDeviceDataType) async throws -> PolarSensorSetting {
        logApiCall("requestFullOfflineRecordingSettings", ("identifier", identifier), ("feature", feature))
        BleLogger.trace("Request full offline stream settings. Feature: \(feature) Device: \(identifier)")
        switch feature {
        case .ecg: return try await queryFullSettings(identifier, type: .ecg, recordingType: .offline)
        case .acc: return try await queryFullSettings(identifier, type: .acc, recordingType: .offline)
        case .ppg: return try await queryFullSettings(identifier, type: .ppg, recordingType: .offline)
        case .magnetometer: return try await queryFullSettings(identifier, type: .mgn, recordingType: .offline)
        case .gyro: return try await queryFullSettings(identifier, type: .gyro, recordingType: .offline)
        case .ppi, .hr, .pressure: throw PolarErrors.operationNotSupported
        case .temperature: return try await queryFullSettings(identifier, type: .temperature, recordingType: .offline)
        case .skinTemperature: return try await queryFullSettings(identifier, type: .skinTemperature, recordingType: .offline)
        }
    }

    func getAvailableOfflineRecordingDataTypes(_ identifier: String) async throws -> Set<PolarDeviceDataType> {
        logApiCall("getAvailableOfflineRecordingDataTypes", ("identifier", identifier))
        let session = try serviceClientUtils.sessionPmdClientReady(identifier)
        guard let client = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as? BlePmdClient else { throw PolarErrors.serviceNotFound }
        let pmdFeature = try await client.readFeature(true)
        var deviceData: Set<PolarDeviceDataType> = Set()
        if pmdFeature.contains(.ecg) { deviceData.insert(.ecg) }
        if pmdFeature.contains(.acc) { deviceData.insert(.acc) }
        if pmdFeature.contains(.ppg) { deviceData.insert(.ppg) }
        if pmdFeature.contains(.ppi) { deviceData.insert(.ppi) }
        if pmdFeature.contains(.gyro) { deviceData.insert(.gyro) }
        if pmdFeature.contains(.mgn) { deviceData.insert(.magnetometer) }
        if pmdFeature.contains(.offline_hr) { deviceData.insert(.hr) }
        if pmdFeature.contains(.temperature) { deviceData.insert(.temperature) }
        if pmdFeature.contains(.skinTemperature) { deviceData.insert(.skinTemperature) }
        return deviceData
    }

    func getOfflineRecordingStatus(_ identifier: String) async throws -> [PolarDeviceDataType: Bool] {
        logApiCall("getOfflineRecordingStatus", ("identifier", identifier))
        let session = try serviceClientUtils.sessionPmdClientReady(identifier)
        guard let client = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as? BlePmdClient else { throw PolarErrors.serviceNotFound }
        BleLogger.trace("Get offline recording status. Device: \(identifier)")
        let status = try await client.readMeasurementStatus()
        var activeOfflineRecordings = [PolarDeviceDataType: Bool]()
        for element in status {
            guard element.0 != .derivedMeasurement else { continue }
            let polarFeature = try PolarDataUtils.mapToPolarFeature(from: element.0)
            activeOfflineRecordings[polarFeature] = (element.1 == .offline_measurement_active || element.1 == .online_offline_measurement_active)
        }
        return activeOfflineRecordings
    }

    func listOfflineRecordings(_ identifier: String) -> AsyncThrowingStream<PolarOfflineRecordingEntry, Error> {
        logApiCall("listOfflineRecordings", ("identifier", identifier))
        return AsyncThrowingStream { continuation in
            Task {
                do {
                    let session = try self.serviceClientUtils.sessionFtpClientReady(identifier)
                    guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                        continuation.finish(throwing: PolarErrors.serviceNotFound)
                        return
                    }
                    // Try fast listing (PMDFiles.txt) first, fall back to recursive
                    let pmdData = try await self.deviceSupportsFasterOfflineRecordListing(identifier: identifier)
                    if !pmdData.isEmpty {
                        let entries = try PolarOfflineRecordingUtils.listOfflineRecordingsV2(fileData: Data(pmdData))
                        for entry in entries { continuation.yield(entry) }
                    } else {
                        let entries = try await self.fetchRecursive("/U/0/", client: client, condition: { entry in
                            entry.matches("^([0-9]{8})(\\/)") ||
                            entry.matches("^([0-9]{6})(\\/)") ||
                            entry == "R/" ||
                            entry.contains(".REC")
                        })
                        for entry in entries {
                            let components = entry.name.split(separator: "/")
                            let dateFormatter = DateFormatter()
                            dateFormatter.calendar = .init(identifier: .iso8601)
                            dateFormatter.locale = Locale(identifier: "en_US_POSIX")
                            dateFormatter.dateFormat = "yyyyMMddHHmmss"
                            dateFormatter.timeZone = TimeZone.current
                            guard components.count >= 6,
                                  entry.size > 0,
                                  let date = dateFormatter.date(from: String(components[2] + components[4])) else { continue }
                            guard let pmdMeasurementType = try? OfflineRecordingUtils.mapOfflineRecordingFileNameToMeasurementType(fileName: String(components[5])),
                                  let type = try? PolarDataUtils.mapToPolarFeature(from: pmdMeasurementType) else { continue }
                            continuation.yield(PolarOfflineRecordingEntry(path: entry.name, size: UInt(entry.size), date: date, type: type))
                        }
                    }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
        }
    }

    private func deviceSupportsFasterOfflineRecordListing(identifier: String) async throws -> [UInt8] {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        var operation = Protocol_PbPFtpOperation()
        operation.command = .get
        operation.path = PMDFilePath
        do {
            let request = try operation.serializedData()
            let data = try await client.request(request)
            return data.isEmpty ? [] : [UInt8](data)
        } catch {
            return []
        }
    }

    private func loadFileorEmpty(path: String, client: BlePsFtpClient) async throws -> [UInt8] {
        var operation = Protocol_PbPFtpOperation()
        operation.command = .get
        operation.path = path
        let requestData = try operation.serializedData()
        do {
            let data = try await client.request(requestData)
            return [UInt8](data)
        } catch {
            return []
        }
    }

    func getOfflineRecord(
        _ identifier: String,
        entry: PolarOfflineRecordingEntry,
        secret: PolarRecordingSecret?
    ) async throws -> PolarOfflineRecordingData {
        logApiCall("getOfflineRecord", ("identifier", identifier), ("entry.type", entry.type), ("entry.size", entry.size), ("entry.date", entry.date), ("secret", secret != nil))
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
            throw PolarErrors.serviceNotFound
        }
        guard .polarFileSystemV2 == BlePolarDeviceCapabilitiesUtility.fileSystemType(session.advertisementContent.polarDeviceType) else {
            throw PolarErrors.operationNotSupported
        }

        let count = try await getSubRecordingCount(identifier: identifier, entry: entry)
        let indices = count > 0 ? Array(0..<count) : [0]

        var polarAccData: PolarOfflineRecordingData?
        var polarGyroData: PolarOfflineRecordingData?
        var polarMagData: PolarOfflineRecordingData?
        var polarPpgData: PolarOfflineRecordingData?
        var polarPpiData: PolarOfflineRecordingData?
        var polarHrData: PolarOfflineRecordingData?
        var polarTemperatureData: PolarOfflineRecordingData?
        var polarSkinTemperatureData: PolarOfflineRecordingData?
        var polarEmptyData: PolarOfflineRecordingData?
        var polarDerivedAccData: PolarOfflineRecordingData?
        let lastTimestamp: UInt64 = 0

        for subRecordingIndex in indices {
            let subRecordingPath: String
            if entry.path.range(of: ".*\\.REC$", options: .regularExpression) != nil && count > 0 {
                subRecordingPath = entry.path.replacingOccurrences(of: "\\d(?=\\.REC$)", with: "\(subRecordingIndex)", options: .regularExpression)
            } else {
                subRecordingPath = entry.path
            }
            var operation = Protocol_PbPFtpOperation()
            operation.command = .get
            operation.path = subRecordingPath.isEmpty ? entry.path : subRecordingPath
            let request = try operation.serializedData()
            BleLogger.trace("Offline record get. Device: \(identifier) Path: \(sanitizePathForLog(subRecordingPath)) Secret used: \(secret != nil)")

            let dataResult = try await client.request(request)

            guard !(dataResult as Data).isEmpty else {
                BleLogger.trace("Subrecording at index \(subRecordingIndex) was missing data. Device: \(identifier) Path: \(subRecordingPath)")
                continue
            }

            do {
                let pmdSecret = try secret.map { try PolarDataUtils.mapToPmdSecret(from: $0) }
                let offlineRecordingData = try OfflineRecordingData<Any>.parseDataFromOfflineFile(
                    fileData: dataResult as Data,
                    type: PolarDataUtils.mapToPmdClientMeasurementType(from: entry.type),
                    secret: pmdSecret,
                    lastTimestamp: lastTimestamp,
                    hintDerivedMethods: lastDerivedMethodsCache[identifier]?[entry.groupId]
                )
                let settings: PolarSensorSetting = offlineRecordingData.recordingSettings?.mapToPolarSettings() ?? PolarSensorSetting()
                switch offlineRecordingData.data {
                case let derivedData as DerivedAccData:
                    polarDerivedAccData = processDerivedAccData(derivedData, polarDerivedAccData, offlineRecordingData)
                case let accData as AccData:
                    polarAccData = processAccData(accData, polarAccData, offlineRecordingData, settings)
                case let gyroData as GyrData:
                    polarGyroData = processGyroData(gyroData, polarGyroData, offlineRecordingData, settings)
                case let magData as MagData:
                    polarMagData = processMagData(magData, polarMagData, offlineRecordingData, settings)
                case let ppgData as PpgData:
                    polarPpgData = processPpgData(ppgData, polarPpgData, offlineRecordingData, settings)
                case let ppiData as PpiData:
                    polarPpiData = processPpiData(ppiData, polarPpiData, offlineRecordingData)
                case let hrData as OfflineHrData:
                    polarHrData = processHrData(hrData, polarHrData, offlineRecordingData)
                case let temperatureData as TemperatureData:
                    polarTemperatureData = processTemperatureData(temperatureData, polarTemperatureData, offlineRecordingData)
                case let skinTemperatureData as SkinTemperatureData:
                    polarSkinTemperatureData = processSkinTemperatureData(skinTemperatureData, polarSkinTemperatureData, offlineRecordingData)
                case _ as EmptyData:
                    polarEmptyData = processEmptyData(offlineRecordingData)
                default:
                    throw PolarErrors.polarOfflineRecordingError(description: "GetOfflineRecording failed. Data type is not supported.")
                }
            } catch {
                throw PolarErrors.polarOfflineRecordingError(description: "Failed to parse data: \(error)")
            }
        }

        for dataObject in [polarDerivedAccData, polarAccData, polarGyroData, polarMagData, polarPpgData, polarPpiData, polarHrData, polarTemperatureData, polarSkinTemperatureData, polarEmptyData] {
            if let data = dataObject { return data }
        }
        throw PolarErrors.polarOfflineRecordingError(description: "Invalid data")
    }

    public func getOfflineRecordWithProgress(
        _ identifier: String,
        entry: PolarOfflineRecordingEntry,
        secret: PolarRecordingSecret?
    ) -> AsyncThrowingStream<PolarOfflineRecordingResult, Error> {
        logApiCall("getOfflineRecordWithProgress", ("identifier", identifier), ("entry.type", entry.type), ("entry.size", entry.size), ("entry.date", entry.date), ("secret", secret != nil))
        return AsyncThrowingStream { continuation in
            Task {
                do {
                    let session = try self.serviceClientUtils.sessionFtpClientReady(identifier)
                    guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                        continuation.finish(throwing: PolarErrors.serviceNotFound)
                        return
                    }
                    guard .polarFileSystemV2 == BlePolarDeviceCapabilitiesUtility.fileSystemType(
                        session.advertisementContent.polarDeviceType) else {
                        continuation.finish(throwing: PolarErrors.operationNotSupported)
                        return
                    }

                    let totalBytes = Int64(entry.size)
                    let lock = NSLock()

                    continuation.yield(.progress(PolarOfflineRecordingProgress(
                        bytesDownloaded: 0,
                        totalBytes: totalBytes,
                        progressPercent: 0
                    )))

                    class ProgressCallbackImpl: BlePsFtpProgressCallback {
                        let totalBytes: Int64
                        let continuation: AsyncThrowingStream<PolarOfflineRecordingResult, Error>.Continuation
                        let lock: NSLock
                        var accumulatedBytes: Int64 = 0

                        init(
                            totalBytes: Int64,
                            continuation: AsyncThrowingStream<PolarOfflineRecordingResult, Error>.Continuation,
                            lock: NSLock
                        ) {
                            self.totalBytes = totalBytes
                            self.continuation = continuation
                            self.lock = lock
                        }

                        func onProgressUpdate(bytesReceived: Int) {
                            lock.lock()
                            accumulatedBytes += Int64(bytesReceived)
                            let currentBytes = accumulatedBytes
                            lock.unlock()
                            let percent = totalBytes > 0 ? Int((currentBytes * 100) / totalBytes) : 0
                            let clampedPercent = min(max(percent, 0), 100)
                            continuation.yield(.progress(PolarOfflineRecordingProgress(
                                bytesDownloaded: currentBytes,
                                totalBytes: totalBytes,
                                progressPercent: clampedPercent
                            )))
                        }
                    }

                    let progressCallback = ProgressCallbackImpl(
                        totalBytes: totalBytes,
                        continuation: continuation,
                        lock: lock
                    )
                    client.progressCallback = progressCallback

                    do {
                        let data = try await self.getOfflineRecord(identifier, entry: entry, secret: secret)
                        client.progressCallback = nil
                        continuation.yield(.progress(PolarOfflineRecordingProgress(
                            bytesDownloaded: totalBytes,
                            totalBytes: totalBytes,
                            progressPercent: 100
                        )))
                        continuation.yield(.complete(data))
                        continuation.finish()
                    } catch {
                        client.progressCallback = nil
                        continuation.finish(throwing: error)
                    }
                } catch {
                    continuation.finish(throwing: self.handleError(error))
                }
            }
        }
    }

    func getSubRecordingCount(identifier: String, entry: PolarOfflineRecordingEntry) async throws -> Int {
        logApiCall("getSubRecordingCount", ("identifier", identifier), ("entry.type", entry.type), ("entry.size", entry.size), ("entry.date", entry.date))
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        var operation = Protocol_PbPFtpOperation()
        operation.command = .get
        let directoryPath = entry.path.components(separatedBy: "/").dropLast().joined(separator: "/") + "/"
        let fileType = try mapDeviceDataTypeToOfflineRecordingFileName(type: entry.type)
        operation.path = directoryPath
        do {
            let data = try await client.request(try operation.serializedData())
            let directory = try Protocol_PbPFtpDirectory(serializedBytes: data as Data)
            return directory.entries.filter { $0.name.hasPrefix(fileType) }.count
        } catch {
            if case let BlePsFtpException.responseError(code) = error, code == 103 { return 0 }
            throw handleError(error)
        }
    }

    func getSubRecordings(identifier: String, entry: PolarOfflineRecordingEntry) async throws -> [String] {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        var operation = Protocol_PbPFtpOperation()
        operation.command = .get
        let directoryPath = entry.path.components(separatedBy: "/").dropLast().joined(separator: "/") + "/"
        let type = entry.path.components(separatedBy: "/").last?.replacingOccurrences(of: "[0-9]+.REC", with: "", options: .regularExpression).replacingOccurrences(of: " ", with: "")
        operation.path = directoryPath
        var parentDir = ""
        if let lastSlashIndex = entry.path.dropLast().lastIndex(of: "/") {
            parentDir = String(entry.path[...lastSlashIndex])
        }
        do {
            let data = try await client.request(try operation.serializedData())
            let directory = try Protocol_PbPFtpDirectory(serializedBytes: data as Data)
            return directory.entries.compactMap { e in e.name.contains(type ?? "") ? parentDir + e.name : nil }
        } catch {
            if case let BlePsFtpException.responseError(code) = error, code == 103 { return [entry.path] }
            throw handleError(error)
        }
    }

    func listSplitOfflineRecordings(_ identifier: String) -> AsyncThrowingStream<PolarOfflineRecordingEntry, Error> {
        logApiCall("listSplitOfflineRecordings", ("identifier", identifier))
        return AsyncThrowingStream { continuation in
            Task {
                do {
                    let session = try self.serviceClientUtils.sessionFtpClientReady(identifier)
                    guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                        continuation.finish(throwing: PolarErrors.serviceNotFound)
                        return
                    }
                    guard .polarFileSystemV2 == BlePolarDeviceCapabilitiesUtility.fileSystemType(session.advertisementContent.polarDeviceType) else {
                        continuation.finish(throwing: PolarErrors.operationNotSupported)
                        return
                    }
                    BleLogger.trace("Start offline recording listing in device: \(identifier)")
                    let entries = try await self.fetchRecursive("/U/0/", client: client, condition: { entry in
                        entry.matches("^([0-9]{8})(\\/)") ||
                        entry.matches("^([0-9]{6})(\\/)") ||
                        entry == "R/" ||
                        entry.contains(".REC")
                    })
                    for entry in entries {
                        let components = entry.name.split(separator: "/")
                        let dateFormatter = DateFormatter()
                        dateFormatter.calendar = .init(identifier: .iso8601)
                        dateFormatter.locale = Locale(identifier: "en_US_POSIX")
                        dateFormatter.dateFormat = "yyyyMMddHHmmss"
                        dateFormatter.timeZone = TimeZone(abbreviation: "UTC")
                        guard components.count >= 6,
                              let date = dateFormatter.date(from: String(components[2] + components[4])) else { continue }
                        guard let pmdMeasurementType = try? OfflineRecordingUtils.mapOfflineRecordingFileNameToMeasurementType(fileName: String(components[5])),
                              let type = try? PolarDataUtils.mapToPolarFeature(from: pmdMeasurementType) else { continue }
                        continuation.yield(PolarOfflineRecordingEntry(path: entry.name, size: UInt(entry.size), date: date, type: type))
                    }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
        }
    }

    func getSplitOfflineRecord(_ identifier: String, entry: PolarOfflineRecordingEntry, secret: PolarRecordingSecret?) async throws -> PolarOfflineRecordingData {
        logApiCall("getSplitOfflineRecord", ("identifier", identifier))
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        guard .polarFileSystemV2 == BlePolarDeviceCapabilitiesUtility.fileSystemType(session.advertisementContent.polarDeviceType) else { throw PolarErrors.operationNotSupported }
        var operation = Protocol_PbPFtpOperation()
        operation.command = .get
        operation.path = entry.path
        let request = try operation.serializedData()
        BleLogger.trace("Offline record get. Device: \(identifier) Path: \(sanitizePathForLog(entry.path)) Secret used: \(secret != nil)")
        let data = try await client.request(request)
        var pmdSecret: PmdSecret? = nil
        if let s = secret { pmdSecret = try PolarDataUtils.mapToPmdSecret(from: s) }
        let type: PmdMeasurementType = PolarDataUtils.mapToPmdClientMeasurementType(from: entry.type)
        let offlineRecData = try OfflineRecordingData<Any>.parseDataFromOfflineFile(fileData: data as Data, type: type, secret: pmdSecret)
        let settings = offlineRecData.recordingSettings?.mapToPolarSettings() ?? PolarSensorSetting()
        switch offlineRecData.data {
        case is AccData: return .accOfflineRecordingData((offlineRecData.data as! AccData).mapToPolarData(), startTime: offlineRecData.startTime, settings: settings)
        case is GyrData: return .gyroOfflineRecordingData((offlineRecData.data as! GyrData).mapToPolarData(), startTime: offlineRecData.startTime, settings: settings)
        case is MagData: return .magOfflineRecordingData((offlineRecData.data as! MagData).mapToPolarData(), startTime: offlineRecData.startTime, settings: settings)
        case is PpgData: return .ppgOfflineRecordingData((offlineRecData.data as! PpgData).mapToPolarData(), startTime: offlineRecData.startTime, settings: settings)
        case is PpiData: return .ppiOfflineRecordingData((offlineRecData.data as! PpiData).mapToPolarData(), startTime: offlineRecData.startTime)
        case is OfflineHrData: return .hrOfflineRecordingData((offlineRecData.data as! OfflineHrData).mapToPolarData(), startTime: offlineRecData.startTime)
        case is TemperatureData: return .temperatureOfflineRecordingData((offlineRecData.data as! TemperatureData).mapToPolarData(), startTime: offlineRecData.startTime)
        case is SkinTemperatureData: return .skinTemperatureOfflineRecordingData((offlineRecData.data as! SkinTemperatureData).mapToPolarData(), startTime: offlineRecData.startTime)
        case is DerivedAccData: return processDerivedAccData(offlineRecData.data as! DerivedAccData, nil, offlineRecData)
        case is EmptyData: return .emptyData(startTime: offlineRecData.startTime)
        default: throw PolarErrors.polarOfflineRecordingError(description: "GetOfflineRecording failed. Data type is not supported.")
        }
    }

    func removeOfflineRecord(_ identifier: String, entry: PolarOfflineRecordingEntry) async throws {
        logApiCall("removeOfflineRecord", ("identifier", identifier), ("entry.type", entry.type), ("entry.size", entry.size), ("entry.date", entry.date))
        BleLogger.trace("Remove offline record. Device: \(identifier) Path: \(sanitizePathForLog(entry.path))")
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) is BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        guard .polarFileSystemV2 == BlePolarDeviceCapabilitiesUtility.fileSystemType(session.advertisementContent.polarDeviceType) else { throw PolarErrors.operationNotSupported }
        let subrecords = try await getSubRecordings(identifier: identifier, entry: entry)
        try await fileUtils.removeMultipleFiles(identifier: identifier, filePaths: subrecords)
        let indices = entry.path.findIndices(lookable: "/")
        var indexCount = 1
        var currentDir = String(entry.path[...indices[indices.count - indexCount]])
        while currentDir != "/U/0/" {
            try await fileUtils.deleteDataDirectory(identifier: identifier, directoryPath: currentDir)
            indexCount += 1
            currentDir = String(entry.path[...indices[indices.count - indexCount]])
        }
    }

    func removeOfflineRecords(_ identifier: String, entry: PolarOfflineRecordingEntry) async throws -> Bool {
        logApiCall("removeOfflineRecords", ("identifier", identifier), ("entry.type", entry.type), ("entry.size", entry.size), ("entry.date", entry.date))
        BleLogger.trace("Remove offline record. Device: \(identifier) Path: \(sanitizePathForLog(entry.path))")
        do {
            try await removeOfflineRecord(identifier, entry: entry)
            return true
        } catch {
            return false
        }
    }

    func mapDeviceDataTypeToOfflineRecordingFileName(type: PolarDeviceDataType) throws -> String {
         switch type {
             case .acc: return "ACC"
             case .gyro: return "GYRO"
             case .magnetometer  : return "MAG"
             case .ppg: return "PPG"
             case .ppi: return "PPI"
             case .hr: return "HR"
             case .temperature: return "TEMP"
             case .skinTemperature: return "SKINTEMP"
             default: throw BleGattException.gattDataError(description: "Unknown pmd measurement type: \(type)")
         }
    }

    func startOfflineRecording(_ identifier: String, feature: PolarDeviceDataType, settings: PolarSensorSetting?, secret: PolarRecordingSecret?) async throws {
        logApiCall("startOfflineRecording", ("identifier", identifier), ("feature", feature), ("settings", settings), ("secret", secret != nil))
        let session = try serviceClientUtils.sessionPmdClientReady(identifier)
        guard let client = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as? BlePmdClient else { throw PolarErrors.serviceNotFound }
        var pmdSecret: PmdSecret? = nil
        if let s = secret { pmdSecret = try PolarDataUtils.mapToPmdSecret(from: s) }
        try await client.startMeasurement(
            PolarDataUtils.mapToPmdClientMeasurementType(from: feature),
            settings: (settings ?? PolarSensorSetting()).map2PmdSetting(),
            .offline, pmdSecret)
    }

    func stopOfflineRecording(_ identifier: String, feature: PolarDeviceDataType) async throws {
        logApiCall("stopOfflineRecording", ("identifier", identifier), ("feature", feature))
        let session = try serviceClientUtils.sessionPmdClientReady(identifier)
        guard let client = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as? BlePmdClient else { throw PolarErrors.serviceNotFound }
        BleLogger.trace("Stop offline recording. Feature: \(feature) Device \(identifier)")
        let measurementType = PolarDataUtils.mapToPmdClientMeasurementType(from: feature)
        try await client.stopMeasurement(measurementType)
        // Poll until measurement is no longer active (up to 5 seconds)
        for _ in 0..<10 {
            try await Task.sleep(nanoseconds: 500_000_000)
            let statuses = try await client.readMeasurementStatus()
            if let status = statuses.first(where: { $0.0 == measurementType }) {
                if status.1 == .no_measurement_active { return }
            } else {
                return
            }
        }
    }

    func setOfflineRecordingTrigger(_ identifier: String, trigger: PolarOfflineRecordingTrigger, secret: PolarRecordingSecret?) async throws {
        logApiCall("setOfflineRecordingTrigger", ("identifier", identifier), ("trigger", trigger), ("secret", secret != nil))
        let session = try serviceClientUtils.sessionPmdClientReady(identifier)
        guard let client = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as? BlePmdClient else { throw PolarErrors.serviceNotFound }
        BleLogger.trace("Setup offline recording trigger. Device: \(identifier) Secret used: \(secret != nil)")
        let pmdOfflineTrigger = try PolarDataUtils.mapToPmdOfflineTrigger(from: trigger)
        var pmdSecret: PmdSecret? = nil
        if let s = secret { pmdSecret = try PolarDataUtils.mapToPmdSecret(from: s) }
        try await client.setOfflineRecordingTrigger(offlineRecordingTrigger: pmdOfflineTrigger, secret: pmdSecret)
    }

    func getOfflineRecordingTriggerSetup(_ identifier: String) async throws -> PolarOfflineRecordingTrigger {
        logApiCall("getOfflineRecordingTriggerSetup", ("identifier", identifier))
        let session = try serviceClientUtils.sessionPmdClientReady(identifier)
        guard let client = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as? BlePmdClient else { throw PolarErrors.serviceNotFound }
        BleLogger.trace("Get offline recording trigger setup. Device: \(identifier)")
        let trigger = try await client.getOfflineRecordingTriggerStatus()
        return try PolarDataUtils.mapToPolarOfflineTrigger(from: trigger)
    }


    private func processDerivedAccData(
        _ derivedData: DerivedAccData,
        _ existingData: PolarOfflineRecordingData?,
        _ offlineRecordingData: OfflineRecordingData<Any>
    ) -> PolarOfflineRecordingData {
        let polarSettings = offlineRecordingData.recordingSettings?.mapToPolarSettings()
        let polarDerived = PolarDataUtils.mapPmdClientDerivedAccDataToPolarDerivedAcc(derivedData)
        switch existingData {
        case let .derivedAccOfflineRecordingData(existing, startTime, existingSettings):
            let merged = PolarDerivedAccData(samples: existing.samples + polarDerived.samples)
            return .derivedAccOfflineRecordingData(merged, startTime: startTime, settings: existingSettings)
        default:
            return .derivedAccOfflineRecordingData(
                polarDerived,
                startTime: offlineRecordingData.startTime,
                settings: polarSettings
            )
        }
    }

    private func processAccData(
        _ accData: AccData,
        _ existingData: PolarOfflineRecordingData?,
        _ offlineRecordingData: OfflineRecordingData<Any>,
        _ settings: PolarSensorSetting
    ) -> PolarOfflineRecordingData {
        switch existingData {
        case let .accOfflineRecordingData(existingData, startTime, existingSettings):
            let newSamples = existingData + accData.samples.map { sample in
                (timeStamp: sample.timeStamp, x: sample.x, y: sample.y, z: sample.z)
            }
            return .accOfflineRecordingData(
                newSamples,
                startTime: startTime,
                settings: existingSettings
            )
        default:
            return .accOfflineRecordingData(
                accData.mapToPolarData(),
                startTime: offlineRecordingData.startTime,
                settings: settings
            )
        }
    }

    private func processGyroData(
        _ gyroData: GyrData,
        _ existingData: PolarOfflineRecordingData?,
        _ offlineRecordingData: OfflineRecordingData<Any>,
        _ settings: PolarSensorSetting
    ) -> PolarOfflineRecordingData {
        switch existingData {
        case let .gyroOfflineRecordingData(existingData, startTime, existingSettings):
            let newSamples: PolarGyroData = existingData + gyroData.samples.map { (timeStamp: $0.timeStamp, x: $0.x, y: $0.y, z: $0.z) }
            return .gyroOfflineRecordingData(
                newSamples,
                startTime: startTime,
                settings: existingSettings
            )
        default:
            return .gyroOfflineRecordingData(
                gyroData.mapToPolarData(),
                startTime: offlineRecordingData.startTime,
                settings: settings
            )
        }
    }

    private func processMagData(
        _ magData: MagData,
        _ existingData: PolarOfflineRecordingData?,
        _ offlineRecordingData: OfflineRecordingData<Any>,
        _ settings: PolarSensorSetting
    ) -> PolarOfflineRecordingData {
        switch existingData {
        case let .magOfflineRecordingData(existingData, startTime, existingSettings):
            let newSamples: PolarMagnetometerData = existingData + magData.samples.map { (timeStamp: $0.timeStamp, x: $0.x, y: $0.y, z: $0.z) }
            return .magOfflineRecordingData(
                newSamples,
                startTime: startTime,
                settings: existingSettings
            )
        default:
            return .magOfflineRecordingData(
                magData.mapToPolarData(),
                startTime: offlineRecordingData.startTime,
                settings: settings
            )
        }
    }

    private func processPpgData(
        _ ppgData: PpgData,
        _ existingData: PolarOfflineRecordingData?,
        _ offlineRecordingData: OfflineRecordingData<Any>,
        _ settings: PolarSensorSetting
    ) -> PolarOfflineRecordingData {
        switch existingData {
        case let .ppgOfflineRecordingData(existingData, startTime, existingSettings):
            let newSamples = existingData.samples + ppgData.mapToPolarData().samples
            return .ppgOfflineRecordingData(
                (type: existingData.type, samples: newSamples),
                startTime: startTime,
                settings: existingSettings
            )
        default:
            return .ppgOfflineRecordingData(
                ppgData.mapToPolarData(),
                startTime: offlineRecordingData.startTime,
                settings: settings
            )
        }
    }

    private func processPpiData(
        _ ppiData: PpiData,
        _ existingData: PolarOfflineRecordingData?,
        _ offlineRecordingData: OfflineRecordingData<Any>
    ) -> PolarOfflineRecordingData {
        switch existingData {
        case let .ppiOfflineRecordingData(existingData, startTime):
            let newSamples = existingData.samples + ppiData.samples.map {
                (
                    timestamp: $0.timeStamp,
                    hr: $0.hr,
                    ppInMs: $0.ppInMs,
                    ppErrorEstimate: $0.ppErrorEstimate,
                    blockerBit: $0.blockerBit,
                    skinContactStatus: $0.skinContactStatus,
                    skinContactSupported: $0.skinContactSupported
                )
            }
            return .ppiOfflineRecordingData(
                (timeStamp: UInt64(startTime.timeIntervalSince1970), samples: newSamples),
                startTime: startTime
            )
        default:
            return .ppiOfflineRecordingData(
                (timeStamp: UInt64(offlineRecordingData.startTime.timeIntervalSince1970), samples: ppiData.samples.map {
                    (
                        timeStamp: $0.timeStamp,
                        hr: $0.hr,
                        ppInMs: $0.ppInMs,
                        ppErrorEstimate: $0.ppErrorEstimate,
                        blockerBit: $0.blockerBit,
                        skinContactStatus: $0.skinContactStatus,
                        skinContactSupported: $0.skinContactSupported
                    )
                }),
                startTime: offlineRecordingData.startTime
            )
        }
    }

    private func processHrData(
        _ hrData: OfflineHrData,
        _ existingData: PolarOfflineRecordingData?,
        _ offlineRecordingData: OfflineRecordingData<Any>
    ) -> PolarOfflineRecordingData {
        switch existingData {
        case let .hrOfflineRecordingData(existingData, startTime):
            let newSamples = existingData + hrData.samples.map {
                (
                    hr: $0.hr,
                    ppgQuality: $0.ppgQuality,
                    correctedHr: $0.correctedHr,
                    rrsMs: [],
                    rrAvailable: false,
                    contactStatus: false,
                    contactStatusSupported: false
                )
            }
            return .hrOfflineRecordingData(
                newSamples,
                startTime: startTime
            )
        default:
            return .hrOfflineRecordingData(
                hrData.samples.map {
                    (
                        hr: $0.hr,
                        ppgQuality: $0.ppgQuality,
                        correctedHr: $0.correctedHr,
                        rrsMs: [],
                        rrAvailable: false,
                        contactStatus: false,
                        contactStatusSupported: false
                    )
                },
                startTime: offlineRecordingData.startTime
            )
        }
    }

    private func processTemperatureData(
        _ temperatureData: TemperatureData,
        _ existingData: PolarOfflineRecordingData?,
        _ offlineRecordingData: OfflineRecordingData<Any>
    ) -> PolarOfflineRecordingData {
        switch existingData {
        case let .temperatureOfflineRecordingData(existingData, startTime):
            let newSamples = existingData.samples + temperatureData.samples.map {
                (
                    timeStamp: $0.timeStamp,
                    temperature: $0.temperature
                )
            }
            let updatedData: PolarTemperatureData = (
                timeStamp: newSamples.last?.timeStamp ?? existingData.timeStamp,
                samples: newSamples
            )
            return .temperatureOfflineRecordingData(
                updatedData,
                startTime: startTime
            )
        default:
            return .temperatureOfflineRecordingData(
                temperatureData.mapToPolarData(),
                startTime: offlineRecordingData.startTime
            )
        }
    }
    
    private func processSkinTemperatureData(
        _ skinTemperatureData: SkinTemperatureData,
        _ existingData: PolarOfflineRecordingData?,
        _ offlineRecordingData: OfflineRecordingData<Any>
    ) -> PolarOfflineRecordingData {
        switch existingData {
        case let .skinTemperatureOfflineRecordingData(existingData, startTime):
            let newSamples = existingData.samples + skinTemperatureData.samples.map {
                (
                    timeStamp: $0.timeStamp,
                    temperature: $0.skinTemperature
                )
            }
            let updatedData: PolarTemperatureData = (
                timeStamp: newSamples.last?.timeStamp ?? existingData.timeStamp,
                samples: newSamples
            )
            return .skinTemperatureOfflineRecordingData(
                updatedData,
                startTime: startTime
            )
        default:
            return .skinTemperatureOfflineRecordingData(
                skinTemperatureData.mapToPolarData(),
                startTime: offlineRecordingData.startTime
            )
        }
    }

    private func processEmptyData(
        _ offlineRecordingData: OfflineRecordingData<Any>
    ) -> PolarOfflineRecordingData {
            return .emptyData(startTime:offlineRecordingData.startTime)
    }
}
