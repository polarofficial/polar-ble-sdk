//  Copyright © 2025 Polar. All rights reserved.

import Foundation
import PolarBleSdk

struct ExerciseUIState {
    var status: PolarExerciseSession.ExerciseStatus = .notStarted
    var statusText: String = "Not started"

    var isRefreshing: Bool = false

    var canStart: Bool = true
    var canPause: Bool = false
    var canResume: Bool = false
    var canStop: Bool = false

    var sportProfiles: [PolarExerciseSession.SportProfile] = PolarExerciseSession.SportProfile.allCases
    var selectedSport: PolarExerciseSession.SportProfile = .running

    mutating func apply(status new: PolarExerciseSession.ExerciseStatus,
                        sport: PolarExerciseSession.SportProfile) {
        status = new

        switch new {
        case .inProgress:
            statusText = "In progress (\(sport.displayName))"
        case .paused:
            statusText = "Paused (\(sport.displayName))"
        case .stopped:
            statusText = "Stopped (\(sport.displayName))"
        case .syncRequired:
            statusText = "Synchronizing…"
        case .notStarted:
            statusText = "Not started"
        }

        canStart  = (new == .notStarted || new == .stopped)
        canPause  = (new == .inProgress)
        canResume = (new == .paused)
        canStop   = (new == .inProgress || new == .paused)
    }
}
