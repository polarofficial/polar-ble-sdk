//  Copyright © 2026 Polar. All rights reserved.


import Foundation
import RxSwift

class PolarFileUtils {
    
    var listener: CBDeviceListenerImpl?
    var serviceClientUtils: PolarServiceClientUtils?
    required init(listener: CBDeviceListenerImpl, serviceClientUtils: PolarServiceClientUtils) {
        self.listener = listener
        self.serviceClientUtils = serviceClientUtils
    }
    
    func listFiles(identifier: String, folderPath: String = "/", condition: @escaping (_ p: String) -> Bool, recurseDeep: Bool = true) -> Observable<String> {
        
        do {
            let session = try self.serviceClientUtils?.sessionFtpClientReady(identifier)
            guard let client = session?.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                return Observable.error(PolarErrors.serviceNotFound)
            }
            
            var path = folderPath
            
            if (path.first != "/") {
                path.insert("/", at: path.startIndex)
            }
            if (path.last != "/") {
                path.insert("/", at: path.endIndex)
            }
            
            return fetchRecursive(path, client: client, condition: condition, recurseDeep: recurseDeep)
                .map { (entry) -> String in
                    return (entry.name)
                }
            
        } catch {
            return Observable.error(PolarErrors.deviceError(description: "Error in listing files from \(folderPath) path."))
        }
    }
    
    func checkAutoSampleFile(identifier: String, filePath: String, until: Date) -> Single<Bool> {
        
        var canDelete = false
        return getFile(identifier: identifier, filePath: filePath)
            .map { file -> Bool in
                let calendar = Calendar.current
                let dateFormatter = DateFormatter()
                dateFormatter.dateFormat = "yyyyMMdd"
                dateFormatter.timeZone = TimeZone(abbreviation: "UTC")
                
                let fileData = try Data_PbAutomaticSampleSessions(serializedData: file as Data)
                let proto = AutomaticSamples.fromProto(proto: fileData)
                let dateCompareResult = calendar.compare(self.dateFromStringWOTime(dateFrom: dateFormatter.string(from: proto.day!)), to: self.dateFromStringWOTime(dateFrom: dateFormatter.string(from: until)), toGranularity: .day)
                
                switch dateCompareResult {
                case .orderedSame:
                    canDelete = true
                case .orderedAscending:
                    canDelete = true
                case .orderedDescending:
                    break
                }
                return canDelete
            }.asSingle()
    }
    
    func deleteDataDirectory(identifier: String, directoryPath: String) -> Completable {
        
        do {
            let session = try serviceClientUtils?.sessionFtpClientReady(identifier)
            guard let client = session?.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                return Completable.error(PolarErrors.serviceNotFound)
            }
            
            let dateFormatter = DateFormatter()
            dateFormatter.dateFormat = "yyyyMMdd"
            dateFormatter.timeZone = TimeZone(abbreviation: "UTC")
            
            return checkIfDirectoryIsEmpty(directoryPath: directoryPath, client: client)
                .flatMapCompletable( { isEmpty in
                    if (isEmpty) {
                        return self.removeSingleFile(identifier: identifier, filePath: directoryPath).asCompletable()
                    } else {
                        return Completable.empty()
                    }
                })
                .catch { error in
                    if case let BlePsFtpException.responseError(code) = error, code == 103 {
                        BleLogger.trace("Directory not found: \(directoryPath). Treating as already deleted.")
                        return Completable.empty()
                    }
                    return Completable.error(error)
                }
        } catch {
            BleLogger.error("Error while getting session \(error)")
            return Completable.error(PolarErrors.serviceNotFound)
        }
    }
    
    func checkIfDirectoryIsEmpty(directoryPath: String, client: BlePsFtpClient) -> Single<Bool> {
        
        var path = directoryPath
        if(!path.hasSuffix("/")) {
            path = path + "/"
        }
        
        var operation = Protocol_PbPFtpOperation()
        operation.command = Protocol_PbPFtpOperation.Command.get
        operation.path = path
        var request: Data!
        do {
            request = try operation.serializedData()
        } catch {
            return Single.error(PolarErrors.deviceError(description: "Error in getting files \(directoryPath) path."))
        }
        return client.request(request)
            .flatMap({ data in
                let directory = try Protocol_PbPFtpDirectory(serializedData: data as Data)
                return Single.just(directory.entries.count == 0)
            })
            .catch { error in
                    if case let BlePsFtpException.responseError(code) = error, code == 103 {
                    BleLogger.trace("Directory not found: \(directoryPath). Treating as empty.")
                    return Single.just(true)
                }
                return Single.error(error)
            }
            .do(onError: { error in
                BleLogger.error("Failed to get data from directory \(directoryPath).  Error: \(error.localizedDescription)")
            })
    }
    
    func removeSingleFile(identifier: String, filePath: String) -> Single<NSData> {
        
        do{
            let session = try serviceClientUtils?.sessionFtpClientReady(identifier)
            guard let client = session?.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                return Single.error(PolarErrors.serviceNotFound)
            }
            var operation = Protocol_PbPFtpOperation()
            operation.command = Protocol_PbPFtpOperation.Command.remove
            operation.path = filePath
            let request = try operation.serializedData()
            return client.request(request)
        } catch {
            return Single.error(PolarErrors.deviceError(description: "Failed to remove file \(filePath) path."))
        }
    }
    
    func removeMultipleFiles(identifier: String, filePaths: [String]) -> Completable {
        
        do{
            let session = try serviceClientUtils?.sessionFtpClientReady(identifier)
            guard let client = session?.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                return Completable.error(handleError(PolarErrors.serviceNotFound))
            }
            return Observable.from(filePaths)
                .enumerated()
                .concatMap { filePath in
                    var operation = Protocol_PbPFtpOperation()
                    operation.command = Protocol_PbPFtpOperation.Command.remove
                    operation.path = filePath.element
                    let request = try operation.serializedData()
                    return client.request(request)
                        .asCompletable()
                        .catch { error in
                            if case let BlePsFtpException.responseError(code) = error, code == 103 {
                                BleLogger.trace("File not found: \(filePath.element). Treating as already deleted.")
                                return Completable.empty()
                            }
                            return Completable.error(error)
                        }
                }.asCompletable()
        } catch {
            return Completable.error(PolarErrors.deviceError(description: "Failed to remove files \(filePaths)."))
        }
    }
    
    func getFile(identifier: String, filePath: String) -> Observable<NSData> {
        do {
            let session = try serviceClientUtils?.sessionFtpClientReady(identifier)
            guard let client = session?.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                return Observable.error(PolarErrors.serviceNotFound)
            }
            
            var operation = Protocol_PbPFtpOperation()
            operation.command = Protocol_PbPFtpOperation.Command.get
            operation.path = filePath
            let request = try operation.serializedData()
            return client.request(request).asObservable()
        } catch let err {
            return Observable.error(PolarErrors.deviceError(description: "Failed to list files from \(filePath) path. Error \(err)"))
        }
    }
    
    private func fetchRecursive(_ path: String, client: BlePsFtpClient, condition: @escaping (_ p: String) -> Bool, recurseDeep: Bool) -> Observable<(name: String, size:UInt64)> {
        do {
            var operation = Protocol_PbPFtpOperation()
            operation.command = Protocol_PbPFtpOperation.Command.get
            operation.path = path
            let request = try operation.serializedData()
            
            return client.request(request)
                .asObservable()
                .flatMap { (data) -> Observable<(name: String, size:UInt64)> in
                    do {
                        let dir = try Protocol_PbPFtpDirectory(serializedData: data as Data)
                        let entries = dir.entries
                            .compactMap { (entry) -> (name: String, size:UInt64)? in
                                if condition(entry.name) {
                                    return (name: path + entry.name, size: entry.size)
                                }
                                return nil
                            }
                        if entries.count != 0 {
                            return Observable<(String, UInt64)>.from(entries)
                                .flatMap { (entry) -> Observable<(name: String, size:UInt64)> in
                                    if entry.0.hasSuffix("/") && recurseDeep {
                                        return self.fetchRecursive(entry.0, client: client, condition: condition, recurseDeep: recurseDeep)
                                    } else {
                                        return Observable.just(entry)
                                    }
                                }
                        }
                        return Observable.empty()
                    } catch let err {
                        return Observable.error(PolarErrors.deviceError(description: "\(err)"))
                    }
                }
        } catch let err {
            return Observable.error(handleError(err))
        }
    }
    
    private func handleError(_ error: Error) -> Error {
        let nsError = error as NSError

        if let mapped = Protocol_PbPFtpError(rawValue: nsError.code) {
            return NSError(
                domain: nsError.domain,
                code: nsError.code,
                userInfo: [NSLocalizedDescriptionKey: "\(mapped) (\(nsError.localizedDescription))"]
            )
        }

        return error
    }
    
    private func dateFromStringWOTime(dateFrom: String) -> Date {
        
        let year = Int(String(dateFrom[dateFrom.index(dateFrom.startIndex, offsetBy: 0)..<dateFrom.index(dateFrom.endIndex, offsetBy: -4)]))
        let month = Int(String(dateFrom[dateFrom.index(dateFrom.startIndex , offsetBy: 4)..<dateFrom.index(dateFrom.endIndex, offsetBy: -2)]))
        let day = Int(String(dateFrom[dateFrom.index(dateFrom.startIndex, offsetBy: 6)..<dateFrom.index(dateFrom.endIndex, offsetBy: 0)]))
        
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(abbreviation: "UTC")!
        
        var datecomponents = DateComponents()
        datecomponents.year = year
        datecomponents.month = month
        datecomponents.day = day
        datecomponents.hour = 0
        datecomponents.minute = 0
        datecomponents.second = 0
        
        return calendar.date(from: datecomponents)!
    }
    
    /// BLE Low Level methods. These are experimental methods. Usage is heavily discouraged,
    /// Use SDK APIs from PolarBleApi instead.
    
    /// Low level API method
    func writeFile(identifier: String, filePath: String, fileData: Data) -> Completable {
        return Completable.create { completable in
            do {
                let session = try self.serviceClientUtils?.sessionFtpClientReady(identifier)
                guard let client = session?.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                    completable(.error(PolarErrors.serviceNotFound))
                    return Disposables.create()
                }

                var builder = Protocol_PbPFtpOperation()
                builder.command = Protocol_PbPFtpOperation.Command.put
                builder.path = filePath
                let proto = try builder.serializedData()
                let inputStream = InputStream(data: fileData)

                _ = client.write(proto as NSData, data: inputStream)
                    .subscribe(
                        onError: { error in
                            BleLogger.error("PolarBleLowLevelApi.writeFile", "Error while writing file to path \(filePath) on device \(identifier), error: \(error)")
                            completable(.error(error))
                        }, onCompleted: {
                            BleLogger.trace("PolarBleLowLevelApi.writeFile", "Writing file to path \(filePath) on device \(identifier) completed")
                            completable(.completed)
                        }
                    )
            } catch let error {
                BleLogger.error("PolarBleLowLevelApi.writeFile", "Error while writing file to path \(filePath) on device \(identifier), error: \(error)")
                completable(.error(error))
            }

            return Disposables.create()
        }
    }
    
    /// Low level API method
    func readFile(identifier: String, filePath: String) -> Single<Data> {
        var fileData: Data = Data()
        return Single.create { single in
            return self.getFile(identifier: identifier, filePath: filePath)
                .map { (data) -> Single<Data> in
                    fileData = data as Data
                    return .just(fileData)
                }.subscribe(onError: { error in
                    BleLogger.error("PolarBleLowLevelApi.readFile", "Error while reading file from path \(filePath) on device \(identifier), error: \(error)")
                    single(.failure(error))
                }, onCompleted: {
                    BleLogger.trace("PolarBleLowLevelApi.readFile", "File read from path \(filePath) on device \(identifier) completed")
                    single(.success(fileData))
                })
        }
    }

    /// Low level API method
    func listFiles(identifier: String, directoryPath: String, recurseDeep: Bool = false) -> Single<[String]> {
        var fileList = [] as [String]
        let condition = { (entry) -> Bool in
            entry.contains(".") ||
            entry == ""
        }
        return Single.create { single in
            return self.listFiles(identifier: identifier, folderPath: directoryPath, condition: condition, recurseDeep: recurseDeep).toArray()
                .flatMap { (file) -> Single<[String]> in
                    fileList.append(contentsOf: file)
                    return .just(file)
                }.subscribe(
                    onSuccess: { _ in
                        BleLogger.trace("PolarBleLowLevelApi.listFiles", "File listing from path \(directoryPath) on device \(identifier) completed")
                        single(.success(fileList))
                    },
                    onFailure: { error in
                        BleLogger.error("PolarBleLowLevelApi.listFiles", "Error while listing file from path \(directoryPath) on device \(identifier), error: \(error)")
                        single(.failure(error))
                    }
            )
        }
    }
    
    /// Low level API method
    func deleteFile(identifier: String, filePath: String) -> Completable {
        
        do {
            let session = try serviceClientUtils?.sessionFtpClientReady(identifier)
            guard let client = session?.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                return Completable.error(PolarErrors.serviceNotFound)
            }
            return Completable.create { completable in
                self.removeSingleFile(identifier: identifier, filePath: filePath).subscribe(
                   onSuccess: { _ in
                       BleLogger.trace("PolarBleLowLevelApi.deleteFile", "Deleting file from path \(filePath) on device \(identifier) finished")
                       completable(.completed)
                   },
                   onFailure: { error in
                       BleLogger.error("PolarBleLowLevelApi.deleteFile", "Error while deleting file from path \(filePath) on device \(identifier), error: \(error)")
                       completable(.error(error))
                   })
            }
        } catch {
            BleLogger.error("PolarBleLowLevelApi.deleteFile", "Error while deleting file from path \(filePath) on device \(identifier), error: \(error)")
            return Completable.error(PolarErrors.serviceNotFound)
        }
    }
}
