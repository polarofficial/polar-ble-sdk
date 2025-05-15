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
    public let exerciseDataTypes: [PolarExerciseDataTypes]
    public let exerciseSummary: Data_PbExerciseBase?

    init(index: Int,
         path: String,
         exerciseDataTypes: [PolarExerciseDataTypes] = [],
         exerciseSummary: Data_PbExerciseBase? = nil) {
        self.index = index
        self.path = path
        self.exerciseDataTypes = exerciseDataTypes
        self.exerciseSummary = exerciseSummary
    }
}

public enum PolarExerciseDataTypes: String {
    case exerciseSummary = "BASE.BPB"
}
