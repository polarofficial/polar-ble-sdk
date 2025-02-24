import Foundation
import XCTest
import RxSwift
import RxTest

class PolarAutomaticSamplesUtilsTests: XCTestCase {
    
    var mockClient: MockBlePsFtpClient!
    
    override func setUpWithError() throws {
        mockClient = MockBlePsFtpClient()
    }
    
    override func tearDownWithError() throws {
        mockClient = nil
    }
    
    func testRead247HrSamples_SuccessfulResponse() {
        // Arrange
        let calendar = Calendar(identifier: .gregorian)
        let fromDate = calendar.date(from: DateComponents(year: 2024, month: 11, day: 10, hour: 0, minute: 0, second: 0))!
        let toDate = calendar.date(from: DateComponents(year: 2024, month: 11, day: 19, hour: 0, minute: 0, second: 0))!
        
        let mockDirectoryContent = try! Protocol_PbPFtpDirectory.with {
            $0.entries = [
                Protocol_PbPFtpEntry.with { $0.name = "AUTOS000.BPB"; $0.size = 333 },
                Protocol_PbPFtpEntry.with { $0.name = "AUTOS001.BPB"; $0.size = 444 }
            ]
        }.serializedData()
        
        let mockFileContent1 = try! Data_PbAutomaticSampleSessions.with {
            $0.samples = [
                Data_PbAutomaticHeartRateSamples.with {
                    $0.heartRate = [60, 61, 63].map { UInt32($0) }
                    $0.time = PbTime.with { $0.hour = 10; $0.minute = 12; $0.seconds = 34 }
                    $0.triggerType = Data_PbMeasTriggerType.triggerTypeHighActivity
                },
                Data_PbAutomaticHeartRateSamples.with {
                    $0.heartRate = [80, 81, 83].map { UInt32($0) }
                    $0.time = PbTime.with { $0.hour = 12; $0.minute = 0; $0.seconds = 0 }
                    $0.triggerType = Data_PbMeasTriggerType.triggerTypeManual
                }
            ]
            $0.day = PbDate.with { $0.year = 2024; $0.month = 11; $0.day = 15 }
        }.serializedData()
        
        let mockFileContent2 = try! Data_PbAutomaticSampleSessions.with {
            $0.samples = [
                Data_PbAutomaticHeartRateSamples.with {
                    $0.heartRate = [70, 72, 74].map { UInt32($0) }
                    $0.time = PbTime.with { $0.hour = 16; $0.minute = 49; $0.seconds = 36 }
                    $0.triggerType = Data_PbMeasTriggerType.triggerTypeLowActivity
                },
                Data_PbAutomaticHeartRateSamples.with {
                    $0.heartRate = [90, 91, 93].map { UInt32($0) }
                    $0.time = PbTime.with { $0.hour = 18; $0.minute = 0; $0.seconds = 0 }
                    $0.triggerType = Data_PbMeasTriggerType.triggerTypeTimed
                }
            ]
            $0.day = PbDate.with { $0.year = 2024; $0.month = 11; $0.day = 18 }
        }.serializedData()
        
        mockClient.requestReturnValues = [
            Single.just(mockDirectoryContent),
            Single.just(mockFileContent1),
            Single.just(mockFileContent2)
        ]
        
        // Act
        let expectation = XCTestExpectation(description: "Read HR samples from day directory")
        PolarAutomaticSamplesUtils.read247HrSamples(client: mockClient, fromDate: fromDate, toDate: toDate)
            .subscribe(onSuccess: { samples in
                // Assert
                XCTAssertEqual(samples.count, 4)
                
                let calendar = Calendar.current
                
                let date1 = calendar.date(from: DateComponents(timeZone: TimeZone.gmt, year: 2024, month: 11, day: 15, hour: 10, minute: 12, second: 34))!
                XCTAssertEqual(samples[0].date, date1)
                XCTAssertEqual(samples[0].hrSamples, [60, 61, 63])
                XCTAssertEqual(samples[0].triggerType, .highActivity)
                
                let date2 = calendar.date(from: DateComponents(timeZone: TimeZone.gmt, year: 2024, month: 11, day: 15, hour: 12, minute: 0, second: 0))!
                XCTAssertEqual(samples[1].date, date2)
                XCTAssertEqual(samples[1].hrSamples, [80, 81, 83])
                XCTAssertEqual(samples[1].triggerType, .manual)
                
                let date3 = calendar.date(from: DateComponents(timeZone: TimeZone.gmt, year: 2024, month: 11, day: 18, hour: 16, minute: 49, second: 36))!
                XCTAssertEqual(samples[2].date, date3)
                XCTAssertEqual(samples[2].hrSamples, [70, 72, 74])
                XCTAssertEqual(samples[2].triggerType, .lowActivity)
                
                let date4 = calendar.date(from: DateComponents(timeZone: TimeZone.gmt, year: 2024, month: 11, day: 18, hour: 18, minute: 0, second: 0))!
                XCTAssertEqual(samples[3].date, date4)
                XCTAssertEqual(samples[3].hrSamples, [90, 91, 93])
                XCTAssertEqual(samples[3].triggerType, .timed)
                
                expectation.fulfill()
            })
        
        wait(for: [expectation], timeout: 1)
    }
    
    func testRead247HrSamples_FilterOutSamplesOutsideDateRange() {
        // Arrange
        let calendar = Calendar(identifier: .gregorian)
        let fromDate = calendar.date(from: DateComponents(year: 2024, month: 11, day: 20, hour: 0, minute: 0, second: 0))!
        let toDate = calendar.date(from: DateComponents(year: 2024, month: 11, day: 9, hour: 0, minute: 0, second: 0))!
        
        let mockDirectoryContent = try! Protocol_PbPFtpDirectory.with {
            $0.entries = [
                Protocol_PbPFtpEntry.with { $0.name = "AUTOS000.BPB"; $0.size = 333 },
                Protocol_PbPFtpEntry.with { $0.name = "AUTOS001.BPB"; $0.size = 444 }
            ]
        }.serializedData()
        
        let mockFileContent1 = try! Data_PbAutomaticSampleSessions.with {
            $0.samples = [
                Data_PbAutomaticHeartRateSamples.with {
                    $0.heartRate = [60, 61, 63].map { UInt32($0) }
                    $0.time = PbTime.with { $0.hour = 10; $0.minute = 12; $0.seconds = 34 }
                    $0.triggerType = Data_PbMeasTriggerType.triggerTypeHighActivity
                },
                Data_PbAutomaticHeartRateSamples.with {
                    $0.heartRate = [80, 81, 83].map { UInt32($0) }
                    $0.time = PbTime.with { $0.hour = 12; $0.minute = 0; $0.seconds = 0 }
                    $0.triggerType = Data_PbMeasTriggerType.triggerTypeManual
                }
            ]
            $0.day = PbDate.with { $0.year = 2024; $0.month = 11; $0.day = 15 }
        }.serializedData()
        
        let mockFileContent2 = try! Data_PbAutomaticSampleSessions.with {
            $0.samples = [
                Data_PbAutomaticHeartRateSamples.with {
                    $0.heartRate = [70, 72, 74].map { UInt32($0) }
                    $0.time = PbTime.with { $0.hour = 16; $0.minute = 49; $0.seconds = 36 }
                    $0.triggerType = Data_PbMeasTriggerType.triggerTypeLowActivity
                },
                Data_PbAutomaticHeartRateSamples.with {
                    $0.heartRate = [90, 91, 93].map { UInt32($0) }
                    $0.time = PbTime.with { $0.hour = 18; $0.minute = 0; $0.seconds = 0 }
                    $0.triggerType = Data_PbMeasTriggerType.triggerTypeTimed
                }
            ]
            $0.day = PbDate.with { $0.year = 2024; $0.month = 11; $0.day = 18 }
        }.serializedData()
        
        mockClient.requestReturnValues = [
            Single.just(mockDirectoryContent),
            Single.just(mockFileContent1),
            Single.just(mockFileContent2)
        ]
        
        // Act
        let expectation = XCTestExpectation(description: "No samples in date range")
        PolarAutomaticSamplesUtils.read247HrSamples(client: mockClient, fromDate: fromDate, toDate: toDate)
            .subscribe(onSuccess: { samples in
                // Assert
                XCTAssertEqual(samples.count, 0)
                expectation.fulfill()
            })
        
        wait(for: [expectation], timeout: 1)
    }

    func testRead247PPiSamples_SuccessfulResponse() {
        // Arrange
        let calendar = Calendar(identifier: .gregorian)
        let fromDate = calendar.date(from: DateComponents(timeZone: TimeZone.gmt, year: 2525, month: 2, day: 24, hour: 0, minute: 0, second: 0))!
        let toDate = calendar.date(from: DateComponents(timeZone: TimeZone.gmt, year: 2525, month: 2, day: 27, hour: 0, minute: 0, second: 0))!

        let mockDirectoryContent = try! Protocol_PbPFtpDirectory.with {
            $0.entries = [
                Protocol_PbPFtpEntry.with { $0.name = "AUTOS000.BPB"; $0.size = 333 },
                Protocol_PbPFtpEntry.with { $0.name = "AUTOS001.BPB"; $0.size = 444 }
            ]
        }.serializedData()

        let mockFileContent1 = try! Data_PbAutomaticSampleSessions.with {
            $0.ppiSamples = [
                Data_PbPpIntervalAutoSamples.with {
                    $0.recordingTime = PbTime.with { $0.hour = 1; $0.minute = 2; $0.seconds = 3; $0.millis = 4 }
                    $0.ppi = Data_PbPpIntervalSamples.with {
                        $0.ppiDelta = [2000, -100, -200, -300].map { Int32($0)}
                        $0.ppiErrorEstimateDelta = [10, 1, 2, 3].map { Int32($0)}
                        $0.status = [1,2,3,4].map { UInt32($0)}
                    }
                    $0.triggerType = Data_PbPpIntervalAutoSamples.PbPpIntervalRecordingTriggerType.ppiTriggerTypeAutomatic
                },
                Data_PbPpIntervalAutoSamples.with {
                    $0.recordingTime = PbTime.with { $0.hour = 2; $0.minute = 3; $0.seconds = 4; $0.millis = 5 }
                    $0.ppi = Data_PbPpIntervalSamples.with {
                        $0.ppiDelta = [2000, -100, -200, -300].map { Int32($0)}
                        $0.ppiErrorEstimateDelta = [10, 1, 2, 3].map { Int32($0)}
                        $0.status = [1,2,3,4].map { UInt32($0)}
                    }
                    $0.triggerType = Data_PbPpIntervalAutoSamples.PbPpIntervalRecordingTriggerType.ppiTriggerTypeAutomatic
                }
            ]
            $0.day = PbDate.with { $0.year = 2525; $0.month = 2; $0.day = 25 }
        }.serializedData()

        let mockFileContent2 = try! Data_PbAutomaticSampleSessions.with {
            $0.ppiSamples = [
                Data_PbPpIntervalAutoSamples.with {
                    $0.recordingTime = PbTime.with { $0.hour = 3; $0.minute = 4; $0.seconds = 5; $0.millis = 6 }
                    $0.ppi = Data_PbPpIntervalSamples.with {
                        $0.ppiDelta = [2000, -100, -200, -300].map { Int32($0)}
                        $0.ppiErrorEstimateDelta = [10, 1, 2, 3].map { Int32($0)}
                        $0.status = [1,2,3,4].map { UInt32($0)}
                    }
                    $0.triggerType = Data_PbPpIntervalAutoSamples.PbPpIntervalRecordingTriggerType.ppiTriggerTypeAutomatic
                },
                Data_PbPpIntervalAutoSamples.with {
                    $0.recordingTime = PbTime.with { $0.hour = 5; $0.minute = 6; $0.seconds = 7; $0.millis = 8 }
                    $0.ppi = Data_PbPpIntervalSamples.with {
                        $0.ppiDelta = [2000, -100, -200, -300].map { Int32($0)}
                        $0.ppiErrorEstimateDelta = [10, 1, 2, 3].map { Int32($0)}
                        $0.status = [1,2,3,4].map { UInt32($0)}
                    }
                    $0.triggerType = Data_PbPpIntervalAutoSamples.PbPpIntervalRecordingTriggerType.ppiTriggerTypeAutomatic
                }
            ]
            $0.day = PbDate.with { $0.year = 2525; $0.month = 2; $0.day = 26 }
        }.serializedData()

        mockClient.requestReturnValues = [
            Single.just(mockDirectoryContent),
            Single.just(mockFileContent1),
            Single.just(mockFileContent2)
        ]

        // Act
        let expectation = XCTestExpectation(description: "Read PPi samples from day directory")
        PolarAutomaticSamplesUtils.read247PPiSamples(client: mockClient, fromDate: fromDate, toDate: toDate)
            .subscribe(onSuccess: { samples in
                // Assert
                XCTAssertEqual(samples.count, 2)

                let calendar = Calendar.current

                let date1 = calendar.date(from: DateComponents(timeZone: TimeZone.gmt, year: 2525, month: 2, day: 25, hour: 0, minute: 0, second: 0))!
                let date2 = calendar.date(from: DateComponents(timeZone: TimeZone.gmt, year: 2525, month: 2, day: 26, hour: 0, minute: 0, second: 0))!

                XCTAssertEqual(samples[0].date, date1)
                XCTAssertEqual(samples[1].date, date2)
                XCTAssertEqual(samples[0].ppiSamples.count, 2)
                XCTAssertEqual(samples[0].ppiSamples[0].ppiValueList, [2000, 1900, 1700, 1400])
                XCTAssertEqual(samples[0].ppiSamples[0].ppiErrorEstimateList, [10, 11, 13, 16])
                XCTAssertEqual(samples[0].ppiSamples[0].statusList[0].intervalStatus, Polar247PPiSamplesData.IntervalStatus.INTERVAL_IS_ONLINE)
                XCTAssertEqual(samples[0].ppiSamples[0].statusList[0].movement, Polar247PPiSamplesData.Movement.NO_MOVING_DETECTED)
                XCTAssertEqual(samples[0].ppiSamples[0].statusList[0].intervalStatus, Polar247PPiSamplesData.IntervalStatus.INTERVAL_IS_ONLINE)
                XCTAssertEqual(samples[0].ppiSamples[0].triggerType, Polar247PPiSamplesData.PPiSampleTriggerType.TRIGGER_TYPE_AUTOMATIC)
                XCTAssertEqual(samples[0].ppiSamples[0].startTime, "01:02:03.04")

                expectation.fulfill()
            })
        wait(for: [expectation], timeout: 1)
    }

    func testRead247PPiSamples_filterOutSamplesOutsideDateRange() {
        // Arrange
        let calendar = Calendar(identifier: .gregorian)
        let fromDate = calendar.date(from: DateComponents(timeZone: TimeZone.gmt, year: 2525, month: 2, day: 24, hour: 0, minute: 0, second: 0))!
        let toDate = calendar.date(from: DateComponents(timeZone: TimeZone.gmt, year: 2525, month: 2, day: 25, hour: 0, minute: 0, second: 0))!

        let mockDirectoryContent = try! Protocol_PbPFtpDirectory.with {
            $0.entries = [
                Protocol_PbPFtpEntry.with { $0.name = "AUTOS000.BPB"; $0.size = 333 },
                Protocol_PbPFtpEntry.with { $0.name = "AUTOS001.BPB"; $0.size = 444 }
            ]
        }.serializedData()

        let mockFileContent1 = try! Data_PbAutomaticSampleSessions.with {
            $0.ppiSamples = [
                Data_PbPpIntervalAutoSamples.with {
                    $0.recordingTime = PbTime.with { $0.hour = 1; $0.minute = 2; $0.seconds = 3; $0.millis = 4 }
                    $0.ppi = Data_PbPpIntervalSamples.with {
                        $0.ppiDelta = [2000, -100, -200, -300].map { Int32($0)}
                        $0.ppiErrorEstimateDelta = [10, 1, 2, 3].map { Int32($0)}
                        $0.status = [1,2,3,4].map { UInt32($0)}
                    }
                    $0.triggerType = Data_PbPpIntervalAutoSamples.PbPpIntervalRecordingTriggerType.ppiTriggerTypeAutomatic
                },
                Data_PbPpIntervalAutoSamples.with {
                    $0.recordingTime = PbTime.with { $0.hour = 2; $0.minute = 3; $0.seconds = 4; $0.millis = 5 }
                    $0.ppi = Data_PbPpIntervalSamples.with {
                        $0.ppiDelta = [2000, -100, -200, -300].map { Int32($0)}
                        $0.ppiErrorEstimateDelta = [10, 1, 2, 3].map { Int32($0)}
                        $0.status = [1,2,3,4].map { UInt32($0)}
                    }
                    $0.triggerType = Data_PbPpIntervalAutoSamples.PbPpIntervalRecordingTriggerType.ppiTriggerTypeAutomatic
                }
            ]
            $0.day = PbDate.with { $0.year = 2525; $0.month = 2; $0.day = 25 }
        }.serializedData()

        let mockFileContent2 = try! Data_PbAutomaticSampleSessions.with {
            $0.ppiSamples = [
                Data_PbPpIntervalAutoSamples.with {
                    $0.recordingTime = PbTime.with { $0.hour = 3; $0.minute = 4; $0.seconds = 5; $0.millis = 6 }
                    $0.ppi = Data_PbPpIntervalSamples.with {
                        $0.ppiDelta = [2000, -100, -200, -300].map { Int32($0)}
                        $0.ppiErrorEstimateDelta = [10, 1, 2, 3].map { Int32($0)}
                        $0.status = [1,2,3,4].map { UInt32($0)}
                    }
                    $0.triggerType = Data_PbPpIntervalAutoSamples.PbPpIntervalRecordingTriggerType.ppiTriggerTypeAutomatic
                },
                Data_PbPpIntervalAutoSamples.with {
                    $0.recordingTime = PbTime.with { $0.hour = 5; $0.minute = 6; $0.seconds = 7; $0.millis = 8 }
                    $0.ppi = Data_PbPpIntervalSamples.with {
                        $0.ppiDelta = [2000, -100, -200, -300].map { Int32($0)}
                        $0.ppiErrorEstimateDelta = [10, 1, 2, 3].map { Int32($0)}
                        $0.status = [1,2,3,4].map { UInt32($0)}
                    }
                    $0.triggerType = Data_PbPpIntervalAutoSamples.PbPpIntervalRecordingTriggerType.ppiTriggerTypeAutomatic
                }
            ]
            $0.day = PbDate.with { $0.year = 2525; $0.month = 2; $0.day = 26 }
        }.serializedData()

        mockClient.requestReturnValues = [
            Single.just(mockDirectoryContent),
            Single.just(mockFileContent1),
            Single.just(mockFileContent2)
        ]

        // Act
        let expectation = XCTestExpectation(description: "Read PPi samples from day directory")
        PolarAutomaticSamplesUtils.read247PPiSamples(client: mockClient, fromDate: fromDate, toDate: toDate)
            .subscribe(onSuccess: { samples in
                // Assert
                XCTAssertEqual(samples.count, 1)
                expectation.fulfill()
            })
        wait(for: [expectation], timeout: 1)
    }
}
