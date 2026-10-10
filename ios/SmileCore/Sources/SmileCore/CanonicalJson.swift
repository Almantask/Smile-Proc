import Foundation

public enum JSONValue: Sendable, Equatable {
    case null
    case bool(Bool)
    case int(Int)
    case double(Double)
    case string(String)
    case array([JSONValue])
    case object([(String, JSONValue)])
}

public enum CanonicalJson {
    public static func encode(_ value: JSONValue) -> String {
        switch value {
        case .null:
            return "null"
        case .bool(let flag):
            return flag ? "true" : "false"
        case .int(let number):
            return String(number)
        case .double(let number):
            return formatDouble(number)
        case .string(let text):
            return escape(text)
        case .array(let items):
            return "[" + items.map(encode).joined(separator: ",") + "]"
        case .object(let fields):
            return "{" + fields.map { escape($0.0) + ":" + encode($0.1) }.joined(separator: ",") + "}"
        }
    }

    public static func encodeData(_ value: JSONValue) -> Data {
        Data(encode(value).utf8)
    }

    public static func formatDouble(_ value: Double) -> String {
        if value.isNaN || value.isInfinite { return "null" }
        if value == 0 {
            return value.sign == .minus ? "-0.0" : "0.0"
        }
        let text = String(value)
        if text == "inf" || text == "+inf" || text == "-inf" || text == "nan" { return "null" }
        if text.contains(".") || text.contains("e") || text.contains("E") {
            return normalizeExponent(text)
        }
        return text + ".0"
    }

    public static func encodeEntry(_ entry: Entry) -> String {
        encode(entryValue(entry))
    }

    public static func encodePlace(_ place: PlaceLabel) -> String {
        encode(placeValue(place))
    }

    public static func entryValue(_ entry: Entry) -> JSONValue {
        .object([
            ("id", .string(entry.id)),
            ("createdAt", .string(InstantFormat.format(entry.createdAt))),
            ("updatedAt", .string(InstantFormat.format(entry.updatedAt))),
            ("timeZoneID", .string(entry.timeZoneID)),
            ("mood", .int(entry.mood.rawValue)),
            ("tags", .array(entry.tags.map { .string($0) })),
            ("comment", entry.comment.map { JSONValue.string($0) } ?? .null),
            ("location", entry.location.map(locationValue) ?? .null),
            ("source", .string(entry.source.rawValue)),
        ])
    }

    public static func locationValue(_ location: LocationSample) -> JSONValue {
        .object([
            ("latitude", .double(location.latitude)),
            ("longitude", .double(location.longitude)),
            ("horizontalAccuracy", .double(location.horizontalAccuracy)),
            ("capturedAt", .string(InstantFormat.format(location.capturedAt))),
            ("kind", .string(location.kind.rawValue)),
        ])
    }

    public static func placeValue(_ place: PlaceLabel) -> JSONValue {
        .object([
            ("id", .string(place.id)),
            ("name", .string(place.name)),
            ("kind", .string(place.kind.rawValue)),
            ("anchorLatitude", .double(place.anchorLatitude)),
            ("anchorLongitude", .double(place.anchorLongitude)),
            ("createdAt", .string(InstantFormat.format(place.createdAt))),
        ])
    }

    public static func parseEntry(_ data: Data) throws -> Entry {
        let object = try jsonObject(data)
        return try parseEntryObject(object)
    }

    public static func parsePlace(_ data: Data) throws -> PlaceLabel {
        try parsePlaceObject(try jsonObject(data))
    }

    public static func parseEntryObject(_ object: [String: Any]) throws -> Entry {
        guard let id = object["id"] as? String,
              let createdText = object["createdAt"] as? String,
              let updatedText = object["updatedAt"] as? String,
              let zone = object["timeZoneID"] as? String,
              let moodValue = intValue(object["mood"]),
              let mood = Mood(raw: moodValue),
              let sourceText = object["source"] as? String,
              let source = EntrySource(rawValue: sourceText)
        else { throw SmileError.invalidContent }
        let createdAt = try InstantFormat.parse(createdText)
        let updatedAt = try InstantFormat.parse(updatedText)
        let tags = (object["tags"] as? [Any] ?? []).compactMap { $0 as? String }
        let comment = EntryValidator.normalizedComment(object["comment"] as? String)
        let location = try parseLocation(object["location"])
        return Entry(
            id: id,
            createdAt: createdAt,
            updatedAt: updatedAt,
            timeZoneID: zone,
            mood: mood,
            tags: tags,
            comment: comment,
            location: location,
            source: source
        )
    }

    public static func parsePlaceObject(_ object: [String: Any]) throws -> PlaceLabel {
        guard let id = object["id"] as? String,
              let name = object["name"] as? String,
              let kindText = object["kind"] as? String,
              let kind = PlaceKind(rawValue: kindText),
              let lat = doubleValue(object["anchorLatitude"]),
              let lon = doubleValue(object["anchorLongitude"]),
              let createdText = object["createdAt"] as? String
        else { throw SmileError.invalidContent }
        return PlaceLabel(
            id: id,
            name: name,
            kind: kind,
            anchorLatitude: lat,
            anchorLongitude: lon,
            createdAt: try InstantFormat.parse(createdText)
        )
    }

    public static func parseLocation(_ value: Any?) throws -> LocationSample? {
        if value == nil || value is NSNull { return nil }
        guard let object = value as? [String: Any] else { throw SmileError.invalidContent }
        guard let lat = doubleValue(object["latitude"]),
              let lon = doubleValue(object["longitude"]),
              let accuracy = doubleValue(object["horizontalAccuracy"]),
              let capturedText = object["capturedAt"] as? String,
              let kindText = object["kind"] as? String,
              let kind = LocationKind(rawValue: kindText)
        else { throw SmileError.invalidContent }
        return LocationSample(
            latitude: lat,
            longitude: lon,
            horizontalAccuracy: accuracy,
            capturedAt: try InstantFormat.parse(capturedText),
            kind: kind
        )
    }

    public static func jsonObject(_ data: Data) throws -> [String: Any] {
        let object = try JSONSerialization.jsonObject(with: data)
        guard let dictionary = object as? [String: Any] else { throw SmileError.invalidContent }
        return dictionary
    }

    public static func intValue(_ value: Any?) -> Int? {
        switch value {
        case let number as Int: return number
        case let number as NSNumber: return number.intValue
        default: return nil
        }
    }

    public static func doubleValue(_ value: Any?) -> Double? {
        switch value {
        case let number as Double: return number
        case let number as Int: return Double(number)
        case let number as NSNumber: return number.doubleValue
        default: return nil
        }
    }

    private static func normalizeExponent(_ text: String) -> String {
        guard let marker = text.firstIndex(where: { $0 == "e" || $0 == "E" }) else { return text }
        let head = text[..<marker]
        var exponent = text[text.index(after: marker)...]
        var sign = "+"
        if exponent.hasPrefix("+") {
            exponent = exponent.dropFirst()
        } else if exponent.hasPrefix("-") {
            sign = "-"
            exponent = exponent.dropFirst()
        }
        while exponent.count > 2 && exponent.hasPrefix("0") {
            exponent = exponent.dropFirst()
        }
        if exponent.count < 2 {
            exponent = "0" + exponent
        }
        return head + "e" + sign + exponent
    }

    private static func escape(_ string: String) -> String {
        var out = "\""
        for scalar in string.unicodeScalars {
            switch scalar.value {
            case 0x22: out += "\\\""
            case 0x5C: out += "\\\\"
            case 0x08: out += "\\b"
            case 0x0C: out += "\\f"
            case 0x0A: out += "\\n"
            case 0x0D: out += "\\r"
            case 0x09: out += "\\t"
            case 0x00...0x1F:
                out += String(format: "\\u%04x", scalar.value)
            default:
                out.unicodeScalars.append(scalar)
            }
        }
        out += "\""
        return out
    }
}
