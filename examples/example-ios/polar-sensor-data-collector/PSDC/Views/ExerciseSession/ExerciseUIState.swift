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
    var selectedSport: PolarExerciseSession.SportProfile? = nil
    var customSportIdText: String = ""
    var startError: String? = nil
    private(set) var lastKnownSportId: UInt64 = UInt64(PolarExerciseSession.SportProfile.otherOutdoor.rawValue)

    var selectedSportId: UInt64? {
        let text = customSportIdText.trimmingCharacters(in: .whitespacesAndNewlines)
        if text.isEmpty { return selectedSport.map { UInt64($0.rawValue) } }
        return UInt64(text).flatMap { $0 > 0 ? $0 : nil }
    }

    var invalidSportId: Bool {
        !customSportIdText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && selectedSportId == nil
    }

    private func sportLabel(_ sport: PolarExerciseSession.SportProfile, sportId: UInt64) -> String {
        if sport == .unknown && sportId > 0 {
            let format = NSLocalizedString("exercise.sportId", value: "Sport ID %@", comment: "Custom exercise sport identifier")
            return String(format: format, String(sportId))
        }
        return sport.displayName
    }

    private func localizedStatus(_ key: String, fallback: String, label: String? = nil) -> String {
        let format = NSLocalizedString(key, value: fallback, comment: "Exercise status")
        return label.map { String(format: format, $0) } ?? format
    }

    var startTime: Date? = nil

    var isObservingNotifications: Bool = false
    var notificationEvent: String? = nil

    mutating func apply(status new: PolarExerciseSession.ExerciseStatus,
                        sport: PolarExerciseSession.SportProfile,
                        startTime newStartTime: Date? = nil,
                        sportId: UInt64? = nil) {
        status = new
        startTime = newStartTime
        let rawSportId = sportId ?? UInt64(sport.rawValue)
        if rawSportId > 0 { lastKnownSportId = rawSportId }
        let effectiveSportId = rawSportId == 0 && new != .inProgress && new != .paused
            ? lastKnownSportId : rawSportId
        let effectiveSport = Int(exactly: effectiveSportId).map { PolarExerciseSession.SportProfile.from(id: $0) } ?? .unknown
        let label = sportLabel(effectiveSport, sportId: effectiveSportId)

        switch new {
        case .inProgress:
            statusText = localizedStatus("exercise.status.inProgress", fallback: "In progress (%@)", label: label)
        case .paused:
            statusText = localizedStatus("exercise.status.paused", fallback: "Paused (%@)", label: label)
        case .stopped:
            statusText = localizedStatus("exercise.status.stopped", fallback: "Stopped (%@)", label: label)
        case .syncRequired:
            statusText = localizedStatus("exercise.status.synchronizing", fallback: "Synchronizing…")
        case .notStarted:
            statusText = localizedStatus("exercise.status.notStarted", fallback: "Not started")
        }

        canStart  = (new == .notStarted || new == .stopped)
        canPause  = (new == .inProgress)
        canResume = (new == .paused)
        canStop   = (new == .inProgress || new == .paused)
    }
    
    mutating func applyNotificationEvent(status: PolarExerciseSession.ExerciseStatus,
                                         sport: PolarExerciseSession.SportProfile,
                                         sportId: UInt64? = nil) {
        let label = sportLabel(sport, sportId: sportId ?? UInt64(sport.rawValue))
        switch status {
        case .notStarted:
            notificationEvent = localizedStatus("exercise.status.notStarted", fallback: "Not started")
        case .inProgress:
            notificationEvent = localizedStatus("exercise.status.inProgress", fallback: "In progress (%@)", label: label)
        case .paused:
            notificationEvent = localizedStatus("exercise.status.paused", fallback: "Paused (%@)", label: label)
        case .stopped:
            notificationEvent = localizedStatus("exercise.notification.stopped", fallback: "Stopped")
        case .syncRequired:
            notificationEvent = localizedStatus("exercise.notification.syncRequired", fallback: "Sync required")
        }
    }
}
