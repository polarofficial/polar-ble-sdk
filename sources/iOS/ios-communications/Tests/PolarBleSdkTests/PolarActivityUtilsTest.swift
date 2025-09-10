//  Copyright © 2024 Polar. All rights reserved.

import Foundation
import XCTest
import RxSwift
import RxTest
@testable import PolarBleSdk

class PolarActivityUtilsTests: XCTestCase {

    var mockClient: MockBlePsFtpClient!
    
    override func setUpWithError() throws {
        mockClient = MockBlePsFtpClient(gattServiceTransmitter: MockGattServiceTransmitterImpl())
    }

    override func tearDownWithError() throws {
        mockClient = nil
    }

    func testReadStepsFromDayDirectory_SuccessfulResponse() {
        
        // Arrange
    
        mockClient.requestReturnValues = []
        
        // Folder listing mock
        let mockRecordingDirectoryContent = try! Protocol_PbPFtpDirectory.with {
            $0.entries = [
                Protocol_PbPFtpEntry.with { $0.name = "ASAMPL.BPB"; $0.size = 123 }
            ]
        }.serializedData()
    
        mockClient.requestReturnValues.append(Single.just(mockRecordingDirectoryContent))
        
        // File mock
        let date = Date()
        let stepSample: UInt32 = 10000
        let stepSample2: UInt32 = 5000
        let stepSample3: UInt32 = 8000
        let expectedSteps: UInt32 = 23000
        
        var proto = Data_PbActivitySamples()
        proto.stepsSamples = [stepSample, stepSample2, stepSample3]
        
        let responseData = try! proto.serializedData()
        
        mockClient.requestReturnValues.append(Single.just(responseData))
        
        // Act
        let expectation = XCTestExpectation(description: "Read steps from day directory")
        let disposable = PolarActivityUtils.readStepsFromDayDirectory(client: mockClient, date: date)
            .subscribe(onSuccess: { steps in
                // Assert
                XCTAssertEqual(steps, Int(expectedSteps))
                expectation.fulfill()
            }, onFailure: { error in
                XCTFail("Error: \(error)")
            })
        
        // Assert
        wait(for: [expectation], timeout: 5)
        disposable.dispose()
    }

    func testReadStepsFromDayDirectory_ActivityFileNotFound() {

        // Arrange
        mockClient.requestReturnValues = []

        // Folder listing mock
        let mockRecordingDirectoryContent = try! Protocol_PbPFtpDirectory.with {
            $0.entries = []
        }.serializedData()
        mockClient.requestReturnValues.append(Single.just(mockRecordingDirectoryContent))
        
        // File not found mock
        mockClient.requestReturnValues.append(Single.error(NSError(domain: "File not found", code: 103, userInfo: nil)))
        
        // Act
        let expectation = XCTestExpectation(description: "Read steps from day directory should return 0 when activity file not found")
        let disposable = PolarActivityUtils.readStepsFromDayDirectory(client: mockClient, date: Date())
            .subscribe(onSuccess: { steps in
                // Assert
                XCTAssertEqual(steps, 0)
                expectation.fulfill()
            }, onFailure: { error in
                XCTFail("Error: \(error)")
                expectation.fulfill()
            })
        
        // Assert
        wait(for: [expectation], timeout: 5)
        disposable.dispose()
    }

    func testReadDistanceFromDayDirectory_SuccessfulResponse() {
        // Arrange
        let date = Date()
        let distance: Float = 1234.56

        var proto = Data_PbDailySummary()
        proto.activityDistance = distance

        let responseData = try! proto.serializedData()

        mockClient.requestReturnValue = Single.just(responseData)
        var sendNotificationCalled = false
        mockClient.sendNotificationReturnValue = Completable.create { completable in
            sendNotificationCalled = true
            completable(.completed)
            return Disposables.create()
        }

        // Act
        let expectation = XCTestExpectation(description: "Read distance from day directory")
        let disposable = PolarActivityUtils.readDistanceFromDayDirectory(client: mockClient, date: date)
            .subscribe(onSuccess: { distance in
                // Assert
                XCTAssertEqual(distance, Float(distance))
                XCTAssertFalse(sendNotificationCalled)
                expectation.fulfill()
            }, onFailure: { error in
                XCTFail("Error: \(error)")
            })

        // Assert
        wait(for: [expectation], timeout: 5)
        disposable.dispose()
    }

    func testReadDistanceFromDayDirectory_ActivityFileNotFound() {
        // Arrange
        mockClient.requestReturnValue = Single.error(NSError(domain: "File not found", code: 103, userInfo: nil))
        var sendNotificationCalled = false
        mockClient.sendNotificationReturnValue = Completable.create { completable in
            sendNotificationCalled = true
            completable(.completed)
            return Disposables.create()
        }

        // Act
        let expectation = XCTestExpectation(description: "Read distance from day directory should return 0 when activity file not found")
        let disposable = PolarActivityUtils.readDistanceFromDayDirectory(client: mockClient, date: Date())
            .subscribe(onSuccess: { distance in
                // Assert
                XCTAssertEqual(distance, 0)
                XCTAssertFalse(sendNotificationCalled)
                expectation.fulfill()
            }, onFailure: { error in
                XCTFail("Error: \(error)")
                expectation.fulfill()
            })

        // Assert
        wait(for: [expectation], timeout: 5)
        disposable.dispose()
    }
    
    func testReadActiveTimeFromDayDirectory_SuccessfulResponse() {
        // Arrange
        let date = Date()
        var proto = Data_PbDailySummary()

        var activityClassTimes = Data_PbActivityClassTimes()

        activityClassTimes.timeNonWear = PbDuration()
        activityClassTimes.timeNonWear.hours = 1
        activityClassTimes.timeNonWear.seconds = 30
        activityClassTimes.timeNonWear.millis = 500

        activityClassTimes.timeSleep = PbDuration()
        activityClassTimes.timeSleep.hours = 7
        activityClassTimes.timeSleep.minutes = 45
        activityClassTimes.timeSleep.seconds = 30
        activityClassTimes.timeSleep.millis = 200

        activityClassTimes.timeSedentary = PbDuration()
        activityClassTimes.timeSedentary.hours = 3
        activityClassTimes.timeSedentary.minutes = 15
        activityClassTimes.timeSedentary.seconds = 20

        activityClassTimes.timeLightActivity = PbDuration()
        activityClassTimes.timeLightActivity.hours = 2
        activityClassTimes.timeLightActivity.seconds = 45

        activityClassTimes.timeContinuousModerate = PbDuration()
        activityClassTimes.timeContinuousModerate.hours = 1
        activityClassTimes.timeContinuousModerate.minutes = 45
        activityClassTimes.timeContinuousModerate.seconds = 10
        activityClassTimes.timeContinuousModerate.millis = 100

        activityClassTimes.timeIntermittentModerate = PbDuration()
        activityClassTimes.timeIntermittentModerate.hours = 1
        activityClassTimes.timeIntermittentModerate.minutes = 15
        activityClassTimes.timeIntermittentModerate.seconds = 5

        activityClassTimes.timeContinuousVigorous = PbDuration()
        activityClassTimes.timeContinuousVigorous.minutes = 45
        activityClassTimes.timeContinuousVigorous.seconds = 30

        activityClassTimes.timeIntermittentVigorous = PbDuration()
        activityClassTimes.timeIntermittentVigorous.minutes = 30
        activityClassTimes.timeIntermittentVigorous.seconds = 15
        activityClassTimes.timeIntermittentVigorous.millis = 50

        proto.activityClassTimes = activityClassTimes

        let responseData = try! proto.serializedData()
        mockClient.requestReturnValue = Single.just(responseData)
        
        var sendNotificationCalled = false
        mockClient.sendNotificationReturnValue = Completable.create { completable in
            sendNotificationCalled = true
            completable(.completed)
            return Disposables.create()
        }

        // Act
        let expectation = XCTestExpectation(description: "Read active time from day directory")
        let disposable = PolarActivityUtils.readActiveTimeFromDayDirectory(client: mockClient, date: date)
            .subscribe(onSuccess: { activeTimeData in
                // Assert
                XCTAssertFalse(sendNotificationCalled)
                
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

                expectation.fulfill()
            }, onFailure: { error in
                XCTFail("Error: \(error)")
            })

        // Assert
        wait(for: [expectation], timeout: 5)
        disposable.dispose()
    }

    func testReadActiveTimeFromDayDirectory_ActivityFileNotFound() {
        // Arrange
        mockClient.requestReturnValue = Single.error(NSError(domain: "File not found", code: 103, userInfo: nil))
        
        var sendNotificationCalled = false
        mockClient.sendNotificationReturnValue = Completable.create { completable in
            sendNotificationCalled = true
            completable(.completed)
            return Disposables.create()
        }

        // Act
        let expectation = XCTestExpectation(description: "Read active time from day directory should return default values when activity file not found")
        let disposable = PolarActivityUtils.readActiveTimeFromDayDirectory(client: mockClient, date: Date())
            .subscribe(onSuccess: { activeTimeData in
                // Assert
                XCTAssertFalse(sendNotificationCalled)
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
                expectation.fulfill()
            }, onFailure: { error in
                XCTFail("Error: \(error)")
                expectation.fulfill()
            })

        // Assert
        wait(for: [expectation], timeout: 5)
        disposable.dispose()
    }

    func testReadCaloriesFromDayDirectory_ActivityCalories() {
        // Arrange
        let date = Date()
        var proto = Data_PbDailySummary()
        proto.activityCalories = 2000
        
        let responseData = try! proto.serializedData()
        mockClient.requestReturnValue = Single.just(responseData)
        
        // Act
        let expectation = XCTestExpectation(description: "Read activity calories")
        PolarActivityUtils.readCaloriesFromDayDirectory(client: mockClient, date: date, caloriesType: CaloriesType.activity)
            .subscribe(onSuccess: { calories in
                // Assert
                XCTAssertEqual(calories, 2000)
                expectation.fulfill()
            }, onFailure: { error in
                XCTFail("Error: \(error)")
            })
        
        wait(for: [expectation], timeout: 5)
    }
    
    func testReadCaloriesFromDayDirectory_TrainingCalories() {
        // Arrange
        let date = Date()
        var proto = Data_PbDailySummary()
        proto.trainingCalories = 1500
        let responseData = try! proto.serializedData()
        mockClient.requestReturnValue = Single.just(responseData)
        
        // Act
        let expectation = XCTestExpectation(description: "Read training calories")
        PolarActivityUtils.readCaloriesFromDayDirectory(client: mockClient, date: date, caloriesType: .training)
            .subscribe(onSuccess: { calories in
                // Assert
                XCTAssertEqual(calories, 1500)
                expectation.fulfill()
            }, onFailure: { error in
                XCTFail("Error: \(error)")
            })
        
        wait(for: [expectation], timeout: 5)
    }

    func testReadCaloriesFromDayDirectory_BmrCalories() {
        // Arrange
        let date = Date()
        var proto = Data_PbDailySummary()
        proto.bmrCalories = 1200
        let responseData = try! proto.serializedData()
        mockClient.requestReturnValue = Single.just(responseData)
        
        // Act
        let expectation = XCTestExpectation(description: "Read BMR calories")
        PolarActivityUtils.readCaloriesFromDayDirectory(client: mockClient, date: date, caloriesType: .bmr)
            .subscribe(onSuccess: { calories in
                // Assert
                XCTAssertEqual(calories, 1200)
                expectation.fulfill()
            }, onFailure: { error in
                XCTFail("Error: \(error)")
            })
        
        wait(for: [expectation], timeout: 5)
    }
    
    func testReadCaloriesFromDayDirectory_FileNotFound() {
        // Arrange
        mockClient.requestReturnValue = Single.error(NSError(domain: "File not found", code: 103, userInfo: nil))
        
        // Act
        let expectation = XCTestExpectation(description: "Read calories from day directory when file not found")
        PolarActivityUtils.readCaloriesFromDayDirectory(client: mockClient, date: Date(), caloriesType: .activity)
            .subscribe(onSuccess: { calories in
                // Assert
                XCTAssertEqual(calories, 0)
                expectation.fulfill()
            }, onFailure: { error in
                XCTFail("Error: \(error)")
            })
        
        wait(for: [expectation], timeout: 5)
    }

    func testReadActivitySamplesDataFromDayDirectory_SuccessfulResponse() {

        // Arrange
        mockClient.requestReturnValues = []

        // Folder listing mock
        let mockRecordingDirectoryContent = try! Protocol_PbPFtpDirectory.with {
            $0.entries = [
                Protocol_PbPFtpEntry.with { $0.name = "ASAMPL.BPB"; $0.size = 123 }
            ]
        }.serializedData()

        mockClient.requestReturnValues.append(Single.just(mockRecordingDirectoryContent))

        // File mock
        let date = Date()
        let stepSample: UInt32 = 10000
        let stepSample2: UInt32 = 5000
        let stepSample3: UInt32 = 8000

        let metSample: Float = 10000
        let metSample2: Float = 5000
        let metSample3: Float = 8000

        var proto = Data_PbActivitySamples()
        proto.stepsSamples = [stepSample, stepSample2, stepSample3]
        proto.metSamples = [metSample, metSample2, metSample3]

        proto.metRecordingInterval = PbDuration()
        proto.metRecordingInterval.hours = 0
        proto.metRecordingInterval.minutes = 0
        proto.metRecordingInterval.seconds = 30
        proto.metRecordingInterval.millis = 0

        proto.stepsRecordingInterval = PbDuration()
        proto.stepsRecordingInterval.seconds = 60
        proto.stepsRecordingInterval.hours = 0
        proto.stepsRecordingInterval.minutes = 0
        proto.stepsRecordingInterval.seconds = 60
        proto.stepsRecordingInterval.millis = 0

        proto.startTime = PbLocalDateTime()
        proto.startTime.date = PbDate()
        proto.startTime.date.day = 1
        proto.startTime.date.month = 2
        proto.startTime.date.year = 2525
        proto.startTime.time = PbTime()
        proto.startTime.time.hour = 1
        proto.startTime.time.minute = 2
        proto.startTime.time.seconds = 3
        proto.startTime.time.millis = 4
        proto.startTime.timeZoneOffset = 5
        proto.startTime.obsoleteTrusted = true

        var activityInfo = Data_PbActivityInfo()
        activityInfo.factor = 100
        activityInfo.value = .continuousModerate
        activityInfo.timeStamp = PbLocalDateTime()
        activityInfo.timeStamp.date = PbDate()
        activityInfo.timeStamp.date.day = 1
        activityInfo.timeStamp.date.month = 2
        activityInfo.timeStamp.date.year = 2525
        activityInfo.timeStamp.time = PbTime()
        activityInfo.timeStamp.time.hour = 1
        activityInfo.timeStamp.time.minute = 2
        activityInfo.timeStamp.time.seconds = 3
        activityInfo.timeStamp.time.millis = 0
        activityInfo.timeStamp.timeZoneOffset = 0
        activityInfo.timeStamp.obsoleteTrusted = true
        proto.activityInfo.append(activityInfo)

        let responseData = try! proto.serializedData()

        mockClient.requestReturnValues.append(Single.just(responseData))

        // Act
        let expectation = XCTestExpectation(description: "Read activity samples data from day directory")

        let disposable = PolarActivityUtils.readActivitySamplesDataFromDayDirectory(client: mockClient, date: date)
            .subscribe(onSuccess: { samplesData in
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

                expectation.fulfill()
            }, onFailure: { error in
                XCTFail("Error: \(error)")
            })

        // Assert
        wait(for: [expectation], timeout: 5)
        disposable.dispose()
    }

    func testReadActivitySamplesDataFromDayDirectory_ActivityFileNotFound() {

        // Arrange
        mockClient.requestReturnValues = []

        // Folder listing mock
        let mockRecordingDirectoryContent = try! Protocol_PbPFtpDirectory.with {
            $0.entries = []
        }.serializedData()
        mockClient.requestReturnValues.append(Single.just(mockRecordingDirectoryContent))

        // File not found mock
        mockClient.requestReturnValues.append(Single.error(NSError(domain: "File not found", code: 103, userInfo: nil)))

        // Act
        let expectation = XCTestExpectation(description: "Read activity samples from day directory should return 0 when activity file not found")
        let disposable = PolarActivityUtils.readActivitySamplesDataFromDayDirectory(client: mockClient, date: Date())
            .subscribe(onSuccess: { samples in
                // Assert
                XCTAssertEqual(samples.polarActivityDataList.count, 0)
                expectation.fulfill()
            }, onFailure: { error in
                XCTFail("Error: \(error)")
                expectation.fulfill()
            })

        // Assert
        wait(for: [expectation], timeout: 5)
        disposable.dispose()
    }
}
