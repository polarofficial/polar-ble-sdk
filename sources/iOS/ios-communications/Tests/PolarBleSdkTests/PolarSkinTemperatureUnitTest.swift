//
//  Copyright © 2025 Polar. All rights reserved.
//

import XCTest
import RxSwift
import RxTest

class PolarSkinTemperatureUtilsTests: XCTestCase {
    
    var mockClient: MockBlePsFtpClient!

    override func setUpWithError() throws {
        mockClient = MockBlePsFtpClient()
    }

    override func tearDownWithError() throws {
        mockClient = nil
    }
    
    func testReadSkinTemperatureDataFromDayDirectory_SuccessfulResponse() {
        // Arrange
        let date = Date()
        var device = PbDeviceId()
        device.deviceID = "C8D9G10F11H12"

        var proto = Data_TemperatureMeasurementPeriod()
        proto.measurementType = .tmSkinTemperature
        proto.sensorLocation = .slProximal

        proto.temperatureMeasurementSamples.append(contentsOf: PolarSkinTemperatureUtilsTests.buildSkinTemperatureSamplesProto())

        let responseData = try! proto.serializedData()
        mockClient.requestReturnValue = Single.just(responseData)

        let expectedResult = PolarSkinTemperatureData.PolarSkinTemperatureResult(
            date: date,
            sensorLocation: .SL_PROXIMAL,
            measurementType: .TM_SKIN_TEMPERATURE,
            skinTemperatureList: PolarSkinTemperatureUtilsTests.buildSkinTemperatureExpectedData()
        )
        // Act
        let mockSkinTempData: PolarSkinTemperatureData.PolarSkinTemperatureResult
        let expectation = XCTestExpectation(description: "Read skin temperature data from day directory")

        let result = PolarSkinTemperatureUtils.readSkinTemperatureData(client: mockClient, date: date)

        var testResult: PolarSkinTemperatureData.PolarSkinTemperatureResult?

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
        XCTAssertEqual(testResult?.measurementType, expectedResult.measurementType)
        XCTAssertEqual(testResult?.sensorLocation, expectedResult.sensorLocation)
        XCTAssertEqual(testResult?.skinTemperatureList?.first?.recordingTimeDeltaMs, expectedResult.skinTemperatureList?.first?.recordingTimeDeltaMs)
        XCTAssertEqual(testResult?.skinTemperatureList?.first?.temperature, expectedResult.skinTemperatureList?.first?.temperature)
    }
    
    func testReadSkinTemperatureDataFromDayDirectory_FileNotFound() {
        // Arrange
        let expectedError = NSError(domain: "File not found", code: 103, userInfo: nil)
        mockClient.requestReturnValue = Single.error(expectedError)

        // Act
        let expectationSuccess = XCTestExpectation(description: "Read skin temperature should complete if skin temperature file is not found")
        let expectationFailure = XCTestExpectation(description: "Read skin temperature should fail.")
        expectationFailure.isInverted = true
        
        let disposable = PolarSkinTemperatureUtils.readSkinTemperatureData(client: mockClient, date: Date())
            .subscribe(onSuccess: { skinTempData in
                XCTFail("Expected completion, but got data: \(skinTempData)")
                expectationSuccess.fulfill()
            }, onError: { error in
                XCTFail("Expected completion, but got error: \(error)")
                expectationFailure.fulfill()
            }, onDisposed: {
                expectationSuccess.fulfill()
            })

        wait(for: [expectationSuccess, expectationFailure], timeout: 5)
        disposable.dispose()
    }

    private static func buildSkinTemperatureSamplesProto() -> [Data_TemperatureMeasurementSample] {
        
        var skinTemperatureList = [Data_TemperatureMeasurementSample]()
        
        var skinTempSample = Data_TemperatureMeasurementSample()
        skinTempSample.recordingTimeDeltaMilliseconds = 0
        skinTempSample.temperatureCelsius = 37.0
        
        skinTemperatureList.append(skinTempSample)
        
        return skinTemperatureList
    }
    
    private static func buildSkinTemperatureExpectedData() -> [PolarSkinTemperatureData.PolarSkinTemperatureDataSample] {
        
        var skinTemperatureList = [PolarSkinTemperatureData.PolarSkinTemperatureDataSample]()
        
        var skinTempSample = PolarSkinTemperatureData.PolarSkinTemperatureDataSample(
            recordingTimeDeltaMs: 0,
            temperature: 37.0
        )
        skinTemperatureList.append(skinTempSample)
        
        return skinTemperatureList
    }
}
