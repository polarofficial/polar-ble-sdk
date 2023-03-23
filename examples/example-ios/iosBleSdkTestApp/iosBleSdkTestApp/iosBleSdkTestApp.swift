/// Copyright © 2021 Polar Electro Oy. All rights reserved.

import SwiftUI

@main
struct iosBleSdkTestApp: App {
    @StateObject var bleSdkManager = PolarBleSdkManager()
    
    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(bleSdkManager)
        }
    }
}
