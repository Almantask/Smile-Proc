import Foundation

public enum Mood: Int, Codable, CaseIterable, Sendable, Equatable, Hashable {
    case low = 1
    case okay = 2
    case good = 3

    public init?(raw: Int) {
        self.init(rawValue: raw)
    }
}
