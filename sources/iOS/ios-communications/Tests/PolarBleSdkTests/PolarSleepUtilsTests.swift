//  Copyright © 2024 Polar. All rights reserved.

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

        // Sleep skin temperature (from readSleepSkinTemperatureResult)
        let skinTemp = try XCTUnwrap(sleepData.sleepSkinTemperatureResult, "Expected sleep skin temperature result to be parsed")
        let mockSkinTemp = try XCTUnwrap(mockSleepData.sleepSkinTemperatureResult)
        XCTAssertEqual(skinTemp.sleepResultDate, mockSkinTemp.sleepResultDate)
        XCTAssertEqual(skinTemp.sleepSkinTemperatureCelsius, mockSkinTemp.sleepSkinTemperatureCelsius)
        XCTAssertEqual(skinTemp.deviationFromBaseLine, mockSkinTemp.deviationFromBaseLine)
    }

    func testReadSleepData_FileNotFound_ReturnsEmptyPlaceholder() async throws {
        mockClient.requestReturnValues.append(
            .failure(BlePsFtpException.responseError(errorCode: Protocol_PbPFtpError.noSuchFileOrDirectory.rawValue))
        )
        let result = try await PolarSleepUtils.readSleepData(client: mockClient, date: Date())
        XCTAssertNil(result.sleepStartTime, "Expected empty placeholder for missing sleep file")
    }

    func testReadSleepData_ParseError_Throws() async throws {
        mockClient.requestReturnValues.append(.success(Data([0xFF, 0xFE, 0xFD])))
        do {
            _ = try await PolarSleepUtils.readSleepData(client: mockClient, date: Date())
            XCTFail("Expected readSleepData to throw on a proto parse error")
        } catch {
        }
    }

    func testReadSleepData_OtherPsFtpError_Throws() async throws {
        mockClient.requestReturnValues.append(
            .failure(BlePsFtpException.responseError(errorCode: 500))
        )
        do {
            _ = try await PolarSleepUtils.readSleepData(client: mockClient, date: Date())
            XCTFail("Expected readSleepData to throw on a non-404 PFTP error")
        } catch BlePsFtpException.responseError(let code) {
            XCTAssertEqual(code, 500)
        }
    }

    func testReadSleepFromDayDirectory_SkinTempFileNotFound_ReturnsSleepResultWithoutTemperature() async throws {
        mockClient.requestReturnValues.append(.success(try Self.minimalValidSleepProtoData()))
        mockClient.requestReturnValues.append(
            .failure(BlePsFtpException.responseError(errorCode: Protocol_PbPFtpError.noSuchFileOrDirectory.rawValue))
        )
        let result = try await PolarSleepUtils.readSleepFromDayDirectory(client: mockClient, date: Date())
        XCTAssertNil(result.sleepSkinTemperatureResult, "Expected no skin-temperature data when file is absent")
        XCTAssertNotNil(result.sleepStartTime, "Expected sleep start time to still be present")
    }

    func testReadSleepFromDayDirectory_SkinTempParseError_Throws() async throws {
        mockClient.requestReturnValues.append(.success(try Self.minimalValidSleepProtoData()))
        mockClient.requestReturnValues.append(.success(Data([0xFF, 0xFE, 0xFD])))
        do {
            // maxAttempts: 1 — this test is about the skin-temperature parse failure itself,
            // not the retry behavior (covered separately below).
            _ = try await PolarSleepUtils.readSleepFromDayDirectory(client: mockClient, date: Date(), maxAttempts: 1)
            XCTFail("Expected readSleepFromDayDirectory to throw on a skin-temperature parse error")
        } catch {
        }
    }

    // MARK: - readSleepFromDayDirectory retry behavior

    // GIVEN a day's sleep file read fails for a real reason (not "no such file") on the first
    //       attempt, but the device/session recovers before the next attempt
    // WHEN readSleepFromDayDirectory is used
    // THEN it retries and returns the data instead of giving up after a single transient failure
    func testReadSleepFromDayDirectory_succeedsAfterTransientFailure() async throws {
        mockClient.requestReturnValues.append(.failure(BlePsFtpException.responseError(errorCode: 500)))
        mockClient.requestReturnValues.append(.success(try Self.minimalValidSleepProtoData()))
        mockClient.requestReturnValues.append(
            .failure(BlePsFtpException.responseError(errorCode: Protocol_PbPFtpError.noSuchFileOrDirectory.rawValue))
        )

        let result = try await PolarSleepUtils.readSleepFromDayDirectory(
            client: mockClient,
            date: Date(),
            maxAttempts: 3,
            baseRetryDelayMs: 1
        )

        XCTAssertNotNil(result.sleepStartTime, "Expected the retried attempt to succeed")
        XCTAssertEqual(mockClient.requestCalls.count, 3, "Expected: failing sleep GET, retried sleep GET, then skin-temp GET")
    }

    // GIVEN a day's sleep file read keeps failing for a real reason on every attempt
    // WHEN readSleepFromDayDirectory exhausts all attempts
    // THEN it throws the last underlying error (same externally-visible failure as before this
    //      fix, so getSleep's existing per-date catch-and-skip behavior is unaffected) instead of
    //      hanging or silently returning a placeholder
    func testReadSleepFromDayDirectory_exhaustsAttempts_throwsLastError() async throws {
        mockClient.requestReturnValues.append(.failure(BlePsFtpException.responseError(errorCode: 500)))
        mockClient.requestReturnValues.append(.failure(BlePsFtpException.responseError(errorCode: 501)))
        mockClient.requestReturnValues.append(.failure(BlePsFtpException.responseError(errorCode: 502)))

        do {
            _ = try await PolarSleepUtils.readSleepFromDayDirectory(
                client: mockClient,
                date: Date(),
                maxAttempts: 3,
                baseRetryDelayMs: 1
            )
            XCTFail("Expected readSleepFromDayDirectory to throw after exhausting all attempts")
        } catch BlePsFtpException.responseError(let code) {
            XCTAssertEqual(code, 502, "Expected the error from the last attempt")
        }
        XCTAssertEqual(mockClient.requestCalls.count, 3, "Expected exactly maxAttempts sleep-file GET attempts, no more")
    }

    // GIVEN a day genuinely has no sleep file (the device reports "no such file")
    // WHEN readSleepFromDayDirectory reads that date
    // THEN it must not retry at all — this is the ordinary "no data" case, not a failure
    func testReadSleepFromDayDirectory_noSuchFile_doesNotRetry() async throws {
        mockClient.requestReturnValues.append(
            .failure(BlePsFtpException.responseError(errorCode: Protocol_PbPFtpError.noSuchFileOrDirectory.rawValue))
        )

        let result = try await PolarSleepUtils.readSleepFromDayDirectory(
            client: mockClient,
            date: Date(),
            maxAttempts: 3,
            baseRetryDelayMs: 1
        )

        XCTAssertNil(result.sleepStartTime)
        XCTAssertEqual(mockClient.requestCalls.count, 2, "Expected a single attempt (sleep GET + skin-temp GET), no retries, for a legitimate 'no data' date")
    }

    // GIVEN every attempt fails for a real reason
    // WHEN readSleepFromDayDirectory retries
    // THEN the delay between attempts doubles each time (exponential backoff: base, base*2,
    //      base*4, ...), giving a slow-to-recover session more time on later attempts instead of
    //      hammering it at a fixed interval
    func testReadSleepFromDayDirectory_backoffDelayDoublesBetweenAttempts() async throws {
        mockClient.requestReturnValues.append(.failure(BlePsFtpException.responseError(errorCode: 500)))
        mockClient.requestReturnValues.append(.failure(BlePsFtpException.responseError(errorCode: 500)))
        mockClient.requestReturnValues.append(.failure(BlePsFtpException.responseError(errorCode: 500)))
        mockClient.requestReturnValues.append(.failure(BlePsFtpException.responseError(errorCode: 500)))

        let start = Date()
        do {
            _ = try await PolarSleepUtils.readSleepFromDayDirectory(
                client: mockClient,
                date: Date(),
                maxAttempts: 4,
                baseRetryDelayMs: 20
            )
            XCTFail("Expected readSleepFromDayDirectory to throw after exhausting all attempts")
        } catch {
        }
        let elapsedMs = Date().timeIntervalSince(start) * 1000

        // Delays before attempts 2, 3, 4: 20ms, 40ms, 80ms = 140ms minimum.
        XCTAssertGreaterThanOrEqual(elapsedMs, 140, "Expected the delay between attempts to double (exponential backoff)")
        XCTAssertEqual(mockClient.requestCalls.count, 4)
    }

    // MARK: - Helpers

    static func minimalValidSleepProtoData() throws -> Data {
        try minimalValidSleepProtoDataImpl()
    }

    private static func minimalValidSleepProtoDataImpl() throws -> Data {
        let pbDate: (UInt32, UInt32, UInt32) -> PbDate = { d, mo, y in
            PbDate.with { $0.day = d; $0.month = mo; $0.year = y }
        }
        let pbTime: (UInt32, UInt32, UInt32) -> PbTime = { h, m, s in
            PbTime.with { $0.hour = h; $0.minute = m; $0.seconds = s }
        }
        let localDT: (UInt32, UInt32, UInt32, UInt32, UInt32, UInt32) -> PbLocalDateTime = { h, m, s, d, mo, y in
            PbLocalDateTime.with {
                $0.date = pbDate(d, mo, y); $0.time = pbTime(h, m, s); $0.obsoleteTrusted = true
            }
        }
        let sysDT: (UInt32, UInt32, UInt32, UInt32, UInt32, UInt32) -> PbSystemDateTime = { h, m, s, d, mo, y in
            PbSystemDateTime.with {
                $0.date = pbDate(d, mo, y); $0.time = pbTime(h, m, s); $0.trusted = true
            }
        }
        let proto = Data_PbSleepAnalysisResult.with {
            $0.sleepStartTime  = localDT(23, 0, 0, 1, 1, 2025)
            $0.sleepEndTime    = localDT(7,  0, 0, 2, 1, 2025)
            $0.lastModified    = sysDT(8, 0, 0, 2, 1, 2025)
            $0.sleepGoalMinutes = 480
            $0.sleepResultDate = pbDate(2, 1, 2025)
            $0.originalSleepRange = PbLocalDateTimeRange.with {
                $0.startTime = localDT(23, 0, 0, 1, 1, 2025)
                $0.endTime   = localDT(7,  0, 0, 2, 1, 2025)
            }
        }
        return try proto.serializedData()
    }

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
