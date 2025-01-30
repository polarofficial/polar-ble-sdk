/// Copyright © 2019 Polar Electro Oy. All rights reserved.

import Foundation
import CoreBluetooth
import RxSwift

#if os(iOS)
import UIKit
#endif

/// Implementation of PolarSleepApi
/// Depends on PolarRestServiceApi

extension PolarBleApiImpl: PolarSleepApi {
    
    func stopSleepRecording(identifier: String) -> RxSwift.Completable {
        return self.putNotification(identifier: identifier,
                                    notification: "{}",
                                    path: "/REST/SLEEP.API?cmd=post&endpoint=stop_sleep_recording")
    }
    
    internal struct SleepRecordingState: Decodable {
        let enabled: Int?
        enum CodingKeys: String, CodingKey {
            case enabled
        }
        var isEnabled: Bool {
            return  enabled ?? 0 == 1
        }
    }
    
    internal struct SleepRecordingStateWrapper: Decodable {
        private let sleep_recording_state: SleepRecordingState
        enum CodingKeys: String, CodingKey {
            case sleep_recording_state
        }
        var sleepRecordingState: SleepRecordingState {
            return sleep_recording_state
        }
    }
    
    func getSleepRecordingState(identifier: String) -> Single<Bool> {
            
        let observeRecordingState = observeSleepRecordingState(identifier: identifier)
            .filter { $0.isEmpty == false }
            .take(1)
            .map { $0.last! }
            .asSingle()
        
        return observeRecordingState
    }
        
    func observeSleepRecordingState(identifier: String) -> Observable<[Bool]> {
        let receiveSleepRecordingStates:Observable<[SleepRecordingStateWrapper]> =
            self.receiveRestApiEvents(identifier: identifier)
        let receiveSleepRecordingEnabled = receiveSleepRecordingStates.compactMap {
            return $0.compactMap { $0.sleepRecordingState.isEnabled }
        }
        let subscribe = self.putNotification(identifier: identifier, notification: "{}",
                             path: "/REST/SLEEP.API?cmd=subscribe&event=sleep_recording_state&details=[enabled]")
        return subscribe.andThen(receiveSleepRecordingEnabled)
    }
    
    func getSleepData(identifier: String, fromDate: Date, toDate: Date) -> Single<[PolarSleepData.PolarSleepAnalysisResult]> {
        
        if (fromDate > toDate) {
            return Single.error(PolarErrors.invalidArgument(description: "toDate cannot be smaller than fromDate."))
        }
        
        do {
            let session = try self.sessionFtpClientReady(identifier)
            guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                return Single.error(PolarErrors.serviceNotFound)
            }

            var sleepDataList = [PolarSleepData.PolarSleepAnalysisResult]()
            let calendar = Calendar.current
            var datesList = [Date]()
            var currentDate = fromDate

            if (fromDate == toDate) {
                datesList.append(fromDate)
            } else {
                while currentDate <= toDate {
                    datesList.append(currentDate)
                    if let nextDate = calendar.date(byAdding: .day, value: 1, to: currentDate) {
                        currentDate = nextDate
                    } else {
                        break
                    }
                }
            }

            return Observable.from(datesList)
                .flatMap { date -> Single<(PolarSleepData.PolarSleepAnalysisResult)> in
                    return PolarSleepUtils.readSleepFromDayDirectory(client: client, date: date)
                        .map { sleepData -> (PolarSleepData.PolarSleepAnalysisResult) in
                            return sleepData
                        }
                }
                .toArray()
                .map { sleepAnalysisResult -> [PolarSleepData.PolarSleepAnalysisResult] in
                    // Create an unwrapped copy of sleepDataList to allow removal of nil PolarSleepAnalysisResults from the list.
                    var sleepDataList = [PolarSleepData.PolarSleepAnalysisResult]()
                    sleepDataList.append(contentsOf: sleepAnalysisResult)
                    for sleepData in sleepDataList {
                        if (sleepData.sleepStartTime == nil) {
                            if let index = sleepDataList.firstIndex(where: { $0.lastModified == sleepData.lastModified }) {
                                sleepDataList.remove(at: index)
                            }
                        }
                    }
                    return sleepDataList
                }
        } catch {
            return Single.error(error)
        }
    }
    
}
