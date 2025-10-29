//  Copyright © 2024 Polar. All rights reserved.

import Foundation
import RxSwift

private let ARABICA_USER_ROOT_FOLDER = "/U/0/"
private let ACTIVITY_DIRECTORY = "ACT/"
private let DAILY_SUMMARY_DIRECTORY = "DSUM/"
private let DAILY_SUMMARY_PROTO = "DSUM.BPB"
private let dateFormat: DateFormatter = {
    let formatter = DateFormatter()
    formatter.dateFormat = "yyyyMMdd"
    formatter.locale = Locale(identifier: "en_US_POSIX")
    return formatter
}()
private let TAG = "PolarActivityUtils"

internal class PolarActivityUtils {
    
    /// Read step count for given date.
    static func readStepsFromDayDirectory(client: BlePsFtpClient, date: Date) -> Single<Int> {
        BleLogger.trace(TAG, "readStepsFromDayDirectory: \(date)")
        return Single<Int>.create { emitter in
            let activityFileDir = "\(ARABICA_USER_ROOT_FOLDER)\(dateFormat.string(from: date))/\(ACTIVITY_DIRECTORY)"
            var stepCount: UInt32 = 0
            var filePaths: [String] = []
            return listFiles(client: client, folderPath: activityFileDir, condition: { (entry) -> Bool in
                return entry.matches("^\(activityFileDir)") ||
                entry == "ASAMPL" ||
                entry.contains(".BPB")})
            .map{ path -> () in
                filePaths.append(path)
            }
            .asObservable()
            .subscribe (
                onError: { error in
                    BleLogger.error("Failed to list Activity sample files.")
                    emitter(.success(0))
                },
                onCompleted: {
                    var index = 0
                    guard filePaths.count > 0 else {
                        emitter(.success(Int(0)))
                        return
                    }
                    for path in filePaths {
                        let operation = Protocol_PbPFtpOperation.with {
                            $0.command = .get
                            $0.path = path
                        }
                        _ = client.request(try! operation.serializedData()).subscribe(
                            onSuccess: { response in
                                do {
                                    index += 1
                                    let proto = try Data_PbActivitySamples(serializedData: Data(response))
                                    stepCount = stepCount + proto.stepsSamples.reduce(0, +)
                                    if (index == filePaths.count) {
                                        emitter(.success(Int(stepCount)))
                                    }
                                } catch {
                                    BleLogger.error("readStepsFromDayDirectory() failed for path: \(activityFileDir), error: \(error)")
                                    emitter(.success(0))
                                }
                            },
                            onFailure: { error in
                                BleLogger.error("readStepsFromDayDirectory() failed for path: \(activityFileDir), error: \(error)")
                                emitter(.success(0))
                            }
                        )
                    }
                }
            )
        }
    }
    
    /// Read distance in meters for given date.
    static func readDistanceFromDayDirectory(client: BlePsFtpClient, date: Date) -> Single<Float> {
        BleLogger.trace(TAG, "readDistanceFromDayDirectory: \(date)")
        return Single<Float>.create { emitter in
                let dailySummaryFilePath = "\(ARABICA_USER_ROOT_FOLDER)\(dateFormat.string(from: date))/\(DAILY_SUMMARY_DIRECTORY)\(DAILY_SUMMARY_PROTO)"
                let operation = Protocol_PbPFtpOperation.with {
                    $0.command = .get
                    $0.path = dailySummaryFilePath
                }
                let disposable = client.request(try! operation.serializedData()).subscribe(
                    onSuccess: { response in
                        do {
                            let proto = try Data_PbDailySummary(serializedData: Data(response))
                            let distance = proto.activityDistance
                            emitter(.success(Float(distance)))
                        } catch {
                            BleLogger.error("readDistanceFromDayDirectory() failed for path: \(dailySummaryFilePath), error: \(error)")
                            emitter(.success(0))
                        }
                    },
                    onFailure: { error in
                        BleLogger.error("readDistanceFromDayDirectory() failed for path: \(dailySummaryFilePath), error: \(error)")
                        emitter(.success(0))
                    }
                )
                return Disposables.create {
                    disposable.dispose()
                }
            }
    }
    
    /// Read active time for given date.
    static func readActiveTimeFromDayDirectory(client: BlePsFtpClient, date: Date) -> Single<PolarActiveTimeData> {
        BleLogger.trace(TAG, "readActiveTimeFromDayDirectory: \(date)")
        return Single<PolarActiveTimeData>.create { emitter in
            let dailySummaryFilePath = "\(ARABICA_USER_ROOT_FOLDER)\(dateFormat.string(from: date))/\(DAILY_SUMMARY_DIRECTORY)\(DAILY_SUMMARY_PROTO)"
            let operation = Protocol_PbPFtpOperation.with {
                $0.command = .get
                $0.path = dailySummaryFilePath
            }
            let disposable = client.request(try! operation.serializedData()).subscribe(
                onSuccess: { response in
                    do {
                        let proto = try Data_PbDailySummary(serializedData: Data(response))
                        let polarActiveTimeData = PolarActiveTimeData(
                            date: date,
                            timeNonWear: PolarActiveTime.fromProto(proto.activityClassTimes.timeNonWear),
                            timeSleep: PolarActiveTime.fromProto(proto.activityClassTimes.timeSleep),
                            timeSedentary: PolarActiveTime.fromProto(proto.activityClassTimes.timeSedentary),
                            timeLightActivity: PolarActiveTime.fromProto(proto.activityClassTimes.timeLightActivity),
                            timeContinuousModerateActivity: PolarActiveTime.fromProto(proto.activityClassTimes.timeContinuousModerate),
                            timeIntermittentModerateActivity: PolarActiveTime.fromProto(proto.activityClassTimes.timeIntermittentModerate),
                            timeContinuousVigorousActivity: PolarActiveTime.fromProto(proto.activityClassTimes.timeContinuousVigorous),
                            timeIntermittentVigorousActivity: PolarActiveTime.fromProto(proto.activityClassTimes.timeIntermittentVigorous)
                        )
                        emitter(.success(polarActiveTimeData))
                    } catch {
                        BleLogger.error("readActiveTimeFromDayDirectory() failed for path: \(dailySummaryFilePath), error: \(error)")
                        emitter(.success(PolarActiveTimeData(date: date, timeNonWear: PolarActiveTime())))
                    }
                },
                onFailure: { error in
                    BleLogger.error("readActiveTimeFromDayDirectory() failed for path: \(dailySummaryFilePath), error: \(error)")
                    emitter(.success(PolarActiveTimeData(date: date, timeNonWear: PolarActiveTime())))
                }
            )
            return Disposables.create {
                disposable.dispose()
            }
        }
    }
    
    /// Read calories for given date.
    static func readCaloriesFromDayDirectory(client: BlePsFtpClient, date: Date, caloriesType: CaloriesType) -> Single<Int> {
        BleLogger.trace(TAG, "readCaloriesFromDayDirectory: \(date), type: \(caloriesType)")
        return Single<Int>.create { emitter in
                let dailySummaryFilePath = "\(ARABICA_USER_ROOT_FOLDER)\(dateFormat.string(from: date))/\(DAILY_SUMMARY_DIRECTORY)\(DAILY_SUMMARY_PROTO)"
                let operation = Protocol_PbPFtpOperation.with {
                    $0.command = .get
                    $0.path = dailySummaryFilePath
                }
                
                let disposable = client.request(try! operation.serializedData()).subscribe(
                    onSuccess: { response in
                        do {
                            let proto = try Data_PbDailySummary(serializedData: Data(response))
                            let caloriesValue: Int
                            switch caloriesType {
                            case .activity:
                                caloriesValue = Int(proto.activityCalories)
                            case .training:
                                caloriesValue = Int(proto.trainingCalories)
                            case .bmr:
                                caloriesValue = Int(proto.bmrCalories)
                            }
                            emitter(.success(caloriesValue))
                        } catch {
                            BleLogger.error("readCaloriesFromDayDirectory() failed for path: \(dailySummaryFilePath), error: \(error)")
                            emitter(.success(0))
                        }
                    },
                    onFailure: { error in
                        BleLogger.error("readCaloriesFromDayDirectory() failed for path: \(dailySummaryFilePath), error: \(error)")
                        emitter(.success(0))
                    }
                )
                
                return Disposables.create {
                    disposable.dispose()
                }
            }
    }
    
    /// Read and return activity samples data for a given date.
    static func readActivitySamplesDataFromDayDirectory(client: BlePsFtpClient, date: Date) -> Single<PolarActivityDayData> {
        BleLogger.trace(TAG, "readActivitySamplesDataFromDayDirectory: \(date)")
        return Single<PolarActivityDayData>.create { emitter in
            let activityFileDir = "\(ARABICA_USER_ROOT_FOLDER)\(dateFormat.string(from: date))/\(ACTIVITY_DIRECTORY)"
            var filePaths: [String] = []
            let activityData: PolarActivityData = PolarActivityData()
            var polarActivityDataList: [PolarActivityData] = []
            return listFiles(client: client, folderPath: activityFileDir, condition: { (entry) -> Bool in
                return entry.matches("^\(activityFileDir)") ||
                entry == "ASAMPL" ||
                entry.contains(".BPB")})
            .map { path -> () in
                filePaths.append(path)
            }.asObservable()
                .subscribe(
                    onError: { error in
                        if error.localizedDescription.contains("103") { // Not found. Return empty list.
                            BleLogger.error("No activity files found for date: \(dateFormat.string(from: date))")
                            emitter(.success(PolarActivityDayData(polarActivityDataList: polarActivityDataList)))
                        } else {
                            BleLogger.error("Failed to list activity sample files.")
                            emitter(.failure(error))
                        }
                    },
                    onCompleted: {
                        var index = 0
                        guard filePaths.count > 0 else {
                            emitter(.success(PolarActivityDayData(polarActivityDataList: polarActivityDataList)))
                            return
                        }
                        for path in filePaths {
                            let operation = Protocol_PbPFtpOperation.with {
                                $0.command = .get
                                $0.path = path
                            }
                            _ = client.request(try! operation.serializedData()).subscribe(
                                onSuccess: { response in
                                    do {
                                        index += 1
                                        let proto = try Data_PbActivitySamples(serializedData: Data(response))
                                        let activitySamplesData = PolarActivityData.PolarActivitySamples.init(
                                            startTime: try PolarTimeUtils.pbLocalDateTimeToDate(pbLocalDateTime: proto.startTime),
                                            metRecordingInterval: PolarTimeUtils.pbDurationToMillis(pbDuration: proto.metRecordingInterval)/1000,
                                            metSamples: proto.metSamples,
                                            stepRecordingInterval: PolarTimeUtils.pbDurationToMillis(pbDuration: proto.stepsRecordingInterval)/1000,
                                            stepSamples: proto.stepsSamples,
                                            activityInfoList: try PolarActivityData.parsePbActivityInfoList(activityInfoList: proto.activityInfo)
                                        )
                                        activityData.samples = activitySamplesData
                                        polarActivityDataList.append(activityData)
                                        if (index == filePaths.count) {
                                            emitter(.success(PolarActivityDayData(polarActivityDataList: polarActivityDataList)))
                                        }
                                    } catch {
                                        BleLogger.error("readActivitySamplesDataFromDayDirectory() failed for path: \(activityFileDir), error: \(error)")
                                        emitter(.failure(error))
                                    }
                                },
                                onFailure: { error in
                                    BleLogger.error("readActivitySamplesDataFromDayDirectory() failed for path: \(activityFileDir), error: \(error)")
                                    emitter(.failure(error))
                                }
                            )
                        }
                    }
                )
        }
    }
    
    /// Read active time for given date.
    static func readDailySummaryDataFromDayDirectory(client: BlePsFtpClient, date: Date) -> Maybe<PolarDailySummary> {
        BleLogger.trace(TAG, "readDailySummaryDataFromDayDirectory: \(date)")
        return Maybe<PolarDailySummary>.create { emitter in
            let dailySummaryFilePath = "\(ARABICA_USER_ROOT_FOLDER)\(dateFormat.string(from: date))/\(DAILY_SUMMARY_DIRECTORY)\(DAILY_SUMMARY_PROTO)"
            let operation = Protocol_PbPFtpOperation.with {
                $0.command = .get
                $0.path = dailySummaryFilePath
            }
            let disposable = client.request(try! operation.serializedData()).subscribe(
                onSuccess: { response in
                    do {
                        let proto = try Data_PbDailySummary(serializedData: Data(response))
                        let polarDailySummary = try PolarDailySummary.fromProto(proto: proto)
                        emitter(.success(polarDailySummary))
                    } catch let error {
                        BleLogger.error("Read daily summary failed for date: \(date), error: \(error)")
                        emitter(.error(PolarErrors.fileError(description: error.localizedDescription)))
                    }
                },
                onFailure: { error in
                        if error.localizedDescription.contains("103") { // Not found. Return empty list.
                            BleLogger.error("No activity files found for date: \(dateFormat.string(from: date))")
                            emitter(.completed)
                        } else {
                            BleLogger.error("Failed to list activity sample files.")
                            emitter(.error(error))
                        }
                    BleLogger.error("Read daily summary failed for date: \(date), error: \(error)")
                    emitter(.error(PolarErrors.fileError(description: error.localizedDescription)))
                }
            )
            return Disposables.create {
                disposable.dispose()
            }
        }
    }

    private static func listFiles(client: BlePsFtpClient, folderPath: String = "/", condition: @escaping (_ p: String) -> Bool) -> Observable<String> {
        
        var path = folderPath
        
        if (path.first != "/") {
            path.insert("/", at: path.startIndex)
        }
        if (path.last != "/") {
            path.insert("/", at: path.endIndex)
        }
        
        return fetchRecursive(path, client: client, condition: condition)
            .map { (entry) -> String in
                return (entry.name)
            }
    }
    
    private static func fetchRecursive(_ path: String, client: BlePsFtpClient, condition: @escaping (_ p: String) -> Bool) -> Observable<(name: String, size:UInt64)> {
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
                                    if entry.0.hasSuffix("/") {
                                        return self.fetchRecursive(entry.0, client: client, condition: condition)
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
            return Observable.error(err)
        }
    }
}
