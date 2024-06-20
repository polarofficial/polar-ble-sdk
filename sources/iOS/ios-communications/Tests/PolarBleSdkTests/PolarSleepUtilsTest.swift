//
//  Copyright © 2024 Polar. All rights reserved.
//

import Foundation
import XCTest
import RxSwift
import RxTest

class PolarSleepUtilsTests: XCTestCase {

    var mockClient: MockBlePsFtpClient!

    override func setUpWithError() throws {
        mockClient = MockBlePsFtpClient()
    }

    override func tearDownWithError() throws {
        mockClient = nil
    }

    func testReadSleepDataFromDayDirectory_SuccessfulResponse() {
        // Arrange
        let date = Date()
        var snoozeTimeList = [PbLocalDateTime]()
        var device = PbDeviceId()
         
        snoozeTimeList.append(PolarSleepUtilsTests.createPbLocalDateTime(hour: 23, minute: 59, seconds: 59, millis: 59, day: 1, month: 2, year: 2525, timeZoneOffset: 2))
        device.deviceID = "C8D9G10F11H12"
        
        var proto = Data_PbSleepAnalysisResult()
        
        proto.sleepwakePhases = PolarSleepUtilsTests.buildSleepWakePhases()
        proto.sleepCycles = PolarSleepUtilsTests.buildSleepCycles()
        proto.snoozeTime = snoozeTimeList
        proto.sleepStartTime = PolarSleepUtilsTests.createPbLocalDateTime(hour: 23, minute: 45, seconds: 45, millis: 1, day: 1, month: 2, year: 2525, timeZoneOffset: 2)
        proto.sleepEndTime = PolarSleepUtilsTests.createPbLocalDateTime(hour: 7, minute: 5, seconds: 7, millis: 6, day: 2, month: 2, year: 2525, timeZoneOffset: 2)
        proto.lastModified = PolarSleepUtilsTests.createPbSystemDateTime(hour: 4, minute: 3, seconds: 2, millis: 1, day: 4, month: 3, year: 2525)
        proto.sleepGoalMinutes = 420
        proto.alarmTime = PolarSleepUtilsTests.createPbLocalDateTime(hour: 7, minute: 0, seconds: 0, millis: 0, day: 2, month: 2, year: 2525, timeZoneOffset: 2)
        proto.sleepStartOffsetSeconds = 1
        proto.sleepEndOffsetSeconds = 1
        proto.originalSleepRange = PolarSleepUtilsTests.createPbLocalDateTimeRange(
            startDateTime: PolarSleepUtilsTests.createPbLocalDateTime(hour: 23, minute: 59, seconds: 59, millis: 59, day: 1, month: 2, year: 2525, timeZoneOffset: 2),
            endDateTime: PolarSleepUtilsTests.createPbLocalDateTime(hour: 7, minute: 0, seconds: 0, millis: 0, day: 2, month: 2, year: 2525, timeZoneOffset: 2)
        )
        proto.batteryRanOut = false
        proto.recordingDevice = device
        proto.userSleepRating = PbSleepUserRating.pbSleptWell
        proto.sleepResultDate = PolarSleepUtilsTests.createPbDate(day: 4, month: 3, year: 2525)
        proto.createdTimestamp = PolarSleepUtilsTests.createPbSystemDateTime(hour: 1, minute: 2, seconds: 3, millis: 4, day: 2, month: 2, year: 2525)

        let responseData = try! proto.serializedData()

        mockClient.requestReturnValue = Single.just(responseData)

        // Act
        let mockSleepData: PolarSleepData.PolarSleepAnalysisResult
        do { try mockSleepData = PolarSleepUtilsTests.createPolarSleepAnalysisData()
        let expectation = XCTestExpectation(description: "Read sleep data from day directory")
        let disposable = PolarSleepUtils.readSleepFromDayDirectory(client: mockClient, date: date)
            .subscribe(onSuccess: { sleepData in
                // Assert
                XCTAssertEqual(sleepData.alarmTime, mockSleepData.alarmTime)
                XCTAssertEqual(sleepData.lastModified, mockSleepData.lastModified)
                XCTAssertEqual(sleepData.sleepStartTime, mockSleepData.sleepStartTime)
                XCTAssertEqual(sleepData.sleepEndTime, mockSleepData.sleepEndTime)
                XCTAssertEqual(sleepData.snoozeTime, mockSleepData.snoozeTime)
                XCTAssertEqual(sleepData.deviceId, mockSleepData.deviceId)
                XCTAssertEqual(sleepData.sleepGoalMinutes, mockSleepData.sleepGoalMinutes)
                XCTAssertEqual(sleepData.sleepResultDate, mockSleepData.sleepResultDate)
                XCTAssertEqual(sleepData.sleepStartOffsetSeconds, mockSleepData.sleepStartOffsetSeconds)
                XCTAssertEqual(sleepData.sleepEndOffsetSeconds, mockSleepData.sleepEndOffsetSeconds)
                XCTAssertEqual(sleepData.userSleepRating, mockSleepData.userSleepRating)
                XCTAssertEqual(sleepData.originalSleepRange?.startTime, mockSleepData.originalSleepRange?.startTime)
                XCTAssertEqual(sleepData.originalSleepRange?.endTime, mockSleepData.originalSleepRange?.endTime)
                XCTAssertEqual(sleepData.sleepCycles.first?.secondsFromSleepStart, mockSleepData.sleepCycles.first?.secondsFromSleepStart)
                XCTAssertEqual(sleepData.sleepCycles.first?.sleepDepthStart, mockSleepData.sleepCycles.first?.sleepDepthStart)
                XCTAssertEqual(sleepData.sleepWakePhases.first?.secondsFromSleepStart, mockSleepData.sleepWakePhases.first?.secondsFromSleepStart)
                XCTAssertEqual(sleepData.sleepWakePhases.first?.state, mockSleepData.sleepWakePhases.first?.state)
                expectation.fulfill()
            }, onFailure: { error in
                XCTFail("Error: \(error)")
            })
            
            // Assert
            wait(for: [expectation], timeout: 5)
            disposable.dispose()
        } catch let error {XCTFail()}
    }

    private static func buildSleepWakePhases() -> [Data_PbSleepWakePhase] {
        
        var sleepWakePhaseList = [Data_PbSleepWakePhase]()
        
        var sleepWakePhase = Data_PbSleepWakePhase()
        sleepWakePhase.secondsFromSleepStart = 1
        sleepWakePhase.sleepwakeState = Data_PbSleepWakeState.pbWake
        
        sleepWakePhaseList.append(sleepWakePhase)
        
        return sleepWakePhaseList
    }

    private static func buildSleepCycles() -> [Data_PbSleepCycle] {
        
        var sleepCycleList = [Data_PbSleepCycle]()
        
        var sleepCycle = Data_PbSleepCycle()
        sleepCycle.secondsFromSleepStart = 2
        sleepCycle.sleepDepthStart = 3.0
        
        sleepCycleList.append(sleepCycle)
        
        return sleepCycleList
    }

    private static func createPbLocalDateTime(hour: UInt32, minute: UInt32, seconds: UInt32, millis: UInt32, day: UInt32, month: UInt32, year: UInt32, timeZoneOffset: Int32) -> PbLocalDateTime {
        
        var localDateTime = PbLocalDateTime()
        localDateTime.time.hour = hour
        localDateTime.time.minute = minute
        localDateTime.time.seconds = seconds
        localDateTime.time.millis = millis
        localDateTime.date.day = day
        localDateTime.date.month = month
        localDateTime.date.year = year
        localDateTime.timeZoneOffset = timeZoneOffset
        
        return localDateTime
    }

    private static func createPbSystemDateTime(hour: UInt32, minute: UInt32, seconds: UInt32, millis: UInt32, day: UInt32, month: UInt32, year: UInt32) -> PbSystemDateTime {

        var pbSystemDateTime = PbSystemDateTime()

        pbSystemDateTime.date.day = day
        pbSystemDateTime.date.month = month
        pbSystemDateTime.date.year = year
        pbSystemDateTime.time.hour = hour
        pbSystemDateTime.time.minute = minute
        pbSystemDateTime.time.seconds = seconds
        pbSystemDateTime.time.millis = millis
        pbSystemDateTime.trusted = true

        return pbSystemDateTime
    }
    
    private static func createPbLocalDateTimeRange(startDateTime: PbLocalDateTime, endDateTime: PbLocalDateTime) -> PbLocalDateTimeRange {

        var pbLocalDateTimeRange = PbLocalDateTimeRange()

        pbLocalDateTimeRange.startTime = startDateTime
        pbLocalDateTimeRange.endTime = endDateTime

        return pbLocalDateTimeRange
    }

    private static func createPbDate(day: UInt32, month: UInt32, year: UInt32) -> PbDate {

        var date = PbDate()
        date.day = day
        date.month = month
        date.year = year

        return date
    }

    private static func createPolarSleepAnalysisData() throws -> PolarSleepData.PolarSleepAnalysisResult {

        var snoozeTimes = [Date]()
        var sleepStartTime = Date()
        var sleepEndTime = Date()
        var lastModified = Date()
        var snoozeTime = Date()
        var alarmTime = Date()
        var sleepCycles = [PolarSleepData.SleepCycle]()
        var sleepResultDate = Date()

        do {
            sleepStartTime = try createDate(hour: 23, minute: 45, second: 45, millis: 1, day: 1, month: 2, year: 2525)
            sleepEndTime = try createDate(hour: 7, minute: 5, second: 7, millis: 6, day: 2, month: 2, year: 2525)
            lastModified = try createDate(hour: 4, minute: 3, second: 2, millis: 1, day: 4, month: 3, year: 2525)
            snoozeTime = try createDate(hour: 23, minute: 59, second: 59, millis: 59, day: 1, month: 2, year: 2525)
            snoozeTimes.append(snoozeTime)
            alarmTime = try createDate(hour: 7, minute: 0, second: 0, millis: 0, day: 2, month: 2, year: 2525)
        } catch let error {
            XCTFail("Error, could not create sleep analysis data: \(error)")
        }

        var polarSleepAnalysisData = PolarSleepData.PolarSleepAnalysisResult.init(
            sleepStartTime: sleepStartTime,
            sleepEndTime: sleepEndTime,
            lastModified: lastModified,
            sleepGoalMinutes: 420,
            sleepWakePhases: [PolarSleepData.SleepWakePhase.init(secondsFromSleepStart: 1, state: .WAKE)],
            snoozeTime: snoozeTimes,
            alarmTime: alarmTime,
            sleepStartOffsetSeconds: 1,
            sleepEndOffsetSeconds: 1,
            userSleepRating: PolarSleepData.SleepRating.SLEPT_WELL,
            deviceId: "C8D9G10F11H12",
            batteryRanOut: false,
            sleepCycles: [PolarSleepData.SleepCycle.init(secondsFromSleepStart: 2, sleepDepthStart: Float(1.0))],
            sleepResultDate: sleepResultDate,
            originalSleepRange: PolarSleepData.OriginalSleepRange(startTime: sleepStartTime, endTime: sleepEndTime)
        )
        
        return polarSleepAnalysisData
    }

    private static func createDate(hour: Int, minute: Int, second: Int, millis: Int, day: Int, month: Int, year: Int) throws -> Date {

        var dateComponents = DateComponents()
        dateComponents.year = year
        dateComponents.month = month
        dateComponents.day = day
        dateComponents.hour = hour
        dateComponents.minute = minute
        dateComponents.second = second
        dateComponents.nanosecond = millis * 1000000

        let userCalendar = Calendar(identifier: .gregorian)

        return userCalendar.date(from: dateComponents)!
    }
}
