///  Copyright © 2025 Polar. All rights reserved.

import Foundation
import RxSwift

extension BlePsFtpClient {
    
    func receiveRestApiEventData(identifier: String) -> Observable<[Data]> {
        let receiveEventData = self.waitNotification()
            .share(replay: 1)
            .filter { $0.id == Protocol_PbPFtpDevToHostNotification.restApiEvent.rawValue }
            .compactMap { notification -> [Data]? in
                guard let params = try? Protocol_PbPftpDHRestApiEvent(serializedData: notification.parameters as Data) else { return nil }
                if params.hasUncompressed && params.uncompressed {
                    return params.event
                } else {
                    return params.event.compactMap { data in
                        guard let uncompressedData = data.inflated() else {
                            BleLogger.trace_hex("Failed to decompress API event parameters, data: ", data: data)
                            return data
                        }
                        return uncompressedData
                    }
                }
            }
        return receiveEventData
    }
    
    func receiveRestApiEvents<T: Decodable>(identifier: String) -> Observable<[T]> {
        return receiveRestApiEventData(identifier: identifier)
            .compactMap {
                $0.compactMap { data in
                    BleLogger.trace("Received REST API event, JSON: \(String(data: data, encoding: .utf8) ?? "<binary>"))")
                    guard let decoded: T = try? JSONDecoder().decode(T.self, from: data ) else {
                        BleLogger.trace("Failed to decode \(T.self) from REST API event data: \(String(data: data, encoding: .utf8) ?? "<binary>")")
                        return nil
                    }
                    return decoded
                }
        }
    }
}
