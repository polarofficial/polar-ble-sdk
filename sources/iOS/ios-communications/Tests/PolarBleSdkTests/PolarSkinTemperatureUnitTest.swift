//
//  Copyright © 2025 Polar. All rights reserved.
//

import XCTest
@testable import PolarBleSdk

class PolarSkinTemperatureUtilsTests: XCTestCase {
    
    var mockClient: MockBlePsFtpClient!

    override func setUpWithError() throws {
        mockClient = MockBlePsFtpClient(gattServiceTransmitter: MockPolarGattServiceTransmitter())
    }

    override func tearDownWithError() throws {
        mockClient = nil
    }
    
    func testReadSkinTemperatureDataFromDayDirectory_SuccessfulResponse() async throws {
        // Arrange
        let date = Date()
        var proto = Data_TemperatureMeasurementPeriod()
        proto.measurementType = .tmSkinTemperature
        proto.sensorLocation = .slProximal
        proto.temperatureMeasurementSamples.append(contentsOf: Self.buildSkinTemperatureSamplesProto())
        mockClient.requestReturnValue = .success(try proto.serializedData())

        let expectedResult = PolarSkinTemperatureData.PolarSkinTemperatureResult(
            date: date,
            sensorLocation: .SL_PROXIMAL,
            measurementType: .TM_SKIN_TEMPERATURE,
            skinTemperatureList: Self.buildSkinTemperatureExpectedData()
        )

        // Act
        let testResult = await PolarSkinTemperatureUtils.readSkinTemperatureData(client: mockClient, date: date)

        // Assert
        XCTAssertNotNil(testResult)
        XCTAssertEqual(testResult?.measurementType, expectedResult.measurementType)
        XCTAssertEqual(testResult?.sensorLocation, expectedResult.sensorLocation)
        XCTAssertEqual(testResult?.skinTemperatureList?.first?.recordingTimeDeltaMs, expectedResult.skinTemperatureList?.first?.recordingTimeDeltaMs)
        XCTAssertEqual(testResult?.skinTemperatureList?.first?.temperature, expectedResult.skinTemperatureList?.first?.temperature)
    }
    
    func testReadSkinTemperatureDataFromDayDirectory_FileNotFound() async {
        // Arrange
        mockClient.requestReturnValue = .failure(NSError(domain: "File not found", code: 103, userInfo: nil))

        // Act – errors are swallowed; method returns nil
        let result = await PolarSkinTemperatureUtils.readSkinTemperatureData(client: mockClient, date: Date())

        // Assert
        XCTAssertNil(result, "Expected nil when file is not found")
    }

    // MARK: - Helpers

    private static func buildSkinTemperatureSamplesProto() -> [Data_TemperatureMeasurementSample] {
        var skinTempSample = Data_TemperatureMeasurementSample()
        skinTempSample.recordingTimeDeltaMilliseconds = 0
        skinTempSample.temperatureCelsius = 37.0
        return [skinTempSample]
    }
    
    private static func buildSkinTemperatureExpectedData() -> [PolarSkinTemperatureData.PolarSkinTemperatureDataSample] {
        return [PolarSkinTemperatureData.PolarSkinTemperatureDataSample(recordingTimeDeltaMs: 0, temperature: 37.0)]
    }
}
