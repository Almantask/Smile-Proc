import Foundation

public enum EntrySource: String, Codable, Sendable, Equatable, CaseIterable {
    case app
    case lockScreenWidget
    case homeScreenWidget
    case notification
}
