//  Copyright © 2026 Polar. All rights reserved.

import Foundation
import CoreBluetooth

class PolarServiceClientUtils {

    let listener: CBDeviceListenerImpl?
    required init(listener: CBDeviceListenerImpl) {
        self.listener = listener
    }

    /// Returns `true` when both PMD control-point and data notifications are enabled.
    static func pmdNotificationsEnabled(_ session: BleDeviceSession) -> Bool {
        guard let pmdClient = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as? BlePmdClient else { return false }
        return pmdClient.isCharacteristicNotificationEnabled(BlePmdClient.PMD_CP) &&
               pmdClient.isCharacteristicNotificationEnabled(BlePmdClient.PMD_DATA)
    }

    /// Returns `true` when both PSFTP MTU and D2H notifications are enabled.
    static func psFtpNotificationsEnabled(_ session: BleDeviceSession) -> Bool {
        guard let ftpClient = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { return false }
        return ftpClient.isCharacteristicNotificationEnabled(BlePsFtpClient.PSFTP_MTU_CHARACTERISTIC) &&
               ftpClient.isCharacteristicNotificationEnabled(BlePsFtpClient.PSFTP_D2H_NOTIFICATION_CHARACTERISTIC)
    }

    func sessionPmdClientReady(_ identifier: String) throws -> BleDeviceSession {
        let session = try sessionServiceReady(identifier, service: BlePmdClient.PMD_SERVICE)
        if PolarServiceClientUtils.pmdNotificationsEnabled(session) { return session }
        throw PolarErrors.notificationNotEnabled
    }

    func sessionPfcClientReady(_ identifier: String) throws -> BleDeviceSession {
        let session = try sessionServiceReady(identifier, service: BlePfcClient.PFC_SERVICE)
        let client = session.fetchGattClient(BlePfcClient.PFC_SERVICE) as! BlePfcClient
        if client.isServiceDiscovered() { return session }
        throw PolarErrors.notificationNotEnabled
    }

    internal func sessionFtpClientReady(_ identifier: String) throws -> BleDeviceSession {
        let session = try sessionServiceReady(identifier, service: BlePsFtpClient.PSFTP_SERVICE)
        let startTime = Date()
        while Date().timeIntervalSince(startTime) < 5.0 {
            if PolarServiceClientUtils.psFtpNotificationsEnabled(session) { return session }
            Thread.sleep(forTimeInterval: 0.1)
        }
        throw PolarErrors.notificationNotEnabled
    }

    /// Async version: waits for PFC client to be ready.
    func waitPfcClientReady(_ identifier: String) async throws -> BleDeviceSession {
        let session = try await waitForServiceDiscovered(identifier, service: BlePfcClient.PFC_SERVICE)
        for _ in 0..<10 {
            let client = session.fetchGattClient(BlePfcClient.PFC_SERVICE) as! BlePfcClient
            if client.isServiceDiscovered() && client.pfcEnabled.get() == 0 {
                return session
            }
            try await Task.sleep(nanoseconds: 100_000_000) // 100ms
        }
        throw PolarErrors.notificationNotEnabled
    }

    /// Async version: waits for PMD client to be ready.
    func waitPmdClientReady(_ identifier: String) async throws -> BleDeviceSession {
        let session = try await waitForServiceDiscovered(identifier, service: BlePmdClient.PMD_SERVICE)
        for _ in 0..<10 {
            if PolarServiceClientUtils.pmdNotificationsEnabled(session) { return session }
            try await Task.sleep(nanoseconds: 1_000_000_000) // 1s
        }
        throw PolarErrors.notificationNotEnabled
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
            if session.state == .sessionOpen {
                if let client = session.fetchGattClient(service) {
                    if client.isServiceDiscovered() { return session }
                    throw PolarErrors.notificationNotEnabled
                }
                throw PolarErrors.serviceNotFound
            }
            throw PolarErrors.deviceNotConnected
        }
        throw PolarErrors.deviceNotFound
    }

    private func waitForServiceDiscovered(_ identifier: String, service: CBUUID) async throws -> BleDeviceSession {
        guard let session = try fetchSession(identifier) else { throw PolarErrors.deviceNotFound }
        guard session.state == .sessionOpen else { throw PolarErrors.deviceNotConnected }
        let deadline = Date().addingTimeInterval(10.0)
        while Date() < deadline {
            if session.fetchGattClient(service)?.isServiceDiscovered() ?? false { return session }
            try await Task.sleep(nanoseconds: 500_000_000) // 500ms
        }
        throw PolarErrors.notificationNotEnabled
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
        if let session = try fetchSession(identifier) { return session.rssi }
        throw PolarErrors.invalidArgument()
    }

    func checkIfDeviceDisconnectedDueRemovedPairing(identifier: String) throws -> Bool {
        do {
            return try fetchSession(identifier)?.disconnectedDueRemovedPairing ?? false
        } catch {
            throw PolarErrors.deviceError(description: "checkIfDeviceDisconnectedDueRemovedPairing failed for device \(identifier). Error: \(error.localizedDescription)")
        }
    }

    fileprivate func sessionByDeviceAddress(_ identifier: String) -> BleDeviceSession? {
        return listener?.allSessions().first { $0.address.uuidString == identifier }
    }

    fileprivate func sessionByDeviceId(_ identifier: String) -> BleDeviceSession? {
        return listener?.allSessions().first { $0.advertisementContent.polarDeviceIdUntouched == identifier }
    }
}
