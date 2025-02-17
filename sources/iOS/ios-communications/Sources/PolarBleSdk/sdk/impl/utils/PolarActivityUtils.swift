//  Copyright © 2024 Polar. All rights reserved.

import Foundation
import RxSwift

private let ARABICA_USER_ROOT_FOLDER = "/U/0/"
private let ACTIVITY_DIRECTORY = "ACT/"
private let ACTIVITY_SAMPLES_PROTO = "ASAMPL0.BPB"
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
            let activityFilePath = "\(ARABICA_USER_ROOT_FOLDER)\(dateFormat.string(from: date))/\(ACTIVITY_DIRECTORY)\(ACTIVITY_SAMPLES_PROTO)"
            let operation = Protocol_PbPFtpOperation.with {
                $0.command = .get
                $0.path = activityFilePath
            }
            let disposable = client.request(try! operation.serializedData()).subscribe(
                onSuccess: { response in
                    do {
                        let proto = try Data_PbActivitySamples(serializedData: Data(response))
                        let steps = proto.stepsSamples.reduce(0, +)
                        emitter(.success(Int(steps)))
                    } catch {
                        BleLogger.error("readStepsFromDayDirectory() failed for path: \(activityFilePath), error: \(error)")
                        emitter(.success(0))
                    }
                },
                onFailure: { error in
                    BleLogger.error("readStepsFromDayDirectory() failed for path: \(activityFilePath), error: \(error)")
                    emitter(.success(0))
                }
            )
            return Disposables.create {
                disposable.dispose()
            }
        }
    }
    
    /// Read distance in meters for given date.
    static func readDistanceFromDayDirectory(client: BlePsFtpClient, date: Date) -> Single<Float> {
        BleLogger.trace(TAG, "readDistanceFromDayDirectory: \(date)")
        return sendSyncStart(client)
            .andThen(Single<Float>.create { emitter in
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
            })
    }

    /// Read active time for given date.
    static func readActiveTimeFromDayDirectory(client: BlePsFtpClient, date: Date) -> Single<PolarActiveTimeData> {
        BleLogger.trace(TAG, "readActiveTimeFromDayDirectory: \(date)")
        return sendSyncStart(client)
            .andThen(Single<PolarActiveTimeData>.create { emitter in
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
                                timeNonWear: polarActiveTimeFromProto(proto.activityClassTimes.timeNonWear),
                                timeSleep: polarActiveTimeFromProto(proto.activityClassTimes.timeSleep),
                                timeSedentary: polarActiveTimeFromProto(proto.activityClassTimes.timeSedentary),
                                timeLightActivity: polarActiveTimeFromProto(proto.activityClassTimes.timeLightActivity),
                                timeContinuousModerateActivity: polarActiveTimeFromProto(proto.activityClassTimes.timeContinuousModerate),
                                timeIntermittentModerateActivity: polarActiveTimeFromProto(proto.activityClassTimes.timeIntermittentModerate),
                                timeContinuousVigorousActivity: polarActiveTimeFromProto(proto.activityClassTimes.timeContinuousVigorous),
                                timeIntermittentVigorousActivity: polarActiveTimeFromProto(proto.activityClassTimes.timeIntermittentVigorous)
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
            })
    }

    /// Read calories for given date.
    static func readCaloriesFromDayDirectory(client: BlePsFtpClient, date: Date, caloriesType: CaloriesType) -> Single<Int> {
        BleLogger.trace(TAG, "readCaloriesFromDayDirectory: \(date), type: \(caloriesType)")
        return sendSyncStart(client)
          .andThen(Single<Int>.create { emitter in
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
          })
      }

    // Send sync start to generate daily summary for the current date
    private static func sendSyncStart(_ client: BlePsFtpClient) -> Completable {
        return client.sendNotification(
            Protocol_PbPFtpHostToDevNotification.startSync.rawValue,
            parameters: nil
        )
    }

    private static func polarActiveTimeFromProto(_ proto: PbDuration) -> PolarActiveTime {
        return PolarActiveTime(
            hours: Int(proto.hours),
            minutes: Int(proto.minutes),
            seconds: Int(proto.seconds),
            millis: Int(proto.millis)
        )
    }
}
