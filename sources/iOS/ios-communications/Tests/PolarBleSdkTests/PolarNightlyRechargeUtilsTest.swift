import XCTest
@testable import PolarBleSdk

class PolarNightlyRechargeUtilsTests: XCTestCase {
    
    var mockClient: MockBlePsFtpClient!

    override func setUpWithError() throws {
        mockClient = MockBlePsFtpClient(gattServiceTransmitter: MockPolarGattServiceTransmitter())
    }
    
    override func tearDownWithError() throws {
        mockClient = nil
    }

    func testReadNightlyRechargeData_shouldReturnNightlyRechargeData() async throws {
        // Arrange
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "yyyyMMdd"
        let date = Date()
        let expectedPath = "/U/0/\(dateFormatter.string(from: date))/NR/NR.BPB"

        let proto = Data_PbNightlyRecoveryStatus.with {
            $0.sleepResultDate = PbDate.with { $0.year = 2024; $0.month = 12; $0.day = 5 }
            $0.createdTimestamp = PbSystemDateTime.with {
                $0.date = PbDate.with { $0.year = 2023; $0.month = 12; $0.day = 5 }
                $0.time = PbTime.with { $0.hour = 10; $0.minute = 0; $0.seconds = 0; $0.millis = 0 }
                $0.trusted = true
            }
            $0.modifiedTimestamp = PbSystemDateTime.with {
                $0.date = PbDate.with { $0.year = 2023; $0.month = 12; $0.day = 5 }
                $0.time = PbTime.with { $0.hour = 10; $0.minute = 30; $0.seconds = 0; $0.millis = 0 }
                $0.trusted = true
            }
            $0.ansStatus = 5.5
            $0.recoveryIndicator = 3
            $0.recoveryIndicatorSubLevel = 50
            $0.ansRate = 4
            $0.scoreRateObsolete = 2
            $0.meanNightlyRecoveryRri = 800
            $0.meanNightlyRecoveryRmssd = 50
            $0.meanNightlyRecoveryRespirationInterval = 1000
            $0.meanBaselineRri = 750
            $0.sdBaselineRri = 30
            $0.meanBaselineRmssd = 45
            $0.sdBaselineRmssd = 20
            $0.meanBaselineRespirationInterval = 950
            $0.sdBaselineRespirationInterval = 25
            $0.sleepTip = "Sleep tip 1"
            $0.vitalityTip = "Vitality tip 2"
            $0.exerciseTip = "Exercise tip 3"
        }

        mockClient.requestReturnValue = .success(try proto.serializedData())

        let createdTimestamp = DateComponents(calendar: Calendar.current, year: 2023, month: 12, day: 5, hour: 10, minute: 0).date!
        let modifiedTimestamp = DateComponents(calendar: Calendar.current, year: 2023, month: 12, day: 5, hour: 10, minute: 30).date!
        let sleepResultDate = DateComponents(year: 2024, month: 12, day: 5)

        let expectedResult = PolarNightlyRechargeData(
            createdTimestamp: createdTimestamp,
            modifiedTimestamp: modifiedTimestamp,
            ansStatus: 5.5,
            recoveryIndicator: 3,
            recoveryIndicatorSubLevel: 50,
            ansRate: 4,
            scoreRateObsolete: 2,
            meanNightlyRecoveryRRI: 800,
            meanNightlyRecoveryRMSSD: 50,
            meanNightlyRecoveryRespirationInterval: 1000,
            meanBaselineRRI: 750,
            sdBaselineRRI: 30,
            meanBaselineRMSSD: 45,
            sdBaselineRMSSD: 20,
            meanBaselineRespirationInterval: 950,
            sdBaselineRespirationInterval: 25,
            sleepTip: "Sleep tip 1",
            vitalityTip: "Vitality tip 2",
            exerciseTip: "Exercise tip 3",
            sleepResultDate: sleepResultDate
        )

        // Act
        let testResult = await PolarNightlyRechargeUtils.readNightlyRechargeData(client: mockClient, date: date)

        // Assert
        XCTAssertNotNil(testResult)
        XCTAssertEqual(testResult?.createdTimestamp, expectedResult.createdTimestamp)
        XCTAssertEqual(testResult?.modifiedTimestamp, expectedResult.modifiedTimestamp)
        XCTAssertEqual(testResult?.ansStatus, expectedResult.ansStatus)
        XCTAssertEqual(testResult?.recoveryIndicator, expectedResult.recoveryIndicator)
        XCTAssertEqual(testResult?.recoveryIndicatorSubLevel, expectedResult.recoveryIndicatorSubLevel)
        XCTAssertEqual(testResult?.ansRate, expectedResult.ansRate)
        XCTAssertEqual(testResult?.scoreRateObsolete, expectedResult.scoreRateObsolete)
        XCTAssertEqual(testResult?.meanNightlyRecoveryRRI, expectedResult.meanNightlyRecoveryRRI)
        XCTAssertEqual(testResult?.meanNightlyRecoveryRMSSD, expectedResult.meanNightlyRecoveryRMSSD)
        XCTAssertEqual(testResult?.meanNightlyRecoveryRespirationInterval, expectedResult.meanNightlyRecoveryRespirationInterval)
        XCTAssertEqual(testResult?.meanBaselineRRI, expectedResult.meanBaselineRRI)
        XCTAssertEqual(testResult?.sdBaselineRRI, expectedResult.sdBaselineRRI)
        XCTAssertEqual(testResult?.meanBaselineRMSSD, expectedResult.meanBaselineRMSSD)
        XCTAssertEqual(testResult?.sdBaselineRMSSD, expectedResult.sdBaselineRMSSD)
        XCTAssertEqual(testResult?.meanBaselineRespirationInterval, expectedResult.meanBaselineRespirationInterval)
        XCTAssertEqual(testResult?.sdBaselineRespirationInterval, expectedResult.sdBaselineRespirationInterval)
        XCTAssertEqual(testResult?.sleepTip, expectedResult.sleepTip)
        XCTAssertEqual(testResult?.vitalityTip, expectedResult.vitalityTip)
        XCTAssertEqual(testResult?.exerciseTip, expectedResult.exerciseTip)
        XCTAssertEqual(testResult?.sleepResultDate, expectedResult.sleepResultDate)
        XCTAssertEqual(mockClient.requestCalls.count, 1)

        let actualPath = try Protocol_PbPFtpOperation(serializedBytes: mockClient.requestCalls[0]).path
        XCTAssertEqual(actualPath, expectedPath)
    }
    
    func testReadNightlyRechargeFromDayDirectory_FileNotFound() async {
        // Arrange
        mockClient.requestReturnValue = .failure(NSError(domain: "File not found", code: 103, userInfo: nil))

        // Act — errors are swallowed; method returns nil
        let result = await PolarNightlyRechargeUtils.readNightlyRechargeData(client: mockClient, date: Date())

        // Assert
        XCTAssertNil(result, "Expected nil when nightly recovery file is not found")
    }
}
