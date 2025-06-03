//  Copyright © 2025 Polar. All rights reserved.

import Foundation

public struct PolarTrainingSessionReference: Equatable {
    public let date: Date
    public let path: String
    public var trainingDataTypes: [PolarTrainingSessionDataTypes]
    public var exercises: [PolarExercise]

    public init(date: Date,
                path: String,
                trainingDataTypes: [PolarTrainingSessionDataTypes],
                exercises: [PolarExercise]) {
        self.date = date
        self.path = path
        self.trainingDataTypes = trainingDataTypes
        self.exercises = exercises
    }
}

public enum PolarTrainingSessionDataTypes: String {
    case trainingSessionSummary = "TSESS.BPB"
}

public struct PolarTrainingSession {
    public let reference: PolarTrainingSessionReference
    public let sessionSummary: Data_PbTrainingSession
    public let exercises: [PolarExercise]

    public init(reference: PolarTrainingSessionReference, sessionSummary: Data_PbTrainingSession, exercises: [PolarExercise]) {
        self.reference = reference
        self.sessionSummary = sessionSummary
        self.exercises = exercises
    }
}

public struct PolarExercise: Equatable {
    public let index: Int
    public let path: String
    public var exerciseDataTypes: [PolarExerciseDataTypes]
    public let exerciseSummary: Data_PbExerciseBase?
    public let route: Data_PbExerciseRouteSamples?
    public let routeAdvanced: Data_PbExerciseRouteSamples2?

    init(index: Int,
         path: String,
         exerciseDataTypes: [PolarExerciseDataTypes] = [],
         exerciseSummary: Data_PbExerciseBase? = nil,
         route: Data_PbExerciseRouteSamples? = nil,
         routeAdvanced: Data_PbExerciseRouteSamples2? = nil) {
        self.index = index
        self.path = path
        self.exerciseDataTypes = exerciseDataTypes
        self.exerciseSummary = exerciseSummary
        self.route = route
        self.routeAdvanced = routeAdvanced
    }
}

public enum PolarExerciseDataTypes: String {
    case exerciseSummary = "BASE.BPB"
    case route = "ROUTE.BPB"
    case routeGzip = "ROUTE.GZB"
    case routeAdvancedFormat = "ROUTE2.BPB"
    case routeAdvancedFormatGzip = "ROUTE2.GZB"
}
