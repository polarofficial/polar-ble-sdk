/// Copyright © 2022 Polar Electro Oy. All rights reserved.

import Foundation
import SwiftUI
import PolarBleSdk

struct H10ExerciseView: View {
    @EnvironmentObject var bleSdkManager: PolarBleSdkManager
    
    var body: some View {
        
        VStack {
            
            Button("List exercises",
                   action: { bleSdkManager.listH10Exercises() }
            ).buttonStyle(SecondaryButtonStyle(buttonState: getButtonState()))
            
            Button(bleSdkManager.h10RecordingFeature.isFetchingRecording ?  "Reading exercise" : "Read exercise",
                   action: {
                
                Task {
                    await bleSdkManager.h10ReadExercise()
                }
                
            })
            .buttonStyle(SecondaryButtonStyle(buttonState: getRecordingReadButtonState()))
            .disabled(bleSdkManager.h10RecordingFeature.isFetchingRecording)
            .overlay {
                if bleSdkManager.h10RecordingFeature.isFetchingRecording {
                    ProgressView()
                }
            }
            
            Button("Remove exercise",
                   action: { bleSdkManager.h10RemoveExercise() }
            ).buttonStyle(SecondaryButtonStyle(buttonState: getButtonState()))
            
            
            Button(bleSdkManager.h10RecordingFeature.isEnabled ? "Stop H10 recording": "Start H10 recording",
                   action: { bleSdkManager.h10RecordingToggle() }
            ).buttonStyle(SecondaryButtonStyle(buttonState: getRecordingButtonState()))
            
            
            
        }
    }
    
    func getButtonState() -> ButtonState {
        if bleSdkManager.h10RecordingFeature.isSupported {
            return ButtonState.released
        } else {
            return ButtonState.disabled
        }
    }
    
    func getRecordingButtonState() -> ButtonState {
        if bleSdkManager.h10RecordingFeature.isSupported {
            if bleSdkManager.h10RecordingFeature.isEnabled {
                return ButtonState.pressedDown
            } else {
                return ButtonState.released
            }
        } else {
            return ButtonState.disabled
        }
    }
    
    func getRecordingStatusButtonState() -> ButtonState {
        if bleSdkManager.h10RecordingFeature.isSupported {
            return ButtonState.released
        } else {
            return ButtonState.disabled
        }
    }
    
    func getRecordingReadButtonState() -> ButtonState {
        if bleSdkManager.h10RecordingFeature.isSupported {
            if(bleSdkManager.h10RecordingFeature.isFetchingRecording) {
                return ButtonState.pressedDown
            } else {
                return ButtonState.released
            }
        } else {
            return ButtonState.disabled
        }
    }
}

struct H10ExerciseView_Previews: PreviewProvider {
    private static let h10RecordingFeature = H10RecordingFeature(
        isSupported: true,
        isEnabled: true,
        isFetchingRecording: true
    )
    
    private static let polarBleSdkManager: PolarBleSdkManager = {
        let polarBleSdkManager = PolarBleSdkManager()
        polarBleSdkManager.h10RecordingFeature = h10RecordingFeature
        return polarBleSdkManager
    }()
    
    static var previews: some View {
        ForEach(["iPhone 7 Plus", "iPad Pro (12.9-inch) (6th generation)"], id: \.self) { deviceName in
            H10ExerciseView()
                .previewDevice(PreviewDevice(rawValue: deviceName))
                .previewDisplayName(deviceName)
                .environmentObject(polarBleSdkManager)
        }
    }
}
