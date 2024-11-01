import Foundation
import SwiftProtobuf

public struct PolarFirstTimeUseConfig {

    public enum Gender {
        case male
        case female
    }

    public enum TypicalDay: Int, CaseIterable {
        case mostlySitting = 1
        case mostlyStanding = 2
        case mostlyMoving = 3

        public var description: String {
            switch self {
            case .mostlySitting:
                return "Mostly Sitting"
            case .mostlyStanding:
                return "Mostly Standing"
            case .mostlyMoving:
                return "Mostly Moving"
            }
        }
    }

    public enum TrainingBackground: Int {
            case occasional = 10
            case regular = 20
            case frequent = 30
            case heavy = 40
            case semiPro = 50
            case pro = 60
        }

    public let gender: Gender
    public let birthDate: Date
    public let height: Float
    public let weight: Float
    public let maxHeartRate: Int
    public let vo2Max: Int
    public let restingHeartRate: Int
    public let trainingBackground: TrainingBackground
    public let deviceTime: String
    public let typicalDay: TypicalDay
    public let sleepGoalMinutes: Int

    static let FTU_CONFIG_FILEPATH = "/U/0/S/PHYSDATA.BPB"

    public init(
        gender: Gender,
        birthDate: Date,
        height: Float,
        weight: Float,
        maxHeartRate: Int,
        vo2Max: Int,
        restingHeartRate: Int,
        trainingBackground: TrainingBackground,
        deviceTime: String,
        typicalDay: TypicalDay,
        sleepGoalMinutes: Int
    ) {
        assert(height >= 90.0 && height <= 240.0, "Height must be between 90 and 240 cm")
        assert(weight >= 15.0 && weight <= 300.0, "Weight must be between 15 and 300 kg")
        assert(maxHeartRate >= 100 && maxHeartRate <= 240, "Max heart rate must be between 100 and 240 bpm")
        assert(restingHeartRate >= 20 && restingHeartRate <= 120, "Resting heart rate must be between 20 and 120 bpm")
        assert(trainingBackground.rawValue >= 10 && trainingBackground.rawValue <= 60, "Training background out of range")
        assert(vo2Max >= 10 && vo2Max <= 95, "VO2 max must be between 10 and 95")
        assert(sleepGoalMinutes >= 300 && sleepGoalMinutes <= 660, "Sleep goal must be between 300 and 660 minutes")

        self.gender = gender
        self.birthDate = birthDate
        self.height = height
        self.weight = weight
        self.maxHeartRate = maxHeartRate
        self.vo2Max = vo2Max
        self.restingHeartRate = restingHeartRate
        self.trainingBackground = trainingBackground
        self.deviceTime = deviceTime
        self.typicalDay = typicalDay
        self.sleepGoalMinutes = sleepGoalMinutes
    }

    func toProto() -> Data_PbUserPhysData? {
        let dateFormatter = ISO8601DateFormatter()
        dateFormatter.formatOptions = [.withInternetDateTime]
        dateFormatter.timeZone = TimeZone(secondsFromGMT: 0)

        guard let deviceTimeDate = dateFormatter.date(from: deviceTime) else {
            BleLogger.error("Failed to parse deviceTime from: \(deviceTime)")
            return nil
        }

        let calendar = Calendar(identifier: .gregorian)
        let utcTimeZone = TimeZone(secondsFromGMT: 0)!
        let components = calendar.dateComponents(in: utcTimeZone, from: deviceTimeDate)

        let lastModified = PbSystemDateTime.with {
            $0.date = PbDate.with {
                $0.year = UInt32(components.year!)
                $0.month = UInt32(components.month!)
                $0.day = UInt32(components.day!)
            }
            $0.time = PbTime.with {
                $0.hour = UInt32(components.hour!)
                $0.minute = UInt32(components.minute!)
                $0.seconds = UInt32(components.second!)
            }
            $0.trusted = true
        }

        let birthday = Data_PbUserBirthday.with {
            $0.value = PbDate.with {
                $0.year = UInt32(Calendar.current.component(.year, from: birthDate))
                $0.month = UInt32(Calendar.current.component(.month, from: birthDate))
                $0.day = UInt32(Calendar.current.component(.day, from: birthDate))
            }
            $0.lastModified = lastModified
        }

        let genderPb = Data_PbUserGender.with {
            $0.value = (gender == .male) ? .male : .female
            $0.lastModified = lastModified
        }

        let weightPb = Data_PbUserWeight.with {
            $0.value = weight
            $0.lastModified = lastModified
        }

        let heightPb = Data_PbUserHeight.with {
            $0.value = height
            $0.lastModified = lastModified
        }

        let maxHeartRatePb = Data_PbUserHrAttribute.with {
            $0.value = UInt32(maxHeartRate)
            $0.lastModified = lastModified
        }

        let restingHeartRatePb = Data_PbUserHrAttribute.with {
            $0.value = UInt32(restingHeartRate)
            $0.lastModified = lastModified
        }

        let trainingBackgroundPb = Data_PbUserTrainingBackground.with {
            $0.value = Data_PbUserTrainingBackground.TrainingBackground(rawValue: trainingBackground.rawValue)!
            $0.lastModified = lastModified
        }

        let vo2MaxPb = Data_PbUserVo2Max.with {
            $0.value = UInt32(vo2Max)
            $0.lastModified = lastModified
        }

        let typicalDayPb = Data_PbUserTypicalDay.with {
            $0.value = Data_PbUserTypicalDay.TypicalDay(rawValue: typicalDay.rawValue)!
            $0.lastModified = lastModified
        }

        let sleepGoalPb = Data_PbSleepGoal.with {
            $0.sleepGoalMinutes = UInt32(sleepGoalMinutes)
            $0.lastModified = lastModified
        }

        return Data_PbUserPhysData.with {
            $0.birthday = birthday
            $0.gender = genderPb
            $0.weight = weightPb
            $0.height = heightPb
            $0.maximumHeartrate = maxHeartRatePb
            $0.restingHeartrate = restingHeartRatePb
            $0.trainingBackground = trainingBackgroundPb
            $0.vo2Max = vo2MaxPb
            $0.typicalDay = typicalDayPb
            $0.sleepGoal = sleepGoalPb
            $0.lastModified = lastModified
        }
    }
}
