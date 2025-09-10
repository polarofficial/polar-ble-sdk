/// Copyright © 2021 Polar Electro Oy. All rights reserved.

import SwiftUI
import UserNotifications

class AppState: ObservableObject {
    @Published var bleDeviceManager = PolarBleDeviceManager()
    @Published var bleSdkManager = PolarBleSdkManager()
    func switchTo(_ bleSdkManager: PolarBleSdkManager) {
        self.bleSdkManager = bleSdkManager
    }
}

@main
struct PSDCApp: App {
    
    @StateObject private var appState = AppState()
    private let nofificationCenterDelegate = NotificationCenterDelegate()
    init() {
        UNUserNotificationCenter.current().delegate = nofificationCenterDelegate
    }
    var body: some Scene {
        WindowGroup {
            ContentView()
                .id(nullPolarDeviceInfo.deviceId)
                .environmentObject(appState.bleDeviceManager)
                .environmentObject(appState.bleSdkManager)
                .environmentObject(appState)
        }
        
    }
}

// Mark: - iOS local notifications, display also when app is in forground
class NotificationCenterDelegate: NSObject, UNUserNotificationCenterDelegate {
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        return [.banner]
    }
}

