//  Copyright © 2026 Polar. All rights reserved.

import UIKit
import SwiftUI

class AccGraphViewController: UIHostingController<AccGraphView> {

    private let onCloseCallback: () -> Void

    init(onClose: @escaping () -> Void) {
        self.onCloseCallback = onClose
        super.init(rootView: AccGraphView(onClose: {}))
        self.rootView = AccGraphView(onClose: { [weak self] in
            self?.dismiss(animated: true) {
                self?.onCloseCallback()
            }
        })
        modalPresentationStyle = .fullScreen
    }

    @MainActor
    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override var supportedInterfaceOrientations: UIInterfaceOrientationMask {
        .landscape
    }

    override var preferredInterfaceOrientationForPresentation: UIInterfaceOrientation {
        .landscapeLeft
    }
}
