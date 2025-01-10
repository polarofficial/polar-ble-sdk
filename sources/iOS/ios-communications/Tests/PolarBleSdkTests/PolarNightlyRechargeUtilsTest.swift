import XCTest
import RxSwift
import RxTest

class PolarNightlyRechargeUtilsTests: XCTestCase {
    
    var mockClient: MockBlePsFtpClient!
    override func setUpWithError() throws {
        mockClient = MockBlePsFtpClient()
    }
    
    override func tearDownWithError() throws {
        mockClient = nil
    }

    func testReadNightlyRechargeData_shouldReturnNightlyRechargeData() {
        // Arrange
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "yyyyMMdd"
        let date = Date()
        let expectedPath = "/U/0/\(dateFormatter.string(from: date))/NR/NR.BPB"

        let proto = Data_PbNightlyRecoveryStatus.with {
            $0.sleepResultDate = PbDate.with { date in
                date.year = 2024
                date.month = 12
                date.day = 5
            }
            $0.createdTimestamp = PbSystemDateTime.with { timestamp in
                timestamp.date = PbDate.with { date in
                    date.year = 2023
                    date.month = 12
                    date.day = 5
                }
                timestamp.time = PbTime.with { time in
                    time.hour = 10
                    time.minute = 0
                    time.seconds = 0
                    time.millis = 0
                }
                timestamp.trusted = true
            }
            $0.modifiedTimestamp = PbSystemDateTime.with { timestamp in
                timestamp.date = PbDate.with { date in
                    date.year = 2023
                    date.month = 12
                    date.day = 5
                }
                timestamp.time = PbTime.with { time in
                    time.hour = 10
                    time.minute = 30
                    time.seconds = 0
                    time.millis = 0
                }
                timestamp.trusted = true
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

        let protoData = try! proto.serializedData()
        mockClient.requestReturnValue = Single.just(protoData)

        let createdTimestamp = DateComponents(calendar: Calendar.current, year: 2023, month: 12, day: 5, hour: 10, minute: 0).date!
        let modifiedTimestamp = DateComponents(calendar: Calendar.current, year: 2023, month: 12, day: 5, hour: 10, minute: 30).date!
        let sleepResultDate = DateComponents(calendar: Calendar.current, year: 2024, month: 12, day: 5).date!

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
        let result = PolarNightlyRechargeUtils.readNightlyRechargeData(client: mockClient, date: date)

        var testResult: PolarNightlyRechargeData?
        let expectation = self.expectation(description: "Read nightly recovery should return nightly recovery data")

        _ = result.subscribe(onSuccess: { data in
            testResult = data
            expectation.fulfill()
        }, onError: { error in
            XCTFail("Unexpected error: \(error)")
        }, onCompleted: {
            XCTFail("Completed without emitting a value")
        })

        wait(for: [expectation], timeout: 1.0)

        // Assert
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

        let actualPath = String(data: mockClient.requestCalls[0], encoding: .utf8)?.trimmingCharacters(in: .controlCharacters)
        XCTAssertEqual(actualPath, expectedPath)
        XCTAssertEqual(mockClient.requestCalls.count, 1)
    }
    
    func testReadNightlyRechargeFromDayDirectory_FileNotFound() {
        // Arrange
        let expectedError = NSError(domain: "File not found", code: 103, userInfo: nil)
        mockClient.requestReturnValue = Single.error(expectedError)

        // Act
        let expectation = XCTestExpectation(description: "Read nightly recovery should complete if nightly recovery file not found")
        
        let disposable = PolarNightlyRechargeUtils.readNightlyRechargeData(client: mockClient, date: Date())
            .subscribe(onSuccess: { nightlyRecoveryData in
                XCTFail("Expected completion, but got data: \(nightlyRecoveryData)")
                expectation.fulfill()
            }, onError: { error in
                XCTFail("Expected completion, but got error: \(error)")
                expectation.fulfill()
            }, onDisposed: {
                expectation.fulfill()
            })

        wait(for: [expectation], timeout: 5)
        disposable.dispose()
    }
}
