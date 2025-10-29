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
                                
                                var polar247HrSampleList: [Polar247HrSamplesData.Polar247HrSample] = []
                                var calendar = Calendar(identifier: .gregorian)
                                calendar.timeZone = TimeZone.current
                                
                                let startDate = Calendar.current.dateComponents([.year, .month, .day], from: fromDate)
                                let endDate = Calendar.current.dateComponents([.year, .month, .day], from: toDate)
                                let sampleDate = DateComponents(
                                    year: Int(sampleDateProto.year),
                                    month: Int(sampleDateProto.month),
                                    day: Int(sampleDateProto.day)
                                )
                                
                                if sampleDate >= startDate && sampleDate <= endDate {
                                    do {
                                        var sample = try Polar247HrSamplesData.fromPbHrDataSamples(samples: sampleSessions.samples)
                                        polar247HrSampleList.append(contentsOf: sample)
                                    } catch let error {
                                        BleLogger.error(TAG, "Failed to parse hr samples: \(error)")
                                        return
                                    }
                                    hrSamplesDataList.append(Polar247HrSamplesData(date: sampleDate, samples: polar247HrSampleList))
                                } else {
                                    BleLogger.trace(TAG, "Sample date \(sampleDate) is out of range: \(fromDate) to \(toDate)")
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

    /// Read 24/7 peak-to-peak interval samples for a given date range.
    static func read247PPiSamples(client: BlePsFtpClient, fromDate: Date, toDate: Date) -> Single<[Polar247PPiSamplesData]> {
        BleLogger.trace(TAG, "read247PPiSamples: from \(fromDate) to \(toDate)")
        return Single<[Polar247PPiSamplesData]>.create { emitter in
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

                        var polar247PpiSamplesDataList: [Polar247PPiSamplesData] = []

                        let fileRequests = filteredFiles.map { fileName -> Single<Void> in
                            let filePath = "\(autoSamplesPath)\(fileName)"
                            let fileOperation = Protocol_PbPFtpOperation.with {
                                $0.command = .get
                                $0.path = filePath
                            }

                            return client.request(try! fileOperation.serializedData()).map { fileResponse in
                                let sampleSessions = try Data_PbAutomaticSampleSessions(serializedData: Data(fileResponse))
                                let sampleDateProto = sampleSessions.day
                                var ppiSamplesDataList: [Polar247PPiSamplesData.PolarPpiDataSample] = []

                                var calendar = Calendar(identifier: .gregorian)
                                calendar.timeZone = TimeZone.current

                                let startDate = Calendar.current.dateComponents([.year, .month, .day], from: fromDate)
                                let endDate = Calendar.current.dateComponents([.year, .month, .day], from: toDate)
                                let sampleDate = DateComponents(
                                    year: Int(sampleDateProto.year),
                                    month: Int(sampleDateProto.month),
                                    day: Int(sampleDateProto.day)
                                )

                                if sampleDate >= startDate && sampleDate <= endDate {
                                    sampleSessions.ppiSamples.forEach { sample in
                                        ppiSamplesDataList.append(Polar247PPiSamplesData.fromPbPPiDataSamples(ppiData: sample))
                                    }
                                    polar247PpiSamplesDataList.append(Polar247PPiSamplesData(date: sampleDate, samples: ppiSamplesDataList))
                                } else {
                                    BleLogger.trace(TAG, "Sample date \(sampleDate) is out of range: \(startDate) to \(endDate)")
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
                                    emitter(.success(polar247PpiSamplesDataList))
                                }
                            )
                    } catch {
                        BleLogger.error(TAG, "read247PPiSamples() failed for path: \(autoSamplesPath), error: \(error)")
                        emitter(.failure(error))
                    }
                },
                onFailure: { error in
                    BleLogger.error(TAG, "read247PPiSamples() failed for path: \(autoSamplesPath), error: \(error)")
                    emitter(.failure(error))
                }
            )

            return Disposables.create {
                disposable.dispose()
            }
        }
    }
}

extension DateComponents: Comparable {
    public static func < (lhs: DateComponents, rhs: DateComponents) -> Bool {
        let now = Date()
        let calendar = Calendar.current
        return calendar.date(byAdding: lhs, to: now)! < calendar.date(byAdding: rhs, to: now)!
    }
}
