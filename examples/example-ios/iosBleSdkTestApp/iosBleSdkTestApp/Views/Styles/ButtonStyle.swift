/// Copyright © 2021 Polar Electro Oy. All rights reserved.

import SwiftUI

enum ButtonState {
    case disabled
    case pressedDown
    case released
}

private let colorButtonDisabled = Color.secondary
private let colorButtonDown = Color.blue
private let colorButtonReleased = Color.red

struct PrimaryButtonStyle : ButtonStyle {
    let buttonState: ButtonState
    
    func makeBody(configuration: Configuration) -> some View {
        switch buttonState {
        case .disabled:
            configuration.label
                .frame(minWidth: 0, maxWidth: .infinity)
                .padding(.vertical, 5)
                .background(configuration.isPressed ? colorButtonDisabled.opacity(0.5) : colorButtonDisabled)
                .foregroundColor(Color.white)
                .clipShape(Capsule())
                .padding(.horizontal, 30)
        case .pressedDown:
            configuration.label
                .frame(minWidth: 0, maxWidth: .infinity)
                .padding(.vertical, 5)
                .background(configuration.isPressed ? colorButtonDown.opacity(0.5) : colorButtonDown)
                .foregroundColor(Color.white)
                .clipShape(Capsule())
                .padding(.horizontal, 30)
        case .released:
            configuration.label
                .frame(minWidth: 0, maxWidth: .infinity)
                .padding(.vertical, 5)
                .background(configuration.isPressed ? colorButtonReleased.opacity(0.5) : colorButtonReleased)
                .foregroundColor(Color.white)
                .clipShape(Capsule())
                .padding(.horizontal, 30)
        }
    }
}

struct SecondaryButtonStyle : ButtonStyle {
    let buttonState: ButtonState
    func makeBody(configuration: Configuration) -> some View {
        switch buttonState {
        case .disabled:
            configuration.label
                .frame(minWidth: 0, maxWidth: .infinity)
                .padding(.vertical, 5)
                .foregroundColor(configuration.isPressed ? colorButtonDisabled.opacity(0.5) : colorButtonDisabled)
                .overlay(
                    RoundedRectangle(cornerRadius: 20)
                        .stroke(configuration.isPressed ? colorButtonDisabled.opacity(0.5) : colorButtonDisabled, lineWidth: 4)
                )
                .padding(.horizontal, 30)
        case .pressedDown:
            configuration.label
                .frame(minWidth: 0, maxWidth: .infinity)
                .padding(.vertical, 5)
                .foregroundColor(configuration.isPressed ? colorButtonDown.opacity(0.5) : colorButtonDown)
                .overlay(
                    RoundedRectangle(cornerRadius: 20)
                        .stroke(configuration.isPressed ? colorButtonDown.opacity(0.5) : colorButtonDown, lineWidth: 4)
                )
                .padding(.horizontal, 30)
        case .released:
            configuration.label
                .frame(minWidth: 0, maxWidth: .infinity)
                .padding(.vertical, 5)
                .foregroundColor(configuration.isPressed ? colorButtonReleased.opacity(0.5) : colorButtonReleased)
                .overlay(
                    RoundedRectangle(cornerRadius: 20)
                        .stroke(configuration.isPressed ? colorButtonReleased.opacity(0.5) : colorButtonReleased, lineWidth: 4)
                )
                .padding(.horizontal, 30)
        }
    }
}

struct ButtonStyle_Previews: PreviewProvider {
    static var previews: some View {
        Button("Primary disabled", action: {})
            .buttonStyle(PrimaryButtonStyle(buttonState: ButtonState.disabled))
            .previewLayout(PreviewLayout.sizeThatFits)
            .padding()
        
        Button("Primary pressed", action: {})
            .buttonStyle(PrimaryButtonStyle(buttonState: ButtonState.pressedDown))
            .previewLayout(PreviewLayout.sizeThatFits)
            .padding()
        Button("Primary released", action: {})
            .buttonStyle(PrimaryButtonStyle(buttonState: ButtonState.released))
            .previewLayout(PreviewLayout.sizeThatFits)
            .padding()
        
        Button("Secondary disabled", action: {})
            .buttonStyle(SecondaryButtonStyle(buttonState: ButtonState.disabled))
            .previewLayout(PreviewLayout.sizeThatFits)
            .padding()
        
        Button("Secondary pressed", action: {})
            .buttonStyle(SecondaryButtonStyle(buttonState: ButtonState.pressedDown))
            .previewLayout(PreviewLayout.sizeThatFits)
            .padding()
        
        Button("Secondary released", action: {})
            .buttonStyle(SecondaryButtonStyle(buttonState: ButtonState.released))
            .previewLayout(PreviewLayout.sizeThatFits)
            .padding()
        
    }
}
