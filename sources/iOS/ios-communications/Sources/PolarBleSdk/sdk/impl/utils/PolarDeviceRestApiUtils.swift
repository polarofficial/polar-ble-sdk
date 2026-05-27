///  Copyright © 2025 Polar. All rights reserved.

import Foundation
import Combine

extension BlePsFtpClient {

    func receiveRestApiEventData(identifier: String) -> AsyncThrowingStream<[Data], Error> {
        return AsyncThrowingStream { continuation in
            Task {
                do {
                    for try await notification in self.waitNotification() {
                        guard notification.id == Protocol_PbPFtpDevToHostNotification.restApiEvent.rawValue else { continue }
                        guard let params = try? Protocol_PbPftpDHRestApiEvent(serializedBytes: notification.parameters as Data) else { continue }
                        let events: [Data]
                        if params.hasUncompressed && params.uncompressed {
                            events = params.event
                        } else {
                            events = params.event.compactMap { data in
                                guard let uncompressedData = data.inflated() else {
                                    BleLogger.trace_hex("Failed to decompress API event parameters, data: ", data: data)
                                    return data
                                }
                                return uncompressedData
                            }
                        }
                        continuation.yield(events)
                    }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
        }
    }

    func receiveRestApiEvents<T: Decodable>(identifier: String) -> AsyncThrowingStream<[T], Error> {
        return AsyncThrowingStream { continuation in
            Task {
                do {
                    for try await eventDataList in self.receiveRestApiEventData(identifier: identifier) {
                        let decoded = eventDataList.compactMap { data -> T? in
                            BleLogger.trace("Received REST API event, JSON: \(String(data: data, encoding: .utf8) ?? "<binary>")")
                            return try? JSONDecoder().decode(T.self, from: data)
                        }
                        continuation.yield(decoded)
                    }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
        }
    }
}
