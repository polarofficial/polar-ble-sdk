//  Copyright © 2022 Polar. All rights reserved.

import XCTest
@testable import iOSCommunications

final class PmdTimeStampUtilsTest: XCTestCase {
       
    func testAssertInvalidInputSampleRateAndTimeStampZero() throws {
        // Arrange
        let previousTimeStamp:UInt64 = 0
        let timeStamp:UInt64 = 100000 //ns
        let samplesSize:UInt = 100
        let samplingRate:UInt = 0
        
        // Act &  Assert
        XCTAssertThrowsError(try PmdTimeStampUtils.getTimeStamps(
            previousFrameTimeStamp: previousTimeStamp,
            frameTimeStamp: timeStamp,
            samplesSize: samplesSize,
            sampleRate: samplingRate
        )) { error in
            guard case BleGattException.gattDataError = error else {
                return XCTFail()
            }
        }
    }
    
    func testAssertInvalidInputTimeStampsGoesNegative() throws {
        // Arrange
        let previousTimeStamp:UInt64 = 0
        let timeStamp:UInt64 = 100000 //ns
        let samplesSize:UInt = 100
        let samplingRate:UInt = 52
        
        // 1/52Hz = 0.019230769230769s = 19230769ns
        // timeStamp - 19230769ns => Exception
        
        // Act & Assert
        XCTAssertThrowsError(try PmdTimeStampUtils.getTimeStamps(
            previousFrameTimeStamp: previousTimeStamp,
            frameTimeStamp: timeStamp,
            samplesSize: samplesSize,
            sampleRate: samplingRate
        )) { error in
            guard case BleGattException.gattDataError = error else {
                return XCTFail()
            }
        }
    }
    
    func testSamplesTimeStampsBasedOnFrequency() throws {
        // Arrange
        let previousTimeStamp:UInt64 = 0
        let timeStamp:UInt64 = 10000000000 // 10 seconds
        let samplesSize:UInt = 10
        let samplingRate:UInt = 1 // 1Hz
        
        // Act
        let timestamps = try PmdTimeStampUtils.getTimeStamps(
            previousFrameTimeStamp: previousTimeStamp,
            frameTimeStamp: timeStamp,
            samplesSize: samplesSize,
            sampleRate: samplingRate
        )
        
        // Assert
        XCTAssertEqual(samplesSize, UInt(timestamps.count))
        XCTAssertEqual(1000000000, timestamps[0])
        XCTAssertEqual(2000000000, timestamps[1])
        XCTAssertEqual(3000000000, timestamps[2])
        XCTAssertEqual(4000000000, timestamps[3])
        XCTAssertEqual(5000000000, timestamps[4])
        XCTAssertEqual(6000000000, timestamps[5])
        XCTAssertEqual(7000000000, timestamps[6])
        XCTAssertEqual(8000000000, timestamps[7])
        XCTAssertEqual(9000000000, timestamps[8])
        XCTAssertEqual(10000000000, timestamps[9])
    }
    
    func testSamplesTimeBasedOnFrequencyOnlyOneSample() throws {
        // Arrange
        let previousTimeStamp:UInt64 = 0
        let timeStamp:UInt64 = 1000000000 // 1 second
        let samplesSize:UInt = 1
        let samplingRate:UInt = 1 // 1Hz
        
        // Act
        let timestamps = try PmdTimeStampUtils.getTimeStamps(
            previousFrameTimeStamp: previousTimeStamp,
            frameTimeStamp: timeStamp,
            samplesSize: samplesSize,
            sampleRate: samplingRate
        )
        
        // Assert
        XCTAssertEqual(samplesSize, UInt(timestamps.count))
        XCTAssertEqual(1000000000, timestamps[0])
    }
    
    func testSamplesTimeStampsBasedOnPreviousTimeStampTest1() throws {
        // Arrange
        let previousTimeStamp:UInt64 = 1000000000 // 1 second
        let timeStamp:UInt64 = 11000000000 // 11 seconds
        let samplesSize:UInt = 10
        let samplingRate:UInt = 1 // 1Hz
        
        /*
         Index: prev   0    1    2    3    4    5    6    7    8    9
         |____|____|____|____|____|____|____|____|____|____|
         Stamp:   1    2    3    4    5    6    7    8    9   10   11
         */
        // Act
        let timestamps = try PmdTimeStampUtils.getTimeStamps(
            previousFrameTimeStamp: previousTimeStamp,
            frameTimeStamp: timeStamp,
            samplesSize: samplesSize,
            sampleRate: samplingRate
        )
        
        // Assert
        XCTAssertEqual(samplesSize,UInt(timestamps.count))
        XCTAssertEqual(2000000000, timestamps[0])
        XCTAssertEqual(3000000000, timestamps[1])
        XCTAssertEqual(4000000000, timestamps[2])
        XCTAssertEqual(5000000000, timestamps[3])
        XCTAssertEqual(6000000000, timestamps[4])
        XCTAssertEqual(7000000000, timestamps[5])
        XCTAssertEqual(8000000000, timestamps[6])
        XCTAssertEqual(9000000000, timestamps[7])
        XCTAssertEqual(10000000000, timestamps[8])
        XCTAssertEqual(11000000000, timestamps[9])
    }
    
    func testSamplesTimeStampsBasedOnPreviousTimeStampTest2() throws {
        // Arrange
        let previousTimeStamp:UInt64 = 100
        let timeStamp:UInt64 = 2000000000 // 2 seconds
        let samplesSize:UInt = 3
        let samplingRate:UInt = 1 // 1Hz
                
        //val startTimeStamp = lastSampleTimeStamp - (timeStampDelta * (samplesSize - 1))
        
        // (2000000000 - 100) / 3 = 666666633.333333333333333
        /*
         Index: prev                 0               1               2
         |__666666633.33ns__|__666666633ns__|__666666633ns__|
         Stamp:  100     666666733.33    1333333366.66      2000000000
         */
        
        // Act
        let timestamps = try PmdTimeStampUtils.getTimeStamps(
            previousFrameTimeStamp: previousTimeStamp,
            frameTimeStamp: timeStamp,
            samplesSize: samplesSize,
            sampleRate: samplingRate
        )
        
        // Assert
        XCTAssertEqual(samplesSize, UInt(timestamps.count))
        XCTAssertEqual(UInt64(round(666666733.33)), timestamps[0])
        XCTAssertEqual(UInt64(round(1333333366.66)), timestamps[1])
        XCTAssertEqual(2000000000, timestamps[2])
    }
    
    func testSamplesTimeStampsBasedOnPreviousTimeStampOnlyOneSample() throws {
        // Arrange
        let previousTimeStamp:UInt64 = 1000000000 //1 second
        let timeStamp:UInt64 = 2000000000 // 2 seconds
        let samplesSize:UInt = 1
        let samplingRate:UInt = 1 // 1Hz
        
        
        /*
         Index: prev   0
         |____|
         Stamp:   1    2
         */
        
        // Act
        let timestamps = try PmdTimeStampUtils.getTimeStamps(
            previousFrameTimeStamp: previousTimeStamp,
            frameTimeStamp: timeStamp,
            samplesSize: samplesSize,
            sampleRate: samplingRate
        )
        // Assert
        XCTAssertEqual(samplesSize, UInt(timestamps.count))
        XCTAssertEqual(timeStamp, timestamps[0])
    }
    
    func testPerformanceExample() throws {
        // Arrange
        let previousTimeStamp:UInt64 = 0
        let timeStamp:UInt64 = 10000000000 // 10 seconds
        let samplesSize:UInt = 100
        let samplingRate:UInt = 100 // 1Hz
        
        
        self.measure {
            // Act
            do {
                try PmdTimeStampUtils.getTimeStamps(
                    previousFrameTimeStamp: previousTimeStamp,
                    frameTimeStamp: timeStamp,
                    samplesSize: samplesSize,
                    sampleRate: samplingRate
                )
            } catch {
                print("catch")
            }
        }
    }
}
