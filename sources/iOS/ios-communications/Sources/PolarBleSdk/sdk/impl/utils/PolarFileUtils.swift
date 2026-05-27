//  Copyright © 2026 Polar. All rights reserved.

import Foundation

class PolarFileUtils {

    var listener: CBDeviceListenerImpl?
    var serviceClientUtils: PolarServiceClientUtils?
    required init(listener: CBDeviceListenerImpl, serviceClientUtils: PolarServiceClientUtils) {
        self.listener = listener
        self.serviceClientUtils = serviceClientUtils
    }

    func listFiles(identifier: String, folderPath: String = "/", condition: @escaping (_ p: String) -> Bool, recurseDeep: Bool = true) -> AsyncThrowingStream<String, Error> {
        return AsyncThrowingStream { continuation in
            Task {
                do {
                    let session = try self.serviceClientUtils?.sessionFtpClientReady(identifier)
                    guard let client = session?.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                        continuation.finish(throwing: PolarErrors.serviceNotFound)
                        return
                    }
                    var path = folderPath
                    if path.first != "/" { path.insert("/", at: path.startIndex) }
                    if path.last != "/" { path.insert("/", at: path.endIndex) }
                    let entries = try await fetchRecursive(path, client: client, condition: condition, recurseDeep: recurseDeep)
                    for entry in entries { continuation.yield(entry.name) }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: PolarErrors.deviceError(description: "Error in listing files from \(folderPath) path."))
                }
            }
        }
    }

    func checkAutoSampleFile(identifier: String, filePath: String, until: Date) async throws -> Bool {
        let file = try await getFile(identifier: identifier, filePath: filePath)
        let calendar = Calendar.current
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "yyyyMMdd"
        dateFormatter.timeZone = TimeZone(abbreviation: "UTC")
        let fileData = try Data_PbAutomaticSampleSessions(serializedBytes: file as Data)
        let proto = AutomaticSamples.fromProto(proto: fileData)
        let result = calendar.compare(
            dateFromStringWOTime(dateFrom: dateFormatter.string(from: proto.day!)),
            to: dateFromStringWOTime(dateFrom: dateFormatter.string(from: until)),
            toGranularity: .day)
        return result == .orderedSame || result == .orderedAscending
    }

    func deleteDataDirectory(identifier: String, directoryPath: String) async throws {
        do {
            let session = try serviceClientUtils?.sessionFtpClientReady(identifier)
            guard let client = session?.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                throw PolarErrors.serviceNotFound
            }
            do {
                let isEmpty = try await checkIfDirectoryIsEmpty(directoryPath: directoryPath, client: client)
                if isEmpty { _ = try await removeSingleFile(identifier: identifier, filePath: directoryPath) }
            } catch {
                if case let BlePsFtpException.responseError(code) = error, code == 103 {
                    BleLogger.trace("Directory not found: \(directoryPath). Treating as already deleted.")
                    return
                }
                throw error
            }
        } catch {
            BleLogger.error("Error while getting session \(error)")
            throw PolarErrors.serviceNotFound
        }
    }

    func checkIfDirectoryIsEmpty(directoryPath: String, client: BlePsFtpClient) async throws -> Bool {
        var path = directoryPath
        if !path.hasSuffix("/") { path = path + "/" }
        var operation = Protocol_PbPFtpOperation()
        operation.command = .get
        operation.path = path
        let request = try operation.serializedData()
        do {
            let data = try await client.request(request)
            let directory = try Protocol_PbPFtpDirectory(serializedBytes: data as Data)
            return directory.entries.count == 0
        } catch {
            if case let BlePsFtpException.responseError(code) = error, code == 103 { return true }
            BleLogger.error("Failed to get data from directory \(directoryPath). Error: \(error.localizedDescription)")
            throw error
        }
    }

    func removeSingleFile(identifier: String, filePath: String) async throws -> NSData {
        let session = try serviceClientUtils?.sessionFtpClientReady(identifier)
        guard let client = session?.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
            throw PolarErrors.serviceNotFound
        }
        var operation = Protocol_PbPFtpOperation()
        operation.command = .remove
        operation.path = filePath
        let request = try operation.serializedData()
        return try await client.request(request)
    }

    func removeMultipleFiles(identifier: String, filePaths: [String]) async throws {
        let session = try serviceClientUtils?.sessionFtpClientReady(identifier)
        guard let client = session?.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
            throw handleError(PolarErrors.serviceNotFound)
        }
        for filePath in filePaths {
            var operation = Protocol_PbPFtpOperation()
            operation.command = .remove
            operation.path = filePath
            let request = try operation.serializedData()
            do {
                _ = try await client.request(request)
            } catch {
                if case let BlePsFtpException.responseError(code) = error, code == 103 {
                    BleLogger.trace("File not found: \(filePath). Treating as already deleted.")
                    continue
                }
                throw error
            }
        }
    }

    func getFile(identifier: String, filePath: String) async throws -> NSData {
        do {
            let session = try serviceClientUtils?.sessionFtpClientReady(identifier)
            guard let client = session?.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                throw PolarErrors.serviceNotFound
            }
            var operation = Protocol_PbPFtpOperation()
            operation.command = .get
            operation.path = filePath
            let request = try operation.serializedData()
            return try await client.request(request)
        } catch {
            throw PolarErrors.deviceError(description: "Failed to list files from \(filePath) path. Error \(error)")
        }
    }

    private func fetchRecursive(_ path: String, client: BlePsFtpClient, condition: @escaping (_ p: String) -> Bool, recurseDeep: Bool) async throws -> [(name: String, size: UInt64)] {
        var operation = Protocol_PbPFtpOperation()
        operation.command = .get
        operation.path = path
        let request = try operation.serializedData()
        do {
            let data = try await client.request(request)
            let dir = try Protocol_PbPFtpDirectory(serializedBytes: data as Data)
            var results: [(name: String, size: UInt64)] = []
            for entry in dir.entries {
                if condition(entry.name) {
                    let fullPath = path + entry.name
                    if fullPath.hasSuffix("/") && recurseDeep {
                        let subResults = try await fetchRecursive(fullPath, client: client, condition: condition, recurseDeep: recurseDeep)
                        results.append(contentsOf: subResults)
                    } else {
                        results.append((name: fullPath, size: entry.size))
                    }
                }
            }
            return results
        } catch {
            throw handleError(error)
        }
    }

    private func handleError(_ error: Error) -> Error {
        let nsError = error as NSError
        if let mapped = Protocol_PbPFtpError(rawValue: nsError.code) {
            return NSError(domain: nsError.domain, code: nsError.code, userInfo: [NSLocalizedDescriptionKey: "\(mapped) (\(nsError.localizedDescription))"])
        }
        return error
    }

    private func dateFromStringWOTime(dateFrom: String) -> Date {
        let year = Int(String(dateFrom[dateFrom.index(dateFrom.startIndex, offsetBy: 0)..<dateFrom.index(dateFrom.endIndex, offsetBy: -4)]))
        let month = Int(String(dateFrom[dateFrom.index(dateFrom.startIndex, offsetBy: 4)..<dateFrom.index(dateFrom.endIndex, offsetBy: -2)]))
        let day = Int(String(dateFrom[dateFrom.index(dateFrom.startIndex, offsetBy: 6)..<dateFrom.index(dateFrom.endIndex, offsetBy: 0)]))
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(abbreviation: "UTC")!
        var dc = DateComponents()
        dc.year = year; dc.month = month; dc.day = day
        dc.hour = 0; dc.minute = 0; dc.second = 0
        return calendar.date(from: dc)!
    }

    // MARK: - BLE Low Level APIs

    func writeFile(identifier: String, filePath: String, fileData: Data) async throws {
        let session = try serviceClientUtils?.sessionFtpClientReady(identifier)
        guard let client = session?.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
            throw PolarErrors.serviceNotFound
        }
        var builder = Protocol_PbPFtpOperation()
        builder.command = .put
        builder.path = filePath
        let proto = try builder.serializedData()
        let inputStream = InputStream(data: fileData)
        for try await _ in client.write(proto as NSData, data: inputStream) {}
    }

    func readFile(identifier: String, filePath: String) async throws -> Data {
        let data = try await getFile(identifier: identifier, filePath: filePath)
        return data as Data
    }

    func listFiles(identifier: String, directoryPath: String, recurseDeep: Bool = false) async throws -> [String] {
        let condition = { (entry: String) -> Bool in entry.contains(".") || entry == "" }
        var fileList = [String]()
        for try await file in listFiles(identifier: identifier, folderPath: directoryPath, condition: condition, recurseDeep: recurseDeep) {
            fileList.append(file)
        }
        return fileList
    }

    func deleteFile(identifier: String, filePath: String) async throws {
        _ = try await removeSingleFile(identifier: identifier, filePath: filePath)
    }
}
