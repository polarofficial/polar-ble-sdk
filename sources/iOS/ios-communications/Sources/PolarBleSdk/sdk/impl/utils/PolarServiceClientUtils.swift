//  Copyright © 2026 Polar. All rights reserved.

import Foundation
import CoreBluetooth
import RxSwift

class PolarServiceClientUtils {
    
    let listener: CBDeviceListenerImpl?
    required init(listener: CBDeviceListenerImpl) {
        self.listener = listener
    }

    /// Returns `true` when both PMD control-point and data notifications are enabled for the given session.
    static func pmdNotificationsEnabled(_ session: BleDeviceSession) -> Bool {
        guard let pmdClient = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as? BlePmdClient else {
            return false
        }
        return pmdClient.isCharacteristicNotificationEnabled(BlePmdClient.PMD_CP) &&
               pmdClient.isCharacteristicNotificationEnabled(BlePmdClient.PMD_DATA)
    }

    /// Returns `true` when both PSFTP MTU and D2H notifications are enabled for the given session.
    static func psFtpNotificationsEnabled(_ session: BleDeviceSession) -> Bool {
        guard let ftpClient = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
            return false
        }
        return ftpClient.isCharacteristicNotificationEnabled(BlePsFtpClient.PSFTP_MTU_CHARACTERISTIC) &&
               ftpClient.isCharacteristicNotificationEnabled(BlePsFtpClient.PSFTP_D2H_NOTIFICATION_CHARACTERISTIC)
    }

    func sessionPmdClientReady(_ identifier: String) throws -> BleDeviceSession {
        let session = try sessionServiceReady(identifier, service: BlePmdClient.PMD_SERVICE)
        if PolarServiceClientUtils.pmdNotificationsEnabled(session) {
            return session
        }
        throw PolarErrors.notificationNotEnabled
    }
    
    func sessionPfcClientReady(_ identifier: String) throws -> BleDeviceSession {
        let session = try sessionServiceReady(identifier, service: BlePfcClient.PFC_SERVICE)
        let client = session.fetchGattClient(BlePfcClient.PFC_SERVICE) as! BlePfcClient
        
        if client.isServiceDiscovered() {
            return session
        }
        throw PolarErrors.notificationNotEnabled
    }
    
    internal func sessionFtpClientReady(_ identifier: String) throws -> BleDeviceSession {
        let session = try sessionServiceReady(identifier, service: BlePsFtpClient.PSFTP_SERVICE)
        
        // Poll for up to 5 seconds waiting for FTP notifications to be enabled
        // This handles the case where service is discovered but automatic notification
        // enabling hasn't completed yet
        
        let startTime = Date()
        let timeout: TimeInterval = 5.0
        
        while Date().timeIntervalSince(startTime) < timeout {
            if PolarServiceClientUtils.psFtpNotificationsEnabled(session) {
                BleLogger.trace("sessionFtpClientReady - Notifications ready after \(Date().timeIntervalSince(startTime))s")
                return session
            }
            Thread.sleep(forTimeInterval: 0.1) // Sleep for 100ms
        }
        
        BleLogger.trace("sessionFtpClientReady - Timeout waiting for notifications")
        throw PolarErrors.notificationNotEnabled
    }
    
    func waitPfcClientReady(_ identifier: String) -> Single<BleDeviceSession> {
        return Observable.create { observer in
            _ = self.waitForServiceDiscovered(identifier, service: BlePfcClient.PFC_SERVICE)
                .subscribe(onNext: { session in
                    Observable.interval(.milliseconds(100), scheduler: MainScheduler.instance)
                        .take(10)
                        .flatMap { (_: Int64) -> Observable<BleDeviceSession> in
                            do {
                                let client = session.fetchGattClient(BlePfcClient.PFC_SERVICE) as! BlePfcClient
                                if client.isServiceDiscovered() && client.pfcEnabled.get() == 0 {
                                    observer.onNext(session)
                                    observer.onCompleted()
                                    return Observable.just(session)
                                }
                                return Observable.empty()
                            } catch {
                                observer.onError(self.handleError(error))
                                return Observable.error(self.handleError(error))
                            }
                        }
                        .subscribe(onError: { error in
                            observer.onError(self.handleError(error))
                        }, onCompleted: {
                            observer.onError(self.handleError(PolarErrors.notificationNotEnabled))
                        })
                }, onError: { error in
                    observer.onError(self.handleError(error))
                })
            return Disposables.create()
        }.asSingle()
    }
    
    func waitPmdClientReady(_ identifier: String) -> Observable<BleDeviceSession> {
        return Observable.create { observer in
            self.waitForServiceDiscovered(identifier, service: BlePmdClient.PMD_SERVICE)
                .subscribe(onNext: { session in
                    Observable.interval(.seconds(1), scheduler: MainScheduler.instance)
                        .take(10)
                        .flatMap { (_: Int64) -> Observable<BleDeviceSession> in
                            do {
                                if PolarServiceClientUtils.pmdNotificationsEnabled(session) {
                                    observer.onNext(session)
                                    observer.onCompleted()
                                    return Observable.just(session)
                                }
                                return Observable.empty()
                            } catch {
                                observer.onError(self.handleError(error))
                                return Observable.error(self.handleError(error))
                            }
                        }
                        .subscribe(onError: { error in
                            observer.onError(self.handleError(error))
                        }, onCompleted: {
                            observer.onError(self.handleError(PolarErrors.notificationNotEnabled))
                        })
                }, onError: { error in
                    observer.onError(self.handleError(error))
                })
            
            return Disposables.create()
        }
    }
    
    func sessionHrClientReady(_ identifier: String) throws -> BleDeviceSession {
        let session = try sessionServiceReady(identifier, service: BleHrClient.HR_SERVICE)
        if let client = session.fetchGattClient(BleHrClient.HR_SERVICE) as? BleHrClient,
           client.isCharacteristicNotificationEnabled(BleHrClient.HR_MEASUREMENT) {
            return session
        }
        throw PolarErrors.notificationNotEnabled
    }
    
    func sessionServiceReady(_ identifier: String, service: CBUUID) throws -> BleDeviceSession {
        if let session = try fetchSession(identifier) {
            if session.state == BleDeviceSession.DeviceSessionState.sessionOpen {
                if let client = session.fetchGattClient(service){
                    if client.isServiceDiscovered() {
                        return session
                    }
                    throw PolarErrors.notificationNotEnabled
                }
                throw PolarErrors.serviceNotFound
            }
            throw PolarErrors.deviceNotConnected
        }
        throw PolarErrors.deviceNotFound
    }
    
    private func waitForServiceDiscovered(_ identifier: String, service: CBUUID) -> Observable<BleDeviceSession> {
        return Observable.create { observer in
            do {
                if let session = try self.fetchSession(identifier) {
                    if session.state == BleDeviceSession.DeviceSessionState.sessionOpen {
                        return Observable.interval(.milliseconds(500), scheduler: MainScheduler.instance)
                            .takeWhile { (time: Int64) -> Bool in
                                return !(session.fetchGattClient(service)?.isServiceDiscovered() ?? false)
                            }
                            .subscribe(onNext: { _ in
                            }, onError: { error in
                                observer.onError(error)
                            }, onCompleted: {
                                observer.onNext(session)
                                observer.onCompleted()
                            })
                    } else {
                        observer.onError(PolarErrors.deviceNotConnected)
                    }
                } else {
                    observer.onError(PolarErrors.deviceNotFound)
                }
            } catch {
                observer.onError(error)
            }
            return Disposables.create()
        }.timeout(.seconds(10), scheduler: MainScheduler.instance)
    }
    
    func fetchSession(_ identifier: String) throws -> BleDeviceSession? {
        if identifier.matches("^([0-9a-fA-F]{8})(-[0-9a-fA-F]{4}){3}-([0-9a-fA-F]{12})") {
            return sessionByDeviceAddress(identifier)
        } else if identifier.matches("([0-9a-fA-F]){6,8}") {
            return sessionByDeviceId(identifier)
        }
        throw PolarErrors.invalidArgument()
    }
    
    func getRSSIValue(_ identifier: String) throws -> Int {
        if let session = try fetchSession(identifier) {
            return session.rssi
        }
        throw PolarErrors.invalidArgument()
    }

    func checkIfDeviceDisconnectedDueRemovedPairing(identifier: String) throws -> Bool {
        do {
            let session = try fetchSession(identifier)
            return session?.disconnectedDueRemovedPairing ?? false
        } catch let err {
            throw PolarErrors.deviceError(description: "checkIfDeviceDisconnectedDueRemovedPairing failed for device \(identifier). Error: \(err.localizedDescription)")
        }
    }

    fileprivate func sessionByDeviceAddress(_ identifier: String) -> BleDeviceSession? {
        return listener?.allSessions().filter { (sess: BleDeviceSession) -> Bool in
            return sess.address.uuidString == identifier
        }.first
    }
    
    fileprivate func sessionByDeviceId(_ identifier: String) -> BleDeviceSession? {
        return listener?.allSessions().filter { (sess: BleDeviceSession) -> Bool in
            return sess.advertisementContent.polarDeviceIdUntouched == identifier
        }.first
    }

    private func handleError(_ error: Error) -> Error {
        let nsError = error as NSError

        if let mapped = Protocol_PbPFtpError(rawValue: nsError.code) {
            return NSError(
                domain: nsError.domain,
                code: nsError.code,
                userInfo: [NSLocalizedDescriptionKey: "\(mapped) (\(nsError.localizedDescription))"]
            )
        }

        return error
    }
    
}
