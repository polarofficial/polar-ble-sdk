// Copyright © 2025 Polar. All rights reserved.

import Foundation

/// Error thrown when PMD data frame parsing fails due to invalid or unexpected device data.
public struct PmdDataParseError: Error, CustomStringConvertible {

    public let message: String

    public var description: String { message }

    public init(message: String) {
        self.message = message
    }

    public static func toHex(_ data: some Collection<UInt8>) -> String {
        data.map { String(format: "%02X", $0) }.joined(separator: " ")
    }
}

