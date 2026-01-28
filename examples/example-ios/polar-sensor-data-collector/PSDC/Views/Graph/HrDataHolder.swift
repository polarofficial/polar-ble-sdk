//  Copyright © 2026 Polar. All rights reserved.

import Foundation

struct HrSample {
    let hr: Int
}

struct HrState {
    var hrSamples: [HrSample] = []
    var currentHr: Int = 0
}

class HrDataHolder: ObservableObject {
    static let shared = HrDataHolder()
    
    private static let maxHrSamples = 300
    
    @Published private(set) var hrState = HrState()
    
    private var hrSamplesList: [HrSample] = []
    
    private init() {}
    
    func updateHr(_ hr: Int) {
        let sample = HrSample(hr: hr)
        hrSamplesList.append(sample)
        
        hrState = HrState(
            hrSamples: hrSamplesList,
            currentHr: hr
        )
        
        if hrSamplesList.count > Self.maxHrSamples {
            hrSamplesList.removeFirst()
        }
    }
    
    func clear() {
        hrSamplesList.removeAll()
        hrState = HrState()
    }
}
