// Copyright © 2026 Polar Electro Oy. All rights reserved.

import SwiftUI
import os.log
import UserNotifications

private let appLogger = Logger(subsystem: "com.polar.bgsync", category: "App")

class AppDelegate: NSObject, UIApplicationDelegate {
    static var orientationLock = UIInterfaceOrientationMask.portrait

    func application(_ application: UIApplication, supportedInterfaceOrientationsFor window: UIWindow?) -> UIInterfaceOrientationMask {
        return AppDelegate.orientationLock
    }

    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        let isBackground = launchOptions?[.bluetoothCentrals] != nil
          appLogger.info("App launched — background BLE relaunch: \(isBackground)")

        // Request notification permission early so the dialog appears before any sync fires.
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { granted, error in
            if let error = error {
                appLogger.error("Notification permission request failed: \(error)")
            } else {
                let status = granted ? "granted" : "denied"
                appLogger.info("Notification permission \(status)")
                if !granted {
                    DispatchQueue.main.async {
                        SyncManager.shared.addLog("⚠️ Notifications denied — enable in Settings › BgSyncSample › Notifications to receive sync alerts")
                    }
                }
            }
        }

        if let centralIds = launchOptions?[.bluetoothCentrals] as? [String] {
            // App was relaunched by iOS due to a BLE event for a restored CBCentralManager
            appLogger.info("BgSync: App relaunched for BLE centrals: \(centralIds)")
            SyncManager.shared.resumeSyncAfterBackgroundRelaunch()
        }
        return true
    }
}

@main
struct BgSyncSampleApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @Environment(\.scenePhase) private var scenePhase
    @State private var hasEnteredBackground = false

    var body: some Scene {
        WindowGroup {
            SyncDashboardView()
        }
        .onChange(of: scenePhase) { newPhase in
            if newPhase == .background {
                hasEnteredBackground = true
            }
            if newPhase == .active && hasEnteredBackground {
                SyncManager.shared.onAppForegrounded()
            }
        }
    }
}
