//  Copyright © 2024 Polar. All rights reserved.

import Foundation
import RxSwift

class FirmwareUpdateApi {

    enum Failure: Error {
        case responseParseError
        case requestFailed
        var localizedDescription: String {
            switch self {
            case .responseParseError:
                return "Failed to parse firmware update response"
            case .requestFailed:
                return "Firmware update request failed"
            }
        }
    }
    
    let baseURL = "https://firmware-management.polar.com"

    func checkFirmwareUpdate(firmwareUpdateRequest: FirmwareUpdateRequest, completion: @escaping (Result<FirmwareUpdateResponse, Error>) -> Void) {

        let url = "\(baseURL)/api/v1/firmware-update/check"
        
        var taskRequest = URLRequest(url: URL(string: url)!)
        taskRequest.setValue("application/json", forHTTPHeaderField: "Accept")
        taskRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")
        taskRequest.httpMethod = "POST"
        taskRequest.httpBody = try? JSONEncoder().encode(firmwareUpdateRequest)
        let request = taskRequest
        
        URLSession.shared.dataTask(with: request) { (data, response, error) in
            Task { @MainActor in
                
                BleLogger.trace("Request URL: \(String(describing: request.url))")
                BleLogger.trace("Request Method: \(request.httpMethod ?? "N/A")")
                BleLogger.trace("Request Headers: \(request.allHTTPHeaderFields ?? [:])")
                BleLogger.trace("Request Body: \(String(describing: firmwareUpdateRequest))")
                
                if let data = data {
                    
                    BleLogger.trace("Response Data: \(String(data: data, encoding: .utf8) ?? "N/A")")
                    
                    if let statusCode = (response as? HTTPURLResponse)?.statusCode {
                        switch statusCode {
                        case 400..<500:
                            BleLogger.error("Client error: (\(statusCode))")
                            if let errorResponse = try? JSONDecoder().decode(FirmwareUpdateErrorResponse.self, from: data) {
                                BleLogger.error("Error Response: \(errorResponse)")
                            } else {
                                BleLogger.error("Failed to decode client error response")
                            }
                        case 500..<600:
                            BleLogger.error("Server error: (\(statusCode))")
                        default:
                            BleLogger.error("Response status code: (\(statusCode))")
                        }
                        
                        if var fwResponse = try? JSONDecoder().decode(FirmwareUpdateResponse.self, from: data) {
                            fwResponse.statusCode = statusCode
                            completion(.success(fwResponse))
                        } else {
                            BleLogger.error("Failed to decode client response")
                            completion(.failure(Failure.responseParseError))
                        }
                    } else {
                        completion(.failure(Failure.requestFailed))
                    }
                }
            }
        }.resume()
    }
    
    func checkFirmwareUpdateFromFirmwareUrl(_ url: URL, completion: @escaping (Result<FirmwareUpdateResponse, Error>) -> Void) {
        let file = url.lastPathComponent
        if url.isFileURL && FileManager.default.fileExists(atPath: url.path) {
            let responseWithUrl = FirmwareUpdateResponse(version: "custom(\(file))", fileUrl: url.absoluteString, statusCode: 200)
            completion(.success(responseWithUrl))
            return
        }
        var request = URLRequest(url: url)
        request.httpMethod = "HEAD"
        URLSession.shared.dataTask(with: request) { (data, response, error) in
            Task { @MainActor in
                let statusCode = (response as? HTTPURLResponse)?.statusCode ?? 0
                let responseWithUrl = FirmwareUpdateResponse(version: "custom(\(file))", fileUrl: url.absoluteString, statusCode: statusCode)
                completion(.success(responseWithUrl))
            }
        }.resume()
    }

    func getFirmwareUpdatePackage(url: String) -> Observable<Data?> {
        return Observable.create { observer in
            URLSession.shared.dataTask(with: URLRequest(url: URL(string: url)!)) { (data, _, _) in
                if let data = data {
                    observer.onNext(data)
                    observer.onCompleted()
                } else {
                    observer.onNext(nil)
                    observer.onCompleted()
                }
            }.resume()
            return Disposables.create()
        }
    }
}
