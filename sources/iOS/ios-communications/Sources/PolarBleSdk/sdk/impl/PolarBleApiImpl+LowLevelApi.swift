//  Copyright © 2026 Polar. All rights reserved.

import Foundation

/// Implementation of PolarBleLowLevelApi.
/// NOTE: Intended for Polar internal use only.
extension PolarBleApiImpl: PolarBleLowLevelApi {

    func readFile(identifier: String, filePath: String) async throws -> Data? {
        logApiCall("readFile", ("identifier", identifier), ("filePath", filePath))
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
            throw PolarErrors.serviceNotFound
        }
        var operation = Protocol_PbPFtpOperation()
        operation.command = .get
        operation.path = filePath
        let request = try operation.serializedData()
        return try await client.request(request) as Data
    }

    func writeFile(identifier: String, filePath: String, fileData: Data) async throws {
        logApiCall("writeFile", ("identifier", identifier), ("filePath", filePath), ("fileDataSize", fileData.count))
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
            throw PolarErrors.serviceNotFound
        }
        var operation = Protocol_PbPFtpOperation()
        operation.command = .put
        operation.path = filePath
        let proto = try operation.serializedData()
        let inputStream = InputStream(data: fileData)
        for try await _ in client.write(proto as NSData, data: inputStream) {}
    }

    func deleteFileOrDirectory(identifier: String, filePath: String) async throws {
        logApiCall("deleteFileOrDirectory", ("identifier", identifier), ("filePath", filePath))
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
            throw PolarErrors.serviceNotFound
        }
        var operation = Protocol_PbPFtpOperation()
        operation.command = .remove
        operation.path = filePath
        let request = try operation.serializedData()
        _ = try await client.request(request)
    }

    func getFileList(identifier: String, directoryPath: String, recurseDeep: Bool) async throws -> [String] {
        logApiCall("getFileList", ("identifier", identifier), ("directoryPath", directoryPath), ("recurseDeep", recurseDeep))
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
            throw PolarErrors.serviceNotFound
        }
        var path = directoryPath
        if path.first != "/" { path.insert("/", at: path.startIndex) }
        if path.last != "/" { path.insert("/", at: path.endIndex) }
        let condition = { (entry: String) -> Bool in entry.contains(".") || entry == "" }
        let entries = try await fetchPftpEntries(path, client: client, condition: condition, recurseDeep: recurseDeep)
        return entries.map { $0.name }
    }

    func createFolder(identifier: String, folderPath: String) async throws {
        logApiCall("createFolder", ("identifier", identifier), ("folderPath", folderPath))
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
            throw PolarErrors.serviceNotFound
        }
        // Ensure the path ends with '/' so the device interprets it as a directory
        let normalizedPath = folderPath.hasSuffix("/") ? folderPath : "\(folderPath)/"
        var operation = Protocol_PbPFtpOperation()
        operation.command = .put
        operation.path = normalizedPath
        let proto = try operation.serializedData()
        // An empty payload signals folder creation to the device
        let emptyStream = InputStream(data: Data())
        for try await _ in client.write(proto as NSData, data: emptyStream) {}
    }
}

// MARK: - Private helpers

private func fetchPftpEntries(
    _ path: String,
    client: BlePsFtpClient,
    condition: @escaping (_ name: String) -> Bool,
    recurseDeep: Bool
) async throws -> [(name: String, size: UInt64)] {
    var operation = Protocol_PbPFtpOperation()
    operation.command = .get
    operation.path = path
    let request = try operation.serializedData()
    let data = try await client.request(request)
    let dir = try Protocol_PbPFtpDirectory(serializedBytes: data as Data)
    var results: [(name: String, size: UInt64)] = []
    for entry in dir.entries {
        if condition(entry.name) {
            let fullPath = path + entry.name
            if fullPath.hasSuffix("/") && recurseDeep {
                let subResults = try await fetchPftpEntries(fullPath, client: client, condition: condition, recurseDeep: recurseDeep)
                results.append(contentsOf: subResults)
            } else {
                results.append((name: fullPath, size: entry.size))
            }
        }
    }
    return results
}



