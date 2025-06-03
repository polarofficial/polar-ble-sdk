import Foundation
import XCTest
import RxSwift
import RxTest
import zlib
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
        let routeFile00 = Protocol_PbPFtpEntry.with { $0.name = "ROUTE.BPB"; $0.size = 2048 }
        let routeGzipFile00 = Protocol_PbPFtpEntry.with { $0.name = "ROUTE.GZB"; $0.size = 2048 }
        let routeAdvancedFile00 = Protocol_PbPFtpEntry.with { $0.name = "ROUTE2.BPB"; $0.size = 2048 }
        let routeAdvancedGzipFile00 = Protocol_PbPFtpEntry.with { $0.name = "ROUTE2.GZB"; $0.size = 2048 }
        
        let exerciseFolder01 = Protocol_PbPFtpEntry.with { $0.name = "01/"; $0.size = 0 }
        let exerciseFile01 = Protocol_PbPFtpEntry.with { $0.name = "BASE.BPB"; $0.size = 4096 }
        let routeFile01 = Protocol_PbPFtpEntry.with { $0.name = "ROUTE.BPB"; $0.size = 2048 }
        let routeGzipFile01 = Protocol_PbPFtpEntry.with { $0.name = "ROUTE.GZB"; $0.size = 2048 }
        let routeAdvancedFile01 = Protocol_PbPFtpEntry.with { $0.name = "ROUTE2.BPB"; $0.size = 2048 }
        let routeAdvancedGzipFile01 = Protocol_PbPFtpEntry.with { $0.name = "ROUTE2.GZB"; $0.size = 2048 }
        
        let entry5 = Protocol_PbPFtpEntry.with { $0.name = "20250201/"; $0.size = 0 }
        let entry6 = Protocol_PbPFtpEntry.with { $0.name = "E/"; $0.size = 0 }
        let entry7 = Protocol_PbPFtpEntry.with { $0.name = "134500/"; $0.size = 0 }
        let entry8 = Protocol_PbPFtpEntry.with { $0.name = "TSESS.BPB"; $0.size = 1024 }
        
        let responses: [String: [Protocol_PbPFtpEntry]] = [
            "/U/0/": [entry1, entry5],
            "/U/0/20250101/": [entry2],
            "/U/0/20250101/E/": [entry3],
            "/U/0/20250101/E/123000/": [entry4, exerciseFolder00, exerciseFolder01],
            "/U/0/20250101/E/123000/00/": [exerciseFile00, routeFile00, routeGzipFile00, routeAdvancedFile00, routeAdvancedGzipFile00],
            "/U/0/20250101/E/123000/01/": [exerciseFile01, routeFile01, routeGzipFile01, routeAdvancedFile01, routeAdvancedGzipFile01],
            
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
        
        XCTAssertEqual(session1.exercises[0].path, "/U/0/20250101/E/123000/00")
        XCTAssertEqual(session1.exercises[0].exerciseDataTypes, [.exerciseSummary, .route, .routeGzip, .routeAdvancedFormat, .routeAdvancedFormatGzip])
        
        XCTAssertEqual(session1.exercises[1].path, "/U/0/20250101/E/123000/01")
        XCTAssertEqual(session1.exercises[1].exerciseDataTypes, [.exerciseSummary, .route, .routeGzip, .routeAdvancedFormat, .routeAdvancedFormatGzip])
        
        let session2 = references[1]
        XCTAssertEqual(session2.path, path2)
        XCTAssertEqual(session2.trainingDataTypes, [.trainingSessionSummary])
        XCTAssertTrue(session2.exercises.isEmpty)
    }
    
    func test_readTrainingSession_shouldReturnTrainingSessionDataWithExercises() throws {
        // Arrange
        let basePath = "/U/0/20250101/E/123000/TSESS.BPB"

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
            $0.start = dateTime1; $0.duration = duration1; $0.sport = sport1; $0.walkingDistance = 10000
        }
        let exerciseProto2 = Data_PbExerciseBase.with {
            $0.start = dateTime2; $0.duration = duration2; $0.sport = sport2; $0.walkingDistance = 12000
        }

        var routeProto = Data_PbExerciseRouteSamples()
        routeProto.duration = [1000]
        routeProto.latitude = [10]
        routeProto.longitude = [20]
        routeProto.gpsAltitude = [5]
        routeProto.satelliteAmount = [6]
        routeProto.obsoleteFix = [true, true]
        routeProto.obsoleteGpsOffline = []
        routeProto.obsoleteGpsDateTime = []
        routeProto.firstLocationTime = PbSystemDateTime.with {
            $0.date.year = 2025; $0.date.month = 1; $0.date.day = 1
            $0.time.hour = 12; $0.time.minute = 30; $0.time.seconds = 45; $0.time.millis = 0
            $0.trusted = true
        }

        func gzipCompress(_ data: Data) throws -> Data {
            var stream = z_stream()
            var status: Int32 = Z_OK

            let bufferSize = 16384
            var output = Data()

            status = data.withUnsafeBytes { (srcPointer: UnsafeRawBufferPointer) -> Int32 in
                stream.next_in = UnsafeMutablePointer<Bytef>(mutating: srcPointer.bindMemory(to: Bytef.self).baseAddress!)
                stream.avail_in = uInt(data.count)
                return deflateInit2_(&stream, Z_DEFAULT_COMPRESSION, Z_DEFLATED, 15 + 16, 8, Z_DEFAULT_STRATEGY, ZLIB_VERSION, Int32(MemoryLayout<z_stream>.size))
            }

            guard status == Z_OK else {
                throw NSError(domain: "CompressionError", code: Int(status), userInfo: [NSLocalizedDescriptionKey: "Failed to init zlib deflate stream"])
            }

            defer {
                deflateEnd(&stream)
            }

            let buffer = UnsafeMutablePointer<UInt8>.allocate(capacity: bufferSize)
            defer { buffer.deallocate() }

            repeat {
                stream.next_out = buffer
                stream.avail_out = uInt(bufferSize)

                status = deflate(&stream, stream.avail_in == 0 ? Z_FINISH : Z_NO_FLUSH)

                if status == Z_STREAM_ERROR {
                    throw NSError(domain: "CompressionError", code: Int(status), userInfo: [NSLocalizedDescriptionKey: "Compression failed with zlib error"])
                }

                let have = bufferSize - Int(stream.avail_out)
                output.append(buffer, count: have)

            } while status != Z_STREAM_END

            return output
        }
        
        let routeGzipData = try gzipCompress(try routeProto.serializedData())
        
        var syncPoint = Data_PbExerciseRouteSyncPoint()
        syncPoint.index = 0
        var location = Data_PbLocationSyncPoint()
        location.latitude = 10
        location.longitude = 20
        syncPoint.location = location

        var route2Proto = Data_PbExerciseRouteSamples2()
        route2Proto.syncPoint = [syncPoint]
        route2Proto.latitude = [0]
        route2Proto.longitude = [0]
        route2Proto.timestamp = [0]
        route2Proto.altitude = [0]
        route2Proto.satelliteAmount = [3]

        let route2GzipData = try gzipCompress(try route2Proto.serializedData())

        let polarExercise1 = PolarExercise(
            index: 0,
            path: "/U/0/20250101/E/123000/00",
            exerciseDataTypes: [.exerciseSummary, .route, .routeGzip, .routeAdvancedFormat, .routeAdvancedFormatGzip]
        )
        let polarExercise2 = PolarExercise(
            index: 1,
            path: "/U/0/20250101/E/123000/01",
            exerciseDataTypes: [.exerciseSummary, .route, .routeGzip, .routeAdvancedFormat, .routeAdvancedFormatGzip]
        )

        let referenceBase = PolarTrainingSessionReference(
            date: Date(),
            path: basePath,
            trainingDataTypes: [.trainingSessionSummary],
            exercises: [polarExercise1, polarExercise2]
        )

        let sessionProto = Data_PbTrainingSession.with {
            $0.start = dateTime1
            $0.exerciseCount = 2
        }

        let routeFiles = [
            "ROUTE.BPB",
            "ROUTE.GZB",
            "ROUTE2.BPB",
            "ROUTE2.GZB"
        ]

        mockClient.requestReturnValueClosure = { headerData in
            guard let op = try? Protocol_PbPFtpOperation(serializedData: headerData) else {
                XCTFail("Failed to parse PFtpOperation header")
                return Single.error(NSError(domain: "Test", code: -1))
            }
            let path = op.path

            switch path {
            case basePath:
                return Single.just(try! sessionProto.serializedData())
            case "/U/0/20250101/E/123000/00/BASE.BPB":
                return Single.just(try! exerciseProto1.serializedData())
            case "/U/0/20250101/E/123000/01/BASE.BPB":
                return Single.just(try! exerciseProto2.serializedData())
            case "/U/0/20250101/E/123000/00/ROUTE.BPB":
                return Single.just(try! routeProto.serializedData())
            case "/U/0/20250101/E/123000/01/ROUTE.BPB":
                return Single.just(try! routeProto.serializedData())
            case "/U/0/20250101/E/123000/00/ROUTE.GZB":
                return Single.just(routeGzipData)
            case "/U/0/20250101/E/123000/01/ROUTE.GZB":
                return Single.just(routeGzipData)
            case "/U/0/20250101/E/123000/00/ROUTE2.BPB":
                return Single.just(try! route2Proto.serializedData())
            case "/U/0/20250101/E/123000/01/ROUTE2.BPB":
                return Single.just(try! route2Proto.serializedData())
            case "/U/0/20250101/E/123000/00/ROUTE2.GZB":
                return Single.just(route2GzipData)
            case "/U/0/20250101/E/123000/01/ROUTE2.GZB":
                return Single.just(route2GzipData)
            default:
                XCTFail("Unexpected path: \(path)")
                return Single.error(NSError(domain: "Unexpected path", code: 2))
            }
        }

        for routeFile in routeFiles {
            let exercisesWithRoute = [
                PolarExercise(
                    index: 0,
                    path: "/U/0/20250101/E/123000/00",
                    exerciseDataTypes: [.exerciseSummary, .route, .routeGzip, .routeAdvancedFormat, .routeAdvancedFormatGzip]
                ),
                PolarExercise(
                    index: 1,
                    path: "/U/0/20250101/E/123000/01",
                    exerciseDataTypes: [.exerciseSummary, .route, .routeGzip, .routeAdvancedFormat, .routeAdvancedFormatGzip]
                )
            ]

            let reference = PolarTrainingSessionReference(
                date: Date(),
                path: basePath,
                trainingDataTypes: [.trainingSessionSummary],
                exercises: exercisesWithRoute
            )

            // Act
            let session = try PolarTrainingSessionUtils
                .readTrainingSession(client: mockClient, reference: reference)
                .toBlocking()
                .first()
            
            // Assert
            XCTAssertNotNil(session, "Session should not be nil for route file \(routeFile)")
            XCTAssertEqual(session?.exercises.count, 2, "Expected 2 exercises for route file \(routeFile)")
            
            XCTAssertEqual(session?.sessionSummary.start.date.year, 2025)
            XCTAssertEqual(session?.sessionSummary.start.date.month, 1)
            XCTAssertEqual(session?.sessionSummary.start.date.day, 1)
            XCTAssertEqual(session?.sessionSummary.start.time.hour, 12)
            XCTAssertEqual(session?.sessionSummary.start.time.minute, 30)
    
            let firstExercise: PolarExercise? = session?.exercises[0]
            XCTAssertEqual(firstExercise?.exerciseSummary?.start.time.hour, 12)
            XCTAssertEqual(firstExercise?.exerciseSummary?.walkingDistance, 10000)
            XCTAssertEqual(firstExercise?.exerciseSummary?.sport.value, 5)
            
            let secondExercise: PolarExercise? = session?.exercises[1]
            XCTAssertEqual(secondExercise?.exerciseSummary?.start.time.hour, 14)
            XCTAssertEqual(secondExercise?.exerciseSummary?.walkingDistance, 12000)
            XCTAssertEqual(secondExercise?.exerciseSummary?.sport.value, 25)
            
            let firstRoute = firstExercise?.route
            XCTAssertNotNil(firstRoute, "First route should not be nil for route file \(routeFile)")
            
            let secondRoute = secondExercise?.route
            XCTAssertNotNil(secondRoute, "Second route should not be nil for route file \(routeFile)")
            
            if routeFile.starts(with: "ROUTE") && !routeFile.contains("2") {
                XCTAssertEqual(firstRoute?.latitude, [10], "Latitude mismatch for \(routeFile)")
                XCTAssertEqual(firstRoute?.longitude, [20], "Longitude mismatch for \(routeFile)")
                XCTAssertEqual(firstRoute?.duration, [1000], "Duration mismatch for \(routeFile)")
                XCTAssertEqual(secondRoute?.gpsAltitude, [5], "GpsAltitude mismatch for \(routeFile)")
                XCTAssertEqual(secondRoute?.satelliteAmount, [6], "SatelliteAmount mismatch for \(routeFile)")
            } else {
                XCTAssertEqual(firstRoute?.latitude, [10], "Latitude mismatch for advanced route \(routeFile)")
                XCTAssertEqual(firstRoute?.longitude, [20], "Longitude mismatch for advanced route \(routeFile)")
                XCTAssertEqual(firstRoute?.satelliteAmount, [6], "SatelliteAmount mismatch for advanced route \(routeFile)")
            }
        }
    }
}
