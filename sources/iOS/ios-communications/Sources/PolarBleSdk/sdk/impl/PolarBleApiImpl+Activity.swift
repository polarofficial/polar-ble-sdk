/// Copyright  2026 Polar Electro Oy. All rights reserved.

import Foundation
import CoreBluetooth

/// Implementation of activity data API methods (steps, distance, calories, etc.)
extension PolarBleApiImpl {

    func getSteps(identifier: String, fromDate: Date, toDate: Date) async throws -> [PolarStepsData] {
        logApiCall("getSteps", ("identifier", identifier), ("fromDate", fromDate), ("toDate", toDate))
        guard toDate >= fromDate else {
            BleLogger.error("getSteps: Invalid date range: toDate \(toDate) is before fromDate \(fromDate)")
            throw PolarErrors.invalidArgument(description: "toDate must be greater than or equal to fromDate")
        }
        
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        var datesList = [Date]()
        let startOfFrom = PolarTimeUtils.utcCalendar.startOfDay(for: fromDate)
        let startOfTo = PolarTimeUtils.utcCalendar.startOfDay(for: toDate)
        var currentDate = startOfFrom
        while currentDate <= startOfTo {
            datesList.append(currentDate)
            guard let nextDate = PolarTimeUtils.utcCalendar.date(byAdding: .day, value: 1, to: currentDate) else { break }
            currentDate = nextDate
        }
        var results = [PolarStepsData]()
        for date in datesList {
            let result = try await PolarActivityUtils.readStepsFromDayDirectory(client: client, date: date)
            results.append(PolarStepsData(date: date, steps: result))
        }
        return results
    }

    func getDistance(identifier: String, fromDate: Date, toDate: Date) async throws -> [PolarDistanceData] {        
        logApiCall("getDistance", ("identifier", identifier), ("fromDate", fromDate), ("toDate", toDate))
        
        guard toDate >= fromDate else {
            BleLogger.error("getDistance: Invalid date range: toDate \(toDate) is before fromDate \(fromDate)")
            throw PolarErrors.invalidArgument(description: "toDate must be greater than or equal to fromDate")
        }
        
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        let calendar = Calendar.current
        var datesList = [Date]()
        var currentDate = fromDate
        while currentDate <= toDate {
            datesList.append(currentDate)
            if let nextDate = calendar.date(byAdding: .day, value: 1, to: currentDate) { currentDate = nextDate } else { break }
        }
        var results = [PolarDistanceData]()
        for date in datesList {
            let result = try await PolarActivityUtils.readDistanceFromDayDirectory(client: client, date: date)
            results.append(PolarDistanceData(date: date, distanceMeters: result))
        }
        return results
    }

    func get247HrSamples(identifier: String, fromDate: Date, toDate: Date) async throws -> [Polar247HrSamplesData] {
        logApiCall("get247HrSamples", ("identifier", identifier), ("fromDate", fromDate), ("toDate", toDate))
        guard toDate >= fromDate else {
            BleLogger.error("get247HrSamples: Invalid date range: toDate \(toDate) is before fromDate \(fromDate)")
            throw PolarErrors.invalidArgument(description: "toDate must be greater than or equal to fromDate")
        }
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        return try await PolarAutomaticSamplesUtils.read247HrSamples(client: client, fromDate: fromDate, toDate: toDate)
    }

    func get247PPiSamples(identifier: String, fromDate: Date, toDate: Date) async throws -> [Polar247PPiSamplesData] {
        logApiCall("get247PPiSamples", ("identifier", identifier), ("fromDate", fromDate), ("toDate", toDate))
        guard toDate >= fromDate else {
            BleLogger.error("get247PPiSamples: Invalid date range: toDate \(toDate) is before fromDate \(fromDate)")
            throw PolarErrors.invalidArgument(description: "toDate must be greater than or equal to fromDate")
        }
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        return try await PolarAutomaticSamplesUtils.read247PPiSamples(client: client, fromDate: fromDate, toDate: toDate)
    }

    func getNightlyRecharge(identifier: String, fromDate: Date, toDate: Date) async throws -> [PolarNightlyRechargeData] {
        logApiCall("getNightlyRecharge", ("identifier", identifier), ("fromDate", fromDate), ("toDate", toDate))
        guard toDate >= fromDate else {
            BleLogger.error("getNightlyRecharge: Invalid date range: toDate \(toDate) is before fromDate \(fromDate)")
            throw PolarErrors.invalidArgument(description: "toDate must be greater than or equal to fromDate")
        }
        
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        let calendar = Calendar.current
        var datesList = [Date]()
        var currentDate = fromDate
        while currentDate <= toDate {
            datesList.append(currentDate)
            if let nextDate = calendar.date(byAdding: .day, value: 1, to: currentDate) { currentDate = nextDate } else { break }
        }
        var results = [PolarNightlyRechargeData]()
        for date in datesList {
            if let result = await PolarNightlyRechargeUtils.readNightlyRechargeData(client: client, date: date) {
                results.append(result)
            }
        }
        return results
    }
    
    func getCalories(identifier: String, fromDate: Date, toDate: Date, caloriesType: CaloriesType) async throws -> [PolarCaloriesData] {
        logApiCall("getCalories", ("identifier", identifier), ("fromDate", fromDate), ("toDate", toDate), ("caloriesType", caloriesType))
        guard toDate >= fromDate else {
            BleLogger.error("getCalories: Invalid date range: toDate \(toDate) is before fromDate \(fromDate)")
            throw PolarErrors.invalidArgument(description: "toDate must be greater than or equal to fromDate")
        }
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        let calendar = Calendar.current
        var datesList = [Date]()
        var currentDate = fromDate
        while currentDate <= toDate {
            datesList.append(currentDate)
            if let nextDate = calendar.date(byAdding: .day, value: 1, to: currentDate) { currentDate = nextDate } else { break }
        }
        var results = [PolarCaloriesData]()
        for date in datesList {
            let result = try await PolarActivityUtils.readCaloriesFromDayDirectory(client: client, date: date, caloriesType: caloriesType)
            results.append(PolarCaloriesData(date: date, calories: result))
        }
        return results
    }

    func getActivitySampleData(identifier: String, fromDate: Date, toDate: Date) async throws -> [PolarActivityDayData] {
        logApiCall("getActivitySampleData", ("identifier", identifier), ("fromDate", fromDate), ("toDate", toDate))
        guard toDate >= fromDate else {
            BleLogger.error("getActivitySampleData: Invalid date range: toDate \(toDate) is before fromDate \(fromDate)")
            throw PolarErrors.invalidArgument(description: "toDate must be greater than or equal to fromDate")
        }
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        let calendar = Calendar.current
        var datesList = [Date]()
        var currentDate = fromDate
        while currentDate <= toDate {
            datesList.append(currentDate)
            if let nextDate = calendar.date(byAdding: .day, value: 1, to: currentDate) { currentDate = nextDate } else { break }
        }
        var results = [PolarActivityDayData]()
        for date in datesList {
            let result = try await PolarActivityUtils.readActivitySamplesDataFromDayDirectory(client: client, date: date)
            results.append(result)
        }
        return results
    }

    func getDailySummaryData(identifier: String, fromDate: Date, toDate: Date) async throws -> [PolarDailySummary] {
        logApiCall("getDailySummaryData", ("identifier", identifier), ("fromDate", fromDate), ("toDate", toDate))
        guard toDate >= fromDate else {
            BleLogger.error("getDailySummaryData: Invalid date range: toDate \(toDate) is before fromDate \(fromDate)")
            throw PolarErrors.invalidArgument(description: "toDate must be greater than or equal to fromDate")
        }
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        let calendar = Calendar.current
        var datesList = [Date]()
        var currentDate = fromDate
        while currentDate <= toDate {
            datesList.append(currentDate)
            if let nextDate = calendar.date(byAdding: .day, value: 1, to: currentDate) { currentDate = nextDate } else { break }
        }
        var results = [PolarDailySummary]()
        for date in datesList {
            if let result = try await PolarActivityUtils.readDailySummaryDataFromDayDirectory(client: client, date: date) {
                results.append(result)
            }
        }
        return results
    }

    func getActiveTime(identifier: String, fromDate: Date, toDate: Date) async throws -> [PolarActiveTimeData] {
        logApiCall("getActiveTime", ("identifier", identifier), ("fromDate", fromDate), ("toDate", toDate))
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        let calendar = Calendar.current
        var datesList = [Date]()
        var currentDate = fromDate
        while currentDate <= toDate {
            datesList.append(currentDate)
            if let nextDate = calendar.date(byAdding: .day, value: 1, to: currentDate) { currentDate = nextDate } else { break }
        }
        var results = [PolarActiveTimeData]()
        for date in datesList {
            let activeTime = try await PolarActivityUtils.readActiveTimeFromDayDirectory(client: client, date: date)
            results.append(PolarActiveTimeData(date: date, timeNonWear: activeTime.timeNonWear, timeSleep: activeTime.timeSleep, timeSedentary: activeTime.timeSedentary, timeLightActivity: activeTime.timeLightActivity, timeContinuousModerateActivity: activeTime.timeContinuousModerateActivity, timeIntermittentModerateActivity: activeTime.timeIntermittentModerateActivity, timeContinuousVigorousActivity: activeTime.timeContinuousVigorousActivity, timeIntermittentVigorousActivity: activeTime.timeIntermittentVigorousActivity))
        }
        return results
    }
}
