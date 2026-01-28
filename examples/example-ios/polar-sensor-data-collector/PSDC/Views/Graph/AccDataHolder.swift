//  Copyright © 2026 Polar. All rights reserved.

import Foundation

struct AccSample {
    let x: Int
    let y: Int
    let z: Int
}

struct AccState {
    var accSamples: [AccSample] = []
    var currentSample: AccSample = AccSample(x: 0, y: 0, z: 0)
}

class AccDataHolder: ObservableObject {
    static let shared = AccDataHolder()
    
    private static let maxAccSamples = 3000
    
    @Published private(set) var accState = AccState()
    
    private var accSamplesList: [AccSample] = []
    
    private init() {}
    
    func updateAcc(x: Int32, y: Int32, z: Int32) {
        let sample = AccSample(
            x: Int(x),
            y: Int(y),
            z: Int(z)
        )
        accSamplesList.append(sample)

        if accSamplesList.count > Self.maxAccSamples {
            accSamplesList.removeFirst(accSamplesList.count - Self.maxAccSamples)
        }

        accState = AccState(
            accSamples: accSamplesList,
            currentSample: sample
        )
    }
    
    func clear() {
        accSamplesList.removeAll()
        accState = AccState()
    }
}
