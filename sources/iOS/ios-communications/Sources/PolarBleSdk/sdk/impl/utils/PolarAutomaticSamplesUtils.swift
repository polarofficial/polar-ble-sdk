//  Copyright © 2024 Polar. All rights reserved.

import Foundation
import RxSwift

private let ARABICA_USER_ROOT_FOLDER = "/U/0/"
private let AUTOMATIC_SAMPLES_DIRECTORY = "AUTOS/"
private let AUTOMATIC_SAMPLES_PATTERN = #"AUTOS\d{3}\.BPB"#
private let TAG = "PolarAutomaticSamplesUtils"


internal class PolarAutomaticSamplesUtils {
    
    /// Read 24/7 heart rate samples for a given date range.
    static func read247HrSamples(client: BlePsFtpClient, fromDate: Date, toDate: Date) -> Single<[Polar247HrSamplesData]> {
        BleLogger.trace(TAG, "read247HrSamples: from \(fromDate) to \(toDate)")
        return Single<[Polar247HrSamplesData]>.create { emitter in
            let autoSamplesPath = "\(ARABICA_USER_ROOT_FOLDER)\(AUTOMATIC_SAMPLES_DIRECTORY)"
            
            let operation = Protocol_PbPFtpOperation.with {
                $0.command = .get
                $0.path = autoSamplesPath
            }
            
            let disposable = client.request(try! operation.serializedData()).subscribe(
                onSuccess: { response in
                    do {
                        let dir = try Protocol_PbPFtpDirectory(serializedData: Data(response))
                        let regex = try NSRegularExpression(pattern: AUTOMATIC_SAMPLES_PATTERN)
                        let filteredFiles = dir.entries.compactMap { entry -> String? in
                            let range = NSRange(location: 0, length: entry.name.count)
                            return regex.firstMatch(in: entry.name, range: range) != nil ? entry.name : nil
                        }
                        
                        var hrSamplesDataList: [Polar247HrSamplesData] = []
                        
                        let fileRequests = filteredFiles.map { fileName -> Single<Void> in
                            let filePath = "\(autoSamplesPath)\(fileName)"
                            let fileOperation = Protocol_PbPFtpOperation.with {
                                $0.command = .get
                                $0.path = filePath
                            }
                            
                            return client.request(try! fileOperation.serializedData()).map { fileResponse in
                                let sampleSessions = try Data_PbAutomaticSampleSessions(serializedData: Data(fileResponse))
                                let sampleDateProto = sampleSessions.day
                                
                                sampleSessions.samples.forEach { sample in
                                    let sampleTimeProto = sample.time
                                    
                                    var calendar = Calendar(identifier: .gregorian)
                                    calendar.timeZone = TimeZone(secondsFromGMT: 0)!
                                    
                                    let sampleDate = calendar.date(from: DateComponents(
                                        year: Int(sampleDateProto.year),
                                        month: Int(sampleDateProto.month),
                                        day: Int(sampleDateProto.day),
                                        hour: Int(sampleTimeProto.hour),
                                        minute: Int(sampleTimeProto.minute),
                                        second: Int(sampleTimeProto.seconds),
                                        nanosecond: 0
                                    ))!
                                    
                                    if sampleDate >= fromDate && sampleDate <= toDate {
                                        let hrSamples = sample.heartRate.map { Int($0) }

                                        let triggerType: AutomaticSampleTriggerType

                                        switch sample.triggerType {
                                        case .triggerTypeHighActivity:
                                            triggerType = .highActivity
                                        case .triggerTypeLowActivity:
                                            triggerType = .lowActivity
                                        case .triggerTypeTimed:
                                            triggerType = .timed
                                        case .triggerTypeManual:
                                            triggerType = .manual
                                        }
                                        
                                        let data = Polar247HrSamplesData(
                                            date: sampleDate,
                                            hrSamples: hrSamples,
                                            triggerType: triggerType
                                        )
                                        
                                        hrSamplesDataList.append(data)
                                    } else {
                                        BleLogger.trace(TAG, "Sample date \(sampleDate) is out of range: \(fromDate) to \(toDate)")
                                    }
                                }
                            }
                        }
                        
                        Observable.merge(fileRequests.map { $0.asObservable() })
                            .subscribe(
                                onError: { error in
                                    BleLogger.error(TAG, "Error processing files: \(error)")
                                    emitter(.failure(error))
                                },
                                onCompleted: {
                                    emitter(.success(hrSamplesDataList))
                                }
                            )
                        
                    } catch {
                        BleLogger.error(TAG, "read247HrSamples() failed for path: \(autoSamplesPath), error: \(error)")
                        emitter(.failure(error))
                    }
                },
                onFailure: { error in
                    BleLogger.error(TAG, "read247HrSamples() failed for path: \(autoSamplesPath), error: \(error)")
                    emitter(.failure(error))
                }
            )
            
            return Disposables.create {
                disposable.dispose()
            }
        }
    }

}
