//  Copyright © 2025 Polar. All rights reserved.

import Foundation
import RxSwift
import PolarBleSdk

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
                return name.range(of: #"^(\d{8}/|\d{6}/|.*\.BPB|E/|\d+/)$"#, options: .regularExpression) != nil
            }

            fetchRecursiveObservable
                .subscribe(onNext: { (path, _) in
                    if let dataType = PolarTrainingSessionDataTypes(rawValue: (path as NSString).lastPathComponent) {
                        let regex = try! NSRegularExpression(pattern: "/U/0/(\\d{8})/E/(\\d{6})/TSESS.BPB$")
                        if let match = regex.firstMatch(in: path, range: NSRange(path.startIndex..., in: path)) {
                            let dateStr = String(path[Range(match.range(at: 1), in: path)!])
                            let timeStr = String(path[Range(match.range(at: 2), in: path)!])
                            let dateTimeStr = dateStr + timeStr
                            let date = dateTimeFormatter.date(from: dateTimeStr) ?? Date()
                            trainingSessionSummaryPaths.insert(path)
                            if let index = updatedReferences.firstIndex(where: { $0.path.contains(dateStr) && $0.path.contains(timeStr) }) {
                                updatedReferences[index].trainingDataTypes.append(dataType)
                            } else {
                                updatedReferences.append(
                                    PolarTrainingSessionReference(
                                        date: date,
                                        path: path,
                                        trainingDataTypes: [dataType],
                                        exercises: []
                                    )
                                )
                            }
                        }
                    } else if let exerciseDataType = PolarExerciseDataTypes(rawValue: (path as NSString).lastPathComponent) {
                        let regex = try! NSRegularExpression(pattern: "/U/0/(\\d{8})/E/(\\d{6})(?:/(\\d+))?/")
                        if let match = regex.firstMatch(in: path, range: NSRange(path.startIndex..., in: path)) {
                            let dateStr = String(path[Range(match.range(at: 1), in: path)!])
                            let timeStr = String(path[Range(match.range(at: 2), in: path)!])
                            let exercisePathPrefix = "/U/0/\(dateStr)/E/\(timeStr)"
                            let possibletrainingSessionSummaryPaths = trainingSessionSummaryPaths.filter { $0.hasPrefix(exercisePathPrefix) }
                            if let tseSsPath = possibletrainingSessionSummaryPaths.first,
                               let index = updatedReferences.firstIndex(where: { $0.path == tseSsPath }) {
                                let exercise = PolarExercise(index: 0, path: path, exerciseDataTypes: [exerciseDataType])
                                updatedReferences[index].exercises.append(exercise)
                            }
                        }
                    }
                }, onError: { error in
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

        return Single.create { emitter in
            var operation = Protocol_PbPFtpOperation()
            operation.command = .get
            operation.path = reference.path
            
            do {
                let request = try operation.serializedData()

                let disposable = client.request(request).subscribe(
                    onSuccess: { response in
                        do {
                            let sessionSummary = try Data_PbTrainingSession(serializedData: response as Data)

                            let exerciseObservables: [Single<PolarExercise>] = reference.exercises.map { exercise in
                                BleLogger.trace("readTrainingSession: Preparing request for exercise at path \(exercise.path)")

                                var exerciseOperation = Protocol_PbPFtpOperation()
                                exerciseOperation.command = .get
                                exerciseOperation.path = exercise.path

                                let request = try! exerciseOperation.serializedData()

                                return client.request(request)
                                    .map { exerciseResponse in
                                        let exerciseSummary = try! Data_PbExerciseBase(serializedData: exerciseResponse as Data)

                                        return PolarExercise(
                                            index: exercise.index,
                                            path: exercise.path,
                                            exerciseDataTypes: exercise.exerciseDataTypes,
                                            exerciseSummary: exerciseSummary
                                        )
                                    }
                            }
                            Single.zip(exerciseObservables)
                                .map { exercises in
                                    return PolarTrainingSession(
                                        reference: reference,
                                        sessionSummary: sessionSummary,
                                        exercises: exercises
                                    )
                                }
                                .subscribe(
                                    onSuccess: { polarTrainingSession in
                                        emitter(.success(polarTrainingSession))
                                    },
                                    onFailure: { error in
                                        BleLogger.error("readTrainingSession: Error occurred at \(reference.path): \(error)")
                                        emitter(.failure(error))
                                    }
                                )

                        } catch {
                            BleLogger.error("readTrainingSession: Failed to parse TSESS.BPB at \(reference.path): \(error)")
                            emitter(.failure(error))
                        }
                    },
                    onFailure: { error in
                        BleLogger.error("readTrainingSession: Failed to fetch TSESS.BPB at \(reference.path): \(error)")
                        emitter(.failure(error))
                    }
                )

                return Disposables.create {
                    disposable.dispose()
                }
            } catch {
                BleLogger.error("readTrainingSession: Failed to serialize request for \(reference.path): \(error)")
                emitter(.failure(error))
                return Disposables.create()
            }
        }
    }
    
    static func parseDateTime(_ dateTime: PbLocalDateTime) -> Date {
        var components = DateComponents()
        components.year = Int(dateTime.date.year)
        components.month = Int(dateTime.date.month)
        components.day = Int(dateTime.date.day)
        components.hour = Int(dateTime.time.hour)
        components.minute = Int(dateTime.time.minute)
        components.second = Int(dateTime.time.seconds)
        components.nanosecond = Int(dateTime.time.millis) * 1_000_000
        
        let calendar = Calendar(identifier: .gregorian)
        return calendar.date(from: components) ?? Date()
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