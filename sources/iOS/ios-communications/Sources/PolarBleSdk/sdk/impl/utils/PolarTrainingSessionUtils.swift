//  Copyright © 2025 Polar. All rights reserved.

import Foundation
import RxSwift
import zlib

private let ARABICA_USER_ROOT_FOLDER = "/U/0/"

internal class PolarTrainingSessionUtils {
    
    private static let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyyMMdd"
        formatter.locale = Locale(identifier: "en_US_POSIX")
        return formatter
    }()
    
    private static let dateTimeFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyyMMddHHmmss"
        formatter.locale = Locale(identifier: "en_US_POSIX")
        return formatter
    }()
    
    static func getTrainingSessionReferences(
        client: BlePsFtpClient,
        fromDate: Date? = nil,
        toDate: Date? = nil
    ) -> Observable<PolarTrainingSessionReference> {
        BleLogger.trace("getTrainingSessions: fromDate=\(String(describing: fromDate)), toDate=\(String(describing: toDate))")

        var updatedReferences: [PolarTrainingSessionReference] = []
        var trainingSessionSummaryPaths: Set<String> = []

        return Observable.create { observer in
            let fetchRecursiveObservable = fetchRecursive(ARABICA_USER_ROOT_FOLDER, client: client) { name in
                return name.range(of: #"^(\d{8}/|\d{6}/|.*\.BPB|.*\.GZB|E/|\d+/)$"#, options: .regularExpression) != nil
            }
            
            fetchRecursiveObservable
                .subscribe(onNext: { (path, size) in
                    let fileName = (path as NSString).lastPathComponent

                    if let dataType = PolarTrainingSessionDataTypes(rawValue: fileName) {
                        let regex = try! NSRegularExpression(pattern: "/U/0/(\\d{8})/E/(\\d{6})/TSESS.BPB$")
                        if let match = regex.firstMatch(in: path, range: NSRange(path.startIndex..., in: path)) {
                            let dateStr = String(path[Range(match.range(at: 1), in: path)!])
                            let timeStr = String(path[Range(match.range(at: 2), in: path)!])
                            let dateTimeStr = dateStr + timeStr
                            let dateTime = dateTimeFormatter.date(from: dateTimeStr) ?? Date()
                            let dateAtPath = Calendar.current.dateComponents([.year, .month, .day], from: dateFormatter.date(from: dateStr) ?? Date())

                            var dateMatches = false

                            if (fromDate != nil && toDate != nil) {
                                if (dateAtPath >= Calendar.current.dateComponents([.year, .month, .day], from: fromDate ?? Date())
                                    && dateAtPath <= Calendar.current.dateComponents([.year, .month, .day], from: toDate ?? Date())) {
                                    dateMatches = true
                                }
                            } else if (fromDate == nil || toDate == nil) {
                                dateMatches = true
                            }
                            if (dateMatches) {
                                    trainingSessionSummaryPaths.insert(path)
                                    if let index = updatedReferences.firstIndex(where: { $0.path.contains(dateStr) && $0.path.contains(timeStr) }) {
                                        if !updatedReferences[index].trainingDataTypes.contains(dataType) {
                                            updatedReferences[index].trainingDataTypes.append(dataType)
                                            updatedReferences[index].fileSize += Int64(size)
                                            BleLogger.trace("Added \(size) bytes to existing session, new total: \(updatedReferences[index].fileSize)")
                                        }
                                    } else {
                                        updatedReferences.append(
                                            PolarTrainingSessionReference(
                                                date: dateTime,
                                                path: path,
                                                trainingDataTypes: [dataType],
                                                exercises: [],
                                                fileSize: Int64(size)
                                            )
                                        )
                                    }
                                    BleLogger.trace("Matched TSESS.BPB at \(path)")
                                }
                        }
                    } else if let exerciseDataType = PolarExerciseDataTypes(rawValue: fileName) {

                        let regex = try! NSRegularExpression(pattern: "/U/0/(\\d{8})/E/(\\d{6})/(\\d{2})/\(exerciseDataType.rawValue)$")
                        if let match = regex.firstMatch(in: path, range: NSRange(path.startIndex..., in: path)) {
                            let dateStr = String(path[Range(match.range(at: 1), in: path)!])
                            let timeStr = String(path[Range(match.range(at: 2), in: path)!])
                            let exerciseFolder = String(path[Range(match.range(at: 3), in: path)!])
                            let fullExercisePath = "/U/0/\(dateStr)/E/\(timeStr)/\(exerciseFolder)"
                            let summaryPrefix = "/U/0/\(dateStr)/E/\(timeStr)"
                            let possibleSummaries = trainingSessionSummaryPaths.filter { $0.hasPrefix(summaryPrefix) }

                            if let tseSsPath = possibleSummaries.first,
                               let index = updatedReferences.firstIndex(where: { $0.path == tseSsPath }) {

                                updatedReferences[index].fileSize += Int64(size)
                                BleLogger.trace("Added exercise file \(exerciseDataType.rawValue) size: \(size) bytes, new total: \(updatedReferences[index].fileSize)")

                                let exercises = updatedReferences[index].exercises

                                if let existingIndex = exercises.firstIndex(where: { $0.path == fullExercisePath }) {
                                    var existing = exercises[existingIndex]
                                    if !existing.exerciseDataTypes.contains(exerciseDataType) {
                                        existing.exerciseDataTypes.append(exerciseDataType)
                                        updatedReferences[index].exercises[existingIndex] = existing
                                        BleLogger.trace("Appended \(exerciseDataType.rawValue) to exercise \(fullExercisePath)")
                                    }
                                } else {
                                    let newExercise = PolarExercise(index: 0, path: fullExercisePath, exerciseDataTypes: [exerciseDataType])
                                    updatedReferences[index].exercises.append(newExercise)
                                    BleLogger.trace("Added new exercise \(fullExercisePath) with data type \(exerciseDataType.rawValue)")
                                }
                            } else {
                                BleLogger.trace("No matching TSESS.BPB found for exercise file at \(path)")
                            }
                        } else {
                            BleLogger.trace("Regex did not match exercise path: \(path)")
                        }
                    }
                }, onError: { error in
                    BleLogger.error("Error while fetching session references: \(error)")
                    observer.onError(error)
                }, onCompleted: {
                    for reference in updatedReferences {
                        observer.onNext(reference)
                    }
                    observer.onCompleted()
                })

            return Disposables.create()
        }
    }

    static func readTrainingSession(client: BlePsFtpClient, reference: PolarTrainingSessionReference) -> Single<PolarTrainingSession> {
        BleLogger.trace("readTrainingSession: Starting to read session from path: \(reference.path)")

        return Single<PolarTrainingSession>.create { emitter in
            do {
                var tsessOp = Protocol_PbPFtpOperation()
                tsessOp.command = .get
                tsessOp.path = reference.path

                let tsessRequest = try tsessOp.serializedData()

                let tsessDisposable = client.request(tsessRequest)
                    .flatMap { response -> Single<PolarTrainingSession> in
                        do {
                            let sessionSummary = try Data_PbTrainingSession(serializedData: response as Data)

                            let exerciseSingles: [Single<PolarExercise>] = reference.exercises.map { exercise in
                                let basePath = exercise.path
                                let dataTypeRequests: [Single<(PolarExerciseDataTypes, Data)>] = exercise.exerciseDataTypes.map { dataType in
                                    let filePath = "\(basePath)/\(dataType.rawValue)"
                                    BleLogger.trace("readTrainingSession: Preparing to fetch file: \(filePath)")

                                    return Single<(PolarExerciseDataTypes, Data)>.create { single in
                                        do {
                                            var operation = Protocol_PbPFtpOperation()
                                            operation.command = .get
                                            operation.path = filePath

                                            let request = try operation.serializedData()

                                            let disposable = client.request(request).subscribe(
                                                onSuccess: { response in
                                                    do {
                                                        let data: Data
                                                        if filePath.hasSuffix(".GZB") {
                                                            data = try unzipGzip(response as Data)
                                                            BleLogger.trace("readTrainingSession: Unzipped \(filePath), size: \(data.count) bytes")
                                                        } else {
                                                            data = response as Data
                                                        }
                                                        single(.success((dataType, data)))
                                                    } catch {
                                                        BleLogger.error("readTrainingSession: Failed to parse or unzip \(filePath): \(error)")
                                                        single(.failure(error))
                                                    }
                                                },
                                                onFailure: { error in
                                                    BleLogger.error("readTrainingSession: Failed to fetch \(filePath): \(error)")
                                                    single(.failure(error))
                                                }
                                            )

                                            return Disposables.create { disposable.dispose() }

                                        } catch {
                                            BleLogger.error("readTrainingSession: Serialization error for \(filePath): \(error)")
                                            single(.failure(error))
                                            return Disposables.create()
                                        }
                                    }
                                }

                                return Single.zip(dataTypeRequests)
                                    .map { results in
                                        var summary: Data_PbExerciseBase?
                                        var route: Data_PbExerciseRouteSamples?
                                        var route2: Data_PbExerciseRouteSamples2?
                                        var samples: Data_PbExerciseSamples?
                                        var samples2: Data_PbExerciseSamples2?

                                        for (type, data) in results {
                                            switch type {
                                            case .exerciseSummary:
                                                summary = try? Data_PbExerciseBase(serializedData: data)
                                            case .route, .routeGzip:
                                                route = try? Data_PbExerciseRouteSamples(serializedData: data)
                                            case .routeAdvancedFormat, .routeAdvancedFormatGzip:
                                                route2 = try? Data_PbExerciseRouteSamples2(serializedData: data)
                                            case .samples, .samplesGzip:
                                                samples = try? Data_PbExerciseSamples(serializedData: data)
                                            case .samplesAdvancedFormatGzip:
                                                samples2 = try? Data_PbExerciseSamples2(serializedData: data)
                                            }
                                        }

                                        return PolarExercise(
                                            index: exercise.index,
                                            path: basePath,
                                            exerciseDataTypes: exercise.exerciseDataTypes,
                                            exerciseSummary: summary,
                                            route: route,
                                            routeAdvanced: route2,
                                            samples: samples,
                                            samplesAdvanced: samples2
                                        )
                                    }
                            }

                            return Single.zip(exerciseSingles).map { exercises in
                                PolarTrainingSession(
                                    reference: reference,
                                    sessionSummary: sessionSummary,
                                    exercises: exercises
                                )
                            }

                        } catch {
                            BleLogger.error("readTrainingSession: Failed to parse TSESS.BPB: \(error)")
                            return Single.error(error)
                        }
                    }
                    .subscribe(
                        onSuccess: { session in
                            emitter(.success(session))
                        },
                        onFailure: { error in
                            emitter(.failure(error))
                        }
                    )

                return Disposables.create { tsessDisposable.dispose() }

            } catch {
                BleLogger.error("readTrainingSession: Failed to serialize request for \(reference.path): \(error)")
                emitter(.failure(error))
                return Disposables.create()
            }
        }
    }
    
    static func readTrainingSessionWithProgress(
        client: BlePsFtpClient,
        reference: PolarTrainingSessionReference,
        progressHandler: @escaping (PolarTrainingSessionProgress) -> Void
    ) -> Single<PolarTrainingSession> {
        
        return Single<PolarTrainingSession>.create { emitter in
            class ProgressCallbackImpl: BlePsFtpProgressCallback {
                let totalBytes: Int64
                var accumulatedBytes: Int64 = 0
                let progressHandler: (PolarTrainingSessionProgress) -> Void
                var updateCount: Int = 0
                let lock = NSLock()
                
                init(totalBytes: Int64, progressHandler: @escaping (PolarTrainingSessionProgress) -> Void) {
                    self.totalBytes = totalBytes
                    self.progressHandler = progressHandler
                }
                
                func onProgressUpdate(bytesReceived: Int) {
                    lock.lock()
                    accumulatedBytes += Int64(bytesReceived)
                    updateCount += 1
                    let currentBytes = accumulatedBytes
                    let currentCount = updateCount
                    lock.unlock()
                    
                    let percent = totalBytes > 0 ? Int((currentBytes * 100) / totalBytes) : 0
                    let progress = PolarTrainingSessionProgress(
                        totalBytes: totalBytes,
                        completedBytes: currentBytes,
                        progressPercent: min(percent, 100),
                        currentFileName: nil
                    )
                    progressHandler(progress)
                }
            }
            
            let totalBytes = reference.fileSize
            let progressCallback = ProgressCallbackImpl(totalBytes: totalBytes, progressHandler: progressHandler)
            client.progressCallback = progressCallback

            progressHandler(PolarTrainingSessionProgress(
                totalBytes: totalBytes,
                completedBytes: 0,
                progressPercent: 0,
                currentFileName: nil
            ))
            
            do {
                var tsessOp = Protocol_PbPFtpOperation()
                tsessOp.command = .get
                tsessOp.path = reference.path
                let tsessRequest = try tsessOp.serializedData()
                
                BleLogger.trace("Fetching session summary with progress callback")

                let tsessDisposable = client.request(tsessRequest)
                    .flatMap { response -> Single<PolarTrainingSession> in
                        BleLogger.trace("Session summary received, processing \(reference.exercises.count) exercises")
                        
                        do {
                            let sessionSummary = try Data_PbTrainingSession(serializedData: response as Data)

                            let exerciseSingles: [Single<PolarExercise>] = reference.exercises.enumerated().map { (exerciseIndex, exercise) in
                                BleLogger.trace("Processing exercise \(exerciseIndex): \(exercise.path)")
                                
                                let basePath = exercise.path
                                let dataTypeRequests: [Single<(PolarExerciseDataTypes, Data)>] = exercise.exerciseDataTypes.map { dataType in
                                    let filePath = "\(basePath)/\(dataType.rawValue)"
                                    BleLogger.trace("Fetching \(filePath)")

                                    return Single<(PolarExerciseDataTypes, Data)>.create { single in
                                        do {
                                            var operation = Protocol_PbPFtpOperation()
                                            operation.command = .get
                                            operation.path = filePath
                                            let request = try operation.serializedData()

                                            let disposable = client.request(request).subscribe(
                                                onSuccess: { response in
                                                    BleLogger.trace("\(dataType.rawValue) received: \(response.count) bytes")
                                                    
                                                    do {
                                                        let data: Data
                                                        if filePath.hasSuffix(".GZB") {
                                                            data = try unzipGzip(response as Data)
                                                            BleLogger.trace("Unzipped to \(data.count) bytes")
                                                        } else {
                                                            data = response as Data
                                                        }
                                                        single(.success((dataType, data)))
                                                    } catch {
                                                        BleLogger.error("Failed to unzip: \(error)")
                                                        single(.failure(error))
                                                    }
                                                },
                                                onFailure: { error in
                                                    BleLogger.error("Failed to fetch: \(error)")
                                                    single(.failure(error))
                                                }
                                            )

                                            return Disposables.create { disposable.dispose() }

                                        } catch {
                                            BleLogger.error("Serialization error: \(error)")
                                            single(.failure(error))
                                            return Disposables.create()
                                        }
                                    }
                                }

                                return Single.zip(dataTypeRequests)
                                    .map { results in
                                        var summary: Data_PbExerciseBase?
                                        var route: Data_PbExerciseRouteSamples?
                                        var route2: Data_PbExerciseRouteSamples2?
                                        var samples: Data_PbExerciseSamples?
                                        var samples2: Data_PbExerciseSamples2?

                                        for (type, data) in results {
                                            switch type {
                                            case .exerciseSummary:
                                                summary = try? Data_PbExerciseBase(serializedData: data)
                                            case .route, .routeGzip:
                                                route = try? Data_PbExerciseRouteSamples(serializedData: data)
                                            case .routeAdvancedFormat, .routeAdvancedFormatGzip:
                                                route2 = try? Data_PbExerciseRouteSamples2(serializedData: data)
                                            case .samples, .samplesGzip:
                                                samples = try? Data_PbExerciseSamples(serializedData: data)
                                            case .samplesAdvancedFormatGzip:
                                                samples2 = try? Data_PbExerciseSamples2(serializedData: data)
                                            }
                                        }
                                        
                                        return PolarExercise(
                                            index: exercise.index,
                                            path: basePath,
                                            exerciseDataTypes: exercise.exerciseDataTypes,
                                            exerciseSummary: summary,
                                            route: route,
                                            routeAdvanced: route2,
                                            samples: samples,
                                            samplesAdvanced: samples2
                                        )
                                    }
                            }

                            if exerciseSingles.isEmpty {
                                return Single.just(PolarTrainingSession(
                                    reference: reference,
                                    sessionSummary: sessionSummary,
                                    exercises: []
                                ))
                            } else {
                                return Single.zip(exerciseSingles).map { exercises in
                                    BleLogger.trace("All exercises combined: \(exercises.count)")
                                    return PolarTrainingSession(
                                        reference: reference,
                                        sessionSummary: sessionSummary,
                                        exercises: exercises
                                    )
                                }
                            }

                        } catch {
                            BleLogger.error("Failed to parse session summary: \(error)")
                            return Single.error(error)
                        }
                    }
                    .subscribe(
                        onSuccess: { session in
                            progressHandler(PolarTrainingSessionProgress(
                                totalBytes: totalBytes,
                                completedBytes: totalBytes,
                                progressPercent: 100,
                                currentFileName: nil
                            ))
                            
                            emitter(.success(session))
                        },
                        onFailure: { error in
                            BleLogger.error("Training session load failed: \(error)")
                            emitter(.failure(error))
                        }
                    )

                return Disposables.create {
                    BleLogger.trace("Disposing training session request")
                    client.progressCallback = nil
                    tsessDisposable.dispose()
                }

            } catch {
                BleLogger.error("Failed to serialize request: \(error)")
                client.progressCallback = nil
                emitter(.failure(error))
                return Disposables.create()
            }
        }
    }
    
    static func deleteTrainingSession(client: BlePsFtpClient, reference: PolarTrainingSessionReference) -> Completable {
        do {
            let components = reference.path.split(separator: "/")
            var operation = Protocol_PbPFtpOperation()
            operation.command = .get
            operation.path = "/U/0/" + components[2] + "/E/"
            let request = try operation.serializedData()
        
            return client.request(request)
                .flatMap { content -> Single<NSData> in
                    do {
                        let dir = try Protocol_PbPFtpDirectory(serializedData: content as Data)
                        var removeOperation = Protocol_PbPFtpOperation()
                        removeOperation.command = .remove
                        if dir.entries.count <= 1 {
                            removeOperation.path = "/U/0/" + components[2] + "/E/"
                        } else {
                            removeOperation.path = "/U/0/" + components[2] + "/E/" + components[4] + "/"
                        }
                        let request = try removeOperation.serializedData()
                        
                        return client.request(request)
                    } catch {
                        return Single.error(PolarErrors.messageDecodeFailed)
                    }
                }.asCompletable()
        } catch let err {
            return Completable.error(err)
        }
    }
    
    private static func unzipGzip(_ data: Data) throws -> Data {
        var stream = z_stream()
        var status: Int32 = Z_OK

        let bufferSize = 16384
        var output = Data(capacity: data.count * 2)

        data.withUnsafeBytes { (srcPointer: UnsafeRawBufferPointer) in
            stream.next_in = UnsafeMutablePointer<Bytef>(mutating: srcPointer.bindMemory(to: Bytef.self).baseAddress!)
            stream.avail_in = uInt(data.count)

            status = inflateInit2_(&stream, 15 + 16, ZLIB_VERSION, Int32(MemoryLayout<z_stream>.size))
        }

        guard status == Z_OK else {
            throw NSError(domain: "DecompressionError", code: Int(status), userInfo: [NSLocalizedDescriptionKey: "Failed to init zlib stream"])
        }

        defer {
            inflateEnd(&stream)
        }

        let buffer = UnsafeMutablePointer<UInt8>.allocate(capacity: bufferSize)
        defer { buffer.deallocate() }

        repeat {
            stream.next_out = buffer
            stream.avail_out = uInt(bufferSize)

            status = inflate(&stream, Z_NO_FLUSH)

            if status == Z_STREAM_ERROR || status == Z_DATA_ERROR || status == Z_MEM_ERROR {
                throw NSError(domain: "DecompressionError", code: Int(status), userInfo: [NSLocalizedDescriptionKey: "Decompression failed with zlib error"])
            }

            let have = bufferSize - Int(stream.avail_out)
            output.append(buffer, count: have)

        } while status != Z_STREAM_END

        return output
    }
    
    private static func fetchRecursive(
        _ path: String,
        client: BlePsFtpClient,
        condition: @escaping (String) -> Bool
    ) -> Observable<(name: String, size: UInt64)> {
        do {
            var operation = Protocol_PbPFtpOperation()
            operation.command = .get
            operation.path = path
            let request = try operation.serializedData()
            
            return client.request(request)
                .asObservable()
                .flatMap { data -> Observable<(name: String, size: UInt64)> in
                    do {
                        let dir = try Protocol_PbPFtpDirectory(serializedData: data as Data)
                        let entries = dir.entries.compactMap { entry -> (name: String, size: UInt64)? in
                            if condition(entry.name) {
                                return (name: path + entry.name, size: entry.size)
                            }
                            return nil
                        }
                        
                        return Observable.from(entries)
                            .flatMap { entry -> Observable<(name: String, size: UInt64)> in
                                if entry.name.hasSuffix("/") {
                                    return fetchRecursive(entry.name, client: client, condition: condition)
                                } else {
                                    return Observable.just(entry)
                                }
                            }
                    } catch {
                        return Observable.error(PolarErrors.deviceError(description: "\(error)"))
                    }
                }
        } catch {
            return Observable.error(error)
        }
    }
}
