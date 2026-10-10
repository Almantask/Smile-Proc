import Foundation

public enum InstantFormat {
    public static let utc: TimeZone = TimeZone(secondsFromGMT: 0) ?? TimeZone(identifier: "UTC") ?? .current

    public static func parse(_ text: String) throws -> Date {
        guard text.hasSuffix("Z") else { throw SmileError.invalidInstant }
        let body = String(text.dropLast())
        let pieces = body.split(separator: "T", maxSplits: 1, omittingEmptySubsequences: false).map(String.init)
        guard pieces.count == 2 else { throw SmileError.invalidInstant }
        let dateParts = pieces[0].split(separator: "-", omittingEmptySubsequences: false).map(String.init)
        guard dateParts.count == 3,
              let year = Int(dateParts[0]),
              let month = Int(dateParts[1]),
              let day = Int(dateParts[2])
        else { throw SmileError.invalidInstant }

        let timePieces = pieces[1].split(separator: ".", maxSplits: 1, omittingEmptySubsequences: false).map(String.init)
        let clock = timePieces[0].split(separator: ":", omittingEmptySubsequences: false).map(String.init)
        guard clock.count == 3,
              let hour = Int(clock[0]),
              let minute = Int(clock[1]),
              let second = Int(clock[2])
        else { throw SmileError.invalidInstant }

        var millis = 0
        if timePieces.count == 2 {
            var fraction = timePieces[1]
            if fraction.count > 3 {
                fraction = String(fraction.prefix(3))
            }
            while fraction.count < 3 {
                fraction.append("0")
            }
            millis = Int(fraction) ?? 0
        }

        var components = DateComponents()
        components.calendar = utcCalendar()
        components.timeZone = utc
        components.year = year
        components.month = month
        components.day = day
        components.hour = hour
        components.minute = minute
        components.second = second
        components.nanosecond = millis * 1_000_000
        guard let date = components.date else { throw SmileError.invalidInstant }
        return date
    }

    public static func format(_ date: Date) -> String {
        let millisTotal = Int((date.timeIntervalSince1970 * 1000).rounded(.toNearestOrAwayFromZero))
        let seconds = millisTotal / 1000
        let millis = abs(millisTotal % 1000)
        let whole = Date(timeIntervalSince1970: TimeInterval(seconds))
        let parts = utcCalendar().dateComponents([.year, .month, .day, .hour, .minute, .second], from: whole)
        let year = parts.year ?? 0
        let month = parts.month ?? 1
        let day = parts.day ?? 1
        let hour = parts.hour ?? 0
        let minute = parts.minute ?? 0
        let second = parts.second ?? 0
        return String(format: "%04d-%02d-%02dT%02d:%02d:%02d.%03dZ", year, month, day, hour, minute, second, millis)
    }

    private static func utcCalendar() -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = utc
        return calendar
    }
}
