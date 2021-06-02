/// Copyright © 2019 Polar Electro Oy. All rights reserved.

import SwiftUI

@main
struct iosBleSdkTestApp: App {
    
    @StateObject var viewModel = PolarBleSdkManager()
    
    var body: some Scene {
        WindowGroup {
            ContentView(viewModel: viewModel)
        }
    }
}
