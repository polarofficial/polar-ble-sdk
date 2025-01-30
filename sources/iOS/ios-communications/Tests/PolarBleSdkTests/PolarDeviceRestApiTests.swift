
///  Copyright © 2024 Polar. All rights reserved.

import XCTest
import RxSwift
import RxBlocking

@testable import PolarBleSdk

class PolarDeviceRestApiServiceTests: XCTestCase {
    
    var mockClient: MockBlePsFtpClient!
    
    override func setUpWithError() throws {
        mockClient = MockBlePsFtpClient()
    }
    
    override func tearDownWithError() throws {
        mockClient = nil
    }
    
    func testConvertsServicesListExampleJson() throws {
        
        // Arrange
        let jsonData = """
        {
            "services": {
             "ui_states":"/REST/UISTATES.API",
             "training":"/REST/TRAINING.API",
            }
        }
        """
        // Act
        let result = try JSONDecoder().decode(PolarDeviceRestApiServices.self, from: jsonData.data(using: .utf8)!)
        
        // Assert
        XCTAssertEqual(result.serviceNames.count, 2)
        XCTAssertTrue(result.serviceNames.contains("ui_states"))
        XCTAssertTrue(result.serviceNames.contains("training"))
        
        XCTAssertEqual(result.servicePaths.count, 2)
        XCTAssertTrue(result.servicePaths.contains("/REST/UISTATES.API"))
        XCTAssertTrue(result.servicePaths.contains("/REST/TRAINING.API"))
        
        XCTAssertEqual(result.pathsForServices?["ui_states"], "/REST/UISTATES.API")
        XCTAssertEqual(result.pathsForServices?["training"], "/REST/TRAINING.API")
    }
    
    func testConvertsLapSummaryExampleJson() throws {
        
        // Arrange
        let jsonData = """
        {
            "events": ["lap_data","exercise_summary"],
            "cmd" : {
                "subscribe"   : "./REST/TRAINING.API?cmd=subscribe&event=&resend=&details=[]&triggers=[]",
                "unsubscribe" : "./REST/TRAINING.API?cmd=unsubscribe&event="
            },
           "lap_data": {
               "details": ["lap_hr_bpm_avg","lap_speed_avg","lap_time"],
               "triggers": ["default","distance","time","end_of_music_track"]
           },
           "exercise_summary": {
               "details": ["duration","distance","hr_bpm_avg"]
           },
        }
        """.data(using: .utf8)!
        
        // Act
        let result = try JSONDecoder().decode(PolarDeviceRestApiServiceDescription.self, from: jsonData)
        
        // Assert
        XCTAssertEqual(result.events.count, 2)
        XCTAssertEqual(result.events.first, "lap_data")
        XCTAssertEqual(result.events.last, "exercise_summary")
        
        XCTAssertEqual(result.actions.count, 2)
        XCTAssertEqual(result.actionNames.count, 2)
        XCTAssertTrue(result.actionNames.contains("subscribe"))
        XCTAssertTrue(result.actionNames.contains("unsubscribe"))
        
        XCTAssertEqual(result.actionPaths.count, 2)
        XCTAssertTrue(result.actionPaths.contains( "./REST/TRAINING.API?cmd=subscribe&event=&resend=&details=[]&triggers=[]"))
        XCTAssertTrue(result.actionPaths.contains("./REST/TRAINING.API?cmd=unsubscribe&event="))
        
        let lapDataEventDetails = result.eventDetails(for: "lap_data")
        XCTAssertEqual(lapDataEventDetails.count, 3)
        XCTAssertTrue(lapDataEventDetails.contains("lap_hr_bpm_avg"))
        XCTAssertTrue(lapDataEventDetails.contains("lap_time"))
        XCTAssertTrue(lapDataEventDetails.contains("lap_speed_avg"))
        
        let lapDAtaEventTriggers = result.eventTriggers(for: "lap_data")
        XCTAssertEqual(lapDAtaEventTriggers.count, 4)
        XCTAssertTrue(lapDAtaEventTriggers.contains("default"))
        XCTAssertTrue(lapDAtaEventTriggers.contains("end_of_music_track"))
        XCTAssertTrue(lapDAtaEventTriggers.contains("distance"))
        XCTAssertTrue(lapDAtaEventTriggers.contains("time"))
        
        let exerciseSummaryEventDetails = result.eventDetails(for: "exercise_summary")
        XCTAssertEqual(exerciseSummaryEventDetails.count, 3)
        XCTAssertTrue(exerciseSummaryEventDetails.contains("duration"))
        XCTAssertTrue(exerciseSummaryEventDetails.contains("hr_bpm_avg"))
        XCTAssertTrue(exerciseSummaryEventDetails.contains("distance"))
        
        let exerciseSummaryEventTriggers = result.eventTriggers(for: "exercise_summary")
        XCTAssertEqual(exerciseSummaryEventTriggers.count, 0)
    }
    
    func testConvertsDeviceUIStatesExampleJson() throws {
        
        // Arrange
        let jsonData = """
        {
            "events": ["training_app_state"],
            "cmd" : {
                "subscribe"   : "./REST/UISTATES.API?cmd=subscribe&event=&resend=&details=[]&triggers=[]",
                "unsubscribe" : "./REST/UISTATES.API?cmd=unsubscribe&event="
            },
            "training_app_state": {
                "details": ["state", "sport_id"]
            }
        }
        """.data(using: .utf8)!
        
        // Act
        let result = try JSONDecoder().decode(PolarDeviceRestApiServiceDescription.self, from: jsonData)
        
        // Assert
        XCTAssertEqual(result.events.count, 1)
        XCTAssertEqual(result.events.first, "training_app_state")
        
        XCTAssertEqual(result.actions.count, 2)
        XCTAssertEqual(result.actionNames.count, 2)
        XCTAssertTrue(result.actionNames.contains("subscribe"))
        XCTAssertTrue(result.actionNames.contains("unsubscribe"))
        
        XCTAssertEqual(result.actionPaths.count, 2)
        XCTAssertTrue(result.actionPaths.contains( "./REST/UISTATES.API?cmd=subscribe&event=&resend=&details=[]&triggers=[]"))
        XCTAssertTrue(result.actionPaths.contains("./REST/UISTATES.API?cmd=unsubscribe&event="))
        
        let lapDataEventDetails = result.eventDetails(for: "training_app_state")
        XCTAssertEqual(lapDataEventDetails.count, 2)
        XCTAssertTrue(lapDataEventDetails.contains("state"))
        XCTAssertTrue(lapDataEventDetails.contains("sport_id"))
    }
    
    func testConvertsSleepAPIDescriptionExampleJson() throws {
        
        // Arrange
        let jsonData = """
        {
            "events": ["sleep_recording_state"],
            "endpoints": ["stop_sleep_recording"],
            "cmd": {
                "subscribe"   : "./REST/SLEEP.API?cmd=subscribe&event=&details=[]",
                "unsubscribe" : "./REST/SLEEP.API?cmd=unsubscribe&event=",
                "post"  : "./REST/SLEEP.API?cmd=post&endpoint="
            },
            "sleep_recording_state": {
                "details": ["enabled"]
            },
           "stop_sleep_recording": {
           }
        }
        """.data(using: .utf8)!
        
        // Act
        let result = try JSONDecoder().decode(PolarDeviceRestApiServiceDescription.self, from: jsonData)
        
        // Assert
        XCTAssertEqual(result.events.count, 1)
        XCTAssertTrue(result.events.contains("sleep_recording_state"))
        XCTAssertEqual(result.endpoints.count, 1)
        XCTAssertTrue(result.endpoints.contains("stop_sleep_recording"))
        XCTAssertEqual(result.actions.count, 3)
        XCTAssertTrue(result.actionNames.contains("subscribe"))
        XCTAssertTrue(result.actionNames.contains("unsubscribe"))
        XCTAssertTrue(result.actionNames.contains("post"))
        XCTAssertEqual(result.actions["subscribe"], "./REST/SLEEP.API?cmd=subscribe&event=&details=[]")
        XCTAssertEqual(result.actions["unsubscribe"], "./REST/SLEEP.API?cmd=unsubscribe&event=")
        XCTAssertEqual(result.actions["post"], "./REST/SLEEP.API?cmd=post&endpoint=")
        let sleepRecordingStateDetails = result.eventDetails(for: "sleep_recording_state")
        XCTAssertEqual(sleepRecordingStateDetails.count, 1)
        XCTAssertTrue(sleepRecordingStateDetails.contains("enabled"))
        let sleepRecordingStateTriggers = result.eventTriggers(for: "sleep_recording_state")
        XCTAssertTrue(sleepRecordingStateTriggers.isEmpty)
        let stopSleepRecordingDetails = result.eventDetails(for: "stop_sleep_recording")
        XCTAssertTrue(stopSleepRecordingDetails.isEmpty)
        let stopSleepRecordingTriggers = result.eventTriggers(for: "stop_sleep_recording")
        XCTAssertTrue(stopSleepRecordingTriggers.isEmpty)
    }
    
    // Helpers
    
    let restApiEventNotifiationId = Protocol_PbPFtpDevToHostNotification.restApiEvent.rawValue
    
    func testNotificationParameters(compressed: Bool) -> [Data] {
        
        let jsonDataSleepRecordingStateEnabledOne = """
        {
            "sleep_recording_state": {
                "enabled": 1
            }
        }
        """.data(using: .utf8)!
        
        let jsonDataSleepRecordingStateEnabledZero = """
        {
            "sleep_recording_state": {
                "enabled": 0
            }
        }
        """.data(using: .utf8)!
        
        let jsonDataSleepRecordingStateEnabledTrue = """
        {
            "sleep_recording_state": {
                "enabled": true
            }
        }
        """.data(using: .utf8)!
        
        let jsonDataSleepRecordingStateEnabledFalse = """
        {
            "sleep_recording_state": {
                "enabled": false
            }
        }
        """.data(using: .utf8)!
        
        let jsonDataSleepRecordingStateEnabledMissing = """
        {
            "sleep_recording_state": {}
        }
        """.data(using: .utf8)!
        
        let jsonDataSleepRecordingStateMissing = """
        {}
        """.data(using: .utf8)!
        
        let jsonDataEmpty = "".data(using: .utf8)!
        
        var notificationParameters = [
           jsonDataSleepRecordingStateEnabledOne,
           jsonDataSleepRecordingStateEnabledZero,
           jsonDataSleepRecordingStateEnabledTrue,
           jsonDataSleepRecordingStateEnabledFalse,
           jsonDataSleepRecordingStateEnabledMissing,
           jsonDataSleepRecordingStateMissing,
           jsonDataEmpty
        ]
        
        if compressed {
            notificationParameters = notificationParameters.map {
                guard let deflatedParam = try? $0.deflated(512) else {
                    return $0
                }
                return deflatedParam
            }
        }
        
        return notificationParameters
    }
    
    
    func testReceivesRestApiEventWhenUncompressed() throws {
        
        // Arrange
        let notificationParameters = self.testNotificationParameters(compressed: false).map { [$0] }
        let notifications = self.testNotificationParameters(compressed: false).map {
            (self.restApiEventNotifiationId, [$0], false)
        }
        
        mockClient.receiveNotificationCalls.append(contentsOf:notifications)

        // Act
        let result =
        try mockClient.receiveRestApiEventData(identifier: UUID().uuidString)
            .toArray()
            .toBlocking()
            .last()
        
        // Assert
        XCTAssertNotNil(result)
        XCTAssertEqual(result, notificationParameters)
    }
    
    func testReceivesRestApiEventWhenCompressed() throws {
        
        // Arrange
        let notificationParameters = self.testNotificationParameters(compressed: false).map { [$0] }
        let notificationsWithCompressedData = self.testNotificationParameters(compressed: true).map {
            (self.restApiEventNotifiationId, [$0], true)
        }
        let notificationParametersCompressed = notificationsWithCompressedData.map { $0.1 }
        
        mockClient.receiveNotificationCalls.append(contentsOf:notificationsWithCompressedData)

        // Act
        let result =
        try mockClient.receiveRestApiEventData(identifier: UUID().uuidString)
            .toArray()
            .toBlocking()
            .last()
        
        // Assert
        
        // Make sure mock data was compressed:
        XCTAssertEqual(notificationParameters.count, notificationParametersCompressed.count)
        XCTAssertNotEqual(notificationParameters, notificationParametersCompressed)
        
        // Check that result received expected uncompressed values
        XCTAssertNotNil(result)
        XCTAssertEqual(result, notificationParameters)
    }
}
