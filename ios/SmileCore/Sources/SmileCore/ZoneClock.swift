import Foundation

public struct LocalDate: Hashable, Sendable, Equatable {
    public var year: Int
    public var month: Int
    public var day: Int

    public init(year: Int, month: Int, day: Int) {
        self.year = year
        self.month = month
        self.day = day
    }
}

public enum ZoneClock {
    public static func zone(_ identifier: String) throws -> TimeZone {
        guard let zone = TimeZone(identifier: identifier) else {
            throw SmileError.unknownTimeZone
        }
        return zone
    }

    public static func calendar(_ zone: TimeZone) -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = zone
        return calendar
    }

    public static func components(_ date: Date, zone: TimeZone) -> DateComponents {
        calendar(zone).dateComponents([.year, .month, .day, .hour, .minute, .second, .weekday], from: date)
    }

    public static func isoWeekday(_ date: Date, zone: TimeZone) -> Int {
        let weekday = components(date, zone: zone).weekday ?? 1
        return weekday == 1 ? 7 : weekday - 1
    }

    public static func hour(_ date: Date, zone: TimeZone) -> Int {
        components(date, zone: zone).hour ?? 0
    }

    public static func localDate(_ date: Date, zone: TimeZone) -> LocalDate {
        let parts = components(date, zone: zone)
        return LocalDate(year: parts.year ?? 1970, month: parts.month ?? 1, day: parts.day ?? 1)
    }

    public static func make(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int = 0,
        zone: TimeZone
    ) throws -> Date {
        var components = DateComponents()
        components.calendar = calendar(zone)
        components.timeZone = zone
        components.year = year
        components.month = month
        components.day = day
        components.hour = hour
        components.minute = minute
        components.second = second
        components.nanosecond = 0
        guard let date = components.date else { throw SmileError.invalidDate }
        return date
    }

    public static func addingDays(_ days: Int, to date: LocalDate, zone: TimeZone) throws -> LocalDate {
        let base = try make(year: date.year, month: date.month, day: date.day, hour: 12, minute: 0, zone: zone)
        guard let shifted = calendar(zone).date(byAdding: .day, value: days, to: base) else {
            throw SmileError.invalidDate
        }
        return localDate(shifted, zone: zone)
    }

    public static func isoWeekdayNumber(_ swiftWeekday: Int) -> Int {
        swiftWeekday == 1 ? 7 : swiftWeekday - 1
    }

    public static func swiftWeekday(iso: Int) -> Int {
        iso == 7 ? 1 : iso + 1
    }
}
