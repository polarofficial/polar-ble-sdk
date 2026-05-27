// Copyright © 2024 Polar Electro Oy. All rights reserved.
//
// Combine bridge shims for BlePsFtpClient — allows existing Combine-based call sites
// in PolarBleApiImpl to keep working while the async/await migration proceeds.

import Foundation
import Combine

extension BlePsFtpClient {

    // MARK: - Request (single response)

    func requestPublisher(_ header: Data) -> AnyPublisher<NSData, Error> {
        asyncPublisher { try await self.request(header) }
    }

    func requestPublisher(_ header: Data, progressCallback: BlePsFtpProgressCallback?) -> AnyPublisher<NSData, Error> {
        asyncPublisher { try await self.request(header, progressCallback: progressCallback) }
    }

    // MARK: - Query

    func queryPublisher(_ id: Int, parameters: NSData?) -> AnyPublisher<NSData, Error> {
        asyncPublisher { try await self.query(id, parameters: parameters) }
    }

    // MARK: - Write (progress stream)

    func writePublisher(_ header: NSData, data: InputStream) -> AnyPublisher<UInt, Error> {
        streamPublisher(self.write(header, data: data))
    }

    // MARK: - Notification send

    func sendNotificationPublisher(_ id: Int, parameters: NSData?) -> AnyPublisher<Never, Error> {
        asyncVoidPublisher { try await self.sendNotification(id, parameters: parameters) }
    }

    // MARK: - Wait notification (infinite stream)

    func waitNotificationPublisher() -> AnyPublisher<PsFtpNotification, Error> {
        streamPublisher(self.waitNotification())
    }

    // MARK: - clientReady / waitPsFtpReady

    func clientReadyPublisher(_ checkConnection: Bool) -> AnyPublisher<Never, Error> {
        asyncVoidPublisher { try await self.clientReady(checkConnection) }
    }

    func waitPsFtpReadyPublisher(_ checkConnection: Bool) -> AnyPublisher<Never, Error> {
        asyncVoidPublisher { try await self.waitPsFtpReady(checkConnection) }
    }
}
