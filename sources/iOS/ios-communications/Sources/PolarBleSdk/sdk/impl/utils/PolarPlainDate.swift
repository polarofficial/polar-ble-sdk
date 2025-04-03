//  Copyright © 2025 Polar. All rights reserved.

/// From https://bootstragram.com/blog/date-with-no-time-operations-swift
/// A string that represents dates using their ISO 8601 representations.
///
/// `PolarPlainDate` is a way to handle dates with no time — such as `2022-03-02` for March 2nd of 2022 — to
/// perform operations with convenience including adding days, dealing with ranges, etc.
///

import Foundation

public struct PolarPlainDate {
    /// Returns a date string initialized using their ISO 8601 representation.
    /// - Parameters:
    ///   - dateAsString: The ISO 8601 representation of the date. For instance, `2022-03-02`for March 2nd of 2022.
    ///   - calendar: The calendar — including the time zone — to use. The default is the current calendar.
    /// - Returns: A date string, or `nil` if a valid date could not be created from `dateAsString`.
    public init?(from dateAsString: String, calendar: Calendar = .current) {
        let formatter = Self.createFormatter(timeZone: calendar.timeZone)
        guard let date = formatter.date(from: dateAsString) else {
            return nil
        }

        self.init(date: date, calendar: calendar, formatter: formatter)
    }

    /// Returns a date string initialized using their ISO 8601 representation.
    /// - Parameters:
    ///   - date: The date to represent.
    ///   - calendar: The calendar — including the time zone — to use. The default is the current calendar.
    public init(date: Date, calendar: Calendar = .current) {
        self.init(date: date, calendar: calendar, formatter: Self.createFormatter(timeZone: calendar.timeZone))
    }

    private init(date: Date, calendar: Calendar = .current, formatter: ISO8601DateFormatter) {
        self.formatter = formatter
        self.date = date
        self.calendar = calendar
    }

    private let formatter: ISO8601DateFormatter
    private let date: Date
    private let calendar: Calendar

    private static func createFormatter(timeZone: TimeZone) -> ISO8601DateFormatter {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withFullDate]
        formatter.timeZone = timeZone
        return formatter
    }
}

extension PolarPlainDate: CustomStringConvertible {
    /// A string description of the `PlainDate` in ISO 8601 format.
    public var description: String {
        formatter.string(from: date)
    }
}

extension PolarPlainDate: ExpressibleByStringLiteral {
    public init(stringLiteral value: String) {
        self.init(from: value)!
    }
}

extension PolarPlainDate: Decodable {
    public init(from decoder: Decoder) throws {
        try self.init(stringLiteral: decoder.singleValueContainer().decode(String.self))
    }
}

extension PolarPlainDate: Encodable {
    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(description)
    }
}
