/// Copyright  2026 Polar Electro Oy. All rights reserved.

import Foundation
import CoreBluetooth

/// Implementation of training session API methods.
extension PolarBleApiImpl {

    func getTrainingSessionReferences(
        identifier: String,
        fromDate: Date? = nil,
        toDate: Date? = nil
    ) async throws -> [PolarTrainingSessionReference] {
        logApiCall("getTrainingSessionReferences", ("identifier", identifier), ("fromDate", fromDate), ("toDate", toDate))
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
            throw PolarErrors.serviceNotFound
        }
        return try await PolarTrainingSessionUtils.getTrainingSessionReferences(
            client: client,
            fromDate: fromDate,
            toDate: toDate
        )
    }

    func getTrainingSession(
        identifier: String,
        trainingSessionReference: PolarTrainingSessionReference
    ) async throws -> PolarTrainingSession {
        logApiCall("getTrainingSession", ("identifier", identifier))
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
            throw PolarErrors.serviceNotFound
        }
        return try await PolarTrainingSessionUtils.readTrainingSession(
            client: client,
            reference: trainingSessionReference
        )
    }
    
    func getTrainingSessionWithProgress(
        identifier: String,
        trainingSessionReference: PolarTrainingSessionReference,
        progressHandler: @escaping (PolarTrainingSessionProgress) -> Void
    ) async throws -> PolarTrainingSession {
        logApiCall("getTrainingSessionWithProgress", ("identifier", identifier))
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
            throw PolarErrors.serviceNotFound
        }
        return try await PolarTrainingSessionUtils.readTrainingSessionWithProgress(
            client: client,
            reference: trainingSessionReference,
            progressHandler: progressHandler
        )
    }
    
    func deleteTrainingSession(identifier: String, reference: PolarTrainingSessionReference) async throws {
        logApiCall("deleteTrainingSession", ("identifier", identifier))
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        BleLogger.trace("Delete training session with path '\(sanitizePathForLog(reference.path))' ")
        try await PolarTrainingSessionUtils.deleteTrainingSession(client: client, reference: reference)
    }
}
