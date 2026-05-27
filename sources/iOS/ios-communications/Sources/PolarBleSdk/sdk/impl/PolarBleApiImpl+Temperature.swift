/// Copyright © 2025 Polar Electro Oy. All rights reserved.

import Foundation
import CoreBluetooth

/// Implementation of PolarTemperatureApi
extension PolarBleApiImpl: PolarTemperatureApi {

    func getSkinTemperature(identifier: String, fromDate: Date, toDate: Date) async throws -> [PolarSkinTemperatureData.PolarSkinTemperatureResult] {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
            throw PolarErrors.serviceNotFound
        }
        var results: [PolarSkinTemperatureData.PolarSkinTemperatureResult] = []
        let calendar = Calendar.current
        var currentDate = fromDate
        while currentDate <= toDate {
            if let skinTemp = await PolarSkinTemperatureUtils.readSkinTemperatureData(client: client, date: currentDate) {
                results.append(skinTemp)
            }
            currentDate = calendar.date(byAdding: .day, value: 1, to: currentDate)!
        }
        return results
    }
}
