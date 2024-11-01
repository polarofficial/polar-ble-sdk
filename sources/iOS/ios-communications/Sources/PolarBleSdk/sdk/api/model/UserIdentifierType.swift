/// Copyright © 2024 Polar Electro Oy. All rights reserved.

import Foundation
import SwiftProtobuf

public struct UserIdentifierType {
    public let userIdLastModified: String

    public static let USER_IDENTIFIER_FILENAME = "/U/0/USERID.BPB"
    private static let MASTER_IDENTIFIER: UInt64 = UInt64.max

    public static func create() -> UserIdentifierType {
        let dateFormatter = ISO8601DateFormatter()
        dateFormatter.formatOptions = [.withInternetDateTime]
        let currentTime = dateFormatter.string(from: Date())
        return UserIdentifierType(userIdLastModified: currentTime)
    }

    func toProto() -> Data_PbUserIdentifier {
        let dateFormatter = ISO8601DateFormatter()
        dateFormatter.formatOptions = [.withInternetDateTime]
        let dateTimeParsed = dateFormatter.date(from: self.userIdLastModified)!

        let lastModified = PbSystemDateTime.with {
            $0.date = PbDate.with {
                $0.year = UInt32(Calendar.current.component(.year, from: dateTimeParsed))
                $0.month = UInt32(Calendar.current.component(.month, from: dateTimeParsed))
                $0.day = UInt32(Calendar.current.component(.day, from: dateTimeParsed))
            }
            $0.time = PbTime.with {
                $0.hour = UInt32(Calendar.current.component(.hour, from: dateTimeParsed))
                $0.minute = UInt32(Calendar.current.component(.minute, from: dateTimeParsed))
                $0.seconds = UInt32(Calendar.current.component(.second, from: dateTimeParsed))
            }
            $0.trusted = true
        }

        return Data_PbUserIdentifier.with {
            $0.masterIdentifier = UserIdentifierType.MASTER_IDENTIFIER
            $0.userIDLastModified = lastModified
        }
    }
}
