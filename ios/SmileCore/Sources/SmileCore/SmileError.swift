import Foundation

public enum SmileError: Error, Equatable, Sendable {
    case invalidInstant
    case unknownTimeZone
    case invalidDate
    case missingConfigKey
    case invalidContent
    case cryptoFailure
    case wrongPassword
    case databaseFailure
    case inboxUnreadable
    case validation
}
