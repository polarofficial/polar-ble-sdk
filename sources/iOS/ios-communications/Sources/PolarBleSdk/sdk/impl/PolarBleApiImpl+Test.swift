/// Copyright © 2026 Polar Electro Oy. All rights reserved.

import Foundation
import CoreBluetooth

extension PolarBleApiImpl: PolarTestApi {

    func getSpo2TestData(identifier: String, fromDate: Date, toDate: Date) async throws -> [PolarSpo2TestData] {
        if fromDate > toDate {
            throw PolarErrors.invalidArgument(description: "toDate cannot be before fromDate.")
        }
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
            throw PolarErrors.serviceNotFound
        }
        var results: [PolarSpo2TestData] = []
        let calendar = Calendar.current
        var currentDate = fromDate
        while currentDate <= toDate {
            for try await item in PolarTestUtils.readSpo2TestFromDayDirectory(client: client, date: currentDate) {
                results.append(item)
            }
            guard let next = calendar.date(byAdding: .day, value: 1, to: currentDate) else { break }
            currentDate = next
        }
        return results
    }
}
