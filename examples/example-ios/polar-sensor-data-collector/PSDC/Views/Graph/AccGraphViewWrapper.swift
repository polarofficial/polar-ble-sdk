//  Copyright © 2026 Polar. All rights reserved.

import SwiftUI

struct AccGraphViewWrapper: UIViewControllerRepresentable {
    let onClose: () -> Void

    func makeUIViewController(context: Context) -> AccGraphViewController {
        return AccGraphViewController(onClose: onClose)
    }

    func updateUIViewController(_ uiViewController: AccGraphViewController, context: Context) {}
}
