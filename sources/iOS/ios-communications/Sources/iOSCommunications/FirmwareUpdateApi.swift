//  Copyright © 2024 Polar. All rights reserved.

import Foundation
import Alamofire
import RxSwift

class FirmwareUpdateApi {
    let baseURL = "https://firmware-management.polar.com"

    func checkFirmwareUpdate(firmwareUpdateRequest: FirmwareUpdateRequest, completion: @escaping (Result<FirmwareUpdateResponse, AFError>) -> Void) {
        let url = "\(baseURL)/api/v1/firmware-update/check"

        let headers: HTTPHeaders = [
            "Accept": "application/json",
            "Content-Type": "application/json"
        ]

        AF.request(url, method: .post, parameters: firmwareUpdateRequest, encoder: JSONParameterEncoder.default, headers: headers)
            .responseDecodable(of: FirmwareUpdateResponse.self) { response in
            if let request = response.request {
                BleLogger.error("Request URL: \(String(describing: request.url))")
                BleLogger.error("Request Method: \(request.method?.rawValue ?? "N/A")")
                BleLogger.error("Request Headers: \(request.allHTTPHeaderFields ?? [:])")
                BleLogger.error("Request Body: \(String(describing: firmwareUpdateRequest))")
            }

                if let data = response.data {
                    BleLogger.trace("Response Data: \(String(data: data, encoding: .utf8) ?? "N/A")")

                    if let statusCode = response.response?.statusCode {
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
                    }
                }

            switch response.result {
            case .success(let firmwareUpdateResponse):
                var responseWithStatusCode = firmwareUpdateResponse
                if let statusCode = response.response?.statusCode {
                    responseWithStatusCode.statusCode = statusCode
                }
                completion(.success(responseWithStatusCode))
            case .failure(let error):
                BleLogger.error("Request failed with error: \(error)")
                completion(.failure(error))
            }
        }
    }

    func getFirmwareUpdatePackage(url: String) -> Observable<Data?> {
        return Observable.create { observer in
            AF.request(url, method: .get).responseData { response in
                switch response.result {
                case .success(let data):
                    observer.onNext(data)
                    observer.onCompleted()
                case .failure:
                    observer.onNext(nil)
                    observer.onCompleted()
                }
            }
            return Disposables.create()
        }
    }
}
