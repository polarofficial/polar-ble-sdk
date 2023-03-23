//  Copyright © 2022 Polar. All rights reserved.

import Foundation

/// SDK internal helper for Time related conversions.
internal class PolarTimeUtils {
    private static let nanoToMillisMultiplier: Int = 1000000
    private static let secondsToMinutesMultiplier: Int = 60
    
    static func dateToPbPftpSetLocalTime(time: Date, zone: TimeZone) -> Protocol_PbPFtpSetLocalTimeParams? {
        guard let dateTimeBuilder = pbDateTimeBuilder(time, zone) else {
            BleLogger.error("Error in dateToPbPftpSetLocalTime: pbDateTime building failed ")
            return nil
        }
        var params = Protocol_PbPFtpSetLocalTimeParams()
        params.date = dateTimeBuilder.0
        params.time = dateTimeBuilder.1
        params.tzOffset = Int32(secondsToMinutes(seconds: zone.secondsFromGMT()))
        return params
    }
    
    static func dateToPbPftpSetSystemTime(time: Date) -> Protocol_PbPFtpSetSystemTimeParams? {
        
        guard let timeZone = TimeZone(secondsFromGMT: 0) else {
            BleLogger.error("Error in dateToPbPftpSetSystemTime: timeZone building failed ")
            return nil
        }
        
        guard let dateTimeBuilder = pbDateTimeBuilder(time, timeZone) else {
            BleLogger.error("Error in dateToPbPftpSetSystemTime: pbDateTime building failed ")
            return nil
        }
        
        var pbPftpSystemDateTime = Protocol_PbPFtpSetSystemTimeParams()
        pbPftpSystemDateTime.date = dateTimeBuilder.0
        pbPftpSystemDateTime.time = dateTimeBuilder.1
        pbPftpSystemDateTime.trusted = true
        return pbPftpSystemDateTime
    }
    
    
    static func dateFromPbPftpLocalDateTime(_ pbPFtpSetLocalTimeParams: Protocol_PbPFtpSetLocalTimeParams?) throws -> Date {
        
        guard let year = pbPFtpSetLocalTimeParams?.date.year,
              let month = pbPFtpSetLocalTimeParams?.date.month,
              let day = pbPFtpSetLocalTimeParams?.date.day,
              let hour = pbPFtpSetLocalTimeParams?.time.hour,
              let minute = pbPFtpSetLocalTimeParams?.time.minute,
              let second = pbPFtpSetLocalTimeParams?.time.seconds,
              let millis = pbPFtpSetLocalTimeParams?.time.millis,
              let timeZoneOffsetInMinutes = pbPFtpSetLocalTimeParams?.tzOffset else {
            
            BleLogger.error("dateFromPbPftpLocalDateTime failed, pbPFtpSetLocalTimeParams is missing parameters")
            throw PolarErrors.dateTimeFormatFailed(description: "dateFromPbPftpLocalDateTime failed, pbPFtpSetLocalTimeParams is missing parameters")
        }
        
        var dateComponents = DateComponents()
        dateComponents.year = Int(year)
        dateComponents.month = Int(month)
        dateComponents.day = Int(day)
        dateComponents.hour = Int(hour)
        dateComponents.minute = Int(minute)
        dateComponents.second = Int(second)
        dateComponents.nanosecond = millisToNanos(milliseconds: Int(millis))
        dateComponents.timeZone = try timeZoneFromMinutes(Int(timeZoneOffsetInMinutes))
        
        let userCalendar = Calendar(identifier: .gregorian)
        guard let dateTime = userCalendar.date(from: dateComponents) else {
            BleLogger.error("dateFromPbPftpLocalDateTime failed, cannot create date from dateComponents")
            throw PolarErrors.dateTimeFormatFailed(description: "dateFromPbPftpLocalDateTime failed, cannot create date from dateComponents")
        }
        return dateTime
    }
    
    static func nanosToMillis(nanoseconds: Int) -> Int {
        return Int(round(Double(nanoseconds) / Double(nanoToMillisMultiplier)))
    }
    
    private static func millisToNanos(milliseconds: Int) -> Int {
        return milliseconds * nanoToMillisMultiplier
    }
    private static func secondsToMinutes(seconds: Int) -> Int {
        return Int(round(Double(seconds) / Double(secondsToMinutesMultiplier)))
    }
    
    private static func minutesToSeconds(minutes: Int) -> Int {
        return minutes * secondsToMinutesMultiplier
    }
    
    private static func timeZoneFromMinutes(_ minutes: Int) throws -> TimeZone {
        let secondsFromGmt = minutesToSeconds(minutes: minutes)
        if let timeZone = TimeZone.init(secondsFromGMT: secondsFromGmt) {
            return timeZone
        } else {
            throw PolarErrors.dateTimeFormatFailed(description: "Could not create valid time zone from \(minutes)min time zone offset")
        }
    }
    
    private static func pbDateTimeBuilder(_ date: Date, _ timeZone: TimeZone) -> (PbDate, PbTime)? {
        var calendar = Calendar(identifier: Calendar.Identifier.iso8601)
        calendar.timeZone = timeZone
        let dateComponents = calendar.dateComponents([.day, .month, .year, .hour, .minute, .second, .nanosecond], from: date)
        
        if let year = dateComponents.year,
           let month = dateComponents.month,
           let day = dateComponents.day,
           let hour = dateComponents.hour,
           let minute = dateComponents.minute,
           let seconds = dateComponents.second {
            
            var pbDate = PbDate()
            pbDate.year = UInt32(year)
            pbDate.month = UInt32(month)
            pbDate.day = UInt32(day)
            
            var pbTime = PbTime()
            pbTime.hour = UInt32(hour)
            pbTime.minute = UInt32(minute)
            pbTime.seconds = UInt32(seconds)
            if (dateComponents.nanosecond ?? 0 > 0) {
                pbTime.millis = UInt32(nanosToMillis(nanoseconds: dateComponents.nanosecond!))
            } else {
                pbTime.millis = 0
            }
            return (pbDate, pbTime)
        } else {
            return nil
        }
    }
}
