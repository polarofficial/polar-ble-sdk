/// Copyright © 2025 Polar Electro Oy. All rights reserved.

import Foundation
import CoreBluetooth

/// Lists REST API services and corresponding paths
///
public struct PolarDeviceRestApiServices: Decodable {
    
    /// Maps available REST API service names to corresponding paths
    let pathsForServices: [String: String]?
    enum CodingKeys: String, CodingKey {
        case pathsForServices = "services"
    }
    
    /// Lists REST API service names
    var serviceNames: [String] {
        Array((pathsForServices ?? [:]).keys)
    }
    
    /// Lists REST API service paths
    var servicePaths: [String] {
        Array((pathsForServices ?? [:]).values)
    }
}

/// Describes specific service API per SAGRFC95
///
public struct PolarDeviceRestApiServiceDescription: Decodable {

    private let dictionary: [String: Decodable]?
    
    struct CodingKeys: CodingKey {
        var intValue: Int? = nil
        init?(intValue: Int) {
            self.intValue = intValue
        }
        var stringValue: String = ""
        init?(stringValue: String) {
            self.stringValue = stringValue
        }
    }
    
    // By conforming to Decodable, this struct can be parsed with JSONDecoder the usual way
    
    public init(from decoder: any Decoder) throws {
        let keyedDecodingContainer:KeyedDecodingContainer = try decoder.container(keyedBy: CodingKeys.self)
        var dictionary = Dictionary<String, Decodable>()
        for key in keyedDecodingContainer.allKeys {
            switch key.stringValue {
            case "events":
                if let events = try? keyedDecodingContainer.decode([String].self, forKey: key) {
                    dictionary["events"] = events
                }
            case "endpoints":
                if let endpoints = try? keyedDecodingContainer.decode([String].self, forKey: key) {
                    dictionary["endpoints"] = endpoints
                }
                
            case "cmd":
                if let actions = try? keyedDecodingContainer.decode([String:String].self, forKey: key) {
                    dictionary["cmd"] = actions
                }
            default:
                // rest are event descriptions, with details and/or triggers
                if let eventDescription = try? keyedDecodingContainer.decode([String:[String]].self, forKey: key) {
                    dictionary[key.stringValue] = eventDescription
                }
            }
        }
        self.dictionary = dictionary
    }
    
    /// Events that can be acted upon using actions. Actions are returned in `actions` and `actionNames` properties.
    var events: [String] {
        return dictionary?["events"] as? [String] ?? []
    }
    
    /// Endpoints that can be applied in **endpoint=** parameter in paths from `actions` and `actionPaths`
    var endpoints: [String] {
        return dictionary?["endpoints"] as? [String] ?? []
    }
    
    /// Actions/commands that can be sent, using put operation of corresponding path string
    ///
    /// Path strings can contain following placeholders:
    /// **event=**: event name may follow equal to sign in path. Event names are listed using `events` property. If given, the action targets the event.
    /// **resend=**: true or false may follow equal sign in path. true means client would like to receive old events passed since last drop of connection
    /// **details=[]**: list of detail names may follow equal sign in path, specifying event detailed data. Details are listed using `eventDetails`.
    /// **triggers=[]**: list of triggers may follow equal sign in path, specifying triggering related to action. Triggers are listed using `eventTriggers`.
    /// **endpoint=**: endpoint, listed by `endpoints`, that is related to the action. This can be used in post action paths.
    var actions: [String: String] {
        return dictionary?["cmd"] as? [String: String] ?? [:]
    }
    
    /// Just the action names from `actions` property
    var actionNames: [String] {
        return Array(actions.keys)
    }
    
    /// Just the action paths from `actions` property
    var actionPaths: [String] {
        return Array(actions.values)
    }
    
    /// Lists event details that may be requested as returned event parameter values using action
    /// path containing **details=[]** parameter placeholder
    /// - parameters:
    ///      - eventName: the REST API event to get details for
    /// - returns: detail names
    func eventDetails(for eventName: String) -> [String] {
        return (dictionary?[eventName] as? [String: [String]])?["details"] ?? []
    }
    
    /// Lists triggers that may be used as trigger parameter list values when action path contains
    /// **triggers=[]** parameter placheholder
    /// - parameters:
    ///      - eventName: the REST API event to get triggers for
    /// - returns: triggers for the events
    func eventTriggers(for eventName: String) -> [String] {
        return  (dictionary?[eventName] as? [String: [String]])?["triggers"] ?? []
    }
}

/// Methods related to working with services conforming to SAGRFC95 Service discovery over PFTP
public protocol PolarRestServiceApi {
    func listRestApiServices(identifier: String) async throws -> PolarDeviceRestApiServices
    func getRestApiDescription(identifier: String, path: String) async throws -> PolarDeviceRestApiServiceDescription
    func putNotification(identifier: String, notification: String, path: String) async throws
    func receiveRestApiEvents<T: Decodable>(identifier: String) -> AsyncThrowingStream<[T], Error>
}

extension PolarBleApiImpl: PolarRestServiceApi {

    func listRestApiServices(identifier: String) async throws -> PolarDeviceRestApiServices {
        return try await getJSONDecodableFromPath(identifier: identifier, path: "/REST/SERVICE.API")
    }

    func getRestApiDescription(identifier: String, path: String) async throws -> PolarDeviceRestApiServiceDescription {
        return try await getJSONDecodableFromPath(identifier: identifier, path: path)
    }

    private func getJSONDecodableFromPath<T: Decodable>(identifier: String, path: String) async throws -> T {
        let data = try await getDataFromPath(identifier: identifier, path: path)
        return try JSONDecoder().decode(T.self, from: data)
    }

    private func getDataFromPath(identifier: String, path: String) async throws -> Data {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
            throw PolarErrors.serviceNotFound
        }
        var operation = Protocol_PbPFtpOperation()
        operation.command = .get
        operation.path = path
        let requestData = try operation.serializedData()
        let responseData = try await client.request(requestData)
        return responseData as Data
    }

    func putNotification(identifier: String, notification: String, path: String) async throws {
        try await pFtpPutOperation(identifier: identifier, path: path, data: notification.data(using: .utf8)!)
    }

    private func pFtpPutOperation(identifier: String, path: String, data: Data) async throws {
        try await pFtpWriteOperation(identifier: identifier, command: .put, path: path, data: data)
    }

    private func pFtpWriteOperation(identifier: String, command: Protocol_PbPFtpOperation.Command, path: String, data: Data) async throws {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
            throw PolarErrors.serviceNotFound
        }
        var operation = Protocol_PbPFtpOperation()
        operation.command = command
        operation.path = path
        let proto = try operation.serializedData()
        let inputStream = InputStream(data: data)
        // Consume the write stream to completion (ignore progress values)
        for try await _ in client.write(proto as NSData, data: inputStream) {}
    }

    func receiveRestApiEvents<T: Decodable>(identifier: String) -> AsyncThrowingStream<[T], Error> {
        return AsyncThrowingStream { continuation in
            Task {
                do {
                    let session = try self.serviceClientUtils.sessionFtpClientReady(identifier)
                    guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                        continuation.finish(throwing: PolarErrors.serviceNotFound)
                        return
                    }
                    for try await items in client.receiveRestApiEvents(identifier: identifier) as AsyncThrowingStream<[T], Error> {
                        continuation.yield(items)
                    }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
        }
    }
}
