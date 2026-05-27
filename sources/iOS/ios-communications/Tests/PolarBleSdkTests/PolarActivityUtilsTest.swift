//  Copyright © 2024 Polar. All rights reserved.

import Foundation
import XCTest
@testable import PolarBleSdk

class PolarActivityUtilsTests: XCTestCase {

    var mockClient: MockBlePsFtpClient!
    
    override func setUpWithError() throws {
        mockClient = MockBlePsFtpClient(gattServiceTransmitter: MockPolarGattServiceTransmitter())
    }

    override func tearDownWithError() throws {
        mockClient = nil
    }

    func testReadStepsFromDayDirectory_SuccessfulResponse() async throws {
        // Arrange
        let mockRecordingDirectoryContent = try Protocol_PbPFtpDirectory.with {
            $0.entries = [Protocol_PbPFtpEntry.with { $0.name = "ASAMPL.BPB"; $0.size = 123 }]
        }.serializedData()
        mockClient.requestReturnValues.append(.success(mockRecordingDirectoryContent))
        
        let date = Date()
        var proto = Data_PbActivitySamples()
        proto.stepsSamples = [10000, 5000, 8000]
        mockClient.requestReturnValues.append(.success(try proto.serializedData()))
        
        // Act
        let steps = try await PolarActivityUtils.readStepsFromDayDirectory(client: mockClient, date: date)
        
        // Assert
        XCTAssertEqual(steps, 23000)
    }

    func testReadStepsFromDayDirectory_ActivityFileNotFound() async throws {
        // Arrange
        let mockRecordingDirectoryContent = try Protocol_PbPFtpDirectory.with {
            $0.entries = []
        }.serializedData()
        mockClient.requestReturnValues.append(.success(mockRecordingDirectoryContent))
        mockClient.requestReturnValues.append(.failure(NSError(domain: "File not found", code: 103, userInfo: nil)))
        
        // Act
        let steps = try await PolarActivityUtils.readStepsFromDayDirectory(client: mockClient, date: Date())
        
        // Assert
        XCTAssertEqual(steps, 0)
    }

    func testReadDistanceFromDayDirectory_SuccessfulResponse() async throws {
        // Arrange
        let date = Date()
        var proto = Data_PbDailySummary()
        proto.activityDistance = 1234.56
        proto.date = PbDate.with { $0.day = 1; $0.month = 1; $0.year = 2525 }
        mockClient.requestReturnValue = .success(try proto.serializedData())
        
        // Act
        let distance = try await PolarActivityUtils.readDistanceFromDayDirectory(client: mockClient, date: date)
        
        // Assert
        XCTAssertEqual(distance, Float(1234.56))
    }

    func testReadDistanceFromDayDirectory_ActivityFileNotFound() async throws {
        // Arrange
        mockClient.requestReturnValue = .failure(NSError(domain: "File not found", code: 103, userInfo: nil))
        
        // Act
        let distance = try await PolarActivityUtils.readDistanceFromDayDirectory(client: mockClient, date: Date())
        
        // Assert
        XCTAssertEqual(distance, 0)
    }
    
    func testReadActiveTimeFromDayDirectory_SuccessfulResponse() async throws {
        // Arrange
        let date = Date()
        var proto = Data_PbDailySummary()
        var activityClassTimes = Data_PbActivityClassTimes()
        activityClassTimes.timeNonWear = PbDuration.with { $0.hours = 1; $0.seconds = 30; $0.millis = 500 }
        activityClassTimes.timeSleep = PbDuration.with { $0.hours = 7; $0.minutes = 45; $0.seconds = 30; $0.millis = 200 }
        activityClassTimes.timeSedentary = PbDuration.with { $0.hours = 3; $0.minutes = 15; $0.seconds = 20 }
        activityClassTimes.timeLightActivity = PbDuration.with { $0.hours = 2; $0.seconds = 45 }
        activityClassTimes.timeContinuousModerate = PbDuration.with { $0.hours = 1; $0.minutes = 45; $0.seconds = 10; $0.millis = 100 }
        activityClassTimes.timeIntermittentModerate = PbDuration.with { $0.hours = 1; $0.minutes = 15; $0.seconds = 5 }
        activityClassTimes.timeContinuousVigorous = PbDuration.with { $0.minutes = 45; $0.seconds = 30 }
        activityClassTimes.timeIntermittentVigorous = PbDuration.with { $0.minutes = 30; $0.seconds = 15; $0.millis = 50 }
        proto.activityClassTimes = activityClassTimes
        proto.date = PbDate.with { $0.day = 1; $0.month = 1; $0.year = 2525 }
        mockClient.requestReturnValue = .success(try proto.serializedData())
        
        // Act
        let activeTimeData = try await PolarActivityUtils.readActiveTimeFromDayDirectory(client: mockClient, date: date)
        
        // Assert
        XCTAssertEqual(activeTimeData.timeNonWear.hours, 1)
        XCTAssertEqual(activeTimeData.timeNonWear.minutes, 0)
        XCTAssertEqual(activeTimeData.timeNonWear.seconds, 30)
        XCTAssertEqual(activeTimeData.timeNonWear.millis, 500)
        XCTAssertEqual(activeTimeData.timeSleep.hours, 7)
        XCTAssertEqual(activeTimeData.timeSleep.minutes, 45)
        XCTAssertEqual(activeTimeData.timeSleep.seconds, 30)
        XCTAssertEqual(activeTimeData.timeSleep.millis, 200)
        XCTAssertEqual(activeTimeData.timeSedentary.hours, 3)
        XCTAssertEqual(activeTimeData.timeSedentary.minutes, 15)
        XCTAssertEqual(activeTimeData.timeSedentary.seconds, 20)
        XCTAssertEqual(activeTimeData.timeSedentary.millis, 0)
        XCTAssertEqual(activeTimeData.timeLightActivity.hours, 2)
        XCTAssertEqual(activeTimeData.timeLightActivity.minutes, 0)
        XCTAssertEqual(activeTimeData.timeLightActivity.seconds, 45)
        XCTAssertEqual(activeTimeData.timeLightActivity.millis, 0)
        XCTAssertEqual(activeTimeData.timeContinuousModerateActivity.hours, 1)
        XCTAssertEqual(activeTimeData.timeContinuousModerateActivity.minutes, 45)
        XCTAssertEqual(activeTimeData.timeContinuousModerateActivity.seconds, 10)
        XCTAssertEqual(activeTimeData.timeContinuousModerateActivity.millis, 100)
        XCTAssertEqual(activeTimeData.timeIntermittentModerateActivity.hours, 1)
        XCTAssertEqual(activeTimeData.timeIntermittentModerateActivity.minutes, 15)
        XCTAssertEqual(activeTimeData.timeIntermittentModerateActivity.seconds, 5)
        XCTAssertEqual(activeTimeData.timeIntermittentModerateActivity.millis, 0)
        XCTAssertEqual(activeTimeData.timeContinuousVigorousActivity.hours, 0)
        XCTAssertEqual(activeTimeData.timeContinuousVigorousActivity.minutes, 45)
        XCTAssertEqual(activeTimeData.timeContinuousVigorousActivity.seconds, 30)
        XCTAssertEqual(activeTimeData.timeContinuousVigorousActivity.millis, 0)
        XCTAssertEqual(activeTimeData.timeIntermittentVigorousActivity.hours, 0)
        XCTAssertEqual(activeTimeData.timeIntermittentVigorousActivity.minutes, 30)
        XCTAssertEqual(activeTimeData.timeIntermittentVigorousActivity.seconds, 15)
        XCTAssertEqual(activeTimeData.timeIntermittentVigorousActivity.millis, 50)
    }

    func testReadActiveTimeFromDayDirectory_ActivityFileNotFound() async throws {
        // Arrange
        mockClient.requestReturnValue = .failure(NSError(domain: "File not found", code: 103, userInfo: nil))
        
        // Act
        let activeTimeData = try await PolarActivityUtils.readActiveTimeFromDayDirectory(client: mockClient, date: Date())
        
        // Assert
        XCTAssertEqual(activeTimeData.timeNonWear.hours, 0)
        XCTAssertEqual(activeTimeData.timeNonWear.minutes, 0)
        XCTAssertEqual(activeTimeData.timeSleep.hours, 0)
        XCTAssertEqual(activeTimeData.timeSleep.minutes, 0)
        XCTAssertEqual(activeTimeData.timeSedentary.hours, 0)
        XCTAssertEqual(activeTimeData.timeSedentary.minutes, 0)
        XCTAssertEqual(activeTimeData.timeLightActivity.hours, 0)
        XCTAssertEqual(activeTimeData.timeLightActivity.minutes, 0)
        XCTAssertEqual(activeTimeData.timeContinuousModerateActivity.hours, 0)
        XCTAssertEqual(activeTimeData.timeContinuousModerateActivity.minutes, 0)
        XCTAssertEqual(activeTimeData.timeIntermittentModerateActivity.hours, 0)
        XCTAssertEqual(activeTimeData.timeIntermittentModerateActivity.minutes, 0)
        XCTAssertEqual(activeTimeData.timeContinuousVigorousActivity.hours, 0)
        XCTAssertEqual(activeTimeData.timeContinuousVigorousActivity.minutes, 0)
        XCTAssertEqual(activeTimeData.timeIntermittentVigorousActivity.hours, 0)
        XCTAssertEqual(activeTimeData.timeIntermittentVigorousActivity.minutes, 0)
    }

    func testReadCaloriesFromDayDirectory_ActivityCalories() async throws {
        // Arrange
        var proto = Data_PbDailySummary()
        proto.activityCalories = 2000
        proto.date = PbDate.with { $0.day = 1; $0.month = 1; $0.year = 2525 }
        mockClient.requestReturnValue = .success(try proto.serializedData())
        
        // Act
        let calories = try await PolarActivityUtils.readCaloriesFromDayDirectory(client: mockClient, date: Date(), caloriesType: .activity)
        
        // Assert
        XCTAssertEqual(calories, 2000)
    }
    
    func testReadCaloriesFromDayDirectory_TrainingCalories() async throws {
        // Arrange
        var proto = Data_PbDailySummary()
        proto.trainingCalories = 1500
        proto.date = PbDate.with { $0.day = 1; $0.month = 1; $0.year = 2525 }
        mockClient.requestReturnValue = .success(try proto.serializedData())
        
        // Act
        let calories = try await PolarActivityUtils.readCaloriesFromDayDirectory(client: mockClient, date: Date(), caloriesType: .training)
        
        // Assert
        XCTAssertEqual(calories, 1500)
    }

    func testReadCaloriesFromDayDirectory_BmrCalories() async throws {
        // Arrange
        var proto = Data_PbDailySummary()
        proto.bmrCalories = 1200
        proto.date = PbDate.with { $0.day = 1; $0.month = 1; $0.year = 2525 }
        mockClient.requestReturnValue = .success(try proto.serializedData())
        
        // Act
        let calories = try await PolarActivityUtils.readCaloriesFromDayDirectory(client: mockClient, date: Date(), caloriesType: .bmr)
        
        // Assert
        XCTAssertEqual(calories, 1200)
    }
    
    func testReadCaloriesFromDayDirectory_FileNotFound() async throws {
        // Arrange
        mockClient.requestReturnValue = .failure(NSError(domain: "File not found", code: 103, userInfo: nil))
        
        // Act
        let calories = try await PolarActivityUtils.readCaloriesFromDayDirectory(client: mockClient, date: Date(), caloriesType: .activity)
        
        // Assert
        XCTAssertEqual(calories, 0)
    }

    func testReadActivitySamplesDataFromDayDirectory_SuccessfulResponse() async throws {
        // Arrange
        let mockRecordingDirectoryContent = try Protocol_PbPFtpDirectory.with {
            $0.entries = [Protocol_PbPFtpEntry.with { $0.name = "ASAMPL.BPB"; $0.size = 123 }]
        }.serializedData()
        mockClient.requestReturnValues.append(.success(mockRecordingDirectoryContent))

        let date = Date()
        var proto = Data_PbActivitySamples()
        proto.stepsSamples = [10000, 5000, 8000]
        proto.metSamples = [10000, 5000, 8000]
        proto.metRecordingInterval = PbDuration.with { $0.seconds = 30 }
        proto.stepsRecordingInterval = PbDuration.with { $0.seconds = 60 }
        proto.startTime = PbLocalDateTime.with {
            $0.date = PbDate.with { $0.day = 1; $0.month = 2; $0.year = 2525 }
            $0.time = PbTime.with { $0.hour = 1; $0.minute = 2; $0.seconds = 3; $0.millis = 4 }
            $0.timeZoneOffset = 5
            $0.obsoleteTrusted = true
        }
        var activityInfo = Data_PbActivityInfo()
        activityInfo.factor = 100
        activityInfo.value = .continuousModerate
        activityInfo.timeStamp = PbLocalDateTime.with {
            $0.date = PbDate.with { $0.day = 1; $0.month = 2; $0.year = 2525 }
            $0.time = PbTime.with { $0.hour = 1; $0.minute = 2; $0.seconds = 3; $0.millis = 0 }
            $0.timeZoneOffset = 0
            $0.obsoleteTrusted = true
        }
        proto.activityInfo.append(activityInfo)
        mockClient.requestReturnValues.append(.success(try proto.serializedData()))

        // Act
        let samplesData = try await PolarActivityUtils.readActivitySamplesDataFromDayDirectory(client: mockClient, date: date)

        // Assert
        XCTAssertEqual(samplesData.polarActivityDataList.count, 1)
        XCTAssertEqual(samplesData.polarActivityDataList[0].samples?.metRecordingInterval, 30)
        XCTAssertEqual(samplesData.polarActivityDataList[0].samples?.metSamples.count, 3)
        XCTAssertEqual(samplesData.polarActivityDataList[0].samples?.metSamples[0], 10000)
        XCTAssertEqual(samplesData.polarActivityDataList[0].samples?.metSamples[1], 5000)
        XCTAssertEqual(samplesData.polarActivityDataList[0].samples?.metSamples[2], 8000)
        XCTAssertEqual(samplesData.polarActivityDataList[0].samples?.stepRecordingInterval, 60)
        XCTAssertEqual(samplesData.polarActivityDataList[0].samples?.stepSamples.count, 3)
        XCTAssertEqual(samplesData.polarActivityDataList[0].samples?.stepSamples[0], 10000)
        XCTAssertEqual(samplesData.polarActivityDataList[0].samples?.stepSamples[1], 5000)
        XCTAssertEqual(samplesData.polarActivityDataList[0].samples?.stepSamples[2], 8000)
        XCTAssertEqual(samplesData.polarActivityDataList[0].samples?.activityInfoList.count, 1)
        XCTAssertEqual(samplesData.polarActivityDataList[0].samples?.activityInfoList[0].activityClass, .CONTINUOUS_MODERATE)
        XCTAssertEqual(samplesData.polarActivityDataList[0].samples?.activityInfoList[0].factor, 100)
    }

    func testReadActivitySamplesDataFromDayDirectory_ActivityFileNotFound() async throws {
        // Arrange
        let mockRecordingDirectoryContent = try Protocol_PbPFtpDirectory.with {
            $0.entries = []
        }.serializedData()
        mockClient.requestReturnValues.append(.success(mockRecordingDirectoryContent))
        mockClient.requestReturnValues.append(.failure(NSError(domain: "File not found", code: 103, userInfo: nil)))

        // Act
        let samples = try await PolarActivityUtils.readActivitySamplesDataFromDayDirectory(client: mockClient, date: Date())

        // Assert
        XCTAssertEqual(samples.polarActivityDataList.count, 0)
    }
    
    func testReadDailySummaryFromDayDirectory_SuccessfulResponse() async throws {
        // Arrange
        let date = Date()
        var proto = Data_PbDailySummary()
        var activityClassTimes = Data_PbDailySummary().activityClassTimes
        activityClassTimes.timeNonWear = PbDuration.with { $0.hours = 1; $0.seconds = 30; $0.millis = 500 }
        activityClassTimes.timeSleep = PbDuration.with { $0.hours = 7; $0.minutes = 45; $0.seconds = 30; $0.millis = 200 }
        activityClassTimes.timeSedentary = PbDuration.with { $0.hours = 3; $0.minutes = 15; $0.seconds = 20 }
        activityClassTimes.timeLightActivity = PbDuration.with { $0.hours = 2; $0.seconds = 45 }
        activityClassTimes.timeContinuousModerate = PbDuration.with { $0.hours = 1; $0.minutes = 45; $0.seconds = 10; $0.millis = 100 }
        activityClassTimes.timeIntermittentModerate = PbDuration.with { $0.hours = 1; $0.minutes = 15; $0.seconds = 5 }
        activityClassTimes.timeContinuousVigorous = PbDuration.with { $0.minutes = 45; $0.seconds = 30 }
        activityClassTimes.timeIntermittentVigorous = PbDuration.with { $0.minutes = 30; $0.seconds = 15; $0.millis = 50 }
        proto.activityClassTimes = activityClassTimes
        proto.activityGoalSummary = Data_PbActivityGoalSummary.with {
            $0.achievedActivity = 100
            $0.activityGoal = 1000
            $0.timeToGoJog = PbDuration.with { $0.hours = 1; $0.minutes = 1; $0.seconds = 1; $0.millis = 1 }
            $0.timeToGoUp = PbDuration.with { $0.hours = 1; $0.minutes = 1; $0.seconds = 1; $0.millis = 1 }
            $0.timeToGoWalk = PbDuration.with { $0.hours = 1; $0.minutes = 1; $0.seconds = 1; $0.millis = 1 }
        }
        proto.dailyBalanceFeedback = .dbFatigueTryToReduceTrainingLoad
        proto.readinessForSpeedAndStrengthTraining = .rsstA1RecoveredReadyForAllTraining
        proto.activityDistance = 100
        proto.activityCalories = 100
        proto.bmrCalories = 100
        proto.trainingCalories = 500
        proto.date = PbDate.with { $0.day = 1; $0.month = 1; $0.year = 2525 }
        mockClient.requestReturnValue = .success(try proto.serializedData())

        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(abbreviation: "UTC")!
        let expectedDate = calendar.date(from: DateComponents(year: 2525, month: 1, day: 1, hour: 0, minute: 0, second: 0))!

        // Act
        let testResult = try await PolarActivityUtils.readDailySummaryDataFromDayDirectory(client: mockClient, date: date)

        // Assert
        XCTAssertEqual(testResult?.activityClassTimes.timeNonWear.hours, 1)
        XCTAssertEqual(testResult?.activityClassTimes.timeNonWear.minutes, 0)
        XCTAssertEqual(testResult?.activityClassTimes.timeNonWear.seconds, 30)
        XCTAssertEqual(testResult?.activityClassTimes.timeNonWear.millis, 500)
        XCTAssertEqual(testResult?.activityClassTimes.timeSleep.hours, 7)
        XCTAssertEqual(testResult?.activityClassTimes.timeSleep.minutes, 45)
        XCTAssertEqual(testResult?.activityClassTimes.timeSleep.seconds, 30)
        XCTAssertEqual(testResult?.activityClassTimes.timeSleep.millis, 200)
        XCTAssertEqual(testResult?.activityClassTimes.timeSedentary.hours, 3)
        XCTAssertEqual(testResult?.activityClassTimes.timeSedentary.minutes, 15)
        XCTAssertEqual(testResult?.activityClassTimes.timeSedentary.seconds, 20)
        XCTAssertEqual(testResult?.activityClassTimes.timeSedentary.millis, 0)
        XCTAssertEqual(testResult?.activityClassTimes.timeLightActivity.hours, 2)
        XCTAssertEqual(testResult?.activityClassTimes.timeLightActivity.minutes, 0)
        XCTAssertEqual(testResult?.activityClassTimes.timeLightActivity.seconds, 45)
        XCTAssertEqual(testResult?.activityClassTimes.timeLightActivity.millis, 0)
        XCTAssertEqual(testResult?.activityClassTimes.timeContinuousModerateActivity.hours, 1)
        XCTAssertEqual(testResult?.activityClassTimes.timeContinuousModerateActivity.minutes, 45)
        XCTAssertEqual(testResult?.activityClassTimes.timeContinuousModerateActivity.seconds, 10)
        XCTAssertEqual(testResult?.activityClassTimes.timeContinuousModerateActivity.millis, 100)
        XCTAssertEqual(testResult?.activityClassTimes.timeIntermittentModerateActivity.hours, 1)
        XCTAssertEqual(testResult?.activityClassTimes.timeIntermittentModerateActivity.minutes, 15)
        XCTAssertEqual(testResult?.activityClassTimes.timeIntermittentModerateActivity.seconds, 5)
        XCTAssertEqual(testResult?.activityClassTimes.timeIntermittentModerateActivity.millis, 0)
        XCTAssertEqual(testResult?.activityClassTimes.timeContinuousVigorousActivity.hours, 0)
        XCTAssertEqual(testResult?.activityClassTimes.timeContinuousVigorousActivity.minutes, 45)
        XCTAssertEqual(testResult?.activityClassTimes.timeContinuousVigorousActivity.seconds, 30)
        XCTAssertEqual(testResult?.activityClassTimes.timeContinuousVigorousActivity.millis, 0)
        XCTAssertEqual(testResult?.activityClassTimes.timeIntermittentVigorousActivity.hours, 0)
        XCTAssertEqual(testResult?.activityClassTimes.timeIntermittentVigorousActivity.minutes, 30)
        XCTAssertEqual(testResult?.activityClassTimes.timeIntermittentVigorousActivity.seconds, 15)
        XCTAssertEqual(testResult?.activityClassTimes.timeIntermittentVigorousActivity.millis, 50)
        XCTAssertEqual(testResult?.activityGoalSummary.timeToGoJog, PolarActiveTime(hours: 1, minutes: 1, seconds: 1, millis: 1))
        XCTAssertEqual(testResult?.activityGoalSummary.timeToGoUp, PolarActiveTime(hours: 1, minutes: 1, seconds: 1, millis: 1))
        XCTAssertEqual(testResult?.activityGoalSummary.timeToGoWalk, PolarActiveTime(hours: 1, minutes: 1, seconds: 1, millis: 1))
        XCTAssertEqual(testResult?.activityDistance, 100)
        XCTAssertEqual(testResult?.activityCalories, 100)
        XCTAssertEqual(testResult?.bmrCalories, 100)
        XCTAssertEqual(testResult?.trainingCalories, 500)
        XCTAssertEqual(testResult?.readinessForSpeedAndStrengthTraining, .RECOVERED_READY_FOR_ALL_TRAINING)
        XCTAssertEqual(testResult?.dailyBalanceFeedback, .FATIGUE_TRY_TO_REDUCE_TRAINING_LOAD)
        XCTAssertEqual(testResult?.date, expectedDate)
    }
    
    func testReadDailySummaryFromDayDirectory_FileNotFound() async throws {
        // Arrange
        mockClient.requestReturnValue = .failure(NSError(domain: "File not found", code: 103, userInfo: nil))
        
        // Act — error 103 is swallowed; method returns nil
        let result = try await PolarActivityUtils.readDailySummaryDataFromDayDirectory(client: mockClient, date: Date())
        
        // Assert
        XCTAssertNil(result, "Expected nil when daily summary file is not found")
    }
}
