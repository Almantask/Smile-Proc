import Foundation
import SmileCore
import XCTest

enum FixtureError: Error {
    case notFound
}

enum FixtureRoot {
    static func url(filePath: String = #filePath) throws -> URL {
        var url = URL(fileURLWithPath: filePath)
        let manager = FileManager.default
        for _ in 0..<12 {
            let fixtures = url.appendingPathComponent("spec/fixtures", isDirectory: true)
            var isDirectory: ObjCBool = false
            if manager.fileExists(atPath: fixtures.path, isDirectory: &isDirectory), isDirectory.boolValue {
                return url
            }
            url.deleteLastPathComponent()
        }
        throw FixtureError.notFound
    }
}

enum FixtureJSON {
    static func object(_ url: URL) throws -> [String: Any] {
        let data = try Data(contentsOf: url)
        let parsed = try JSONSerialization.jsonObject(with: data)
        guard let object = parsed as? [String: Any] else {
            throw FixtureError.notFound
        }
        return object
    }

    static func entries(_ value: Any?) throws -> [Entry] {
        guard let rows = value as? [[String: Any]] else { return [] }
        return try rows.map { try CanonicalJson.parseEntryObject($0) }
    }

    static func places(_ value: Any?) throws -> [PlaceLabel] {
        guard let rows = value as? [[String: Any]] else { return [] }
        return try rows.map { try CanonicalJson.parsePlaceObject($0) }
    }

    static func stringArray(_ value: Any?) -> [String] {
        (value as? [Any])?.compactMap { $0 as? String } ?? []
    }

    static func sameNumber(_ actual: Double?, _ expected: Any?, file: StaticString = #filePath, line: UInt = #line) {
        if expected == nil || expected is NSNull {
            XCTAssertNil(actual, file: file, line: line)
            return
        }
        guard let actual, let expectedNumber = CanonicalJson.doubleValue(expected) else {
            XCTFail("missing number", file: file, line: line)
            return
        }
        let locale = Locale(identifier: "en_US_POSIX")
        let left = String(format: "%.6f", locale: locale, Rounding.r6(actual))
        let right = String(format: "%.6f", locale: locale, Rounding.r6(expectedNumber))
        XCTAssertEqual(left, right, file: file, line: line)
    }
}
