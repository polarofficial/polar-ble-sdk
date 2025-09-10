//  Copyright © 2024 Polar. All rights reserved.

import Foundation
import SwiftUI

struct PrimaryMenuStyle : MenuStyle {
    func makeBody(configuration: Configuration) -> some View {
        Menu(configuration)
            .padding(3)
            .padding(.horizontal, 13)
            .foregroundColor(Color.red)
            .border(Color.red, width: 2.0)
            .clipShape(Capsule())
    }
}

struct ButtonMenuStyle: MenuStyle {
    
    private let colorButtonDisabled = Color.secondary
    private let colorButtonDown = Color.blue
    private let colorButtonReleased = Color.red

    func makeBody(configuration: Configuration) -> some View {
        Menu(configuration)
        .frame(minWidth: 0, maxWidth: .infinity)
        .padding(.vertical, 5)
        .foregroundColor(colorButtonReleased)
        .overlay(
            RoundedRectangle(cornerRadius: 20)
                .stroke(colorButtonReleased, lineWidth: 4)
        )
        .padding(.horizontal, 30)
    }
}
