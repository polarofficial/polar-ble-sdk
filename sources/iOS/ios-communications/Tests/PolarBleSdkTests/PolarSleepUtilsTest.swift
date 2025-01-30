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
        
        let proto = Data_PbSleepAnalysisResult.with {
            $0.alarmTime = PbLocalDateTime.with {
                $0.date = PbDate.with { $0.day = 2; $0.month = 2; $0.year = 2525 };
                $0.time = PbTime.with{ $0.hour = 7; $0.minute = 0; $0.seconds = 0 };
                $0.timeZoneOffset = 120;
                $0.obsoleteTrusted = true;
            };
            $0.batteryRanOut = false;
            $0.createdTimestamp = PbSystemDateTime.with {
                $0.date = PbDate.with { $0.day = 2; $0.month = 2; $0.year = 2525 };
                $0.time = PbTime.with{ $0.hour = 7; $0.minute = 0; $0.seconds = 0 };
                $0.trusted = true;
            };
            $0.lastModified = PbSystemDateTime.with {
                $0.date = PbDate.with { $0.day = 4; $0.month = 3; $0.year = 2525 };
                $0.time = PbTime.with{ $0.hour = 4; $0.minute = 3; $0.seconds = 2;};
                $0.trusted = true;
            };
            $0.originalSleepRange = PbLocalDateTimeRange.with {
                $0.startTime = PbLocalDateTime.with {
                    $0.date = PbDate.with { $0.day = 1; $0.month = 2; $0.year = 2525 };
                    $0.time = PbTime.with{ $0.hour = 23; $0.minute = 59; $0.seconds = 59; };
                    $0.timeZoneOffset = 120;
                    $0.obsoleteTrusted = true;
                };
                $0.endTime = PbLocalDateTime.with {
                    $0.date = PbDate.with { $0.day = 2; $0.month = 2; $0.year = 2525 };
                    $0.time = PbTime.with{ $0.hour = 7; $0.minute = 0; $0.seconds = 0 };
                    $0.timeZoneOffset = 120;
                    $0.obsoleteTrusted = true;
                };
            };
            $0.recordingDevice = PbDeviceId.with {
                $0.deviceID = "C8D9G10F11H12";
            }
            $0.sleepCycles = [Data_PbSleepCycle.with {
                $0.secondsFromSleepStart = 2;
                $0.sleepDepthStart = 3.0;
            }];
            $0.sleepGoalMinutes = 420;
            $0.sleepEndOffsetSeconds = 1;
            $0.sleepStartOffsetSeconds = 1;
            $0.sleepResultDate = PbDate.with {
                $0.day = 2; $0.month = 2; $0.year = 2525
            };
            $0.sleepStartTime = PbLocalDateTime.with {
                $0.date = PbDate.with { $0.day = 1; $0.month = 2; $0.year = 2525 };
                $0.time = PbTime.with{ $0.hour = 23; $0.minute = 45; $0.seconds = 45; };
                $0.timeZoneOffset = 120;
                $0.obsoleteTrusted = true;
                
            };
            $0.sleepEndTime = PbLocalDateTime.with {
                $0.date = PbDate.with { $0.day = 2; $0.month = 2; $0.year = 2525 };
                $0.time = PbTime.with{ $0.hour = 7; $0.minute = 5; $0.seconds = 7; };
                $0.timeZoneOffset = 120;
                $0.obsoleteTrusted = true;
            };
            $0.sleepwakePhases = [Data_PbSleepWakePhase.with {
                $0.secondsFromSleepStart = 1;
                $0.sleepwakeState = Data_PbSleepWakeState.pbWake;
            }];
            $0.snoozeTime = [PbLocalDateTime.with {
                $0.date = PbDate.with { $0.day = 1; $0.month = 2; $0.year = 2525 };
                $0.time = PbTime.with{ $0.hour = 23; $0.minute = 59; $0.seconds = 59; };
                $0.timeZoneOffset = 120;
                $0.obsoleteTrusted = true;
            }];
            $0.userSleepRating = PbSleepUserRating.pbSleptWell;
        }
        
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
                XCTAssertEqual(sleepData.snoozeTime?.first, mockSleepData.snoozeTime?.first)
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
            sleepStartTime = try createDate(hour: 23, minute: 45, second: 45, day: 1, month: 2, year: 2525)
            sleepEndTime = try createDate(hour: 7, minute: 5, second: 7, day: 2, month: 2, year: 2525)
            lastModified = try createDate(hour: 4, minute: 3, second: 2, day: 4, month: 3, year: 2525)
            snoozeTime = try createDate(hour: 23, minute: 59, second: 59, day: 1, month: 2, year: 2525)
            snoozeTimes.append(snoozeTime)
            alarmTime = try createDate(hour: 7, minute: 0, second: 0, day: 2, month: 2, year: 2525)
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
            sleepCycles: [PolarSleepData.SleepCycle.init(secondsFromSleepStart: 2, sleepDepthStart: Float(3.0))],
            sleepResultDate: try createDate(hour: 0, minute: 0, second: 0, day: 2, month: 2, year: 2525),
            originalSleepRange: PolarSleepData.OriginalSleepRange(
                startTime: try createDate(hour: 23, minute: 59, second: 59, day: 1, month: 2, year: 2525),
                endTime: try createDate(hour: 7, minute: 0, second: 0, day: 2, month: 2, year: 2525)
            )
        )
        
        return polarSleepAnalysisData
    }

    private static func createDate(hour: Int, minute: Int, second: Int, day: Int, month: Int, year: Int) throws -> Date! {

        var dateComponents = DateComponents()
        dateComponents.year = year
        dateComponents.month = month
        dateComponents.day = day
        dateComponents.hour = hour
        dateComponents.minute = minute
        dateComponents.second = second
        dateComponents.timeZone = TimeZone(secondsFromGMT: 2 * 60 * 60)!

        let userCalendar = Calendar(identifier: .iso8601)

        let res = userCalendar.date(from: dateComponents)!
        return userCalendar.date(from: dateComponents)!
    }
}
