/// Copyright © 2026 Polar Electro Oy. All rights reserved.

import Foundation
import CoreBluetooth
import RxSwift

extension PolarBleApiImpl: PolarTestApi {

    func getSpo2TestData(identifier: String, fromDate: Date, toDate: Date) -> Single<[PolarSpo2TestData]> {
        if fromDate > toDate {
            return Single.error(PolarErrors.invalidArgument(description: "toDate cannot be before fromDate."))
        }

        do {
            let session = try serviceClientUtils.sessionFtpClientReady(identifier)
            guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                return Single.error(PolarErrors.serviceNotFound)
            }

            var datesList = [Date]()
            let calendar = Calendar.current
            var currentDate = fromDate
            while currentDate <= toDate {
                datesList.append(currentDate)
                guard let next = calendar.date(byAdding: .day, value: 1, to: currentDate) else { break }
                currentDate = next
            }

            return Observable.from(datesList)
            .flatMap { date in
                PolarTestUtils.readSpo2TestFromDayDirectory(client: client, date: date)
            }
            .toArray()
        } catch {
            return Single.error(error)
        }
    }
}
