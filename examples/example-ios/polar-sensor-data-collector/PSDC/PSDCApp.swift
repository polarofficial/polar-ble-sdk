/// Copyright © 2021 Polar Electro Oy. All rights reserved.

import SwiftUI
import UserNotifications

@MainActor
class AppState: ObservableObject {
    @Published var bleDeviceManager: PolarBleDeviceManager
    @Published var bleSdkManager: PolarBleSdkManager

    init() {
        // Create bleDeviceManager first, then create the initial bleSdkManager
        // using its shared api — so only ONE CBDeviceListenerImpl / CBCentralManager
        // is ever instantiated at startup.
        let deviceManager = PolarBleDeviceManager()
        self.bleDeviceManager = deviceManager
        self.bleSdkManager = deviceManager.makeSdkManager()
    }

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
    
    // UserDefaults key for storing the preference
    private static let showAlertsKey = "showUNNotificationsAsAlerts"
    
    /// Controls whether notifications should be shown as alerts when app is in foreground
    var showUNNotificationsAsAlerts: Bool {
        get {
            // Default to false if not set
            UserDefaults.standard.bool(forKey: Self.showAlertsKey)
        }
        set {
            UserDefaults.standard.set(newValue, forKey: Self.showAlertsKey)
        }
    }
    
    // This method is called when a notification is received while the app is in the foreground
    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        
        
        
        // Optionally, show an alert with the full notification content if enabled
        if showUNNotificationsAsAlerts {
            completionHandler([])
            DispatchQueue.main.async {
                self.showNotificationAlert(notification: notification)
            }
        } else {
            completionHandler([.banner, .sound, .list])
        }
    }
    
    // Show a native alert with the full notification content
    private func showNotificationAlert(notification: UNNotification) {
        let content = notification.request.content
        let title = content.title
        let body = content.body
        
        // Only show alert if there's actual content
        guard !body.isEmpty else { return }
        
        let alert = UIAlertController(
            title: title.isEmpty ? "Notification" : title,
            message: body,
            preferredStyle: .alert
        )
        
        alert.addAction(UIAlertAction(title: "OK", style: .default))
        
        // Get the top-most view controller to present the alert
        if let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
           let topViewController = windowScene.windows.first(where: { $0.isKeyWindow })?.rootViewController {
            var presenter = topViewController
            while let presented = presenter.presentedViewController {
                presenter = presented
            }
            presenter.present(alert, animated: true)
        }
    }
}

