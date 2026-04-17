import Foundation
import RxSwift
import SwiftProtobuf

extension PolarBleApiImpl: PolarOfflineExerciseV2Api {

    private static let samplesFile = "SAMPLES.BPB"

    private func handleError(_ error: Error) -> Error {
        let nsError = error as NSError

        if let mapped = Protocol_PbPFtpError(rawValue: nsError.code) {
            return NSError(
                domain: nsError.domain,
                code: nsError.code,
                userInfo: [
                    NSLocalizedDescriptionKey:
                        "\(mapped) (\(nsError.localizedDescription))"
                ]
            )
        }

        return error
    }

    func startOfflineExerciseV2(
        identifier: String,
        sportProfile: PolarExerciseSession.SportProfile
    ) -> Single<OfflineExerciseStartResult> {
        

        do {
            let session = try serviceClientUtils.sessionFtpClientReady(identifier)

            guard let client = session.fetchGattClient(
                BlePsFtpClient.PSFTP_SERVICE
            ) as? BlePsFtpClient else {
                return Single.error(PolarErrors.serviceNotFound)
            }

            var sportId = PbSportIdentifier()
            sportId.value = UInt64(sportProfile.rawValue)

            var params = Protocol_PbPFtpStartDmExerciseParams()
            params.sportIdentifier = sportId

            let request = try params.serializedData()

            return client.query(
                Protocol_PbPFtpQuery.startDmExercise.rawValue,
                parameters: request as NSData
            )
            .map { (response: NSData) -> OfflineExerciseStartResult in

                let proto = try Protocol_PbPftpStartDmExerciseResult(
                    serializedBytes: Data(response)
                )

                let result: OfflineExerciseStartResultType

                switch proto.result {
                case .resultSuccess: result = .success
                case .resultExeOngoing: result = .exerciseOngoing
                case .resultLowBattery: result = .lowBattery
                case .resultSdkMode: result = .sdkMode
                case .resultUnknownSport: result = .unknownSport
                default: result = .other
                }

                return OfflineExerciseStartResult(
                    result: result,
                    directoryPath: proto.dmDirectoryPath
                )
            }
            .catch { error in
                Single.error(self.handleError(error))
            }

        } catch {
            return Single.error(handleError(error))
        }
    }

    func stopOfflineExerciseV2(identifier: String) -> Completable {

        do {
            let session = try serviceClientUtils.sessionFtpClientReady(identifier)

            guard let client = session.fetchGattClient(
                BlePsFtpClient.PSFTP_SERVICE
            ) as? BlePsFtpClient else {
                return Completable.error(PolarErrors.serviceNotFound)
            }

            var params = Protocol_PbPFtpStopExerciseParams()
            params.save = true

            let request = try params.serializedData()

            return client.query(
                Protocol_PbPFtpQuery.stopExercise.rawValue,
                parameters: request as NSData
            )
            .map { _ in () }
            .asCompletable()
            .catch { error in
                Completable.error(self.handleError(error))
            }

        } catch {
            return Completable.error(handleError(error))
        }
    }

    func getOfflineExerciseStatusV2(identifier: String) -> Single<Bool> {
        
        do {
            let session = try serviceClientUtils.sessionFtpClientReady(identifier)

            guard let client = session.fetchGattClient(
                BlePsFtpClient.PSFTP_SERVICE
            ) as? BlePsFtpClient else {
                return Single.error(PolarErrors.serviceNotFound)
            }

            return client.query(
                Protocol_PbPFtpQuery.getExerciseStatus.rawValue,
                parameters: Data() as NSData
            )
            .map { (response: NSData) -> Bool in

                let proto = try Protocol_PbPftpGetExerciseStatusResult(
                    serializedBytes: Data(response)
                )

                return proto.exerciseType == .exerciseTypeDataMerge &&
                       proto.exerciseState == .exerciseStateRunning
            }
            .catch { error in
                Single.error(self.handleError(error))
            }

        } catch {
            return Single.error(handleError(error))
        }
    }

    func listOfflineExercisesV2(
        identifier: String,
        directoryPath: String
    ) -> Observable<PolarExerciseEntry> {

        let fileUtils = PolarFileUtils(
            listener: listener,
            serviceClientUtils: serviceClientUtils
        )

        let source: Observable<String> = fileUtils.listFiles(
            identifier: identifier,
            folderPath: directoryPath,
            condition: { $0.hasSuffix(Self.samplesFile) },
            recurseDeep: true
        )

        return source.map { path in
            (
                path: path,
                date: Date(),
                entryId: URL(fileURLWithPath: path).lastPathComponent
            )
        }
    }

    func fetchOfflineExerciseV2(
        identifier: String,
        entry: PolarExerciseEntry
    ) -> Single<PolarExerciseData> {
        
        do {
            let session = try serviceClientUtils.sessionFtpClientReady(identifier)

            guard let client = session.fetchGattClient(
                BlePsFtpClient.PSFTP_SERVICE
            ) as? BlePsFtpClient else {
                return Single.error(PolarErrors.serviceNotFound)
            }

            var operation = Protocol_PbPFtpOperation()
            operation.command = .get
            operation.path = entry.path

            let request = try operation.serializedData()

            return client.request(request)
                .map { (response: NSData) -> PolarExerciseData in

                    let samples = try Data_PbExerciseSamples(
                        serializedData: Data(response)
                    )

                    if samples.hasRrSamples {
                        return PolarExerciseData(
                            interval: samples.recordingInterval.seconds,
                            samples: samples.rrSamples.rrIntervals
                        )
                    } else {
                        return PolarExerciseData(
                            interval: samples.recordingInterval.seconds,
                            samples: samples.heartRateSamples
                        )
                    }
                }
                .catch { error in
                    Single.error(self.handleError(error))
                }

        } catch {
            return Single.error(handleError(error))
        }
    }

    func removeOfflineExerciseV2(
        identifier: String,
        entry: PolarExerciseEntry
    ) -> Completable {

        do {
            let session = try serviceClientUtils.sessionFtpClientReady(identifier)

            guard let client = session.fetchGattClient(
                BlePsFtpClient.PSFTP_SERVICE
            ) as? BlePsFtpClient else {
                return Completable.error(PolarErrors.serviceNotFound)
            }

            var operation = Protocol_PbPFtpOperation()
            operation.command = .remove
            operation.path = entry.path

            let request = try operation.serializedData()

            return client.request(request)
                .map { _ in () }
                .asCompletable()
                .catch { error in
                    Completable.error(self.handleError(error))
                }

        } catch {
            return Completable.error(handleError(error))
        }
    }

    func isOfflineExerciseV2Supported(identifier: String) -> Single<Bool> {

        return waitDeviceSessionWithPftpToOpen(
            identifier: identifier,
            timeoutSeconds: 10,
            waitForDeviceDownSeconds: 0
        )
        .andThen(
            Single.deferred {

                let session = try self.serviceClientUtils.sessionFtpClientReady(identifier)

                let deviceType = session.advertisementContent.polarDeviceType
                guard BlePolarDeviceCapabilitiesUtility.isRecordingSupported(deviceType) else {
                    return Single.just(false)
                }

                guard let client = session.fetchGattClient(
                    BlePsFtpClient.PSFTP_SERVICE
                ) as? BlePsFtpClient else {
                    return Single.just(false)
                }

                return self.checkDmExerciseSupport(client)
            }
        )
    }
    
    private func checkDmExerciseSupport(_ client: BlePsFtpClient) -> Single<Bool> {

        var operation = Protocol_PbPFtpOperation()
        operation.command = .get
        operation.path = "/DEVICE.BPB"

        do {
            let request = try operation.serializedData()

            return client.request(request)
                .map { (response: NSData) -> Bool in
                    let deviceInfo = try Data_PbDeviceInfo(
                        serializedBytes: Data(response)
                    )

                    return deviceInfo.capabilities.contains("dm_exercise")
                }
                .do(onError: { error in
                    BleLogger.error("Failed to check dm_exercise capability: \(error)")
                })
                .catchAndReturn(false)

        } catch {
            return Single.just(false)
        }
    }
}
