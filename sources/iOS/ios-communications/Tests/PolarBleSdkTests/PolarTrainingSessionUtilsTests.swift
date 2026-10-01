//  Copyright © 2026 Polar. All rights reserved.

import Foundation
import XCTest
import Combine
import zlib
@testable import PolarBleSdk

final class PolarTrainingSessionUtilsTests: XCTestCase {

    private var mockClient: MockBlePsFtpClient!
    private var cancellables = Set<AnyCancellable>()

    override func setUpWithError() throws {
        mockClient = MockBlePsFtpClient(gattServiceTransmitter: MockPolarGattServiceTransmitter())
    }

    override func tearDownWithError() throws {
        mockClient = nil
        cancellables.removeAll()
    }

    // MARK: - Helpers

    private func awaitFirst<T>(_ publisher: AnyPublisher<T, Error>, timeout: TimeInterval = 5) throws -> T? {
        var result: T?
        var receivedError: Error?
        let expectation = XCTestExpectation(description: "publisher completes")
        publisher
            .first()
            .sink(receiveCompletion: { completion in
                if case .failure(let e) = completion { receivedError = e }
                expectation.fulfill()
            }, receiveValue: { result = $0 })
            .store(in: &cancellables)
        wait(for: [expectation], timeout: timeout)
        if let e = receivedError { throw e }
        return result
    }

    // MARK: - Tests

    func test_getTrainingSessionReferences_shouldReturnAllTrainingSessionReferences() async throws {
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
        let samplesFile00 = Protocol_PbPFtpEntry.with { $0.name = "SAMPLES.BPB"; $0.size = 2048 }
        let samplesGzipFile00 = Protocol_PbPFtpEntry.with { $0.name = "SAMPLES.GZB"; $0.size = 2048 }
        let samplesAdvancedGzipFile00 = Protocol_PbPFtpEntry.with { $0.name = "SAMPLES2.GZB"; $0.size = 2048 }

        let exerciseFolder01 = Protocol_PbPFtpEntry.with { $0.name = "01/"; $0.size = 0 }
        let exerciseFile01 = Protocol_PbPFtpEntry.with { $0.name = "BASE.BPB"; $0.size = 4096 }
        let routeFile01 = Protocol_PbPFtpEntry.with { $0.name = "ROUTE.BPB"; $0.size = 2048 }
        let routeGzipFile01 = Protocol_PbPFtpEntry.with { $0.name = "ROUTE.GZB"; $0.size = 2048 }
        let routeAdvancedFile01 = Protocol_PbPFtpEntry.with { $0.name = "ROUTE2.BPB"; $0.size = 2048 }
        let routeAdvancedGzipFile01 = Protocol_PbPFtpEntry.with { $0.name = "ROUTE2.GZB"; $0.size = 2048 }
        let samplesFile01 = Protocol_PbPFtpEntry.with { $0.name = "SAMPLES.BPB"; $0.size = 2048 }
        let samplesGzipFile01 = Protocol_PbPFtpEntry.with { $0.name = "SAMPLES.GZB"; $0.size = 2048 }
        let samplesAdvancedGzipFile01 = Protocol_PbPFtpEntry.with { $0.name = "SAMPLES2.GZB"; $0.size = 2048 }

        let entry5 = Protocol_PbPFtpEntry.with { $0.name = "20250201/"; $0.size = 0 }
        let entry6 = Protocol_PbPFtpEntry.with { $0.name = "E/"; $0.size = 0 }
        let entry7 = Protocol_PbPFtpEntry.with { $0.name = "134500/"; $0.size = 0 }
        let entry8 = Protocol_PbPFtpEntry.with { $0.name = "TSESS.BPB"; $0.size = 1024 }

        let responses: [String: [Protocol_PbPFtpEntry]] = [
            "/U/0/": [entry1, entry5],
            "/U/0/20250101/": [entry2],
            "/U/0/20250101/E/": [entry3],
            "/U/0/20250101/E/123000/": [entry4, exerciseFolder00, exerciseFolder01],
            "/U/0/20250101/E/123000/00/": [
                exerciseFile00, routeFile00, routeGzipFile00, routeAdvancedFile00, routeAdvancedGzipFile00,
                samplesFile00, samplesGzipFile00, samplesAdvancedGzipFile00
            ],
            "/U/0/20250101/E/123000/01/": [
                exerciseFile01, routeFile01, routeGzipFile01, routeAdvancedFile01, routeAdvancedGzipFile01,
                samplesFile01, samplesGzipFile01, samplesAdvancedGzipFile01
            ],
            "/U/0/20250201/": [entry6],
            "/U/0/20250201/E/": [entry7],
            "/U/0/20250201/E/134500/": [entry8]
        ]

        mockClient.requestReturnValueClosure = { header in
            let op = try Protocol_PbPFtpOperation(serializedData: header)
            let path = op.path
            let dir = Protocol_PbPFtpDirectory.with {
                $0.entries = responses[path, default: []]
            }
            return try dir.serializedData()
        }

        // Act
        let references = try await PolarTrainingSessionUtils
            .getTrainingSessionReferences(client: mockClient)

        // Assert (order-independent: look up sessions by path, sort exercises/data types)
        XCTAssertEqual(references.count, 2)

        let expectedDataTypes: [PolarExerciseDataTypes] = [
            .exerciseSummary, .route, .routeGzip, .routeAdvancedFormat,
            .routeAdvancedFormatGzip, .samples, .samplesGzip, .samplesAdvancedFormatGzip
        ].sorted { $0.rawValue < $1.rawValue }

        let session1 = try XCTUnwrap(references.first { $0.path == path1 })
        XCTAssertEqual(session1.trainingDataTypes, [PolarTrainingSessionDataTypes.trainingSessionSummary])
        XCTAssertEqual(session1.exercises.count, 2)

        let session1Exercises = session1.exercises.sorted { $0.path < $1.path }
        XCTAssertEqual(session1Exercises[0].path, "/U/0/20250101/E/123000/00")
        XCTAssertEqual(session1Exercises[0].exerciseDataTypes.sorted { $0.rawValue < $1.rawValue }, expectedDataTypes)
        XCTAssertEqual(session1Exercises[1].path, "/U/0/20250101/E/123000/01")
        XCTAssertEqual(session1Exercises[1].exerciseDataTypes.sorted { $0.rawValue < $1.rawValue }, expectedDataTypes)

        let session2 = try XCTUnwrap(references.first { $0.path == path2 })
        XCTAssertEqual(session2.trainingDataTypes, [PolarTrainingSessionDataTypes.trainingSessionSummary])
        XCTAssertTrue(session2.exercises.isEmpty)
    }

    func test_readTrainingSession_shouldReturnTrainingSessionDataWithExercises() async throws {
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

        var sampleProto = Data_PbExerciseSamples()
        sampleProto.recordingInterval = PbDuration.with { $0.seconds = 1 }
        sampleProto.heartRateSamples = [120, 125, 130]
        sampleProto.speedSamples = [2000, 2100]
        sampleProto.altitudeSamples = [300]

        var sampleProto2 = Data_PbExerciseSamples2()
        var intervalledSample = Data_PbExerciseIntervalledSample2List()
        intervalledSample.sampleType = PbSampleType.sampleTypeHeartRate
        intervalledSample.recordingIntervalMs = 1000
        intervalledSample.heartRateSamples = [131, 132, 133]
        sampleProto2.exerciseIntervalledSample2List = [intervalledSample]

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

            defer { deflateEnd(&stream) }

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
        let samplesGzipData = try gzipCompress(try sampleProto.serializedData())
        let samples2GzipData = try gzipCompress(try sampleProto2.serializedData())

        let sessionProto = Data_PbTrainingSession.with {
            $0.start = dateTime1
            $0.exerciseCount = 2
        }

        let routeFiles = ["ROUTE.BPB", "ROUTE.GZB", "ROUTE2.BPB", "ROUTE2.GZB"]

        mockClient.requestReturnValueClosure = { headerData in
            let op = try Protocol_PbPFtpOperation(serializedData: headerData)
            let path = op.path
            switch path {
            case basePath:
                return try sessionProto.serializedData()
            case "/U/0/20250101/E/123000/00/BASE.BPB":
                return try exerciseProto1.serializedData()
            case "/U/0/20250101/E/123000/01/BASE.BPB":
                return try exerciseProto2.serializedData()
            case "/U/0/20250101/E/123000/00/ROUTE.BPB", "/U/0/20250101/E/123000/01/ROUTE.BPB":
                return try routeProto.serializedData()
            case "/U/0/20250101/E/123000/00/ROUTE.GZB", "/U/0/20250101/E/123000/01/ROUTE.GZB":
                return routeGzipData
            case "/U/0/20250101/E/123000/00/ROUTE2.BPB", "/U/0/20250101/E/123000/01/ROUTE2.BPB":
                return try route2Proto.serializedData()
            case "/U/0/20250101/E/123000/00/ROUTE2.GZB", "/U/0/20250101/E/123000/01/ROUTE2.GZB":
                return route2GzipData
            case "/U/0/20250101/E/123000/00/SAMPLES.BPB", "/U/0/20250101/E/123000/01/SAMPLES.BPB":
                return try sampleProto.serializedData()
            case "/U/0/20250101/E/123000/00/SAMPLES.GZB", "/U/0/20250101/E/123000/01/SAMPLES.GZB":
                return samplesGzipData
            case "/U/0/20250101/E/123000/00/SAMPLES2.GZB", "/U/0/20250101/E/123000/01/SAMPLES2.GZB":
                return samples2GzipData
            default:
                throw NSError(domain: "UnexpectedPath", code: 2,
                              userInfo: [NSLocalizedDescriptionKey: "Unexpected path: \(path)"])
            }
        }

        for routeFile in routeFiles {
            let exercisesWithRoute = [
                PolarExercise(
                    index: 0,
                    path: "/U/0/20250101/E/123000/00",
                    exerciseDataTypes: [.exerciseSummary, .route, .routeGzip, .routeAdvancedFormat, .routeAdvancedFormatGzip, .samples, .samplesGzip, .samplesAdvancedFormatGzip]
                ),
                PolarExercise(
                    index: 1,
                    path: "/U/0/20250101/E/123000/01",
                    exerciseDataTypes: [.exerciseSummary, .route, .routeGzip, .routeAdvancedFormat, .routeAdvancedFormatGzip, .samples, .samplesGzip, .samplesAdvancedFormatGzip]
                )
            ]

            let reference = PolarTrainingSessionReference(
                date: Date(),
                path: basePath,
                trainingDataTypes: [.trainingSessionSummary],
                exercises: exercisesWithRoute
            )

            // Act
            let session = try await PolarTrainingSessionUtils
                .readTrainingSession(client: mockClient, reference: reference)

            // Assert
            XCTAssertEqual(session.exercises.count, 2, "Expected 2 exercises for route file \(routeFile)")

            XCTAssertEqual(session.sessionSummary.start.date.year, 2025)
            XCTAssertEqual(session.sessionSummary.start.date.month, 1)
            XCTAssertEqual(session.sessionSummary.start.date.day, 1)
            XCTAssertEqual(session.sessionSummary.start.time.hour, 12)
            XCTAssertEqual(session.sessionSummary.start.time.minute, 30)

            // Sort exercises by start hour so assertions are order-independent.
            let sortedExercises = session.exercises.sorted {
                ($0.exerciseSummary?.start.time.hour ?? 0) < ($1.exerciseSummary?.start.time.hour ?? 0)
            }

            let firstExercise: PolarExercise? = sortedExercises[0]
            XCTAssertEqual(firstExercise?.exerciseSummary?.start.time.hour, 12)
            XCTAssertEqual(firstExercise?.exerciseSummary?.walkingDistance, 10000)
            XCTAssertEqual(firstExercise?.exerciseSummary?.sport.value, 5)
            XCTAssertEqual(firstExercise?.samples?.heartRateSamples, [120, 125, 130])
            XCTAssertEqual(firstExercise?.samplesAdvanced?.exerciseIntervalledSample2List.map { $0.heartRateSamples }, [[131, 132, 133]])

            let secondExercise: PolarExercise? = sortedExercises[1]
            XCTAssertEqual(secondExercise?.exerciseSummary?.start.time.hour, 14)
            XCTAssertEqual(secondExercise?.exerciseSummary?.walkingDistance, 12000)
            XCTAssertEqual(secondExercise?.exerciseSummary?.sport.value, 25)
            XCTAssertEqual(secondExercise?.samples?.heartRateSamples, [120, 125, 130])
            XCTAssertEqual(secondExercise?.samplesAdvanced?.exerciseIntervalledSample2List.map { $0.heartRateSamples }, [[131, 132, 133]])

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

    func test_getTrainingSessionReferences_shouldAttachExercises_whenExerciseFoldersListedBeforeSummary() async throws {
        // Arrange - the time folder lists exercise folders summary.
        // Without summary-first processing the exercises are dropped.
        let path = "/U/0/20250101/E/123000/TSESS.BPB"
        let responses: [String: [Protocol_PbPFtpEntry]] = [
            "/U/0/": [Protocol_PbPFtpEntry.with { $0.name = "20250101/"; $0.size = 0 }],
            "/U/0/20250101/": [Protocol_PbPFtpEntry.with { $0.name = "E/"; $0.size = 0 }],
            "/U/0/20250101/E/": [Protocol_PbPFtpEntry.with { $0.name = "123000/"; $0.size = 0 }],
            "/U/0/20250101/E/123000/": [
                Protocol_PbPFtpEntry.with { $0.name = "00/"; $0.size = 0 },
                Protocol_PbPFtpEntry.with { $0.name = "01/"; $0.size = 0 },
                Protocol_PbPFtpEntry.with { $0.name = "TSESS.BPB"; $0.size = 1024 }
            ],
            "/U/0/20250101/E/123000/00/": [
                Protocol_PbPFtpEntry.with { $0.name = "BASE.BPB"; $0.size = 63 },
                Protocol_PbPFtpEntry.with { $0.name = "SAMPLES.BPB"; $0.size = 281 }
            ],
            "/U/0/20250101/E/123000/01/": [
                Protocol_PbPFtpEntry.with { $0.name = "BASE.BPB"; $0.size = 50 }
            ]
        ]

        mockClient.requestReturnValueClosure = { header in
            let op = try Protocol_PbPFtpOperation(serializedData: header)
            let dir = Protocol_PbPFtpDirectory.with { $0.entries = responses[op.path, default: []] }
            return try dir.serializedData()
        }

        // Act
        let references = try await PolarTrainingSessionUtils.getTrainingSessionReferences(client: mockClient)

        // Assert
        XCTAssertEqual(references.count, 1)
        XCTAssertEqual(references[0].path, path)
        XCTAssertEqual(references[0].exercises.count, 2, "Exercises must not be dropped when listed before session summary")
        let exercisePaths = Set(references[0].exercises.map { $0.path })
        XCTAssertTrue(exercisePaths.contains("/U/0/20250101/E/123000/00"))
        XCTAssertTrue(exercisePaths.contains("/U/0/20250101/E/123000/01"))
    }

    func test_getTrainingSessionReferences_shouldPopulateIndexAndFileSizes() async throws {
        // Arrange
        let responses: [String: [Protocol_PbPFtpEntry]] = [
            "/U/0/": [Protocol_PbPFtpEntry.with { $0.name = "20250101/"; $0.size = 0 }],
            "/U/0/20250101/": [Protocol_PbPFtpEntry.with { $0.name = "E/"; $0.size = 0 }],
            "/U/0/20250101/E/": [Protocol_PbPFtpEntry.with { $0.name = "123000/"; $0.size = 0 }],
            "/U/0/20250101/E/123000/": [
                Protocol_PbPFtpEntry.with { $0.name = "TSESS.BPB"; $0.size = 100 },
                Protocol_PbPFtpEntry.with { $0.name = "00/"; $0.size = 0 },
                Protocol_PbPFtpEntry.with { $0.name = "01/"; $0.size = 0 }
            ],
            "/U/0/20250101/E/123000/00/": [
                Protocol_PbPFtpEntry.with { $0.name = "BASE.BPB"; $0.size = 63 },
                Protocol_PbPFtpEntry.with { $0.name = "SAMPLES.BPB"; $0.size = 281 }
            ],
            "/U/0/20250101/E/123000/01/": [
                Protocol_PbPFtpEntry.with { $0.name = "BASE.BPB"; $0.size = 50 }
            ]
        ]

        mockClient.requestReturnValueClosure = { header in
            let op = try Protocol_PbPFtpOperation(serializedData: header)
            let dir = Protocol_PbPFtpDirectory.with { $0.entries = responses[op.path, default: []] }
            return try dir.serializedData()
        }

        // Act
        let references = try await PolarTrainingSessionUtils.getTrainingSessionReferences(client: mockClient)

        // Assert
        XCTAssertEqual(references.count, 1)
        let reference = references[0]
        // TSESS (100) + BASE (63) + SAMPLES (281) + BASE (50)
        XCTAssertEqual(reference.fileSize, 494)
        XCTAssertEqual(reference.exercises.count, 2)

        let exercise0 = try XCTUnwrap(reference.exercises.first { $0.index == 0 })
        XCTAssertEqual(exercise0.fileSizes ?? [:], ["BASE.BPB": 63, "SAMPLES.BPB": 281])
        XCTAssertTrue(exercise0.exerciseDataTypes.contains(.exerciseSummary))
        XCTAssertTrue(exercise0.exerciseDataTypes.contains(.samples))

        let exercise1 = try XCTUnwrap(reference.exercises.first { $0.index == 1 })
        XCTAssertEqual(exercise1.fileSizes ?? [:], ["BASE.BPB": 50])
        XCTAssertEqual(exercise1.exerciseDataTypes, [.exerciseSummary])
    }

    func test_getTrainingSessionReferences_shouldReturnAllSessionsOnSameDate() async throws {
        // Arrange - two sessions recorded on the same date.
        let responses: [String: [Protocol_PbPFtpEntry]] = [
            "/U/0/": [Protocol_PbPFtpEntry.with { $0.name = "20260824/"; $0.size = 0 }],
            "/U/0/20260824/": [Protocol_PbPFtpEntry.with { $0.name = "E/"; $0.size = 0 }],
            "/U/0/20260824/E/": [
                Protocol_PbPFtpEntry.with { $0.name = "114623/"; $0.size = 0 },
                Protocol_PbPFtpEntry.with { $0.name = "133106/"; $0.size = 0 }
            ],
            "/U/0/20260824/E/114623/": [Protocol_PbPFtpEntry.with { $0.name = "TSESS.BPB"; $0.size = 145 }],
            "/U/0/20260824/E/133106/": [Protocol_PbPFtpEntry.with { $0.name = "TSESS.BPB"; $0.size = 145 }]
        ]

        mockClient.requestReturnValueClosure = { header in
            let op = try Protocol_PbPFtpOperation(serializedData: header)
            let dir = Protocol_PbPFtpDirectory.with { $0.entries = responses[op.path, default: []] }
            return try dir.serializedData()
        }

        // Act
        let references = try await PolarTrainingSessionUtils.getTrainingSessionReferences(client: mockClient)

        // Assert
        XCTAssertEqual(references.count, 2)
        let paths = Set(references.map { $0.path })
        XCTAssertTrue(paths.contains("/U/0/20260824/E/114623/TSESS.BPB"))
        XCTAssertTrue(paths.contains("/U/0/20260824/E/133106/TSESS.BPB"))
    }

    private func dateFromYYYYMMDD(_ string: String) -> Date {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyyMMdd"
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(abbreviation: "UTC")
        return formatter.date(from: string)!
    }

    private func threeSessionsResponses() -> [String: [Protocol_PbPFtpEntry]] {
        return [
            "/U/0/": [
                Protocol_PbPFtpEntry.with { $0.name = "20250101/"; $0.size = 0 },
                Protocol_PbPFtpEntry.with { $0.name = "20250201/"; $0.size = 0 },
                Protocol_PbPFtpEntry.with { $0.name = "20250301/"; $0.size = 0 }
            ],
            "/U/0/20250101/": [Protocol_PbPFtpEntry.with { $0.name = "E/"; $0.size = 0 }],
            "/U/0/20250101/E/": [Protocol_PbPFtpEntry.with { $0.name = "100000/"; $0.size = 0 }],
            "/U/0/20250101/E/100000/": [Protocol_PbPFtpEntry.with { $0.name = "TSESS.BPB"; $0.size = 100 }],
            "/U/0/20250201/": [Protocol_PbPFtpEntry.with { $0.name = "E/"; $0.size = 0 }],
            "/U/0/20250201/E/": [Protocol_PbPFtpEntry.with { $0.name = "110000/"; $0.size = 0 }],
            "/U/0/20250201/E/110000/": [Protocol_PbPFtpEntry.with { $0.name = "TSESS.BPB"; $0.size = 100 }],
            "/U/0/20250301/": [Protocol_PbPFtpEntry.with { $0.name = "E/"; $0.size = 0 }],
            "/U/0/20250301/E/": [Protocol_PbPFtpEntry.with { $0.name = "120000/"; $0.size = 0 }],
            "/U/0/20250301/E/120000/": [Protocol_PbPFtpEntry.with { $0.name = "TSESS.BPB"; $0.size = 100 }]
        ]
    }

    func test_getTrainingSessionReferences_shouldReturnAllSessions_whenFromDateAndToDateAreNil() async throws {
        // Arrange
        let responses = threeSessionsResponses()
        mockClient.requestReturnValueClosure = { header in
            let op = try Protocol_PbPFtpOperation(serializedData: header)
            let dir = Protocol_PbPFtpDirectory.with { $0.entries = responses[op.path, default: []] }
            return try dir.serializedData()
        }

        // Act
        let references = try await PolarTrainingSessionUtils.getTrainingSessionReferences(client: mockClient)

        // Assert - no date bounds given, so no session is filtered out.
        XCTAssertEqual(references.count, 3)
    }

    func test_getTrainingSessionReferences_shouldReturnSessionsUpToToDate_whenFromDateIsNil() async throws {
        // Arrange
        let responses = threeSessionsResponses()
        mockClient.requestReturnValueClosure = { header in
            let op = try Protocol_PbPFtpOperation(serializedData: header)
            let dir = Protocol_PbPFtpDirectory.with { $0.entries = responses[op.path, default: []] }
            return try dir.serializedData()
        }

        // Act - only an upper bound is provided; the lower bound should default to the widest past.
        let references = try await PolarTrainingSessionUtils.getTrainingSessionReferences(
            client: mockClient,
            fromDate: nil,
            toDate: dateFromYYYYMMDD("20250201")
        )

        // Assert
        let paths = Set(references.map { $0.path })
        XCTAssertEqual(references.count, 2)
        XCTAssertTrue(paths.contains("/U/0/20250101/E/100000/TSESS.BPB"))
        XCTAssertTrue(paths.contains("/U/0/20250201/E/110000/TSESS.BPB"))
        XCTAssertFalse(paths.contains("/U/0/20250301/E/120000/TSESS.BPB"))
    }

    func test_getTrainingSessionReferences_shouldReturnSessionsFromFromDate_whenToDateIsNil() async throws {
        // Arrange
        let responses = threeSessionsResponses()
        mockClient.requestReturnValueClosure = { header in
            let op = try Protocol_PbPFtpOperation(serializedData: header)
            let dir = Protocol_PbPFtpDirectory.with { $0.entries = responses[op.path, default: []] }
            return try dir.serializedData()
        }

        // Act - only a lower bound is provided; the upper bound should default to the widest future.
        let references = try await PolarTrainingSessionUtils.getTrainingSessionReferences(
            client: mockClient,
            fromDate: dateFromYYYYMMDD("20250201"),
            toDate: nil
        )

        // Assert
        let paths = Set(references.map { $0.path })
        XCTAssertEqual(references.count, 2)
        XCTAssertFalse(paths.contains("/U/0/20250101/E/100000/TSESS.BPB"))
        XCTAssertTrue(paths.contains("/U/0/20250201/E/110000/TSESS.BPB"))
        XCTAssertTrue(paths.contains("/U/0/20250301/E/120000/TSESS.BPB"))
    }

    func test_getTrainingSessionReferences_shouldThrow_whenToDateIsBeforeFromDate() async throws {
        // Arrange
        let responses = threeSessionsResponses()
        mockClient.requestReturnValueClosure = { header in
            let op = try Protocol_PbPFtpOperation(serializedData: header)
            let dir = Protocol_PbPFtpDirectory.with { $0.entries = responses[op.path, default: []] }
            return try dir.serializedData()
        }

        // Act & Assert
        do {
            _ = try await PolarTrainingSessionUtils.getTrainingSessionReferences(
                client: mockClient,
                fromDate: dateFromYYYYMMDD("20250301"),
                toDate: dateFromYYYYMMDD("20250101")
            )
            XCTFail("Expected PolarErrors.invalidArgument to be thrown")
        } catch PolarErrors.invalidArgument {
            // expected
        }
    }

    func test_deleteTrainingSession_shouldRemoveSessionFolder_whenSingleExercise() async throws {
        // Arrange - directory lists a single entry, so the whole E/ folder is removed.
        let reference = PolarTrainingSessionReference(
            date: Date(),
            path: "/U/0/20250101/E/123000/TSESS.BPB",
            trainingDataTypes: [.trainingSessionSummary],
            exercises: []
        )
        var capturedRemovePath: String?
        mockClient.requestReturnValueClosure = { header in
            let op = try Protocol_PbPFtpOperation(serializedData: header)
            switch op.command {
            case .get:
                let dir = Protocol_PbPFtpDirectory.with {
                    $0.entries = [Protocol_PbPFtpEntry.with { $0.name = "123000/"; $0.size = 0 }]
                }
                return try dir.serializedData()
            case .remove:
                capturedRemovePath = op.path
                return Data()
            default:
                return Data()
            }
        }

        // Act
        try await PolarTrainingSessionUtils.deleteTrainingSession(client: mockClient, reference: reference)

        // Assert
        XCTAssertEqual(capturedRemovePath, "/U/0/20250101/E/")
    }

    func test_deleteTrainingSession_shouldRemoveExerciseFolder_whenMultipleExercises() async throws {
        // Arrange - directory lists multiple entries, so only the specific exercise is removed.
        let reference = PolarTrainingSessionReference(
            date: Date(),
            path: "/U/0/20250101/E/123000/TSESS.BPB",
            trainingDataTypes: [.trainingSessionSummary],
            exercises: []
        )
        var capturedRemovePath: String?
        mockClient.requestReturnValueClosure = { header in
            let op = try Protocol_PbPFtpOperation(serializedData: header)
            switch op.command {
            case .get:
                let dir = Protocol_PbPFtpDirectory.with {
                    $0.entries = [
                        Protocol_PbPFtpEntry.with { $0.name = "123000/"; $0.size = 0 },
                        Protocol_PbPFtpEntry.with { $0.name = "140000/"; $0.size = 0 }
                    ]
                }
                return try dir.serializedData()
            case .remove:
                capturedRemovePath = op.path
                return Data()
            default:
                return Data()
            }
        }

        // Act
        try await PolarTrainingSessionUtils.deleteTrainingSession(client: mockClient, reference: reference)

        // Assert
        XCTAssertEqual(capturedRemovePath, "/U/0/20250101/E/123000/")
    }

    func test_deleteTrainingSession_shouldThrow_whenPathTooShort() async throws {
        // Arrange - a malformed path with too few components must throw, not crash.
        let reference = PolarTrainingSessionReference(
            date: Date(),
            path: "/U/0",
            trainingDataTypes: [],
            exercises: []
        )
        mockClient.requestReturnValueClosure = { _ in Data() }

        // Act / Assert
        do {
            try await PolarTrainingSessionUtils.deleteTrainingSession(client: mockClient, reference: reference)
            XCTFail("Expected deleteTrainingSession to throw for malformed path")
        } catch {
            // Expected: bounds guard throws instead of crashing.
        }
    }

    func test_readTrainingSession_shouldRequestExpectedFilePaths() async throws {
        // Arrange
        let basePath = "/U/0/20250101/E/123000/TSESS.BPB"
        let exercisePath = "/U/0/20250101/E/123000/00"

        let dateTime = PbLocalDateTime.with {
            $0.date.year = 2025; $0.date.month = 1; $0.date.day = 1
            $0.time.hour = 12; $0.time.minute = 0; $0.time.seconds = 0; $0.time.millis = 0
            $0.obsoleteTrusted = true
        }
        let sessionProto = Data_PbTrainingSession.with { $0.start = dateTime; $0.exerciseCount = 1 }
        let exerciseProto = Data_PbExerciseBase.with {
            $0.start = dateTime
            $0.duration = PbDuration.with { $0.seconds = 60 }
            $0.sport = PbSportIdentifier.with { $0.value = 1 }
        }
        let emptyGzip = try gzipCompress(Data())

        mockClient.requestReturnValueClosure = { header in
            let op = try Protocol_PbPFtpOperation(serializedData: header)
            if op.path == basePath { return try sessionProto.serializedData() }
            if op.path.hasSuffix(".GZB") { return emptyGzip }
            if op.path.hasSuffix("BASE.BPB") { return try exerciseProto.serializedData() }
            return Data()
        }

        let reference = PolarTrainingSessionReference(
            date: Date(),
            path: basePath,
            trainingDataTypes: [.trainingSessionSummary],
            exercises: [
                PolarExercise(
                    index: 0,
                    path: exercisePath,
                    exerciseDataTypes: [.exerciseSummary, .route, .routeGzip, .routeAdvancedFormat,
                                        .routeAdvancedFormatGzip, .samples, .samplesGzip, .samplesAdvancedFormatGzip]
                )
            ]
        )

        // Act
        _ = try await PolarTrainingSessionUtils.readTrainingSession(client: mockClient, reference: reference)

        // Assert - every expected GET path was requested.
        let requestedPaths = Set(mockClient.requestCalls.compactMap { try? Protocol_PbPFtpOperation(serializedData: $0).path })
        let expectedPaths: Set<String> = [
            basePath,
            "\(exercisePath)/BASE.BPB",
            "\(exercisePath)/ROUTE.BPB",
            "\(exercisePath)/ROUTE.GZB",
            "\(exercisePath)/ROUTE2.BPB",
            "\(exercisePath)/ROUTE2.GZB",
            "\(exercisePath)/SAMPLES.BPB",
            "\(exercisePath)/SAMPLES.GZB",
            "\(exercisePath)/SAMPLES2.GZB"
        ]
        XCTAssertTrue(expectedPaths.isSubset(of: requestedPaths),
                      "Missing requested paths: \(expectedPaths.subtracting(requestedPaths))")
    }

    func test_readTrainingSessionWithProgress_shouldReportProgressAndReturnSession() async throws {
        // Arrange
        let basePath = "/U/0/20250101/E/123000/TSESS.BPB"
        let exercisePath = "/U/0/20250101/E/123000/00"

        let dateTime = PbLocalDateTime.with {
            $0.date.year = 2025; $0.date.month = 1; $0.date.day = 1
            $0.time.hour = 12; $0.time.minute = 0; $0.time.seconds = 0; $0.time.millis = 0
            $0.obsoleteTrusted = true
        }
        let sessionProto = Data_PbTrainingSession.with { $0.start = dateTime; $0.exerciseCount = 1 }
        let exerciseProto = Data_PbExerciseBase.with {
            $0.start = dateTime
            $0.duration = PbDuration.with { $0.seconds = 60 }
            $0.sport = PbSportIdentifier.with { $0.value = 1 }
        }
        var sampleProto = Data_PbExerciseSamples()
        sampleProto.recordingInterval = PbDuration.with { $0.seconds = 1 }
        sampleProto.heartRateSamples = [100, 101, 102]

        mockClient.requestReturnValueClosure = { header in
            let op = try Protocol_PbPFtpOperation(serializedData: header)
            switch op.path {
            case basePath: return try sessionProto.serializedData()
            case "\(exercisePath)/BASE.BPB": return try exerciseProto.serializedData()
            case "\(exercisePath)/SAMPLES.BPB": return try sampleProto.serializedData()
            default: return Data()
            }
        }

        let reference = PolarTrainingSessionReference(
            date: Date(),
            path: basePath,
            trainingDataTypes: [.trainingSessionSummary],
            exercises: [PolarExercise(index: 0, path: exercisePath, exerciseDataTypes: [.exerciseSummary, .samples])],
            fileSize: 1000
        )

        // Act
        var progressUpdates: [PolarTrainingSessionProgress] = []
        let session = try await PolarTrainingSessionUtils.readTrainingSessionWithProgress(
            client: mockClient,
            reference: reference,
            progressHandler: { progressUpdates.append($0) }
        )

        // Assert
        XCTAssertEqual(session.exercises.count, 1)
        XCTAssertNotNil(session.sessionSummary)
        XCTAssertEqual(session.exercises.first?.samples?.heartRateSamples, [100, 101, 102])
        XCTAssertFalse(progressUpdates.isEmpty)
        XCTAssertEqual(progressUpdates.first?.progressPercent, 0)
        XCTAssertEqual(progressUpdates.last?.progressPercent, 100)
        XCTAssertEqual(progressUpdates.last?.completedBytes, 1000)
    }

    // MARK: - Test helpers

    private func gzipCompress(_ data: Data) throws -> Data {
        var stream = z_stream()
        var status: Int32 = Z_OK
        let bufferSize = 16384
        var output = Data()

        status = data.withUnsafeBytes { (srcPointer: UnsafeRawBufferPointer) -> Int32 in
            if let base = srcPointer.bindMemory(to: Bytef.self).baseAddress {
                stream.next_in = UnsafeMutablePointer<Bytef>(mutating: base)
            }
            stream.avail_in = uInt(data.count)
            return deflateInit2_(&stream, Z_DEFAULT_COMPRESSION, Z_DEFLATED, 15 + 16, 8, Z_DEFAULT_STRATEGY, ZLIB_VERSION, Int32(MemoryLayout<z_stream>.size))
        }

        guard status == Z_OK else {
            throw NSError(domain: "CompressionError", code: Int(status), userInfo: [NSLocalizedDescriptionKey: "Failed to init zlib deflate stream"])
        }
        defer { deflateEnd(&stream) }

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
}
