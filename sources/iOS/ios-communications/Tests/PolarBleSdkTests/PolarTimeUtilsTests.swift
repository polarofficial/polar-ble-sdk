//  Copyright © 2022 Polar. All rights reserved.

import XCTest
import CoreBluetooth
@testable import PolarBleSdk

class PolarTimeUtilsTests: XCTestCase {
    func testConversionToPftpSystemTimeFromTimeZoneGMT() throws {
        // Arrange
        let localTime = "2022-01-01T11:59:01.999+00:00"
        let utcDateComponents = try getDateComponentsInUTC(localTime)
        let expectedMillis = PolarTimeUtils.nanosToMillis(nanoseconds: utcDateComponents.nanosecond!)
        let date = try dateFromISO8601(localTime)
        
        // Act
        let result = PolarTimeUtils.dateToPbPftpSetSystemTime(time: date)
        
        // Assert
        guard let result = result else {
            XCTAssert(false, "result is nil")
            return
        }
        XCTAssertTrue(result.hasDate)
        XCTAssertTrue(result.hasTime)
        XCTAssertTrue(result.trusted)
        XCTAssertEqual(UInt32(utcDateComponents.year!), result.date.year)
        XCTAssertEqual(UInt32(utcDateComponents.month!), result.date.month)
        XCTAssertEqual(UInt32(utcDateComponents.day!), result.date.day)
        XCTAssertEqual(UInt32(utcDateComponents.hour!), result.time.hour)
        XCTAssertEqual(UInt32(utcDateComponents.minute!), result.time.minute)
        XCTAssertEqual(UInt32(utcDateComponents.second!), result.time.seconds)
        XCTAssertEqual(UInt32(expectedMillis), result.time.millis)
        
    }
    
    func testConversionToPftpSystemTimeFromTimeZoneGMT1() throws {
        // Arrange
        let localTime = "2022-01-01T11:59:01.999+01:00"
        let utcDateComponents = try getDateComponentsInUTC(localTime)
        let expectedMillis = PolarTimeUtils.nanosToMillis(nanoseconds: utcDateComponents.nanosecond!)
        let date = try dateFromISO8601(localTime)
        
        // Act
        let result = PolarTimeUtils.dateToPbPftpSetSystemTime(time: date)
        
        // Assert
        guard let result = result else {
            XCTAssert(false, "result is nil")
            return
        }
        XCTAssertTrue(result.hasDate)
        XCTAssertTrue(result.hasTime)
        XCTAssertTrue(result.trusted)
        XCTAssertEqual(UInt32(utcDateComponents.year!), result.date.year)
        XCTAssertEqual(UInt32(utcDateComponents.month!), result.date.month)
        XCTAssertEqual(UInt32(utcDateComponents.day!), result.date.day)
        XCTAssertEqual(UInt32(utcDateComponents.hour!), result.time.hour)
        XCTAssertEqual(UInt32(utcDateComponents.minute!), result.time.minute)
        XCTAssertEqual(UInt32(utcDateComponents.second!), result.time.seconds)
        XCTAssertEqual(UInt32(expectedMillis), result.time.millis)
    }
    
    func testConversionToPftpSystemTimeFromTimeZoneGmtMinus1() throws {
        // Arrange
        let localTime = "2022-01-01T11:59:01.999-01:00"
        let utcDateComponents = try getDateComponentsInUTC(localTime)
        let expectedMillis = PolarTimeUtils.nanosToMillis(nanoseconds: utcDateComponents.nanosecond!)
        let date = try dateFromISO8601(localTime)
        
        // Act
        let result = PolarTimeUtils.dateToPbPftpSetSystemTime(time: date)
        
        // Assert
        guard let result = result else {
            XCTAssert(false, "result is nil")
            return
        }
        XCTAssertTrue(result.hasDate)
        XCTAssertTrue(result.hasTime)
        XCTAssertTrue(result.trusted)
        XCTAssertEqual(UInt32(utcDateComponents.year!), result.date.year)
        XCTAssertEqual(UInt32(utcDateComponents.month!), result.date.month)
        XCTAssertEqual(UInt32(utcDateComponents.day!), result.date.day)
        XCTAssertEqual(UInt32(utcDateComponents.hour!), result.time.hour)
        XCTAssertEqual(UInt32(utcDateComponents.minute!), result.time.minute)
        XCTAssertEqual(UInt32(utcDateComponents.second!), result.time.seconds)
        XCTAssertEqual(UInt32(expectedMillis), result.time.millis)
    }
    
    func testConversionToPftpSystemTimeWhenLocalTimeIsTomorrow() throws {
        // Arrange
        let localTime = "2022-01-01T01:59:01.099+03:00"
        let utcDateComponents = try getDateComponentsInUTC(localTime)
        let expectedMillis = PolarTimeUtils.nanosToMillis(nanoseconds: utcDateComponents.nanosecond!)
        let date = try dateFromISO8601(localTime)
        
        // Act
        let result = PolarTimeUtils.dateToPbPftpSetSystemTime(time: date)
        
        // Assert
        guard let result = result else {
            XCTAssert(false, "result is nil")
            return
        }
        XCTAssertTrue(result.hasDate)
        XCTAssertTrue(result.hasTime)
        XCTAssertTrue(result.trusted)
        XCTAssertEqual(UInt32(utcDateComponents.year!), result.date.year)
        XCTAssertEqual(UInt32(utcDateComponents.month!), result.date.month)
        XCTAssertEqual(UInt32(utcDateComponents.day!), result.date.day)
        XCTAssertEqual(UInt32(utcDateComponents.hour!), result.time.hour)
        XCTAssertEqual(UInt32(utcDateComponents.minute!), result.time.minute)
        XCTAssertEqual(UInt32(utcDateComponents.second!), result.time.seconds)
        XCTAssertEqual(UInt32(expectedMillis), result.time.millis)
    }
    
    func testConversionToPftpSystemTimeWhenLocalTimeIsLeapYear() throws {
        // Arrange
        let localTime = "2024-02-29T22:59:01.001+01:00"
        let utcDateComponents = try getDateComponentsInUTC(localTime)
        let expectedMillis = PolarTimeUtils.nanosToMillis(nanoseconds: utcDateComponents.nanosecond!)
        let date = try dateFromISO8601(localTime)
        
        // Act
        let result = PolarTimeUtils.dateToPbPftpSetSystemTime(time: date)
        
        // Assert
        guard let result = result else {
            XCTAssert(false, "result is nil")
            return
        }
        XCTAssertTrue(result.hasDate)
        XCTAssertTrue(result.hasTime)
        XCTAssertTrue(result.trusted)
        XCTAssertEqual(UInt32(utcDateComponents.year!), result.date.year)
        XCTAssertEqual(UInt32(utcDateComponents.month!), result.date.month)
        XCTAssertEqual(UInt32(utcDateComponents.day!), result.date.day)
        XCTAssertEqual(UInt32(utcDateComponents.hour!), result.time.hour)
        XCTAssertEqual(UInt32(utcDateComponents.minute!), result.time.minute)
        XCTAssertEqual(UInt32(utcDateComponents.second!), result.time.seconds)
        XCTAssertEqual(UInt32(expectedMillis), result.time.millis)
    }
    
    
    func testConversionToPftpSystemTimeWhenLocalTimeIsYesterday() throws {
        // Arrange
        let localTime = "2021-12-31T22:59:01.999-03:00"
        let utcDateComponents = try getDateComponentsInUTC(localTime)
        let expectedMillis = PolarTimeUtils.nanosToMillis(nanoseconds: utcDateComponents.nanosecond!)
        let date = try dateFromISO8601(localTime)
        
        // Act
        let result = PolarTimeUtils.dateToPbPftpSetSystemTime(time: date)
        
        // Assert
        guard let result = result else {
            XCTAssert(false, "result is nil")
            return
        }
        XCTAssertTrue(result.hasDate)
        XCTAssertTrue(result.hasTime)
        XCTAssertTrue(result.trusted)
        XCTAssertEqual(UInt32(utcDateComponents.year!), result.date.year)
        XCTAssertEqual(UInt32(utcDateComponents.month!), result.date.month)
        XCTAssertEqual(UInt32(utcDateComponents.day!), result.date.day)
        XCTAssertEqual(UInt32(utcDateComponents.hour!), result.time.hour)
        XCTAssertEqual(UInt32(utcDateComponents.minute!), result.time.minute)
        XCTAssertEqual(UInt32(utcDateComponents.second!), result.time.seconds)
        XCTAssertEqual(UInt32(expectedMillis), result.time.millis)
    }
    
    func testDateAndTimeZoneConversionToPftpLocalTime_timeZoneGMT() throws {
        // Arrange
        let timeZone = TimeZone.init(secondsFromGMT: 0)
        var dateComponents = DateComponents()
        dateComponents.year = 2022
        dateComponents.month = 1
        dateComponents.day = 1
        dateComponents.hour = 11
        dateComponents.minute = 59
        dateComponents.second = 1
        dateComponents.nanosecond = 0
        dateComponents.timeZone = timeZone
        let date = Calendar(identifier: .gregorian).date(from: dateComponents)
        
        // Act
        let result = PolarTimeUtils.dateToPbPftpSetLocalTime(time: date!, zone: timeZone!)
        
        // Assert
        guard let result = result else {
            XCTAssert(false, "result is nil")
            return
        }
        XCTAssertTrue(result.hasDate)
        XCTAssertTrue(result.hasTime)
        XCTAssertTrue(result.hasTzOffset)
        XCTAssertEqual(UInt32(dateComponents.year!), result.date.year)
        XCTAssertEqual(UInt32(dateComponents.month!), result.date.month)
        XCTAssertEqual(UInt32(dateComponents.day!), result.date.day)
        XCTAssertEqual(UInt32(dateComponents.hour!), result.time.hour)
        XCTAssertEqual(UInt32(dateComponents.minute!), result.time.minute)
        XCTAssertEqual(UInt32(dateComponents.second!), result.time.seconds)
        XCTAssertEqual(UInt32(dateComponents.nanosecond!), result.time.millis)
        
        let timeZoneInMinutes = ((timeZone!.secondsFromGMT() % 3600) / 60)
        XCTAssertEqual(Int32(timeZoneInMinutes), result.tzOffset)
    }
    
    func testDateAndTimeZoneConversionToPftpLocalTime_timeZoneGMT1() throws {
        // Arrange
        let minutesFromGMT = 1 * 60 //GMT+1hour
        let secondsFromGMT = minutesFromGMT * 60
        let timeZone = TimeZone.init(secondsFromGMT: secondsFromGMT)
        var dateComponents = DateComponents()
        dateComponents.year = 2022
        dateComponents.month = 12
        dateComponents.day = 30
        dateComponents.hour = 11
        dateComponents.minute = 59
        dateComponents.second = 1
        dateComponents.nanosecond = 0
        dateComponents.timeZone = timeZone
        
        let date = Calendar(identifier: .gregorian).date(from: dateComponents)
        // Act
        let result = PolarTimeUtils.dateToPbPftpSetLocalTime(time: date!, zone: timeZone!)
        
        // Assert
        guard let result = result else {
            XCTAssert(false, "result is nil")
            return
        }
        XCTAssertTrue(result.hasDate)
        XCTAssertTrue(result.hasTime)
        XCTAssertTrue(result.hasTzOffset)
        XCTAssertEqual(UInt32(dateComponents.year!), result.date.year)
        XCTAssertEqual(UInt32(dateComponents.month!), result.date.month)
        XCTAssertEqual(UInt32(dateComponents.day!), result.date.day)
        XCTAssertEqual(UInt32(dateComponents.hour!), result.time.hour)
        XCTAssertEqual(UInt32(dateComponents.minute!), result.time.minute)
        XCTAssertEqual(UInt32(dateComponents.second!), result.time.seconds)
        XCTAssertEqual(UInt32(dateComponents.nanosecond!), result.time.millis)
        
        XCTAssertEqual(Int32(minutesFromGMT), result.tzOffset)
    }
    
    func testDateAndTimeZoneConversionToPftpLocalTime_timeZoneGMTMinus1() throws {
        // Arrange
        let minutesFromGMT = -1 * 60 //GMT-1hour
        let secondsFromGMT = minutesFromGMT * 60
        let timeZone = TimeZone.init(secondsFromGMT: secondsFromGMT)
        var dateComponents = DateComponents()
        dateComponents.year = 2022
        dateComponents.month = 12
        dateComponents.day = 30
        dateComponents.hour = 11
        dateComponents.minute = 59
        dateComponents.second = 1
        let milliSeconds = 100 // 100ms
        let nanoSeconds = milliSeconds * 1000 * 1000
        dateComponents.nanosecond = nanoSeconds
        dateComponents.timeZone = timeZone
        
        let date = Calendar(identifier: .gregorian).date(from: dateComponents)
        
        // Act
        let result = PolarTimeUtils.dateToPbPftpSetLocalTime(time: date!, zone: timeZone!)
        
        // Assert
        guard let result = result else {
            XCTAssert(false, "result is nil")
            return
        }
        XCTAssertTrue(result.hasDate)
        XCTAssertTrue(result.hasTime)
        XCTAssertTrue(result.hasTzOffset)
        XCTAssertEqual(UInt32(dateComponents.year!), result.date.year)
        XCTAssertEqual(UInt32(dateComponents.month!), result.date.month)
        XCTAssertEqual(UInt32(dateComponents.day!), result.date.day)
        XCTAssertEqual(UInt32(dateComponents.hour!), result.time.hour)
        XCTAssertEqual(UInt32(dateComponents.minute!), result.time.minute)
        XCTAssertEqual(UInt32(dateComponents.second!), result.time.seconds)
        XCTAssertEqual(UInt32(milliSeconds), result.time.millis)
        XCTAssertEqual(Int32(minutesFromGMT), result.tzOffset)
    }
    
    func testDateAndTimeZoneConversionToPftpLocalTime_timeZoneGMT14() throws {
        
        // Arrange
        let minutesFromGMT = 14 * 60 //GMT+14hour
        let secondsFromGMT = minutesFromGMT * 60
        let timeZone = TimeZone.init(secondsFromGMT: secondsFromGMT)
        var dateComponents = DateComponents()
        dateComponents.year = 2022
        dateComponents.month = 12
        dateComponents.day = 30
        dateComponents.hour = 11
        dateComponents.minute = 59
        dateComponents.second = 1
        let milliSeconds = 999 // 999ms
        let nanoSeconds = milliSeconds * 1000 * 1000
        dateComponents.nanosecond = nanoSeconds
        dateComponents.timeZone = timeZone
        
        let date = Calendar(identifier: .gregorian).date(from: dateComponents)
        
        // Act
        let result = PolarTimeUtils.dateToPbPftpSetLocalTime(time: date!, zone: timeZone!)
        
        // Assert
        guard let result = result else {
            XCTAssert(false, "result is nil")
            return
        }
        XCTAssertTrue(result.hasDate)
        XCTAssertTrue(result.hasTime)
        XCTAssertTrue(result.hasTzOffset)
        XCTAssertEqual(UInt32(dateComponents.year!), result.date.year)
        XCTAssertEqual(UInt32(dateComponents.month!), result.date.month)
        XCTAssertEqual(UInt32(dateComponents.day!), result.date.day)
        XCTAssertEqual(UInt32(dateComponents.hour!), result.time.hour)
        XCTAssertEqual(UInt32(dateComponents.minute!), result.time.minute)
        XCTAssertEqual(UInt32(dateComponents.second!), result.time.seconds)
        XCTAssertEqual(UInt32(milliSeconds), result.time.millis)
        XCTAssertEqual(Int32(minutesFromGMT), result.tzOffset)
    }
    
    func testDateAndTimeZoneConversionToPftpLocalTime_timeZoneGMTMinus12() throws {
        
        // Arrange
        let minutesFromGMT = -12 * 60 //GMT-12hour
        let secondsFromGMT = minutesFromGMT * 60
        let timeZone = TimeZone.init(secondsFromGMT: secondsFromGMT)
        var dateComponents = DateComponents()
        dateComponents.year = 2022
        dateComponents.month = 12
        dateComponents.day = 30
        dateComponents.hour = 11
        dateComponents.minute = 59
        dateComponents.second = 1
        let milliSeconds = 999 // 999ms
        let nanoSeconds = milliSeconds * 1000 * 1000
        dateComponents.nanosecond = nanoSeconds
        dateComponents.timeZone = timeZone
        
        let date = Calendar(identifier: .gregorian).date(from: dateComponents)
        
        // Act
        let result = PolarTimeUtils.dateToPbPftpSetLocalTime(time: date!, zone: timeZone!)
        
        // Assert
        guard let result = result else {
            XCTAssert(false, "result is nil")
            return
        }
        XCTAssertTrue(result.hasDate)
        XCTAssertTrue(result.hasTime)
        XCTAssertTrue(result.hasTzOffset)
        XCTAssertEqual(UInt32(dateComponents.year!), result.date.year)
        XCTAssertEqual(UInt32(dateComponents.month!), result.date.month)
        XCTAssertEqual(UInt32(dateComponents.day!), result.date.day)
        XCTAssertEqual(UInt32(dateComponents.hour!), result.time.hour)
        XCTAssertEqual(UInt32(dateComponents.minute!), result.time.minute)
        XCTAssertEqual(UInt32(dateComponents.second!), result.time.seconds)
        XCTAssertEqual(UInt32(milliSeconds), result.time.millis)
        XCTAssertEqual(Int32(minutesFromGMT), result.tzOffset)
    }
    
    private func getDateComponentsInUTC(_ iso8061: String) throws -> DateComponents {
        var calendar = Calendar(identifier: Calendar.Identifier.iso8601)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        return calendar.dateComponents([.day, .month, .year, .hour, .minute, .second, .nanosecond], from: try dateFromISO8601(iso8061))
    }
    
    private func dateFromISO8601(_ iso8061: String) throws -> Date  {
        let dateFormatter = ISO8601DateFormatter()
        dateFormatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds, .withTimeZone]
        guard let date = dateFormatter.date(from: iso8061) else {
            throw TestError.dateConversionFromISO8601Error
        }
        return date
    }
}

enum TestError: Error {
    case dateConversionFromISO8601Error
}
