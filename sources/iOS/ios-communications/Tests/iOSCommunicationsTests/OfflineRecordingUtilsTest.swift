import XCTest
@testable import iOSCommunications

final class OfflineRecordingUtilsTest: XCTestCase {
    
    func testMapOfflineRecordingFileNameToMeasurementType() throws {
        try XCTAssertEqual(
            PmdMeasurementType.acc,
            OfflineRecordingUtils.mapOfflineRecordingFileNameToMeasurementType(fileName: "ACC.REC")
        )
        try XCTAssertEqual(
            PmdMeasurementType.gyro,
            OfflineRecordingUtils.mapOfflineRecordingFileNameToMeasurementType(fileName: "GYRO.REC")
        )
        try XCTAssertEqual(
            PmdMeasurementType.mgn,
            OfflineRecordingUtils.mapOfflineRecordingFileNameToMeasurementType(fileName: "MAG.REC")
        )
        try XCTAssertEqual(
            PmdMeasurementType.ppg,
            OfflineRecordingUtils.mapOfflineRecordingFileNameToMeasurementType(fileName: "PPG.REC")
        )
        try XCTAssertEqual(
            PmdMeasurementType.ppi,
            OfflineRecordingUtils.mapOfflineRecordingFileNameToMeasurementType(fileName: "PPI.REC")
        )
        try XCTAssertEqual(
            PmdMeasurementType.offline_hr,
            OfflineRecordingUtils.mapOfflineRecordingFileNameToMeasurementType(fileName: "HR.REC")
        )
        try XCTAssertEqual(
            PmdMeasurementType.acc,
            OfflineRecordingUtils.mapOfflineRecordingFileNameToMeasurementType(fileName: "ACC0.REC")
        )
        try XCTAssertEqual(
            PmdMeasurementType.gyro,
            OfflineRecordingUtils.mapOfflineRecordingFileNameToMeasurementType(fileName: "GYRO5.REC")
        )
        try XCTAssertEqual(
            PmdMeasurementType.mgn,
            OfflineRecordingUtils.mapOfflineRecordingFileNameToMeasurementType(fileName: "MAG18.REC")
        )
        try XCTAssertThrowsError(
      OfflineRecordingUtils.mapOfflineRecordingFileNameToMeasurementType(fileName: "INVALID.REC"),
            "Invalid file name"
        )
    }
}
