//  Copyright © 2025 Polar. All rights reserved.

import Foundation
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
    ) async throws -> [PolarTrainingSessionReference] {
        BleLogger.trace("getTrainingSessions: fromDate=\(String(describing: fromDate)), toDate=\(String(describing: toDate))")
        var updatedReferences: [PolarTrainingSessionReference] = []
        var trainingSessionSummaryPaths: Set<String> = []
        
        let entries = try await fetchRecursive(ARABICA_USER_ROOT_FOLDER, client: client) { name in
            return name.range(of: #"^(\d{8}/|\d{6}/|.*\.BPB|.*\.GZB|E/|\d+/)$"#, options: .regularExpression) != nil
        }
        
        for (path, size) in entries {
            let fileName = (path as NSString).lastPathComponent
            if let dataType = PolarTrainingSessionDataTypes(rawValue: fileName) {
                let regex = try! NSRegularExpression(pattern: "/U/0/(\\d{8})/E/(\\d{6})/TSESS.BPB$")
                if let match = regex.firstMatch(in: path, range: NSRange(path.startIndex..., in: path)) {
                    let dateStr = String(path[Range(match.range(at: 1), in: path)!])
                    let timeStr = String(path[Range(match.range(at: 2), in: path)!])
                    let dateTimeStr = dateStr + timeStr
                    let dateTime = dateTimeFormatter.date(from: dateTimeStr) ?? Date()
                    let dateAtPath = Calendar.current.dateComponents([.year, .month, .day], from: dateFormatter.date(from: dateStr) ?? Date())
                    let effectiveFrom = fromDate.map { Calendar.current.dateComponents([.year, .month, .day], from: $0) }
                    let effectiveTo = toDate.map { Calendar.current.dateComponents([.year, .month, .day], from: $0) }
                    let afterFrom = effectiveFrom.map { dateAtPath >= $0 } ?? true
                    let beforeTo = effectiveTo.map { dateAtPath <= $0 } ?? true
                    let dateMatches = afterFrom && beforeTo
                    if dateMatches {
                        trainingSessionSummaryPaths.insert(path)
                        if let index = updatedReferences.firstIndex(where: { $0.path.contains(dateStr) && $0.path.contains(timeStr) }) {
                            if !updatedReferences[index].trainingDataTypes.contains(dataType) {
                                updatedReferences[index].trainingDataTypes.append(dataType)
                                updatedReferences[index].fileSize += Int64(size)
                            }
                        } else {
                            updatedReferences.append(PolarTrainingSessionReference(
                                date: dateTime, path: path, trainingDataTypes: [dataType], exercises: [], fileSize: Int64(size)
                            ))
                        }
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
                        if let existingIndex = updatedReferences[index].exercises.firstIndex(where: { $0.path == fullExercisePath }) {
                            var existing = updatedReferences[index].exercises[existingIndex]
                            if !existing.exerciseDataTypes.contains(exerciseDataType) {
                                existing.exerciseDataTypes.append(exerciseDataType)
                                updatedReferences[index].exercises[existingIndex] = existing
                            }
                        } else {
                            updatedReferences[index].exercises.append(PolarExercise(index: 0, path: fullExercisePath, exerciseDataTypes: [exerciseDataType]))
                        }
                    }
                }
            }
        }
        
        return updatedReferences
    }
    
    static func readTrainingSession(client: BlePsFtpClient, reference: PolarTrainingSessionReference) async throws -> PolarTrainingSession {
        BleLogger.trace("readTrainingSession: Starting to read session from path: \(reference.path)")
        var tsessOp = Protocol_PbPFtpOperation()
        tsessOp.command = .get
        tsessOp.path = reference.path
        let response = try await client.request(try tsessOp.serializedBytes())
        let sessionSummary = try Data_PbTrainingSession(serializedBytes: response as Data)
        let exercises = try await withThrowingTaskGroup(of: PolarExercise.self) { group in
            for exercise in reference.exercises {
                group.addTask {
                    return try await readExercise(client: client, exercise: exercise)
                }
            }
            var results: [PolarExercise] = []
            for try await exercise in group { results.append(exercise) }
            return results
        }
        return PolarTrainingSession(reference: reference, sessionSummary: sessionSummary, exercises: exercises)
    }
    
    static func readTrainingSessionWithProgress(
        client: BlePsFtpClient,
        reference: PolarTrainingSessionReference,
        progressHandler: @escaping (PolarTrainingSessionProgress) -> Void
    ) async throws -> PolarTrainingSession {
        class ProgressCallbackImpl: BlePsFtpProgressCallback {
            let totalBytes: Int64
            var accumulatedBytes: Int64 = 0
            let progressHandler: (PolarTrainingSessionProgress) -> Void
            let lock = NSLock()
            init(totalBytes: Int64, progressHandler: @escaping (PolarTrainingSessionProgress) -> Void) {
                self.totalBytes = totalBytes
                self.progressHandler = progressHandler
            }
            func onProgressUpdate(bytesReceived: Int) {
                lock.lock(); accumulatedBytes += Int64(bytesReceived); let cur = accumulatedBytes; lock.unlock()
                let percent = totalBytes > 0 ? Int((cur * 100) / totalBytes) : 0
                progressHandler(PolarTrainingSessionProgress(totalBytes: totalBytes, completedBytes: cur, progressPercent: min(percent, 100), currentFileName: nil))
            }
        }
        let totalBytes = reference.fileSize
        let progressCallback = ProgressCallbackImpl(totalBytes: totalBytes, progressHandler: progressHandler)
        client.progressCallback = progressCallback
        defer { client.progressCallback = nil }
        progressHandler(PolarTrainingSessionProgress(totalBytes: totalBytes, completedBytes: 0, progressPercent: 0, currentFileName: nil))
        
        var tsessOp = Protocol_PbPFtpOperation()
        tsessOp.command = .get
        tsessOp.path = reference.path
        let response = try await client.request(try tsessOp.serializedBytes())
        let sessionSummary = try Data_PbTrainingSession(serializedBytes: response as Data)
        let exercises = try await withThrowingTaskGroup(of: PolarExercise.self) { group in
            for exercise in reference.exercises {
                group.addTask { return try await readExercise(client: client, exercise: exercise) }
            }
            var results: [PolarExercise] = []
            for try await exercise in group { results.append(exercise) }
            return results
        }
        progressHandler(PolarTrainingSessionProgress(totalBytes: totalBytes, completedBytes: totalBytes, progressPercent: 100, currentFileName: nil))
        return PolarTrainingSession(reference: reference, sessionSummary: sessionSummary, exercises: exercises)
    }
    
    static func deleteTrainingSession(client: BlePsFtpClient, reference: PolarTrainingSessionReference) async throws {
        let components = reference.path.split(separator: "/")
        var operation = Protocol_PbPFtpOperation()
        operation.command = .get
        operation.path = "/U/0/" + components[2] + "/E/"
        let content = try await client.request(try operation.serializedBytes())
        let dir = try Protocol_PbPFtpDirectory(serializedBytes: content as Data)
        var removeOperation = Protocol_PbPFtpOperation()
        removeOperation.command = .remove
        if dir.entries.count <= 1 {
            removeOperation.path = "/U/0/" + components[2] + "/E/"
        } else {
            removeOperation.path = "/U/0/" + components[2] + "/E/" + components[4] + "/"
        }
        _ = try await client.request(try removeOperation.serializedBytes())
    }
    
    // MARK: - Private helpers
    
    private static func readExercise(client: BlePsFtpClient, exercise: PolarExercise) async throws -> PolarExercise {
        let basePath = exercise.path
        let dataResults = try await withThrowingTaskGroup(of: (PolarExerciseDataTypes, Data).self) { group in
            for dataType in exercise.exerciseDataTypes {
                group.addTask {
                    let filePath = "\(basePath)/\(dataType.rawValue)"
                    var op = Protocol_PbPFtpOperation()
                    op.command = .get
                    op.path = filePath
                    let response = try await client.request(try op.serializedBytes())
                    let data: Data = filePath.hasSuffix(".GZB") ? try unzipGzip(response as Data) : response as Data
                    return (dataType, data)
                }
            }
            var results: [(PolarExerciseDataTypes, Data)] = []
            for try await r in group { results.append(r) }
            return results
        }
        var summary: Data_PbExerciseBase?
        var route: Data_PbExerciseRouteSamples?
        var route2: Data_PbExerciseRouteSamples2?
        var samples: Data_PbExerciseSamples?
        var samples2: Data_PbExerciseSamples2?
        for (type, data) in dataResults {
            switch type {
            case .exerciseSummary:            summary  = try? Data_PbExerciseBase(serializedBytes: data)
            case .route, .routeGzip:          route    = try? Data_PbExerciseRouteSamples(serializedBytes: data)
            case .routeAdvancedFormat, .routeAdvancedFormatGzip: route2 = try? Data_PbExerciseRouteSamples2(serializedBytes: data)
            case .samples, .samplesGzip:      samples  = try? Data_PbExerciseSamples(serializedBytes: data)
            case .samplesAdvancedFormatGzip:  samples2 = try? Data_PbExerciseSamples2(serializedBytes: data)
            }
        }
        return PolarExercise(index: exercise.index, path: basePath, exerciseDataTypes: exercise.exerciseDataTypes,
                             exerciseSummary: summary, route: route, routeAdvanced: route2, samples: samples, samplesAdvanced: samples2)
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
        defer { inflateEnd(&stream) }
        let buffer = UnsafeMutablePointer<UInt8>.allocate(capacity: bufferSize)
        defer { buffer.deallocate() }
        repeat {
            stream.next_out = buffer; stream.avail_out = uInt(bufferSize)
            status = inflate(&stream, Z_NO_FLUSH)
            if status == Z_STREAM_ERROR || status == Z_DATA_ERROR || status == Z_MEM_ERROR {
                throw NSError(domain: "DecompressionError", code: Int(status), userInfo: [NSLocalizedDescriptionKey: "Decompression failed with zlib error"])
            }
            output.append(buffer, count: bufferSize - Int(stream.avail_out))
        } while status != Z_STREAM_END
        return output
    }
    
    private static func fetchRecursive(
        _ path: String,
        client: BlePsFtpClient,
        condition: @escaping (String) -> Bool
    ) async throws -> [(name: String, size: UInt64)] {
        var operation = Protocol_PbPFtpOperation()
        operation.command = .get
        operation.path = path
        let data = try await client.request(try operation.serializedBytes())
        let dir = try Protocol_PbPFtpDirectory(serializedBytes: data as Data)
        var results: [(name: String, size: UInt64)] = []
        for entry in dir.entries where condition(entry.name) {
            let fullPath = path + entry.name
            if fullPath.hasSuffix("/") {
                let children = try await fetchRecursive(fullPath, client: client, condition: condition)
                results.append(contentsOf: children)
            } else {
                results.append((name: fullPath, size: entry.size))
            }
        }
        return results
    }
}
