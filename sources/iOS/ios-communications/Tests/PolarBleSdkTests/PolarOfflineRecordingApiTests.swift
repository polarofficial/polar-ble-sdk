//
//  PolarOffilineRecordingApiTests.swift
//  iOSCommunicationsTests
//
//  Created by Pasi Hytönen on 25.3.2025.
//  Copyright © 2025 Polar. All rights reserved.
//

import XCTest
import RxSwift
import RxTest
import CoreBluetooth

@testable import PolarBleSdk

class PolarBleApiImplWithMockSession: PolarBleApiImpl {
    required init(_ queue: DispatchQueue, features: Set<PolarBleSdkFeature>) {
        fatalError("init(_:features:) has not been implemented")
    }
    
    init(mockDeviceSession: MockBleDeviceSession) {
        self.mockDeviceSession = mockDeviceSession
        super.init(DispatchQueue(label: "test"), features: [])
    }
    let mockDeviceSession: MockBleDeviceSession
    override func sessionFtpClientReady(_ identifier: String) throws -> BleDeviceSession {
        return mockDeviceSession
    }
}

class MockAdvertisementContent: BleAdvertisementContent {
    override var polarDeviceType: String {
        return "360"
    }
}

class MockBleDeviceSession: BleDeviceSession {
    init(mockFtpClient: MockBlePsFtpClient) {
        self.mockFtpClient = mockFtpClient
        super.init(UUID(), advertisementContent: MockAdvertisementContent())
    }
    let mockFtpClient: MockBlePsFtpClient
    public override func fetchGattClient(_ serviceUuid: CBUUID) -> BleGattClientBase? {
        return mockFtpClient
    }
}

final class PolarOfflineRecordingApiTests: XCTestCase {

    var mockClient: MockBlePsFtpClient!
    var mockSession: MockBleDeviceSession!
    var mockGattServiceTransmitterImpl: MockGattServiceTransmitterImpl!
    
    override func setUpWithError() throws {
        mockGattServiceTransmitterImpl = MockGattServiceTransmitterImpl()
        mockClient = MockBlePsFtpClient(gattServiceTransmitter: mockGattServiceTransmitterImpl)
        mockSession = MockBleDeviceSession(mockFtpClient: mockClient)
    }
    
    override func tearDownWithError() throws {
        mockClient = nil
        mockSession = nil
       
    }
    
    // Tests to check that listing offline recordings does not throw errors when date/time
    // of recorded file would fall within one hour of Daylight Saving Time shifts in a few
    // test locales/time zones.
    
    // Note: The tests do not change system time during the tests, only timezone and locale.
    // The system time at the time of test is the current system time of the runtime environment.
    
    func testListOfflineRecordingsFromDaylightSavingsTimeShiftHourForwardInNewYork2025_shouldReturnListing() {
        
        // Sunday, 9 March 2025, 02:00:00 clocks were turned forward 1 hour to
        // Sunday, 9 March 2025, 03:00:00 local daylight time instead.
        
        // Arrange
        let api = PolarBleApiImplWithMockSession(mockDeviceSession: mockSession)
        let testTimeZone = TimeZone(identifier: "America/New_York")!
        let originalTimeZone = mockTimeZoneReturningCurrent(testTimeZone)
        
        let mockUserDirectoryContent = try! Protocol_PbPFtpDirectory.with {
            $0.entries = [
                Protocol_PbPFtpEntry.with { $0.name = "20250309/"; $0.size = 0 }
            ]
        }.serializedData()
        let mockDateDirectoryContent = try! Protocol_PbPFtpDirectory.with {
            $0.entries = [
                Protocol_PbPFtpEntry.with { $0.name = "R/"; $0.size = 0 }
            ]
        }.serializedData()
        let mockRecordingsDirectoryContent = try! Protocol_PbPFtpDirectory.with {
            $0.entries = [
                Protocol_PbPFtpEntry.with { $0.name = "020130/"; $0.size = 0 }
            ]
        }.serializedData()
        let mockRecordingDirectoryContent = try! Protocol_PbPFtpDirectory.with {
            $0.entries = [
                Protocol_PbPFtpEntry.with { $0.name = "HR.REC"; $0.size = 444 }
            ]
        }.serializedData()
        let mockRecordingFileContent = try! Protocol_PbPFtpEntry.with {
            $0.name = "HR.REC"; $0.size = 444
        }.serializedData()
        
        mockClient.requestReturnValues = [
//            Single.error(PolarErrors.fileError(description: "NO PMDFILES.TXT FILE")),
            Single.just(mockUserDirectoryContent),
            Single.just(mockDateDirectoryContent),
            Single.just(mockRecordingsDirectoryContent),
            Single.just(mockRecordingDirectoryContent),
            Single.just(mockRecordingFileContent)
        ]
        
        // Act
        let result = api.listOfflineRecordings("123456")
        
        let expectation = self.expectation(description: "List offline recordings should return data")
        
        _ = result.subscribe(onNext: { entry in
    
            // Assert
            // Time should be shifted one hour forward
            let dateFormatter = DateFormatter()
            dateFormatter.calendar = Calendar(identifier: .iso8601)
            dateFormatter.timeZone = testTimeZone
            dateFormatter.locale = Locale(identifier: "en_US")
            dateFormatter.dateFormat = "yyyy-MM-dd, HH:mm:ss zzzz"
            let dateAsString = dateFormatter.string(from: entry.date)
            XCTAssertEqual(dateAsString, "2025-03-09, 03:01:30 Eastern Daylight Time")
            XCTAssertEqual(entry.path, "/U/0/20250309/R/020130/HR.REC")
            XCTAssertEqual(entry.size, 444)
            XCTAssertEqual(entry.type, PolarDeviceDataType.hr)

            expectation.fulfill()
            
        }, onError: { error in
            XCTFail("Unexpected error: \(error)")
        }, onCompleted: {
            // Do nothing.
        })
        
        wait(for: [expectation], timeout: 1.0)
    
        restoreTimeZone(originalTimeZone)
        if #available(iOS 17.0, *) {
            setenv("TZ", originalTimeZone.identifier, 1)
            CFTimeZoneResetSystem()
            XCTAssertEqual(TimeZone.current.identifier, originalTimeZone.identifier)
        } else {
            NSTimeZone.default = originalTimeZone
            XCTAssertEqual(TimeZone.current.identifier, originalTimeZone.identifier)
        }
    }
    
    private func mockTimeZoneReturningCurrent(_ testTimeZone: TimeZone) -> TimeZone {
        let originalTimeZone = TimeZone.current
        if #available(iOS 17.0, *) {
            setenv("TZ", testTimeZone.identifier, 1)
            CFTimeZoneResetSystem()
        } else {
            //let originalNSTimeZone = NSTimeZone.default
            NSTimeZone.default = testTimeZone
        }
        return originalTimeZone
    }
    
    private func restoreTimeZone(_ originalTimeZone: TimeZone) {
        if #available(iOS 17.0, *) {
            setenv("TZ", originalTimeZone.identifier, 1)
            CFTimeZoneResetSystem()
        } else {
            NSTimeZone.default = originalTimeZone
        }
    }
    
    func testReadOfflineRecordingBeforeDaylightSavingsTimeShiftHourBackwardInNewYork2025_shouldReturnListing() {
        
        // Sunday, 2 November 2025, 02:00:00 clocks are turned backward 1 hour to
        // Sunday, 2 November 2025, 01:00:00 local standard time instead.
        
        // Arrange
        
        let api = PolarBleApiImplWithMockSession(mockDeviceSession: mockSession)
        let testTimeZone = TimeZone(identifier: "America/New_York")!
        let originalTimeZone = mockTimeZoneReturningCurrent(testTimeZone)
        
        let mockUserDirectoryContent = try! Protocol_PbPFtpDirectory.with {
            $0.entries = [
                Protocol_PbPFtpEntry.with { $0.name = "20251102/"; $0.size = 0 }
            ]
        }.serializedData()
        let mockDateDirectoryContent = try! Protocol_PbPFtpDirectory.with {
            $0.entries = [
                Protocol_PbPFtpEntry.with { $0.name = "R/"; $0.size = 0 }
            ]
        }.serializedData()
        let mockRecordingsDirectoryContent = try! Protocol_PbPFtpDirectory.with {
            $0.entries = [
                Protocol_PbPFtpEntry.with { $0.name = "013007/"; $0.size = 0 }
            ]
        }.serializedData()
        let mockRecordingDirectoryContent = try! Protocol_PbPFtpDirectory.with {
            $0.entries = [
                Protocol_PbPFtpEntry.with { $0.name = "HR.REC"; $0.size = 444 }
            ]
        }.serializedData()
        let mockRecordingFileContent = try! Protocol_PbPFtpEntry.with {
            $0.name = "HR.REC"; $0.size = 444
        }.serializedData()
        
        mockClient.requestReturnValues = [
//            Single.error(PolarErrors.fileError(description: "NO PMDFILES.TXT FILE")),
            Single.just(mockUserDirectoryContent),
            Single.just(mockDateDirectoryContent),
            Single.just(mockRecordingsDirectoryContent),
            Single.just(mockRecordingDirectoryContent),
            Single.just(mockRecordingFileContent)
        ]
        
        // Act
        
        let result = api.listOfflineRecordings("123456")
        
        let expectation = self.expectation(description: "List offline recordings should return data")
        
        _ = result.subscribe(onNext: { entry in
    
            // Assert
           
            let dateFormatter = DateFormatter()
            dateFormatter.calendar = Calendar(identifier: .iso8601)
            dateFormatter.timeZone = testTimeZone
            dateFormatter.locale = Locale(identifier: "en_US")
            dateFormatter.dateFormat = "yyyy-MM-dd, HH:mm:ss zzzz"
            let dateAsString = dateFormatter.string(from: entry.date)
            XCTAssertEqual(dateAsString, "2025-11-02, 01:30:07 Eastern Standard Time")
            
            XCTAssertEqual(entry.path, "/U/0/20251102/R/013007/HR.REC")
            XCTAssertEqual(entry.size, 444)
            XCTAssertEqual(entry.type, PolarDeviceDataType.hr)

            expectation.fulfill()
            
        }, onError: { error in
            XCTFail("Unexpected error: \(error)")
        }, onCompleted: {
            // Do nothing.
        })
        
        wait(for: [expectation], timeout: 1.0)
        restoreTimeZone(originalTimeZone)
    }
    
    
    func testReadOfflineRecordingsAfterDaylightSavingsTimeShiftHourBackwardInNewYork2025_shouldReturnListing() {
        
        // Sunday, 2 November 2025, 02:00:00 clocks are turned backward 1 hour to
        // Sunday, 2 November 2025, 01:00:00 local standard time instead.
        
        // Arrange
        
        let api = PolarBleApiImplWithMockSession(mockDeviceSession: mockSession)
        let testTimeZone = TimeZone(identifier: "America/New_York")!
        let originalTimeZone = mockTimeZoneReturningCurrent(testTimeZone)
        
        let mockUserDirectoryContent = try! Protocol_PbPFtpDirectory.with {
            $0.entries = [
                Protocol_PbPFtpEntry.with { $0.name = "20251102/"; $0.size = 0 }
            ]
        }.serializedData()
        let mockDateDirectoryContent = try! Protocol_PbPFtpDirectory.with {
            $0.entries = [
                Protocol_PbPFtpEntry.with { $0.name = "R/"; $0.size = 0 }
            ]
        }.serializedData()
        let mockRecordingsDirectoryContent = try! Protocol_PbPFtpDirectory.with {
            $0.entries = [
                Protocol_PbPFtpEntry.with { $0.name = "020130/"; $0.size = 0 }
            ]
        }.serializedData()
        let mockRecordingDirectoryContent = try! Protocol_PbPFtpDirectory.with {
            $0.entries = [
                Protocol_PbPFtpEntry.with { $0.name = "HR.REC"; $0.size = 444 }
            ]
        }.serializedData()
        let mockRecordingFileContent = try! Protocol_PbPFtpEntry.with {
            $0.name = "HR.REC"; $0.size = 444
        }.serializedData()
        
        mockClient.requestReturnValues = [
//            Single.error(PolarErrors.fileError(description: "NO PMDFILES.TXT FILE")),
            Single.just(mockUserDirectoryContent),
            Single.just(mockDateDirectoryContent),
            Single.just(mockRecordingsDirectoryContent),
            Single.just(mockRecordingDirectoryContent),
            Single.just(mockRecordingFileContent)
        ]
        
        // Act
        
        let result = api.listOfflineRecordings("123456")
        
        let expectation = self.expectation(description: "List offline recordings should return data")
        
        _ = result.subscribe(onNext: { entry in
    
            // Assert
            
            let dateFormatter = DateFormatter()
            dateFormatter.calendar = Calendar(identifier: .iso8601)
            dateFormatter.timeZone = testTimeZone
            dateFormatter.locale = Locale(identifier: "en_US")
            dateFormatter.dateFormat = "yyyy-MM-dd, HH:mm:ss zzzz"
            let dateAsString = dateFormatter.string(from: entry.date)
            XCTAssertEqual(dateAsString, "2025-11-02, 02:01:30 Eastern Standard Time")
            
            XCTAssertEqual(entry.path, "/U/0/20251102/R/020130/HR.REC")
            XCTAssertEqual(entry.size, 444)
            XCTAssertEqual(entry.type, PolarDeviceDataType.hr)

            expectation.fulfill()
            
        }, onError: { error in
            XCTFail("Unexpected error: \(error)")
        }, onCompleted: {
            // Do nothing.
        })
        
        wait(for: [expectation], timeout: 1.0)
        restoreTimeZone(originalTimeZone)
        
    }
    
    
    func testReadOfflineRecordingsAfterDaylightSavingsTimeShiftHourForwardInHelsinki2025_shouldReturnListing() {
        
        //Sunday, 30 March 2025, 03:00:00 clocks are turned forward 1 hour to
        //Sunday, 30 March 2025, 04:00:00 local daylight time instead.
        
        let api = PolarBleApiImplWithMockSession(mockDeviceSession: mockSession)
        let testTimeZone = TimeZone(identifier: "Europe/Helsinki")!
        let originalTimeZone = mockTimeZoneReturningCurrent(testTimeZone)
        
        let mockUserDirectoryContent = try! Protocol_PbPFtpDirectory.with {
            $0.entries = [
                Protocol_PbPFtpEntry.with { $0.name = "20250330/"; $0.size = 0 }
            ]
        }.serializedData()
        let mockDateDirectoryContent = try! Protocol_PbPFtpDirectory.with {
            $0.entries = [
                Protocol_PbPFtpEntry.with { $0.name = "R/"; $0.size = 0 }
            ]
        }.serializedData()
        let mockRecordingsDirectoryContent = try! Protocol_PbPFtpDirectory.with {
            $0.entries = [
                Protocol_PbPFtpEntry.with { $0.name = "031500/"; $0.size = 0 }
            ]
        }.serializedData()
        let mockRecordingDirectoryContent = try! Protocol_PbPFtpDirectory.with {
            $0.entries = [
                Protocol_PbPFtpEntry.with { $0.name = "HR.REC"; $0.size = 444 }
            ]
        }.serializedData()
        let mockRecordingFileContent = try! Protocol_PbPFtpEntry.with {
            $0.name = "HR.REC"; $0.size = 444
        }.serializedData()
        
        mockClient.requestReturnValues = [
//            Single.error(PolarErrors.fileError(description: "NO PMDFILES.TXT FILE")),
            Single.just(mockUserDirectoryContent),
            Single.just(mockDateDirectoryContent),
            Single.just(mockRecordingsDirectoryContent),
            Single.just(mockRecordingDirectoryContent),
            Single.just(mockRecordingFileContent)
        ]
        
        // Act
        
        let result = api.listOfflineRecordings("123456")
        
        let expectation = self.expectation(description: "List offline recordings should return data")
        
        _ = result.subscribe(onNext: { entry in
    
            // Assert
           
            // Time should be shifted one hour forward
            let dateFormatter = DateFormatter()
            dateFormatter.calendar = Calendar(identifier: .iso8601)
            dateFormatter.timeZone = testTimeZone
            dateFormatter.locale = Locale(identifier: "fi_FI")
            dateFormatter.dateFormat = "yyyy-MM-dd, HH:mm:ss zzzz"
            let dateAsString = dateFormatter.string(from: entry.date)
            XCTAssertEqual(dateAsString, "2025-03-30, 04:15:00 Itä-Euroopan kesäaika")
            
            XCTAssertEqual(entry.path, "/U/0/20250330/R/031500/HR.REC")
            XCTAssertEqual(entry.size, 444)
            XCTAssertEqual(entry.type, PolarDeviceDataType.hr)

            expectation.fulfill()
            
        }, onError: { error in
            XCTFail("Unexpected error: \(error)")
        }, onCompleted: {
            // Do nothing.
        })
        
        wait(for: [expectation], timeout: 1.0)
        restoreTimeZone(originalTimeZone)
        
    }
    
    func testReadOfflineRecordingsBeforeDaylightSavingsTimeShiftHourBackwardInHelsinki2025_shouldReturnListing() {
        
        //Sunday, 26 October 2025, 04:00:00 clocks are turned backward 1 hour to
        //Sunday, 26 October 2025, 03:00:00 local standard time instead.
        
        let api = PolarBleApiImplWithMockSession(mockDeviceSession: mockSession)
        let testTimeZone = TimeZone(identifier: "Europe/Helsinki")!
        let originalTimeZone = mockTimeZoneReturningCurrent(testTimeZone)

        let mockUserDirectoryContent = try! Protocol_PbPFtpDirectory.with {
            $0.entries = [
                Protocol_PbPFtpEntry.with { $0.name = "20251026/"; $0.size = 0 }
            ]
        }.serializedData()
        let mockDateDirectoryContent = try! Protocol_PbPFtpDirectory.with {
            $0.entries = [
                Protocol_PbPFtpEntry.with { $0.name = "R/"; $0.size = 0 }
            ]
        }.serializedData()
        let mockRecordingsDirectoryContent = try! Protocol_PbPFtpDirectory.with {
            $0.entries = [
                Protocol_PbPFtpEntry.with { $0.name = "031500/"; $0.size = 0 }
            ]
        }.serializedData()
        let mockRecordingDirectoryContent = try! Protocol_PbPFtpDirectory.with {
            $0.entries = [
                Protocol_PbPFtpEntry.with { $0.name = "HR.REC"; $0.size = 444 }
            ]
        }.serializedData()
        let mockRecordingFileContent = try! Protocol_PbPFtpEntry.with {
            $0.name = "HR.REC"; $0.size = 444
        }.serializedData()
        
        mockClient.requestReturnValues = [
//            Single.error(PolarErrors.fileError(description: "NO PMDFILES.TXT FILE")),
            Single.just(mockUserDirectoryContent),
            Single.just(mockDateDirectoryContent),
            Single.just(mockRecordingsDirectoryContent),
            Single.just(mockRecordingDirectoryContent),
            Single.just(mockRecordingFileContent)
        ]
        
        // Act
        
        let result = api.listOfflineRecordings("123456")
        
        let expectation = self.expectation(description: "List offline recordings should return data")
        
        _ = result.subscribe(onNext: { entry in
    
            // Assert
            let dateFormatter = DateFormatter()
            dateFormatter.calendar = Calendar(identifier: .iso8601)
            dateFormatter.timeZone = testTimeZone
            dateFormatter.locale = Locale(identifier: "fi_FI")
            dateFormatter.dateFormat = "yyyy-MM-dd, HH:mm:ss zzzz"
            let dateAsString = dateFormatter.string(from: entry.date)
            XCTAssertEqual(dateAsString, "2025-10-26, 03:15:00 Itä-Euroopan normaaliaika")
            
            XCTAssertEqual(entry.path, "/U/0/20251026/R/031500/HR.REC")
            XCTAssertEqual(entry.size, 444)
            XCTAssertEqual(entry.type, PolarDeviceDataType.hr)

            expectation.fulfill()
            
        }, onError: { error in
            XCTFail("Unexpected error: \(error)")
        }, onCompleted: {
            // Do nothing.
        })
        
        wait(for: [expectation], timeout: 1.0)
        restoreTimeZone(originalTimeZone)
        
    }
    
    func testReadOfflineRecordingsAfterDaylightSavingsTimeShiftHourBackwardInHelsinki2025_shouldReturnListing() {
        
        //Sunday, 26 October 2025, 04:00:00 clocks are turned backward 1 hour to
        //Sunday, 26 October 2025, 03:00:00 local standard time instead.
        
        let api = PolarBleApiImplWithMockSession(mockDeviceSession: mockSession)
        let testTimeZone = TimeZone(identifier: "Europe/Helsinki")!
        let originalTimeZone = mockTimeZoneReturningCurrent(testTimeZone)
        
        let mockUserDirectoryContent = try! Protocol_PbPFtpDirectory.with {
            $0.entries = [
                Protocol_PbPFtpEntry.with { $0.name = "20251026/"; $0.size = 0 }
            ]
        }.serializedData()
        let mockDateDirectoryContent = try! Protocol_PbPFtpDirectory.with {
            $0.entries = [
                Protocol_PbPFtpEntry.with { $0.name = "R/"; $0.size = 0 }
            ]
        }.serializedData()
        let mockRecordingsDirectoryContent = try! Protocol_PbPFtpDirectory.with {
            $0.entries = [
                Protocol_PbPFtpEntry.with { $0.name = "041000/"; $0.size = 0 }
            ]
        }.serializedData()
        let mockRecordingDirectoryContent = try! Protocol_PbPFtpDirectory.with {
            $0.entries = [
                Protocol_PbPFtpEntry.with { $0.name = "HR.REC"; $0.size = 444 }
            ]
        }.serializedData()
        let mockRecordingFileContent = try! Protocol_PbPFtpEntry.with {
            $0.name = "HR.REC"; $0.size = 444
        }.serializedData()
        
        mockClient.requestReturnValues = [
//            Single.error(PolarErrors.fileError(description: "NO PMDFILES.TXT FILE")),
            Single.just(mockUserDirectoryContent),
            Single.just(mockDateDirectoryContent),
            Single.just(mockRecordingsDirectoryContent),
            Single.just(mockRecordingDirectoryContent),
            Single.just(mockRecordingFileContent)
        ]
        
        // Act
        
        let result = api.listOfflineRecordings("123456")
        
        let expectation = self.expectation(description: "List offline recordings should return data")
        
        _ = result.subscribe(onNext: { entry in
    
            // Assert

            let dateFormatter = DateFormatter()
            dateFormatter.calendar = Calendar(identifier: .iso8601)
            dateFormatter.timeZone = testTimeZone
            dateFormatter.locale = Locale(identifier: "fi_FI")
            dateFormatter.dateFormat = "yyyy-MM-dd, HH:mm:ss zzzz"
            let dateAsString = dateFormatter.string(from: entry.date)
            XCTAssertEqual(dateAsString, "2025-10-26, 04:10:00 Itä-Euroopan normaaliaika")
            
            XCTAssertEqual(entry.path, "/U/0/20251026/R/041000/HR.REC")
            XCTAssertEqual(entry.size, 444)
            XCTAssertEqual(entry.type, PolarDeviceDataType.hr)

            expectation.fulfill()
            
        }, onError: { error in
            XCTFail("Unexpected error: \(error)")
        }, onCompleted: {
            // Do nothing.
        })
        
        wait(for: [expectation], timeout: 1.0)
        restoreTimeZone(originalTimeZone)
    }
}
