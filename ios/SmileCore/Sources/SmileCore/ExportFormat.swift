import CCommonCrypto
import CryptoKit
import Foundation
import Security

public struct Reminder: Identifiable, Sendable, Equatable {
    public var id: String
    public var hour: Int
    public var minute: Int
    public var weekdays: [Int]

    public init(id: String, hour: Int, minute: Int, weekdays: [Int]) {
        self.id = id
        self.hour = hour
        self.minute = minute
        self.weekdays = weekdays
    }
}

public struct WeeklyCardSchedule: Sendable, Equatable {
    public var weekday: Int
    public var hour: Int
    public var minute: Int

    public init(weekday: Int, hour: Int, minute: Int) {
        self.weekday = weekday
        self.hour = hour
        self.minute = minute
    }

    public static let `default` = WeeklyCardSchedule(weekday: 7, hour: 19, minute: 0)
}

public enum TagPickerMode: String, Sendable, Equatable, Hashable {
    case gradual
    case detailed
}

public struct ExportSettings: Sendable, Equatable {
    public var reminders: [Reminder]
    public var weeklyCard: WeeklyCardSchedule
    public var tagPickerMode: TagPickerMode

    public init(reminders: [Reminder], weeklyCard: WeeklyCardSchedule, tagPickerMode: TagPickerMode) {
        self.reminders = reminders
        self.weeklyCard = weeklyCard
        self.tagPickerMode = tagPickerMode
    }
}

public struct ExportDocument: Sendable, Equatable {
    public var exportedAt: Date
    public var platform: String
    public var version: String
    public var entries: [Entry]
    public var places: [PlaceLabel]
    public var settings: ExportSettings

    public init(
        exportedAt: Date,
        platform: String,
        version: String,
        entries: [Entry],
        places: [PlaceLabel],
        settings: ExportSettings
    ) {
        self.exportedAt = exportedAt
        self.platform = platform
        self.version = version
        self.entries = entries
        self.places = places
        self.settings = settings
    }
}

public struct ExportEnvelope: Sendable, Equatable {
    public var iterations: Int
    public var salt: Data
    public var payload: Data

    public init(iterations: Int, salt: Data, payload: Data) {
        self.iterations = iterations
        self.salt = salt
        self.payload = payload
    }
}

public enum ExportFormat {
    public static let productionIterations = 600_000
    public static let minimumPasswordLength = 8

    public static func canonicalPlaintext(_ document: ExportDocument) -> Data {
        let value = JSONValue.object([
            ("exportedAt", .string(InstantFormat.format(document.exportedAt))),
            ("app", .object([
                ("platform", .string(document.platform)),
                ("version", .string(document.version)),
            ])),
            ("entries", .array(document.entries.map { CanonicalJson.entryValue($0) })),
            ("places", .array(document.places.map { CanonicalJson.placeValue($0) })),
            ("settings", settingsValue(document.settings)),
        ])
        return CanonicalJson.encodeData(value)
    }

    public static func parsePlaintext(_ data: Data) throws -> ExportDocument {
        let object = try CanonicalJson.jsonObject(data)
        guard let exportedText = object["exportedAt"] as? String,
              let app = object["app"] as? [String: Any],
              let platform = app["platform"] as? String,
              let version = app["version"] as? String,
              let entryRows = object["entries"] as? [[String: Any]],
              let placeRows = object["places"] as? [[String: Any]],
              let settings = object["settings"] as? [String: Any]
        else { throw SmileError.invalidContent }
        return ExportDocument(
            exportedAt: try InstantFormat.parse(exportedText),
            platform: platform,
            version: version,
            entries: try entryRows.map { try CanonicalJson.parseEntryObject($0) },
            places: try placeRows.map { try CanonicalJson.parsePlaceObject($0) },
            settings: try parseSettings(settings)
        )
    }

    public static func deriveKey(password: String, salt: Data, iterations: Int) throws -> SymmetricKey {
        let passwordData = Data(password.utf8)
        var derived = Data(count: 32)
        let status: Int32 = derived.withUnsafeMutableBytes { derivedBuf in
            salt.withUnsafeBytes { saltBuf in
                passwordData.withUnsafeBytes { passwordBuf in
                    guard let derivedPtr = derivedBuf.bindMemory(to: UInt8.self).baseAddress,
                          let saltPtr = saltBuf.bindMemory(to: UInt8.self).baseAddress,
                          let passwordPtr = passwordBuf.bindMemory(to: Int8.self).baseAddress
                    else { return kCCParamError }
                    return CCKeyDerivationPBKDF(
                        kCCPBKDF2,
                        passwordPtr,
                        passwordData.count,
                        saltPtr,
                        salt.count,
                        kCCPRFHmacAlgSHA256,
                        UInt32(iterations),
                        derivedPtr,
                        derived.count
                    )
                }
            }
        }
        guard status == kCCSuccess else { throw SmileError.cryptoFailure }
        return SymmetricKey(data: derived)
    }

    public static func seal(plaintext: Data, key: SymmetricKey, nonce: Data) throws -> Data {
        let gcmNonce = try AES.GCM.Nonce(data: nonce)
        let sealed = try AES.GCM.seal(plaintext, using: key, nonce: gcmNonce)
        guard let combined = sealed.combined else { throw SmileError.cryptoFailure }
        return combined
    }

    public static func open(payload: Data, key: SymmetricKey) throws -> Data {
        do {
            let box = try AES.GCM.SealedBox(combined: payload)
            return try AES.GCM.open(box, using: key)
        } catch {
            throw SmileError.wrongPassword
        }
    }

    public static func envelope(iterations: Int, salt: Data, payload: Data) -> Data {
        let value = JSONValue.object([
            ("format", .string("smileproc-export")),
            ("version", .int(1)),
            ("kdf", .object([
                ("name", .string("PBKDF2-HMAC-SHA256")),
                ("iterations", .int(iterations)),
                ("salt", .string(salt.base64EncodedString())),
            ])),
            ("cipher", .string("AES-256-GCM")),
            ("payload", .string(payload.base64EncodedString())),
        ])
        return CanonicalJson.encodeData(value)
    }

    public static func parseEnvelope(_ data: Data) throws -> ExportEnvelope {
        let object = try CanonicalJson.jsonObject(data)
        guard object["format"] as? String == "smileproc-export",
              CanonicalJson.intValue(object["version"]) == 1,
              object["cipher"] as? String == "AES-256-GCM",
              let kdf = object["kdf"] as? [String: Any],
              kdf["name"] as? String == "PBKDF2-HMAC-SHA256",
              let iterations = CanonicalJson.intValue(kdf["iterations"]),
              let saltText = kdf["salt"] as? String,
              let salt = Data(base64Encoded: saltText),
              let payloadText = object["payload"] as? String,
              let payload = Data(base64Encoded: payloadText)
        else { throw SmileError.invalidContent }
        return ExportEnvelope(iterations: iterations, salt: salt, payload: payload)
    }

    public static func encrypt(
        document: ExportDocument,
        password: String,
        salt: Data,
        nonce: Data,
        iterations: Int
    ) throws -> Data {
        let key = try deriveKey(password: password, salt: salt, iterations: iterations)
        let plaintext = canonicalPlaintext(document)
        let payload = try seal(plaintext: plaintext, key: key, nonce: nonce)
        return envelope(iterations: iterations, salt: salt, payload: payload)
    }

    public static func decrypt(envelopeData: Data, password: String) throws -> ExportDocument {
        let envelope = try parseEnvelope(envelopeData)
        let key = try deriveKey(password: password, salt: envelope.salt, iterations: envelope.iterations)
        let plaintext = try open(payload: envelope.payload, key: key)
        return try parsePlaintext(plaintext)
    }

    public static func randomSalt() -> Data {
        var bytes = [UInt8](repeating: 0, count: 16)
        let status = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        if status == errSecSuccess {
            return Data(bytes)
        }
        return Data((0..<16).map { _ in UInt8.random(in: 0...255) })
    }

    public static func randomNonce() -> Data {
        var bytes = [UInt8](repeating: 0, count: 12)
        let status = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        if status == errSecSuccess {
            return Data(bytes)
        }
        return Data((0..<12).map { _ in UInt8.random(in: 0...255) })
    }

    private static func settingsValue(_ settings: ExportSettings) -> JSONValue {
        .object([
            ("reminders", .array(settings.reminders.map { reminder in
                .object([
                    ("id", .string(reminder.id)),
                    ("hour", .int(reminder.hour)),
                    ("minute", .int(reminder.minute)),
                    ("weekdays", .array(reminder.weekdays.map { .int($0) })),
                ])
            })),
            ("weeklyCard", .object([
                ("weekday", .int(settings.weeklyCard.weekday)),
                ("hour", .int(settings.weeklyCard.hour)),
                ("minute", .int(settings.weeklyCard.minute)),
            ])),
            ("tagPickerMode", .string(settings.tagPickerMode.rawValue)),
        ])
    }

    private static func parseSettings(_ object: [String: Any]) throws -> ExportSettings {
        guard let reminderRows = object["reminders"] as? [[String: Any]],
              let weekly = object["weeklyCard"] as? [String: Any],
              let weekday = CanonicalJson.intValue(weekly["weekday"]),
              let hour = CanonicalJson.intValue(weekly["hour"]),
              let minute = CanonicalJson.intValue(weekly["minute"]),
              let modeText = object["tagPickerMode"] as? String,
              let mode = TagPickerMode(rawValue: modeText)
        else { throw SmileError.invalidContent }
        let reminders = try reminderRows.map { row -> Reminder in
            guard let id = row["id"] as? String,
                  let hour = CanonicalJson.intValue(row["hour"]),
                  let minute = CanonicalJson.intValue(row["minute"]),
                  let days = row["weekdays"] as? [Any]
            else { throw SmileError.invalidContent }
            let weekdays = days.compactMap { CanonicalJson.intValue($0) }
            return Reminder(id: id, hour: hour, minute: minute, weekdays: weekdays)
        }
        return ExportSettings(
            reminders: reminders,
            weeklyCard: WeeklyCardSchedule(weekday: weekday, hour: hour, minute: minute),
            tagPickerMode: mode
        )
    }
}
