//  Copyright © 2026 Polar. All rights reserved.

import SwiftUI

struct HrGraphViewWrapper: UIViewControllerRepresentable {
    let onClose: () -> Void
    
    func makeUIViewController(context: Context) -> HrGraphViewController {
        let viewController = HrGraphViewController(onClose: onClose)
        return viewController
    }
    
    func updateUIViewController(_ uiViewController: HrGraphViewController, context: Context) {}
}
