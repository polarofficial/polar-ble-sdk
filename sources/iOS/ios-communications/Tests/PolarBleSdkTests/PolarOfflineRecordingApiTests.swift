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
        let originalTimeZone = TimeZone.current
        let testTimeZone = TimeZone(identifier: "America/New_York")!
        
        // Time zone of system is set for the test duration
        setenv("TZ", testTimeZone.identifier, 1)
        CFTimeZoneResetSystem()
        
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
            
            XCTAssertEqual(TimeZone.current.identifier, testTimeZone.identifier)
           
            // Time should be shifted one hour forward
            let dateFormatter = DateFormatter()
            dateFormatter.calendar = Calendar(identifier: .iso8601)
            dateFormatter.timeZone = testTimeZone
            dateFormatter.locale = Locale(identifier: "en_US")
            dateFormatter.dateStyle = .short
            dateFormatter.timeStyle = .full
            let dateAsString = dateFormatter.string(from: entry.date)
            XCTAssertEqual(dateAsString, "3/9/25, 3:01:30 AM Eastern Daylight Time")
            
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
            
        setenv("TZ", originalTimeZone.identifier, 1)
        CFTimeZoneResetSystem()
        XCTAssertEqual(TimeZone.current.identifier, originalTimeZone.identifier)
        
    }
    
    func testReadOfflineRecordingBeforeDaylightSavingsTimeShiftHourBackwardInNewYork2025_shouldReturnListing() {
        
        // Sunday, 2 November 2025, 02:00:00 clocks are turned backward 1 hour to
        // Sunday, 2 November 2025, 01:00:00 local standard time instead.
        
        // Arrange
        
        let api = PolarBleApiImplWithMockSession(mockDeviceSession: mockSession)
        let originalTimeZone = TimeZone.current
        let testTimeZone = TimeZone(identifier: "America/New_York")!
        setenv("TZ", testTimeZone.identifier, 1)
        CFTimeZoneResetSystem()
        
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
            
            XCTAssertEqual(TimeZone.current.identifier, testTimeZone.identifier)
           
            let dateFormatter = DateFormatter()
            dateFormatter.calendar = Calendar(identifier: .iso8601)
            dateFormatter.timeZone = testTimeZone
            dateFormatter.locale = Locale(identifier: "en_US")
            dateFormatter.dateStyle = .short
            dateFormatter.timeStyle = .full
            let dateAsString = dateFormatter.string(from: entry.date)
            XCTAssertEqual(dateAsString, "11/2/25, 1:30:07 AM Eastern Standard Time")
            
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
            
        setenv("TZ", originalTimeZone.identifier, 1)
        CFTimeZoneResetSystem()
        XCTAssertEqual(TimeZone.current.identifier, originalTimeZone.identifier)
        
    }
    
    
    func testReadOfflineRecordingsAfterDaylightSavingsTimeShiftHourBackwardInNewYork2025_shouldReturnListing() {
        
        // Sunday, 2 November 2025, 02:00:00 clocks are turned backward 1 hour to
        // Sunday, 2 November 2025, 01:00:00 local standard time instead.
        
        // Arrange
        
        let api = PolarBleApiImplWithMockSession(mockDeviceSession: mockSession)
        let originalTimeZone = TimeZone.current
        let testTimeZone = TimeZone(identifier: "America/New_York")!
        setenv("TZ", testTimeZone.identifier, 1)
        CFTimeZoneResetSystem()
        
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
            
            XCTAssertEqual(TimeZone.current.identifier, testTimeZone.identifier)
           
            let dateFormatter = DateFormatter()
            dateFormatter.calendar = Calendar(identifier: .iso8601)
            dateFormatter.timeZone = testTimeZone
            dateFormatter.locale = Locale(identifier: "en_US")
            dateFormatter.dateStyle = .short
            dateFormatter.timeStyle = .full
            let dateAsString = dateFormatter.string(from: entry.date)
            XCTAssertEqual(dateAsString, "11/2/25, 2:01:30 AM Eastern Standard Time")
            
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
            
        setenv("TZ", originalTimeZone.identifier, 1)
        CFTimeZoneResetSystem()
        XCTAssertEqual(TimeZone.current.identifier, originalTimeZone.identifier)
        
    }
    
    
    func testReadOfflineRecordingsAfterDaylightSavingsTimeShiftHourForwardInHelsinki2025_shouldReturnListing() {
        
        //Sunday, 30 March 2025, 03:00:00 clocks are turned forward 1 hour to
        //Sunday, 30 March 2025, 04:00:00 local daylight time instead.
        
        let api = PolarBleApiImplWithMockSession(mockDeviceSession: mockSession)
        let originalTimeZone = TimeZone.current
        let testTimeZone = TimeZone(identifier: "Europe/Helsinki")!
        setenv("TZ", testTimeZone.identifier, 1)
        CFTimeZoneResetSystem()

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
            
            XCTAssertEqual(TimeZone.current.identifier, testTimeZone.identifier)
           
            // Time should be shifted one hour forward
            let dateFormatter = DateFormatter()
            dateFormatter.calendar = Calendar(identifier: .iso8601)
            dateFormatter.timeZone = testTimeZone
            dateFormatter.locale = Locale(identifier: "fi_FI")
            dateFormatter.dateStyle = .short
            dateFormatter.timeStyle = .full
            let dateAsString = dateFormatter.string(from: entry.date)
            XCTAssertEqual(dateAsString, "30.3.2025 klo 4.15.00 Itä-Euroopan kesäaika")
            
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
            
        setenv("TZ", originalTimeZone.identifier, 1)
        CFTimeZoneResetSystem()
        XCTAssertEqual(TimeZone.current.identifier, originalTimeZone.identifier)
        
    }
    
    func testReadOfflineRecordingsBeforeDaylightSavingsTimeShiftHourBackwardInHelsinki2025_shouldReturnListing() {
        
        //Sunday, 26 October 2025, 04:00:00 clocks are turned backward 1 hour to
        //Sunday, 26 October 2025, 03:00:00 local standard time instead.
        
        let api = PolarBleApiImplWithMockSession(mockDeviceSession: mockSession)
        let originalTimeZone = TimeZone.current
        let testTimeZone = TimeZone(identifier: "Europe/Helsinki")!
        setenv("TZ", testTimeZone.identifier, 1)
        CFTimeZoneResetSystem()

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
            
            XCTAssertEqual(TimeZone.current.identifier, testTimeZone.identifier)
           
            let dateFormatter = DateFormatter()
            dateFormatter.calendar = Calendar(identifier: .iso8601)
            dateFormatter.timeZone = testTimeZone
            dateFormatter.locale = Locale(identifier: "fi_FI")
            dateFormatter.dateStyle = .short
            dateFormatter.timeStyle = .full
            let dateAsString = dateFormatter.string(from: entry.date)
            XCTAssertEqual(dateAsString, "26.10.2025 klo 3.15.00 Itä-Euroopan normaaliaika")
            
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
            
        setenv("TZ", originalTimeZone.identifier, 1)
        CFTimeZoneResetSystem()
        XCTAssertEqual(TimeZone.current.identifier, originalTimeZone.identifier)
        
    }
    
    func testReadOfflineRecordingsAfterDaylightSavingsTimeShiftHourBackwardInHelsinki2025_shouldReturnListing() {
        
        //Sunday, 26 October 2025, 04:00:00 clocks are turned backward 1 hour to
        //Sunday, 26 October 2025, 03:00:00 local standard time instead.
        
        let api = PolarBleApiImplWithMockSession(mockDeviceSession: mockSession)
        let originalTimeZone = TimeZone.current
        let testTimeZone = TimeZone(identifier: "Europe/Helsinki")!
        setenv("TZ", testTimeZone.identifier, 1)
        CFTimeZoneResetSystem()

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
            
            XCTAssertEqual(TimeZone.current.identifier, testTimeZone.identifier)
           
            let dateFormatter = DateFormatter()
            dateFormatter.calendar = Calendar(identifier: .iso8601)
            dateFormatter.timeZone = testTimeZone
            dateFormatter.locale = Locale(identifier: "fi_FI")
            dateFormatter.dateStyle = .short
            dateFormatter.timeStyle = .full
            let dateAsString = dateFormatter.string(from: entry.date)
            XCTAssertEqual(dateAsString, "26.10.2025 klo 4.10.00 Itä-Euroopan normaaliaika")
            
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
            
        setenv("TZ", originalTimeZone.identifier, 1)
        CFTimeZoneResetSystem()
        XCTAssertEqual(TimeZone.current.identifier, originalTimeZone.identifier)
        
    }
}
