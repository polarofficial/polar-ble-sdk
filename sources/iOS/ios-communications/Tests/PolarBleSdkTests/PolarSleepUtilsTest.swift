//
//  Copyright © 2024 Polar. All rights reserved.
//

import Foundation
import XCTest
@testable import PolarBleSdk

class PolarSleepUtilsTests: XCTestCase {

    var mockClient: MockBlePsFtpClient!

    override func setUpWithError() throws {
        mockClient = MockBlePsFtpClient(gattServiceTransmitter: MockPolarGattServiceTransmitter())
    }

    override func tearDownWithError() throws {
        mockClient = nil
    }

    func testReadSleepDataFromDayDirectory_SuccessfulResponse() async throws {
        // Arrange
        let date = Date()
        
        let sleepProto = Data_PbSleepAnalysisResult.with {
            $0.alarmTime = PbLocalDateTime.with {
                $0.date = PbDate.with { $0.day = 28; $0.month = 2; $0.year = 2525 }
                $0.time = PbTime.with { $0.hour = 7; $0.minute = 0; $0.seconds = 0 }
                $0.timeZoneOffset = 120
                $0.obsoleteTrusted = true
            }
            $0.batteryRanOut = false
            $0.createdTimestamp = PbSystemDateTime.with {
                $0.date = PbDate.with { $0.day = 28; $0.month = 2; $0.year = 2525 }
                $0.time = PbTime.with { $0.hour = 7; $0.minute = 0; $0.seconds = 0 }
                $0.trusted = true
            }
            $0.lastModified = PbSystemDateTime.with {
                $0.date = PbDate.with { $0.day = 4; $0.month = 3; $0.year = 2525 }
                $0.time = PbTime.with { $0.hour = 4; $0.minute = 3; $0.seconds = 2 }
                $0.trusted = true
            }
            $0.originalSleepRange = PbLocalDateTimeRange.with {
                $0.startTime = PbLocalDateTime.with {
                    $0.date = PbDate.with { $0.day = 27; $0.month = 2; $0.year = 2525 }
                    $0.time = PbTime.with { $0.hour = 23; $0.minute = 59; $0.seconds = 59 }
                    $0.timeZoneOffset = 120
                    $0.obsoleteTrusted = true
                }
                $0.endTime = PbLocalDateTime.with {
                    $0.date = PbDate.with { $0.day = 28; $0.month = 2; $0.year = 2525 }
                    $0.time = PbTime.with { $0.hour = 7; $0.minute = 0; $0.seconds = 0 }
                    $0.timeZoneOffset = 120
                    $0.obsoleteTrusted = true
                }
            }
            $0.recordingDevice = PbDeviceId.with { $0.deviceID = "C8D9G10F11H12" }
            $0.sleepCycles = [Data_PbSleepCycle.with {
                $0.secondsFromSleepStart = 2
                $0.sleepDepthStart = 3.0
            }]
            $0.sleepGoalMinutes = 420
            $0.sleepEndOffsetSeconds = 1
            $0.sleepStartOffsetSeconds = 1
            $0.sleepResultDate = PbDate.with { $0.day = 28; $0.month = 2; $0.year = 2525 }
            $0.sleepStartTime = PbLocalDateTime.with {
                $0.date = PbDate.with { $0.day = 27; $0.month = 2; $0.year = 2525 }
                $0.time = PbTime.with { $0.hour = 23; $0.minute = 45; $0.seconds = 45 }
                $0.timeZoneOffset = 120
                $0.obsoleteTrusted = true
            }
            $0.sleepEndTime = PbLocalDateTime.with {
                $0.date = PbDate.with { $0.day = 28; $0.month = 2; $0.year = 2525 }
                $0.time = PbTime.with { $0.hour = 7; $0.minute = 5; $0.seconds = 7 }
                $0.timeZoneOffset = 120
                $0.obsoleteTrusted = true
            }
            $0.sleepwakePhases = [Data_PbSleepWakePhase.with {
                $0.secondsFromSleepStart = 1
                $0.sleepwakeState = .pbWake
            }]
            $0.snoozeTime = [PbLocalDateTime.with {
                $0.date = PbDate.with { $0.day = 27; $0.month = 2; $0.year = 2525 }
                $0.time = PbTime.with { $0.hour = 23; $0.minute = 59; $0.seconds = 59 }
                $0.timeZoneOffset = 120
                $0.obsoleteTrusted = true
            }]
            $0.userSleepRating = .pbSleptWell
        }
        
        let sleepSkinTemperatureProto = Data_PbSleepSkinTemperatureResult.with {
            $0.sleepDate = PbDateProto3.with { $0.day = 28; $0.month = 2; $0.year = 2525 }
            $0.deviationFromBaselineCelsius = -0.111111
            $0.sleepSkinTemperatureCelsius = 35.123456
        }
        
        mockClient.requestReturnValues.append(.success(try sleepProto.serializedData()))
        mockClient.requestReturnValues.append(.success(try sleepSkinTemperatureProto.serializedData()))

        let mockSleepData = try Self.createPolarSleepAnalysisData()

        // Act
        let sleepData = try await PolarSleepUtils.readSleepFromDayDirectory(client: mockClient, date: date)

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
    }

    // MARK: - Helpers

    private static func createPolarSleepAnalysisData() throws -> PolarSleepData.PolarSleepAnalysisResult {
        return PolarSleepData.PolarSleepAnalysisResult(
            sleepStartTime: try createDate(hour: 23, minute: 45, second: 45, day: 27, month: 2, year: 2525),
            sleepEndTime: try createDate(hour: 7, minute: 5, second: 7, day: 28, month: 2, year: 2525),
            lastModified: try createDate(hour: 4, minute: 3, second: 2, day: 4, month: 3, year: 2525),
            sleepGoalMinutes: 420,
            sleepWakePhases: [PolarSleepData.SleepWakePhase(secondsFromSleepStart: 1, state: .WAKE)],
            snoozeTime: [try createDate(hour: 23, minute: 59, second: 59, day: 27, month: 2, year: 2525)!],
            alarmTime: try createDate(hour: 7, minute: 0, second: 0, day: 28, month: 2, year: 2525),
            sleepStartOffsetSeconds: 1,
            sleepEndOffsetSeconds: 1,
            userSleepRating: PolarSleepData.SleepRating.SLEPT_WELL,
            deviceId: "C8D9G10F11H12",
            batteryRanOut: false,
            sleepCycles: [PolarSleepData.SleepCycle(secondsFromSleepStart: 2, sleepDepthStart: Float(3.0))],
            sleepResultDate: DateComponents(year: 2525, month: 2, day: 28),
            originalSleepRange: PolarSleepData.OriginalSleepRange(
                startTime: try createDate(hour: 23, minute: 59, second: 59, day: 27, month: 2, year: 2525),
                endTime: try createDate(hour: 7, minute: 0, second: 0, day: 28, month: 2, year: 2525)
            ),
            sleepSkinTemperatureResult: PolarSleepData.SleepSkinTemperatureResult(
                sleepResultDate: try createDate(hour: nil, minute: nil, second: nil, day: 28, month: 2, year: 2525)!,
                sleepSkinTemperatureCelsius: 35.123456,
                deviationFromBaseLine: -0.111111
            )
        )
    }

    private static func createDate(hour: Int?, minute: Int?, second: Int?, day: Int, month: Int, year: Int) throws -> Date! {
        var dateComponents = DateComponents()
        dateComponents.year = year
        dateComponents.month = month
        dateComponents.day = day
        dateComponents.hour = hour
        dateComponents.minute = minute
        dateComponents.second = second
        dateComponents.timeZone = TimeZone(secondsFromGMT: 2 * 60 * 60)!
        let userCalendar = Calendar(identifier: .iso8601)
        return userCalendar.date(from: dateComponents)!
    }
}
