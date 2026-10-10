import Foundation

public enum Rounding {
    /// Format the IEEE-754 double with 12 digits after the decimal, then round half away from zero.
    public static func roundHalfAway(_ value: Double, places: Int) -> Double {
        let locale = Locale(identifier: "en_US_POSIX")
        let formatted = String(format: "%.12f", locale: locale, value)
        guard var decimal = Decimal(string: formatted, locale: locale) else {
            return value
        }
        var rounded = Decimal()
        NSDecimalRound(&rounded, &decimal, places, .plain)
        return NSDecimalNumber(decimal: rounded).doubleValue
    }

    public static func r6(_ value: Double) -> Double {
        roundHalfAway(value, places: 6)
    }

    public static func r9(_ value: Double) -> Double {
        roundHalfAway(value, places: 9)
    }
}
