/// Copyright © 2025 Polar Electro Oy. All rights reserved.

import Foundation
import CoreBluetooth
import RxSwift

/// Implementation of PolarTemperatureApi
extension PolarBleApiImpl: PolarTemperatureApi {

    func getSkinTemperature(identifier: String, fromDate: Date, toDate: Date) -> Single<[PolarSkinTemperatureData.PolarSkinTemperatureResult]> {
        do {
            let session = try serviceClientUtils.sessionFtpClientReady(identifier)
            guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                return Single.error(PolarErrors.serviceNotFound)
            }

            var skinTemperatureDataList = [PolarSkinTemperatureData.PolarSkinTemperatureResult]()

            let calendar = Calendar.current
            var currentDate = fromDate

            var datesList = [Date]()

            while currentDate <= toDate {
                datesList.append(currentDate)
                currentDate = calendar.date(byAdding: .day, value: 1, to: currentDate)!
            }

            return Observable.from(datesList)
                .flatMap { date in
                    PolarSkinTemperatureUtils.readSkinTemperatureData(client: client, date: date)
                        .asObservable()
                        .do(onNext: { skinTemp in
                            skinTemperatureDataList.append(skinTemp)
                        })
                }
                .toArray()
                .flatMap { _ in
                    Single.just(skinTemperatureDataList)
                }
        } catch {
            return Single.error(error)
        }
    }
}
