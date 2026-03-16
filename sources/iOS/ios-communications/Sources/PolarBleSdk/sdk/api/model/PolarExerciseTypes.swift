//  Copyright © 2026 Polar Electro Oy. All rights reserved.

import Foundation

/// Polar exercise entry.
///
///     - path: Resource location in the device
///     - date: Entry date and time
///     - entryId: unique identifier
public typealias PolarExerciseEntry = (path: String, date: Date, entryId: String)

/// Polar Exercise Data
///
///     - interval: in seconds
///     - samples: List of HR or RR samples in BPM
public typealias PolarExerciseData = (interval: UInt32, samples: [UInt32])