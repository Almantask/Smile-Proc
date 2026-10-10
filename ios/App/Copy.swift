import SmileCore
import SwiftUI

enum Copy {
    static func fill(_ key: String, _ values: [String: String]) -> String {
        var text = L10n.string(key)
        for (name, value) in values {
            text = text.replacingOccurrences(of: "{\(name)}", with: value)
        }
        return text
    }

    static func moodName(_ mood: Mood) -> String {
        switch mood {
        case .low: return L10n.string("mood.low")
        case .okay: return L10n.string("mood.okay")
        case .good: return L10n.string("mood.good")
        }
    }

    static func moodLogLabel(_ mood: Mood) -> String {
        switch mood {
        case .low: return L10n.string("mood.a11y.low")
        case .okay: return L10n.string("mood.a11y.okay")
        case .good: return L10n.string("mood.a11y.good")
        }
    }

    static func tagName(_ id: String) -> String {
        L10n.string("tag.\(id)")
    }

    static func weekdayName(_ iso: Int) -> String {
        L10n.string("weekday.\(iso)")
    }

    static func daypartName(_ segment: String) -> String {
        L10n.string("daypart.\(segment)")
    }

    static func kindName(_ kind: PlaceKind) -> String {
        switch kind {
        case .home: return L10n.string("place.home")
        case .work: return L10n.string("place.work")
        case .other: return L10n.string("place.other")
        }
    }

    static func clock(hour: Int, minute: Int) -> String {
        var parts = DateComponents()
        parts.hour = hour
        parts.minute = minute
        let calendar = Calendar(identifier: .gregorian)
        guard let date = calendar.date(from: parts) else { return "\(hour):\(minute)" }
        let formatter = DateFormatter()
        formatter.locale = Locale.current
        formatter.timeStyle = .short
        formatter.dateStyle = .none
        return formatter.string(from: date)
    }

    static func zone(_ identifier: String) -> TimeZone {
        (try? ZoneClock.zone(identifier)) ?? .current
    }

    static func isToday(_ entry: Entry, now: Date = Date()) -> Bool {
        let zone = zone(entry.timeZoneID)
        return ZoneClock.localDate(entry.createdAt, zone: zone) == ZoneClock.localDate(now, zone: zone)
    }

    static func dayID(_ entry: Entry) -> String {
        let date = ZoneClock.localDate(entry.createdAt, zone: zone(entry.timeZoneID))
        return String(format: "%04d-%02d-%02d", date.year, date.month, date.day)
    }

    static func entryDayTitle(_ entry: Entry) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale.current
        formatter.timeStyle = .none
        formatter.dateStyle = .full
        formatter.timeZone = zone(entry.timeZoneID)
        return formatter.string(from: entry.createdAt)
    }

    static func entryClock(_ entry: Entry) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale.current
        formatter.timeStyle = .short
        formatter.dateStyle = .none
        formatter.timeZone = zone(entry.timeZoneID)
        return formatter.string(from: entry.createdAt)
    }

    static func range(start: Date, end: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale.current
        formatter.dateStyle = .medium
        formatter.timeStyle = .none
        let last = end.addingTimeInterval(-1)
        return "\(formatter.string(from: start)) – \(formatter.string(from: last))"
    }

    static func color(hex: String) -> Color {
        var text = hex.trimmingCharacters(in: .whitespacesAndNewlines)
        if text.hasPrefix("#") { text.removeFirst() }
        guard text.count == 6, let value = UInt64(text, radix: 16) else { return .secondary }
        return Color(
            red: Double((value >> 16) & 0xFF) / 255,
            green: Double((value >> 8) & 0xFF) / 255,
            blue: Double(value & 0xFF) / 255
        )
    }

    static func number(_ value: Double) -> String {
        String(format: "%.6f", locale: Locale(identifier: "en_US_POSIX"), Rounding.r6(value))
    }
}
