///  Copyright © 2024 Polar. All rights reserved.

import Foundation

public struct PolarActiveTimeData: Codable  {
    public let date: Date
    public let timeNonWear: PolarActiveTime
    public let timeSleep: PolarActiveTime
    public let timeSedentary: PolarActiveTime
    public let timeLightActivity: PolarActiveTime
    public let timeContinuousModerateActivity: PolarActiveTime
    public let timeIntermittentModerateActivity: PolarActiveTime
    public let timeContinuousVigorousActivity: PolarActiveTime
    public let timeIntermittentVigorousActivity: PolarActiveTime

    public init(date: Date,
                timeNonWear: PolarActiveTime = PolarActiveTime(),
                timeSleep: PolarActiveTime = PolarActiveTime(),
                timeSedentary: PolarActiveTime = PolarActiveTime(),
                timeLightActivity: PolarActiveTime = PolarActiveTime(),
                timeContinuousModerateActivity: PolarActiveTime = PolarActiveTime(),
                timeIntermittentModerateActivity: PolarActiveTime = PolarActiveTime(),
                timeContinuousVigorousActivity: PolarActiveTime = PolarActiveTime(),
                timeIntermittentVigorousActivity: PolarActiveTime = PolarActiveTime()) {
        self.date = date
        self.timeNonWear = timeNonWear
        self.timeSleep = timeSleep
        self.timeSedentary = timeSedentary
        self.timeLightActivity = timeLightActivity
        self.timeContinuousModerateActivity = timeContinuousModerateActivity
        self.timeIntermittentModerateActivity = timeIntermittentModerateActivity
        self.timeContinuousVigorousActivity = timeContinuousVigorousActivity
        self.timeIntermittentVigorousActivity = timeIntermittentVigorousActivity
    }
}

public struct PolarActiveTime: Codable {
    public let hours: Int
    public let minutes: Int
    public let seconds: Int
    public let millis: Int

    public init(hours: Int = 0, minutes: Int = 0, seconds: Int = 0, millis: Int = 0) {
        self.hours = hours
        self.minutes = minutes
        self.seconds = seconds
        self.millis = millis
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(hours*3600 + minutes*60 + seconds + millis/1000)
    }
}
