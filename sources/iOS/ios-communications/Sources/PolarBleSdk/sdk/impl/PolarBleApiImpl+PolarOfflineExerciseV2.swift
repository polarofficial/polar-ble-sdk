import Foundation
import SwiftProtobuf

extension PolarBleApiImpl: PolarOfflineExerciseV2Api {

    private static let samplesFile = "SAMPLES.BPB"

    private func handleError(_ error: Error) -> Error {
        let nsError = error as NSError
        if let mapped = Protocol_PbPFtpError(rawValue: nsError.code) {
            return NSError(domain: nsError.domain, code: nsError.code,
                userInfo: [NSLocalizedDescriptionKey: "\(mapped) (\(nsError.localizedDescription))"])
        }
        return error
    }

    // MARK: - PolarOfflineExerciseV2Api

    func startOfflineExerciseV2(
        identifier: String,
        sportProfile: PolarExerciseSession.SportProfile
    ) async throws -> OfflineExerciseStartResult {
        do {
            let session = try serviceClientUtils.sessionFtpClientReady(identifier)
            guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                throw PolarErrors.serviceNotFound
            }
            var sportId = PbSportIdentifier()
            sportId.value = UInt64(sportProfile.rawValue)
            var params = Protocol_PbPFtpStartDmExerciseParams()
            params.sportIdentifier = sportId
            let response = try await client.query(Protocol_PbPFtpQuery.startDmExercise.rawValue, parameters: try params.serializedData() as NSData)
            let proto = try Protocol_PbPftpStartDmExerciseResult(serializedBytes: Data(response))
            let result: OfflineExerciseStartResultType
            switch proto.result {
            case .resultSuccess:     result = .success
            case .resultExeOngoing:  result = .exerciseOngoing
            case .resultLowBattery:  result = .lowBattery
            case .resultSdkMode:     result = .sdkMode
            case .resultUnknownSport: result = .unknownSport
            default:                 result = .other
            }
            return OfflineExerciseStartResult(result: result, directoryPath: proto.dmDirectoryPath)
        } catch {
            throw handleError(error)
        }
    }

    func stopOfflineExerciseV2(identifier: String) async throws {
        do {
            let session = try serviceClientUtils.sessionFtpClientReady(identifier)
            guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                throw PolarErrors.serviceNotFound
            }
            var params = Protocol_PbPFtpStopExerciseParams()
            params.save = true
            _ = try await client.query(Protocol_PbPFtpQuery.stopExercise.rawValue, parameters: try params.serializedData() as NSData)
        } catch {
            throw handleError(error)
        }
    }

    func getOfflineExerciseStatusV2(identifier: String) async throws -> Bool {
        do {
            let session = try serviceClientUtils.sessionFtpClientReady(identifier)
            guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                throw PolarErrors.serviceNotFound
            }
            let response = try await client.query(Protocol_PbPFtpQuery.getExerciseStatus.rawValue, parameters: Data() as NSData)
            let proto = try Protocol_PbPftpGetExerciseStatusResult(serializedBytes: Data(response))
            return proto.exerciseType == .exerciseTypeDataMerge && proto.exerciseState == .exerciseStateRunning
        } catch {
            throw handleError(error)
        }
    }

    func listOfflineExercisesV2(identifier: String, directoryPath: String) -> AsyncThrowingStream<PolarExerciseEntry, Error> {
        let fileUtilsLocal = PolarFileUtils(listener: listener, serviceClientUtils: serviceClientUtils)
        return AsyncThrowingStream { continuation in
            Task {
                do {
                    for try await path in fileUtilsLocal.listFiles(identifier: identifier, folderPath: directoryPath, condition: { $0.hasSuffix(Self.samplesFile) }, recurseDeep: true) {
                        continuation.yield((path: path, date: Date(), entryId: URL(fileURLWithPath: path).lastPathComponent))
                    }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
        }
    }

    func fetchOfflineExerciseV2(identifier: String, entry: PolarExerciseEntry) async throws -> PolarExerciseData {
        do {
            let session = try serviceClientUtils.sessionFtpClientReady(identifier)
            guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                throw PolarErrors.serviceNotFound
            }
            var operation = Protocol_PbPFtpOperation()
            operation.command = .get
            operation.path = entry.path
            let response = try await client.request(try operation.serializedBytes())
            let samples = try Data_PbExerciseSamples(serializedBytes: Data(response))
            if samples.hasRrSamples {
                return PolarExerciseData(interval: samples.recordingInterval.seconds, samples: samples.rrSamples.rrIntervals)
            } else {
                return PolarExerciseData(interval: samples.recordingInterval.seconds, samples: samples.heartRateSamples)
            }
        } catch {
            throw handleError(error)
        }
    }

    func removeOfflineExerciseV2(identifier: String, entry: PolarExerciseEntry) async throws {
        do {
            let session = try serviceClientUtils.sessionFtpClientReady(identifier)
            guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                throw PolarErrors.serviceNotFound
            }
            var operation = Protocol_PbPFtpOperation()
            operation.command = .remove
            operation.path = entry.path
            _ = try await client.request(try operation.serializedBytes())
        } catch {
            throw handleError(error)
        }
    }

    func isOfflineExerciseV2Supported(identifier: String) async throws -> Bool {
        // Wait for PFTP session to be ready (up to 10 seconds, polling every 5s)
        let timeoutAt = Date().addingTimeInterval(10)
        while Date() < timeoutAt {
            do {
                let session = try serviceClientUtils.sessionFtpClientReady(identifier)
                let deviceType = session.advertisementContent.polarDeviceType
                guard BlePolarDeviceCapabilitiesUtility.isRecordingSupported(deviceType) else { return false }
                guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { return false }
                return try await checkDmExerciseSupport(client)
            } catch {
                try? await Task.sleep(nanoseconds: 5_000_000_000)
            }
        }
        throw PolarErrors.timeout(description: "Timeout while waiting for device session, deviceId: \(identifier)")
    }

    private func checkDmExerciseSupport(_ client: BlePsFtpClient) async throws -> Bool {
        var operation = Protocol_PbPFtpOperation()
        operation.command = .get
        operation.path = "/DEVICE.BPB"
        do {
            let response = try await client.request(try operation.serializedBytes())
            let deviceInfo = try Data_PbDeviceInfo(serializedBytes: Data(response))
            return deviceInfo.capabilities.contains("dm_exercise")
        } catch {
            BleLogger.error("Failed to check dm_exercise capability: \(error)")
            return false
        }
    }
}
