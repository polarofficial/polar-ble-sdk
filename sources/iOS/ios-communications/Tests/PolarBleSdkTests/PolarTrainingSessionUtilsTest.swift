import Foundation
import XCTest
import RxSwift
import RxTest
@testable import PolarBleSdk

final class PolarTrainingSessionUtilsTests: XCTestCase {
    
    private var mockClient: MockBlePsFtpClient!
    
    override func setUpWithError() throws {
        mockClient = MockBlePsFtpClient(gattServiceTransmitter: MockGattServiceTransmitterImpl())
    }

    override func tearDownWithError() throws {
        mockClient = nil
    }

    func test_getTrainingSessionReferences_shouldReturnAllTrainingSessionReferences() throws {
        // Arrange
        let date1 = "20250101"
        let time1 = "123000"
        let path1 = "/U/0/\(date1)/E/\(time1)/TSESS.BPB"

        let date2 = "20250201"
        let time2 = "134500"
        let path2 = "/U/0/\(date2)/E/\(time2)/TSESS.BPB"

        let entry1 = Protocol_PbPFtpEntry.with { $0.name = "20250101/"; $0.size = 0 }
        let entry2 = Protocol_PbPFtpEntry.with { $0.name = "E/"; $0.size = 0 }
        let entry3 = Protocol_PbPFtpEntry.with { $0.name = "123000/"; $0.size = 0 }
        let entry4 = Protocol_PbPFtpEntry.with { $0.name = "TSESS.BPB"; $0.size = 1024 }

        let exerciseFolder00 = Protocol_PbPFtpEntry.with { $0.name = "00/"; $0.size = 0 }
        let exerciseFile00 = Protocol_PbPFtpEntry.with { $0.name = "BASE.BPB"; $0.size = 2048 }

        let exerciseFolder01 = Protocol_PbPFtpEntry.with { $0.name = "01/"; $0.size = 0 }
        let exerciseFile01 = Protocol_PbPFtpEntry.with { $0.name = "BASE.BPB"; $0.size = 4096 }

        let entry5 = Protocol_PbPFtpEntry.with { $0.name = "20250201/"; $0.size = 0 }
        let entry6 = Protocol_PbPFtpEntry.with { $0.name = "E/"; $0.size = 0 }
        let entry7 = Protocol_PbPFtpEntry.with { $0.name = "134500/"; $0.size = 0 }
        let entry8 = Protocol_PbPFtpEntry.with { $0.name = "TSESS.BPB"; $0.size = 1024 }
        
        let responses: [String: [Protocol_PbPFtpEntry]] = [
            "/U/0/": [entry1, entry5],
            "/U/0/20250101/": [entry2],
            "/U/0/20250101/E/": [entry3],
            "/U/0/20250101/E/123000/": [entry4, exerciseFolder00, exerciseFolder01],
            "/U/0/20250101/E/123000/00/": [exerciseFile00],
            "/U/0/20250101/E/123000/01/": [exerciseFile01],
            
            "/U/0/20250201/": [entry6],
            "/U/0/20250201/E/": [entry7],
            "/U/0/20250201/E/134500/": [entry8]
        ]

        mockClient.requestReturnValueClosure = { header in
            let op = try! Protocol_PbPFtpOperation(serializedData: header)
            let path = op.path
            let dir = Protocol_PbPFtpDirectory.with {
                $0.entries = responses[path, default: []]
            }
            return Single.just(try! dir.serializedData())
        }

        // Act
        let references = try PolarTrainingSessionUtils
            .getTrainingSessionReferences(client: mockClient)
            .toBlocking()
            .toArray()

        // Assert
        XCTAssertEqual(references.count, 2)
        
        let session1 = references[0]
        XCTAssertEqual(session1.path, path1)
        XCTAssertEqual(session1.trainingDataTypes, [.trainingSessionSummary])
        XCTAssertEqual(session1.exercises.count, 2)
        
        XCTAssertEqual(session1.exercises[0].path, "/U/0/20250101/E/123000/00/BASE.BPB")
        XCTAssertEqual(session1.exercises[0].exerciseDataTypes, [.exerciseSummary])
        
        XCTAssertEqual(session1.exercises[1].path, "/U/0/20250101/E/123000/01/BASE.BPB")
        XCTAssertEqual(session1.exercises[1].exerciseDataTypes, [.exerciseSummary])
        
        let session2 = references[1]
        XCTAssertEqual(session2.path, path2)
        XCTAssertEqual(session2.trainingDataTypes, [.trainingSessionSummary])
        XCTAssertTrue(session2.exercises.isEmpty)
    }
    
    func test_readTrainingSession_shouldReturnTrainingSessionDataWithExercises() throws {
        // Arrange
        let path = "/U/0/20250101/E/123000/TSESS.BPB"
        
        let dateTime1 = PbLocalDateTime.with {
            $0.date.year = 2025
            $0.date.month = 1
            $0.date.day = 1
            $0.time.hour = 12
            $0.time.minute = 30
            $0.time.seconds = 45
            $0.time.millis = 888
            $0.obsoleteTrusted = true
        }
        
        let dateTime2 = PbLocalDateTime.with {
            $0.date.year = 2025
            $0.date.month = 1
            $0.date.day = 1
            $0.time.hour = 14
            $0.time.minute = 1
            $0.time.seconds = 30
            $0.time.millis = 400
            $0.obsoleteTrusted = true
        }
        
        let duration1 = PbDuration.with {
            $0.hours = 1
            $0.minutes = 30
            $0.seconds = 45
            $0.millis = 400
        }
        
        let duration2 = PbDuration.with {
            $0.hours = 0
            $0.minutes = 55
            $0.seconds = 11
            $0.millis = 111
        }
        
        let sport1 = PbSportIdentifier.with { $0.value = 5 }
        let sport2 = PbSportIdentifier.with { $0.value = 25 }
        
        let exerciseProto1 = Data_PbExerciseBase.with {
            $0.start = dateTime1
            $0.duration = duration1
            $0.sport = sport1
            $0.walkingDistance = 10000
        }
        
        let exerciseProto2 = Data_PbExerciseBase.with {
            $0.start = dateTime2
            $0.duration = duration2
            $0.sport = sport2
            $0.walkingDistance = 12000
        }
        
        let polarExercise1 = PolarExercise(
            index: 0,
            path: "/U/0/20250101/E/123000/00/BASE.BPB",
            exerciseDataTypes: [.exerciseSummary]
        )
        
        let polarExercise2 = PolarExercise(
            index: 1,
            path: "/U/0/20250101/E/123000/01/BASE.BPB",
            exerciseDataTypes: [.exerciseSummary]
        )
        
        let reference = PolarTrainingSessionReference(
            date: Date(),
            path: path,
            trainingDataTypes: [.trainingSessionSummary],
            exercises: [polarExercise1, polarExercise2]
        )
        
        let sessionProto = Data_PbTrainingSession.with {
            $0.start = dateTime1
            $0.exerciseCount = 2
        }
        
        mockClient.requestReturnValueClosure = { header in
            let op = try! Protocol_PbPFtpOperation(serializedData: header)
            switch op.path {
            case path:
                return Single.just(try! sessionProto.serializedData())
            case polarExercise1.path:
                return Single.just(try! exerciseProto1.serializedData())
            case polarExercise2.path:
                return Single.just(try! exerciseProto2.serializedData())
            default:
                XCTFail("Unexpected path: \(op.path)")
                return Single.error(NSError(domain: "Unexpected path", code: 1))
            }
        }
        
        // Act
        let session = try PolarTrainingSessionUtils
            .readTrainingSession(client: mockClient, reference: reference)
            .toBlocking()
            .first()
        
        // Assert
        XCTAssertNotNil(session)
        XCTAssertEqual(session?.sessionSummary.start.date.year, 2025)
        XCTAssertEqual(session?.sessionSummary.start.date.month, 1)
        XCTAssertEqual(session?.sessionSummary.start.date.day, 1)
        XCTAssertEqual(session?.sessionSummary.start.time.hour, 12)
        XCTAssertEqual(session?.sessionSummary.start.time.minute, 30)
        
        XCTAssertEqual(session?.exercises.count, 2)
        
        let firstExercise = session?.exercises[0]
        XCTAssertEqual(firstExercise?.exerciseSummary?.start.time.hour, 12)
        XCTAssertEqual(firstExercise?.exerciseSummary?.walkingDistance, 10000)
        XCTAssertEqual(firstExercise?.exerciseSummary?.sport.value, 5)
        
        let secondExercise = session?.exercises[1]
        XCTAssertEqual(secondExercise?.exerciseSummary?.start.time.hour, 14)
        XCTAssertEqual(secondExercise?.exerciseSummary?.walkingDistance, 12000)
        XCTAssertEqual(secondExercise?.exerciseSummary?.sport.value, 25)
    }
}
