/// Copyright © 2025 Polar Electro Oy. All rights reserved.

import Foundation
import CoreBluetooth
import RxSwift

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
///
public protocol PolarRestServiceApi {
   
    /// Discover available services from device
    /// - parameters:
    ///   - identifier: Polar Device ID or BT address
    /// - returns:
    ///   emits single `PolarDeviceRestApiServices` object listing service names and corresponding paths or error
    ///
    func listRestApiServices(identifier: String) -> Single<PolarDeviceRestApiServices>
    
    /// Get details related to particular REST API.
    /// - parameters:
    ///   - identifier: Polar Device ID or BT address
    ///   - path: the REST API path corresponding to a named service returned by listRestApiServices
    /// - returns:
    ///  emits single `PolarDeviceRestApiServiceDescription` object with detailed description of the service or error
    ///
    func getRestApiDescription(identifier: String, path: String) -> Single<PolarDeviceRestApiServiceDescription>
    
    /// Notify device via a REST API in the device.
    ///
    /// - parameters:
    ///   - identifier: Polar device ID or BT address
    ///   - notification: content of the notification in JSON format.
    ///   - path:  the API endpoint that will be notified; the path of the REST API file in device + REST API parameters.
    /// - returns: Completable emitting success or error
    ///
    func putNotification(identifier: String, notification: String, path: String) -> Completable
    
    /// Streams for received device REST API events  parameters decoded as given Decodable type T endlessly.
    /// Only dispose , take(1) etc ... stops stream.
    ///
    /// Normally requires event action that enables subscribing to the event using putNotification()
    /// - parameters:
    ///   - identifier: Polar device ID or BT address
    /// - Returns:
    ///     Observable stream of REST API event parameters decoded as decodable T from JSON format.
    ///     Produces   onNext after successfully received notification and decoded as [T].
    ///             onCompleted not produced unless stream is further configured.
    ///             onError, see `BlePsFtpException, BleGattException`
    ///
    func receiveRestApiEvents<T:Decodable>(identifier: String) -> Observable<[T]>
}

extension PolarBleApiImpl : PolarRestServiceApi {
    
    func listRestApiServices(identifier: String) -> Single<PolarDeviceRestApiServices> {
        let observable: Single<PolarDeviceRestApiServices> =
        getJSONDecodableFromPath(identifier: identifier, path: "/REST/SERVICE.API")
        return observable
    }
    
    func getRestApiDescription(identifier: String, path: String) -> Single<PolarDeviceRestApiServiceDescription> {
        let observable: Single<PolarDeviceRestApiServiceDescription> =
        getJSONDecodableFromPath(identifier: identifier, path: path)
        return observable
    }
    
    private func getJSONDecodableFromPath<T: Decodable>(identifier: String, path: String) -> Single<T> {
        return getDataFromPath(identifier: identifier, path: path)
            .map { try JSONDecoder().decode(T.self, from: $0) }
    }
    
    private func getDataFromPath(identifier: String, path: String) -> Single<Data> {
        return Single<Data>.create { single in
            do {
                let session = try self.sessionFtpClientReady(identifier)
                guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                    single(.failure(PolarErrors.serviceNotFound))
                    return Disposables.create()
                }
                var operation = Protocol_PbPFtpOperation()
                operation.command =  Protocol_PbPFtpOperation.Command.get
                operation.path = path
                let requestData = try operation.serializedData()
                
                let disposable = client.request(requestData)
                    .subscribe(
                        onSuccess: { responseData in
                            single(.success(responseData as Data))
                        },
                        onFailure: { error in
                            single(.failure(error))
                        }
                    )
                return Disposables.create {
                    disposable.dispose()
                }
            } catch {
                single(.failure(error))
                return Disposables.create()
            }
        }
    }
    
    func putNotification(identifier: String, notification: String, path: String) -> Completable {
        return pFtpPutOperation(identifier: identifier, path: path, data: notification.data(using: .utf8)!)
    }
    
    private func pFtpPutOperation(identifier: String, path: String, data: Data) -> Completable {
        return pFtpWriteOperation(identifier: identifier, command:.put, path: path, data: data)
    }
    
    private func pFtpWriteOperation(identifier: String, command: Protocol_PbPFtpOperation.Command, path: String, data: Data) -> Completable {
        do {
            let session = try self.sessionFtpClientReady(identifier)
            guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                return Completable.error(PolarErrors.serviceNotFound)
            }
            var operation = Protocol_PbPFtpOperation()
            operation.command = command
            operation.path = path
            let proto = try operation.serializedData()
            let inputStream = InputStream(data: data)
            
            return client.write(proto as NSData, data: inputStream)
                .ignoreElements().asCompletable()
        } catch let err {
            return Completable.error(err)
        }
    }
    
    func receiveRestApiEvents<T:Decodable>(identifier: String) -> Observable<[T]> {
        do {
            let session = try self.sessionFtpClientReady(identifier)
            guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                return Observable.error(PolarErrors.serviceNotFound)
            }
            return client.receiveRestApiEvents(identifier: identifier)
        } catch let error {
            return Observable.error(error)
        }
    }
}
