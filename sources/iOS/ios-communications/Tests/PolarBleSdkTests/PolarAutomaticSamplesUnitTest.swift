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
                
                let date1 = calendar.date(from: DateComponents(year: 2024, month: 11, day: 15, hour: 10, minute: 12, second: 34))!
                XCTAssertEqual(samples[0].date, date1)
                XCTAssertEqual(samples[0].hrSamples, [60, 61, 63])
                XCTAssertEqual(samples[0].triggerType, .highActivity)
                
                let date2 = calendar.date(from: DateComponents(year: 2024, month: 11, day: 15, hour: 12, minute: 0, second: 0))!
                XCTAssertEqual(samples[1].date, date2)
                XCTAssertEqual(samples[1].hrSamples, [80, 81, 83])
                XCTAssertEqual(samples[1].triggerType, .manual)
                
                let date3 = calendar.date(from: DateComponents(year: 2024, month: 11, day: 18, hour: 16, minute: 49, second: 36))!
                XCTAssertEqual(samples[2].date, date3)
                XCTAssertEqual(samples[2].hrSamples, [70, 72, 74])
                XCTAssertEqual(samples[2].triggerType, .lowActivity)
                
                let date4 = calendar.date(from: DateComponents(year: 2024, month: 11, day: 18, hour: 18, minute: 0, second: 0))!
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
}