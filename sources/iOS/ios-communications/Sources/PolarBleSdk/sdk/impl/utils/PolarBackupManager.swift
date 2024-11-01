//  Copyright © 2024 Polar. All rights reserved.

import Foundation
import RxSwift

private let TAG = "PolarDeviceBackup"
private let ARABICA_SYS_FOLDER = "/SYS/"
private let ARABICA_USER_ROOT_FOLDER = "/U/0/"
private let USER_WILD_CARD_ROOT_FOLDER = "/U/*/"
private let WILD_CARD_CHARACTER = "*"

public class PolarBackupManager {
    
    public struct BackupFileData {
        let data: Data
        let directory: String
        let fileName: String
    }
    
    private struct DeviceFolderEntries {
        let entriesList: [DeviceFolderEntry]
    }
    
    private struct DeviceFolderEntry {
        let name: String
        let size: Int64
        let folderPath: String
    }
    
    let client: BlePsFtpClient
    
    public init(client: BlePsFtpClient) {
        self.client = client
    }
    
    /// Backs up the device.
    ///
    /// - Returns: Single stream
    ///   - success: emitting backup files once after read from the device
    ///   - onError: see logs for more details
   public func backupDevice() -> Single<[BackupFileData]> {
       BleLogger.trace("backupDevice() called")
       let operation = Protocol_PbPFtpOperation.with {
           $0.command = .get
           $0.path = ARABICA_SYS_FOLDER
       }
       let requestData = try! operation.serializedData()

       return client.request(requestData)
           .flatMap { content -> Single<[BackupFileData]> in
               do {
                   let parentDirEntries = try Protocol_PbPFtpDirectory(serializedData: content as Data)

                   let entries: [String] = parentDirEntries.entries.map { entry in
                       ARABICA_SYS_FOLDER + entry.name
                   }

                   if let backupEntry = entries.first(where: { $0.hasSuffix("BACKUP.TXT") }) {
                       BleLogger.trace("Found BACKUP.TXT: \(backupEntry)")

                       return self.loadFile(path: backupEntry)
                           .flatMap { backupData -> Single<[BackupFileData]> in
                               let data = Data(backupData)

                               let stream = InputStream(data: data)
                               var backupDirectories: [String] = []
                               stream.open()
                               defer { stream.close() }

                               let bufferSize = 1024
                               let buffer = UnsafeMutablePointer<UInt8>.allocate(capacity: bufferSize)
                               defer { buffer.deallocate() }

                               var accumulatedString = ""

                               while stream.hasBytesAvailable {
                                   let bytesRead = stream.read(buffer, maxLength: bufferSize)
                                   if bytesRead > 0, let chunk = String(bytesNoCopy: buffer, length: bytesRead, encoding: .utf8, freeWhenDone: false) {
                                       accumulatedString += chunk

                                       while let range = accumulatedString.range(of: "\n") {
                                           let line = String(accumulatedString[..<range.lowerBound]).trimmingCharacters(in: .whitespacesAndNewlines)
                                           backupDirectories.append(line)
                                           BleLogger.trace("Read line from BACKUP.TXT: \(line)")
                                           accumulatedString.removeSubrange(..<range.upperBound)
                                       }
                                   }
                               }

                               BleLogger.trace("Backup directories found: \(backupDirectories)")
                               let backupDataSingles = backupDirectories.map { self.backupDirectory(backupDirectory: $0) }
                               return Single.zip(backupDataSingles)
                                   .map { $0.flatMap { $0 } }
                           }
                   } else {
                       BleLogger.error("No BACKUP.TXT found in entries: \(entries)")
                       return .just([])
                   }
               } catch {
                   BleLogger.error("Failed to parse /SYS/ directory: \(error)")
                   return .error(error)
               }
           }
           .catch { error in
               BleLogger.error("Failed to get backup content, error: \(error)")
               return .just([])
           }
   }

    /// Restores backup to the device.
    ///
    /// - Parameters:
    ///   - backupFiles: backup files to restore to the device
    /// - Returns: Completable stream
    ///   - success: once after backup restored
    ///   - onError: see logs for more details
    public func restoreBackup(backupFiles: [BackupFileData]) -> Completable {
        return Completable.create { completable in
            BleLogger.trace("Starting backup restoration process for \(backupFiles.count) files")
            let subscription = Observable.from(backupFiles)
                .flatMap { backupFileData in
                    BleLogger.trace("Restoring backup file: \(backupFileData.fileName) from directory: \(backupFileData.directory)")
                    do {
                        var operation = Protocol_PbPFtpOperation()
                        operation.command = Protocol_PbPFtpOperation.Command.put
                        operation.path = backupFileData.directory + backupFileData.fileName
                        let header = try operation.serializedData() as NSData
                        BleLogger.trace("Serialized operation for file: \(backupFileData.fileName), path: \(operation.path)")
                        
                        let dataStream = InputStream(data: backupFileData.data)
                        
                        return self.client.write(header, data: dataStream)
                            .asObservable()
                            .ignoreElements()
                            .do(onSubscribe: {
                                BleLogger.trace("Restoring file: \(backupFileData.fileName)")
                            })
                            .catch { error in
                                BleLogger.error("Failed to restore backup file: \(backupFileData.fileName), error: \(error)")
                                completable(.error(error))
                                return Observable.empty()
                            }
                    } catch {
                        BleLogger.error("Failed to serialize operation for file: \(backupFileData.fileName), error: \(error)")
                        completable(.error(error))
                        return Observable.empty()
                    }
                }
                .subscribe(
                    onError: { error in
                        BleLogger.error("Error occurred during backup restoration: \(error)")
                        completable(.error(error))
                    },
                    onCompleted: {
                        BleLogger.trace("Successfully completed backup restoration")
                        completable(.completed)
                    }
                )
            
            return Disposables.create {
                subscription.dispose()
            }
        }
    }

    
    private func loadFile(path: String) -> Single<[UInt8]> {
        return Single<[UInt8]>.create { single in
            do {
                var operation = Protocol_PbPFtpOperation()
                operation.command =  Protocol_PbPFtpOperation.Command.get
                operation.path = path
                let requestData = try operation.serializedData()
                
                let disposable = self.client.request(requestData)
                    .subscribe(
                        onSuccess: { responseData in
                            let bytes = [UInt8](responseData)
                            single(.success(bytes))
                        },
                        onFailure: { error in
                            single(.failure(error))
                        }
                    )
                return Disposables.create {
                    disposable.dispose()
                }
            } catch {
                single(.failure(error))
                return Disposables.create()
            }
        }
    }
    
    private func backupDirectory(backupDirectory: String) -> Single<[BackupFileData]> {
        let directoryPaths = backupDirectory.components(separatedBy: .newlines)
        
        let backupDataSingles = directoryPaths.compactMap { directoryPath -> Single<[BackupFileData]>? in
            guard !directoryPath.isEmpty else {
                return Single<[BackupFileData]>.just([])
            }
            
            let path = directoryPath.replacingOccurrences(of: USER_WILD_CARD_ROOT_FOLDER, with: ARABICA_USER_ROOT_FOLDER)
            switch true {
            case path.contains(WILD_CARD_CHARACTER):
                let rootPath = path.components(separatedBy: WILD_CARD_CHARACTER).first ?? ""
                let entries = self.loadEntries(path: rootPath)
                var backupDirectories: [String] = []
                
                for entry in entries {
                    if entry.folderPath.isFolder {
                        backupDirectories.append(rootPath + entry.name)
                    }
                }
                
                return Observable.from(backupDirectories)
                    .flatMap { directory in
                        return Observable.just(self.backUpDirectories([directory]))
                    }
                    .toArray()
                    .map { $0.flatMap { $0 } }
                
            default:
                if path.isFolder {
                    let backupFiles = backUpDirectories(loadSubDirectories(path: path))
                    return Single.just(backupFiles)
                } else {
                    return loadFile(path: path)
                        .map { data in
                            let file = (path as NSString).lastPathComponent
                            let filePath = (path as NSString).deletingLastPathComponent + "/"
                            return [BackupFileData(data: Data(data), directory: filePath, fileName: file)]
                        }
                        .catch { error in
                            BleLogger.error("Error loading file: \(path), error: \(error)")
                            return .just([])
                        }
                }
            }
        }
        
        return Single.zip(backupDataSingles)
            .map { $0.flatMap { $0 } }
    }

    private func loadEntries(path: String) -> [DeviceFolderEntry] {
        return fetchRecursively(path: path)
    }

    private func fetchRecursively(path: String) -> [DeviceFolderEntry] {
        var operation = Protocol_PbPFtpOperation()
        operation.command = Protocol_PbPFtpOperation.Command.get
        operation.path = path
        let request = try! operation.serializedData()
        
        var entries = [DeviceFolderEntry]()
        
        _ = client.request(request)
            .subscribe(
                onSuccess: { data in
                    do {
                        let dir = try Protocol_PbPFtpDirectory(serializedData: data as Data)
                        
                        for entry in dir.entries {
                            let deviceFolderEntry = DeviceFolderEntry(name: (entry.name as NSString).lastPathComponent, size: Int64(entry.size), folderPath: entry.name)
                            entries.append(deviceFolderEntry)
                            if entry.name.hasSuffix("/") {
                                entries.append(contentsOf: self.fetchRecursively(path: entry.name))
                            }
                        }
                    } catch {
                        BleLogger.error("fetchRecursively() error: \(error)")
                    }
                },
                onFailure: { error in
                    BleLogger.error("fetchRecursively() onFailure: \(error)")
                }
            )
        return entries
    }

    private func loadSubDirectories(path: String) -> [String] {
        return loadEntries(path: path).map { entry in
            path + entry.name
        }
    }
    
    private func backUpDirectories(_ folders: [String]) -> [BackupFileData] {
        let singles = folders.map { path in
            backupDirectory(backupDirectory: path)
                .catchAndReturn([])
                .asObservable()
        }
        

        let observable = Observable.merge(singles)

        var backupData: [BackupFileData] = []
        _ = observable.subscribe(onNext: { data in
            backupData.append(contentsOf: data)
        })

        return backupData
    }
}

extension String {
    var isFolder: Bool {
        return hasSuffix("/")
    }
}
