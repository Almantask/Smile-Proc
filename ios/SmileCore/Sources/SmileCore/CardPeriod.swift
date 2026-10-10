import Foundation

public struct CardPeriod: Sendable, Equatable {
    public var periodStart: Date
    public var periodEnd: Date
    public var zoneID: String
    public var hour: Int
    public var minute: Int

    public init(periodStart: Date, periodEnd: Date, zoneID: String, hour: Int, minute: Int) {
        self.periodStart = periodStart
        self.periodEnd = periodEnd
        self.zoneID = zoneID
        self.hour = hour
        self.minute = minute
    }

    public static func resolve(now: Date, zoneID: String, weekday: Int, hour: Int, minute: Int) throws -> CardPeriod {
        let zone = try ZoneClock.zone(zoneID)
        let local = ZoneClock.localDate(now, zone: zone)
        let iso = ZoneClock.isoWeekday(now, zone: zone)
        let delta = (iso - weekday + 7) % 7
        var endDate = try ZoneClock.addingDays(-delta, to: local, zone: zone)
        var endLocal = try ZoneClock.make(
            year: endDate.year,
            month: endDate.month,
            day: endDate.day,
            hour: hour,
            minute: minute,
            zone: zone
        )
        if endLocal > now {
            endDate = try ZoneClock.addingDays(-7, to: endDate, zone: zone)
            endLocal = try ZoneClock.make(
                year: endDate.year,
                month: endDate.month,
                day: endDate.day,
                hour: hour,
                minute: minute,
                zone: zone
            )
        }
        let startDate = try ZoneClock.addingDays(-7, to: endDate, zone: zone)
        let startLocal = try ZoneClock.make(
            year: startDate.year,
            month: startDate.month,
            day: startDate.day,
            hour: hour,
            minute: minute,
            zone: zone
        )
        return CardPeriod(periodStart: startLocal, periodEnd: endLocal, zoneID: zoneID, hour: hour, minute: minute)
    }

    public static func shiftLocal(instant: Date, zoneID: String, days: Int, hour: Int, minute: Int) throws -> Date {
        let zone = try ZoneClock.zone(zoneID)
        let date = ZoneClock.localDate(instant, zone: zone)
        let shifted = try ZoneClock.addingDays(-days, to: date, zone: zone)
        return try ZoneClock.make(
            year: shifted.year,
            month: shifted.month,
            day: shifted.day,
            hour: hour,
            minute: minute,
            zone: zone
        )
    }
}
