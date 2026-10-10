import CryptoKit
import Foundation
import SmileCore
import XCTest

final class CryptoStorageTests: XCTestCase {
    private var root: URL!

    override func setUpWithError() throws {
        root = try FixtureRoot.url()
    }

    func testSealVectorOpenTamperAndWrongID() throws {
        let vector = try FixtureJSON.object(root.appendingPathComponent("spec/fixtures/export/seal-vector.json"))
        let keyData = try XCTUnwrap(Data(base64Encoded: try XCTUnwrap(vector["keyBase64"] as? String)))
        let nonce = try XCTUnwrap(Data(base64Encoded: try XCTUnwrap(vector["nonceBase64"] as? String)))
        let plaintext = try XCTUnwrap(Data(base64Encoded: try XCTUnwrap(vector["plaintextBase64"] as? String)))
        let expected = try XCTUnwrap(vector["sealedBase64"] as? String)
        let id = try XCTUnwrap(vector["aadUtf8"] as? String)
        let sealer = Sealer(key: SymmetricKey(data: keyData))
        let sealed = try sealer.seal(plaintext, id: id, nonce: nonce)
        XCTAssertEqual(sealed.base64EncodedString(), expected)
        XCTAssertEqual(try sealer.open(sealed, id: id), plaintext)
        var tampered = sealed
        tampered[20] ^= 0x01
        XCTAssertThrowsError(try sealer.open(tampered, id: id))
        XCTAssertThrowsError(try sealer.open(sealed, id: id + "x"))
    }

    func testExportVectorRoundTripAndEnvelope() throws {
        let vector = try FixtureJSON.object(root.appendingPathComponent("spec/fixtures/export/vector-1.json"))
        let password = try XCTUnwrap(vector["password"] as? String)
        let iterations = try XCTUnwrap(CanonicalJson.intValue(vector["iterations"]))
        let salt = try XCTUnwrap(Data(base64Encoded: try XCTUnwrap(vector["saltBase64"] as? String)))
        let nonce = try XCTUnwrap(Data(base64Encoded: try XCTUnwrap(vector["nonceBase64"] as? String)))
        let plaintext = try XCTUnwrap(Data(base64Encoded: try XCTUnwrap(vector["plaintextBase64"] as? String)))
        let envelope = try XCTUnwrap(vector["envelope"] as? [String: Any])
        let payloadText = try XCTUnwrap(envelope["payload"] as? String)
        let document = try ExportFormat.parsePlaintext(plaintext)
        XCTAssertEqual(ExportFormat.canonicalPlaintext(document), plaintext)

        let key = try ExportFormat.deriveKey(password: password, salt: salt, iterations: iterations)
        let payload = try ExportFormat.seal(plaintext: plaintext, key: key, nonce: nonce)
        XCTAssertEqual(payload.base64EncodedString(), payloadText)
        XCTAssertEqual(try ExportFormat.open(payload: payload, key: key), plaintext)

        let encoded = ExportFormat.envelope(iterations: iterations, salt: salt, payload: payload)
        let saltText = try XCTUnwrap(vector["saltBase64"] as? String)
        let expected = "{\"format\":\"smileproc-export\",\"version\":1,\"kdf\":{\"name\":\"PBKDF2-HMAC-SHA256\",\"iterations\":1000,\"salt\":\"\(saltText)\"},\"cipher\":\"AES-256-GCM\",\"payload\":\"\(payloadText)\"}"
        XCTAssertEqual(String(data: encoded, encoding: .utf8), expected)
        XCTAssertThrowsError(try ExportFormat.open(payload: payload, key: try ExportFormat.deriveKey(password: "not-the-password", salt: salt, iterations: iterations)))
    }

    func testImportMerge() throws {
        let vector = try FixtureJSON.object(root.appendingPathComponent("spec/fixtures/export/vector-1.json"))
        let plaintext = try XCTUnwrap(Data(base64Encoded: try XCTUnwrap(vector["plaintextBase64"] as? String)))
        let document = try ExportFormat.parsePlaintext(plaintext)
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        let store = InMemoryKeyStore()
        let opened = try DatabaseOpener.bootstrap(directory: directory, keyStore: store)
        guard case let .ready(database, key) = opened else {
            XCTFail("expected a new database")
            return
        }
        let entries = EntryRepository(database: database, key: key)
        let places = PlaceRepository(database: database, key: key)
        for entry in document.entries { try entries.upsertImported(entry) }
        for place in document.places { try places.upsert(place) }
        XCTAssertEqual(try entries.all().count, 1)
        for entry in document.entries { try entries.upsertImported(entry) }
        XCTAssertEqual(try entries.all().count, 1)

        var older = try XCTUnwrap(document.entries.first)
        older.comment = "older"
        older.updatedAt = older.updatedAt.addingTimeInterval(-60)
        try entries.upsertImported(older)
        XCTAssertNil(try entries.entry(id: older.id)?.comment)

        var newer = older
        newer.comment = "newer"
        newer.updatedAt = document.entries[0].updatedAt.addingTimeInterval(60)
        try entries.upsertImported(newer)
        XCTAssertEqual(try entries.entry(id: newer.id)?.comment, "newer")
        try? FileManager.default.removeItem(at: directory)
    }

    func testDatabaseCRUDAndMissingKeyRecovery() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        let store = InMemoryKeyStore()
        guard case let .ready(database, key) = try DatabaseOpener.bootstrap(directory: directory, keyStore: store) else {
            XCTFail("expected ready")
            return
        }
        let entries = EntryRepository(database: database, key: key)
        let created = try InstantFormat.parse("2026-10-12T13:05:00.000Z")
        var entry = Entry(
            id: "00000000-0000-4000-8000-000000009001",
            createdAt: created,
            updatedAt: created,
            timeZoneID: "Europe/Rome",
            mood: .good,
            tags: ["calm"],
            comment: "hello",
            location: nil,
            source: .app
        )
        try entries.insert(entry)
        XCTAssertEqual(try entries.entry(id: entry.id)?.mood, .good)
        entry.mood = .low
        entry.updatedAt = created.addingTimeInterval(5)
        try entries.update(entry)
        XCTAssertEqual(try entries.all().count, 1)
        XCTAssertEqual(try entries.entry(id: entry.id)?.mood, .low)
        try entries.delete(id: entry.id)
        XCTAssertNil(try entries.entry(id: entry.id))

        try store.deleteMasterKey()
        let again = try DatabaseOpener.bootstrap(directory: directory, keyStore: store)
        guard case .needsRecovery = again else {
            XCTFail("missing key must not wipe the database")
            return
        }
        try? FileManager.default.removeItem(at: directory)
    }

    func testInboxIngestIsIdempotent() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        let inboxURL = directory.appendingPathComponent("Inbox", isDirectory: true)
        let dbURL = directory.appendingPathComponent("db", isDirectory: true)
        let store = InMemoryKeyStore()
        guard case let .ready(database, key) = try DatabaseOpener.bootstrap(directory: dbURL, keyStore: store) else {
            return XCTFail("expected ready")
        }
        let repository = EntryRepository(database: database, key: key)
        let writer = try InboxWriter(directory: inboxURL)
        let ingestor = InboxIngestor()
        let created = try InstantFormat.parse("2026-10-12T13:05:00.000Z")
        let captured = try InstantFormat.parse("2026-10-12T13:05:03.000Z")
        let id = "00000000-0000-4000-8000-000000001900"
        let location = LocationSample(latitude: 45.4642, longitude: 9.19, horizontalAccuracy: 12, capturedAt: captured, kind: .fresh)
        try writer.writeLocation(InboxLocationFile(id: id, location: location))
        var report = try ingestor.ingest(directory: inboxURL, repository: repository, now: created)
        XCTAssertEqual(report.inserted, 0)
        XCTAssertTrue(FileManager.default.fileExists(atPath: inboxURL.appendingPathComponent("\(id).loc.json").path))

        try writer.writeEntry(InboxEntryFile(id: id, createdAt: created, timeZoneID: "Europe/Rome", mood: .okay, source: .lockScreenWidget))
        report = try ingestor.ingest(directory: inboxURL, repository: repository, now: created)
        XCTAssertEqual(report.inserted, 1)
        XCTAssertEqual(try repository.all().count, 1)
        XCTAssertEqual(try repository.entry(id: id)?.location?.kind, .fresh)
        XCTAssertEqual(try FileManager.default.contentsOfDirectory(atPath: inboxURL.path).count, 0)

        try writer.writeEntry(InboxEntryFile(id: id, createdAt: created, timeZoneID: "Europe/Rome", mood: .okay, source: .lockScreenWidget))
        report = try ingestor.ingest(directory: inboxURL, repository: repository, now: created)
        XCTAssertEqual(try repository.all().count, 1)
        XCTAssertEqual(report.inserted, 0)

        let fixtures = root.appendingPathComponent("spec/fixtures/inbox")
        let partial = inboxURL.appendingPathComponent("partial.entry.json")
        try FileManager.default.copyItem(at: fixtures.appendingPathComponent("partial.entry.json"), to: partial)
        let corrupt = inboxURL.appendingPathComponent("old.entry.json")
        try Data(contentsOf: fixtures.appendingPathComponent("corrupt.entry.json")).write(to: corrupt)
        try FileManager.default.setAttributes(
            [.modificationDate: Date(timeIntervalSinceNow: -48 * 60 * 60)],
            ofItemAtPath: corrupt.path
        )
        report = try ingestor.ingest(directory: inboxURL, repository: repository, now: Date())
        XCTAssertEqual(report.deletedCorrupt, 1)
        XCTAssertGreaterThanOrEqual(report.skipped, 1)
        XCTAssertTrue(FileManager.default.fileExists(atPath: partial.path))
        XCTAssertFalse(FileManager.default.fileExists(atPath: corrupt.path))
        let valid = try InboxCodec.parseEntry(Data(contentsOf: fixtures.appendingPathComponent("valid.entry.json")))
        XCTAssertEqual(valid.mood, .okay)
        XCTAssertThrowsError(try InboxCodec.parseEntry(Data(contentsOf: fixtures.appendingPathComponent("partial.entry.json"))))
        try? FileManager.default.removeItem(at: directory)
    }

    func testInMemoryKeyStore() throws {
        let store = InMemoryKeyStore()
        XCTAssertNil(try store.loadMasterKey())
        let key = SymmetricKey(size: .bits256)
        try store.saveMasterKey(key)
        let loaded = try XCTUnwrap(try store.loadMasterKey())
        XCTAssertEqual(loaded.withUnsafeBytes { Data($0) }, key.withUnsafeBytes { Data($0) })
        try store.deleteMasterKey()
        XCTAssertNil(try store.loadMasterKey())
    }
}
