import Foundation
import SmileCore
import XCTest

final class EngineFixtureTests: XCTestCase {
    private var root: URL!
    private var config: PatternConfig!
    private var library: [LibraryEntry]!

    override func setUpWithError() throws {
        root = try FixtureRoot.url()
        let configData = try Data(contentsOf: root.appendingPathComponent("spec/content/pattern-config.json"))
        config = try ContentLoader.loadPatternConfig(configData)
        let libraryData = try Data(contentsOf: root.appendingPathComponent("spec/content/pattern-library.en.json"))
        library = try ContentLoader.loadLibrary(libraryData)
    }

    func testEveryPatternConfigKeyIsRead() throws {
        let url = root.appendingPathComponent("spec/content/pattern-config.json")
        let data = try Data(contentsOf: url)
        let object = try XCTUnwrap(try JSONSerialization.jsonObject(with: data) as? [String: Any])
        XCTAssertEqual(Set(config.consumedKeys), Set(object.keys))
        XCTAssertEqual(Set(config.consumedKeys), Set(PatternConfig.requiredKeys))
        var missing = object
        missing.removeValue(forKey: "sdFloor")
        let stripped = try JSONSerialization.data(withJSONObject: missing)
        XCTAssertThrowsError(try ContentLoader.loadPatternConfig(stripped))
    }

    func testPatternFixtures() throws {
        let directory = root.appendingPathComponent("spec/fixtures/patterns")
        let names = try fixtureNames(in: directory)
        XCTAssertFalse(names.isEmpty)
        let builder = WeeklyCardBuilder(config: config, library: library)
        for name in names.sorted() {
            let input = try FixtureJSON.object(directory.appendingPathComponent("\(name).input.json"))
            let expect = try FixtureJSON.object(directory.appendingPathComponent("\(name).expect.json"))
            let golden = try FixtureJSON.object(directory.appendingPathComponent("\(name).golden.json"))
            let entries = try FixtureJSON.entries(input["entries"])
            let places = try FixtureJSON.places(input["places"])
            let weekly = try XCTUnwrap(input["weeklyCard"] as? [String: Any])
            let now = try InstantFormat.parse(try XCTUnwrap(input["now"] as? String))
            let zone = try XCTUnwrap(input["timeZoneID"] as? String)
            let card = try builder.build(
                entries: entries,
                labels: places,
                now: now,
                zoneID: zone,
                weekday: try XCTUnwrap(CanonicalJson.intValue(weekly["weekday"])),
                hour: try XCTUnwrap(CanonicalJson.intValue(weekly["hour"])),
                minute: try XCTUnwrap(CanonicalJson.intValue(weekly["minute"]))
            )
            XCTAssertEqual(card.variant.rawValue, expect["variant"] as? String, name)
            XCTAssertEqual(card.isLowWeek, expect["isLowWeek"] as? Bool, name)
            XCTAssertEqual(card.windowKind.rawValue, expect["windowKind"] as? String, name)
            XCTAssertEqual(card.maxItemCount, CanonicalJson.intValue(expect["maxItemCount"]), name)
            XCTAssertEqual(card.items.count, CanonicalJson.intValue(expect["itemCount"]), name)
            let first = card.items.first?.libraryID
            if expect["firstItemLibraryID"] is NSNull || expect["firstItemLibraryID"] == nil {
                XCTAssertNil(first, name)
            } else {
                XCTAssertEqual(first, expect["firstItemLibraryID"] as? String, name)
            }
            XCTAssertEqual(card.items.map(\.libraryID), FixtureJSON.stringArray(expect["itemsIncludeLibraryIDs"]), name)
            let expectedCandidates = try XCTUnwrap(expect["candidatesInclude"] as? [[String: Any]])
            XCTAssertEqual(
                card.candidates.map { ["id": $0.id, "confidence": $0.confidence.rawValue] },
                expectedCandidates.map { ["id": $0["id"] as? String ?? "", "confidence": $0["confidence"] as? String ?? ""] },
                name
            )
            if let dayparts = expect["dayparts"] as? [String: String] {
                for (id, part) in dayparts {
                    XCTAssertEqual(card.dayparts[id], part, name)
                }
            }
            assertGolden(card, golden, name: name)
        }
    }

    func testPlaceFixtures() throws {
        let directory = root.appendingPathComponent("spec/fixtures/places")
        let names = try fixtureNames(in: directory)
        XCTAssertFalse(names.isEmpty)
        let resolver = PlaceResolver(config: config)
        for name in names.sorted() {
            let input = try FixtureJSON.object(directory.appendingPathComponent("\(name).input.json"))
            let expect = try FixtureJSON.object(directory.appendingPathComponent("\(name).expect.json"))
            let golden = try FixtureJSON.object(directory.appendingPathComponent("\(name).golden.json"))
            let assignment = resolver.resolve(
                entries: try FixtureJSON.entries(input["entries"]),
                labels: try FixtureJSON.places(input["places"])
            )
            let groups = try XCTUnwrap(expect["sameCluster"] as? [[Any]])
            for group in groups {
                let ids = group.compactMap { $0 as? String }
                let clusterIDs = Set(ids.compactMap { assignment.entryClusters[$0] })
                XCTAssertEqual(clusterIDs.count, 1, name)
            }
            let different = (expect["differentCluster"] as? [[Any]]) ?? []
            for pair in different {
                let ids = pair.compactMap { $0 as? String }
                XCTAssertEqual(ids.count, 2, name)
                if ids.count == 2 {
                    XCTAssertNotEqual(assignment.entryClusters[ids[0]], assignment.entryClusters[ids[1]], name)
                }
            }
            for id in FixtureJSON.stringArray(expect["unclustered"]) {
                XCTAssertNil(assignment.entryClusters[id], name)
            }
            if let clusterIDs = expect["clusterIDs"] as? [String: String], let expected = clusterIDs["all"] {
                for id in assignment.entryClusters.values {
                    XCTAssertEqual(id, expected, name)
                }
            }
            let goldenClusters = try XCTUnwrap(golden["entryClusters"] as? [String: String])
            XCTAssertEqual(assignment.entryClusters, goldenClusters, name)
            let goldenRows = try XCTUnwrap(golden["clusters"] as? [[String: Any]])
            XCTAssertEqual(assignment.clusters.count, goldenRows.count, name)
            for (cluster, row) in zip(assignment.clusters, goldenRows) {
                XCTAssertEqual(cluster.id, row["id"] as? String, name)
                FixtureJSON.sameNumber(cluster.centroidLatitude, row["centroidLatitude"])
                FixtureJSON.sameNumber(cluster.centroidLongitude, row["centroidLongitude"])
                XCTAssertEqual(cluster.memberIDs, FixtureJSON.stringArray(row["memberIDs"]), name)
                if row["labelID"] is NSNull || row["labelID"] == nil {
                    XCTAssertNil(cluster.labelID, name)
                } else {
                    XCTAssertEqual(cluster.labelID, row["labelID"] as? String, name)
                }
            }
        }
    }

    func testPeriodsAndBuildGate() throws {
        let periods = try FixtureJSON.object(root.appendingPathComponent("spec/fixtures/periods/periods.json"))
        let cases = try XCTUnwrap(periods["cases"] as? [[String: Any]])
        for item in cases {
            let now = try InstantFormat.parse(try XCTUnwrap(item["now"] as? String))
            let zone = try XCTUnwrap(item["timeZoneID"] as? String)
            let weekly = try XCTUnwrap(item["weeklyCard"] as? [String: Any])
            let period = try CardPeriod.resolve(
                now: now,
                zoneID: zone,
                weekday: try XCTUnwrap(CanonicalJson.intValue(weekly["weekday"])),
                hour: try XCTUnwrap(CanonicalJson.intValue(weekly["hour"])),
                minute: try XCTUnwrap(CanonicalJson.intValue(weekly["minute"]))
            )
            XCTAssertEqual(InstantFormat.format(period.periodStart), item["periodStart"] as? String, item["name"] as? String ?? "")
            XCTAssertEqual(InstantFormat.format(period.periodEnd), item["periodEnd"] as? String, item["name"] as? String ?? "")
        }

        let gate = try FixtureJSON.object(root.appendingPathComponent("spec/fixtures/periods/build-gate.json"))
        let gateCases = try XCTUnwrap(gate["cases"] as? [[String: Any]])
        let builder = WeeklyCardBuilder(config: config, library: library)
        for item in gateCases {
            let now = try InstantFormat.parse(try XCTUnwrap(item["now"] as? String))
            let entries = try FixtureJSON.entries(item["entries"])
            let existing = FixtureJSON.stringArray(item["existingPeriodEnds"])
            let should = try builder.shouldBuildCard(
                entries: entries,
                now: now,
                zoneID: "Europe/Rome",
                weekday: 7,
                hour: 19,
                minute: 0,
                existingPeriodEnds: existing
            )
            XCTAssertEqual(should, item["shouldBuild"] as? Bool, item["name"] as? String ?? "")
        }
    }

    func testLowMoodCases() throws {
        let file = try FixtureJSON.object(root.appendingPathComponent("spec/fixtures/lowmood/cases.json"))
        let cases = try XCTUnwrap(file["cases"] as? [[String: Any]])
        for item in cases {
            let entries = try FixtureJSON.entries(item["entries"])
            let dismissed = item["dismissedEntryID"] as? String
            let armed = LowMoodMonitor.lowRunArmed(entries: entries, config: config, dismissedEntryID: dismissed)
            XCTAssertEqual(armed, item["armed"] as? Bool, item["name"] as? String ?? "")
        }
    }

    func testLibraryCoverage() throws {
        let file = try FixtureJSON.object(root.appendingPathComponent("spec/fixtures/library/coverage.json"))
        let cases = try XCTUnwrap(file["cases"] as? [[String: Any]])
        let generated = LibraryMatcher.coverage(library: library)
        XCTAssertEqual(generated.count, cases.count)
        for (row, expected) in zip(generated, cases) {
            XCTAssertEqual(row.detector, expected["detector"] as? String)
            XCTAssertEqual(row.direction, expected["direction"] as? String)
            XCTAssertEqual(row.segment, expected["segment"] as? String)
            XCTAssertEqual(row.weekday, expected["weekday"] as? String)
            XCTAssertEqual(row.placeKind, expected["placeKind"] as? String)
            XCTAssertEqual(row.tag, expected["tag"] as? String)
            XCTAssertEqual(row.libraryID, expected["libraryID"] as? String)
            XCTAssertNotNil(row.libraryID)
        }
    }

    func testNoiseGuard() throws {
        var rng = PythonRandom(seed: 20261010)
        XCTAssertEqual(rng.randint(from: 1, through: 3), 2)
        XCTAssertEqual(rng.randint(from: 0, through: 6), 1)
        XCTAssertEqual(rng.randint(from: 0, through: 23), 21)
        XCTAssertEqual(rng.randint(from: 0, through: 59), 51)
        rng = PythonRandom(seed: 20261010)
        let zone = try ZoneClock.zone("Europe/Rome")
        let now = try ZoneClock.make(year: 2026, month: 10, day: 18, hour: 19, minute: 0, zone: zone)
        let endDate = ZoneClock.localDate(now, zone: zone)
        let builder = WeeklyCardBuilder(config: config, library: library)
        var patternItems = 0
        for week in 0..<100 {
            var entries: [Entry] = []
            for index in 0..<25 {
                let moodValue = rng.randint(from: 1, through: 3)
                let dayOffset = rng.randint(from: 0, through: 6)
                let hour = rng.randint(from: 0, through: 23)
                let minute = rng.randint(from: 0, through: 59)
                let mood = try XCTUnwrap(Mood(raw: moodValue))
                let day = try ZoneClock.addingDays(-dayOffset, to: endDate, zone: zone)
                var created = try ZoneClock.make(year: day.year, month: day.month, day: day.day, hour: hour, minute: minute, zone: zone)
                if created >= now {
                    created = created.addingTimeInterval(-7 * 86400)
                }
                entries.append(
                    Entry(
                        id: String(format: "n%03d-%02d", week, index),
                        createdAt: created,
                        updatedAt: created,
                        timeZoneID: "Europe/Rome",
                        mood: mood,
                        tags: [],
                        comment: nil,
                        location: nil,
                        source: .app
                    )
                )
            }
            let card = try builder.build(entries: entries, labels: [], now: now, zoneID: "Europe/Rome", weekday: 7, hour: 19, minute: 0)
            let confidenceByID = Dictionary(uniqueKeysWithValues: card.candidates.map { ($0.id, $0.confidence) })
            patternItems += card.items.filter { confidenceByID[$0.patternID] == .pattern }.count
        }
        let average = Double(patternItems) / 100
        XCTAssertLessThanOrEqual(average, 0.3)
    }

    func testCanonicalVectors() throws {
        let entryURL = root.appendingPathComponent("spec/fixtures/canonical/entry-1.json")
        let placeURL = root.appendingPathComponent("spec/fixtures/canonical/place-1.json")
        let entry = try CanonicalJson.parseEntry(Data(contentsOf: entryURL))
        let place = try CanonicalJson.parsePlace(Data(contentsOf: placeURL))
        XCTAssertEqual(CanonicalJson.encodeEntry(entry), try document(entryURL))
        XCTAssertEqual(CanonicalJson.encodePlace(place), try document(placeURL))
        XCTAssertNil(entry.comment)
        XCTAssertEqual(entry.mood, .okay)
    }

    private func assertGolden(_ card: WeeklyCard, _ golden: [String: Any], name: String) {
        XCTAssertEqual(InstantFormat.format(card.periodStart), golden["periodStart"] as? String, name)
        XCTAssertEqual(InstantFormat.format(card.periodEnd), golden["periodEnd"] as? String, name)
        XCTAssertEqual(card.windowKind.rawValue, golden["windowKind"] as? String, name)
        XCTAssertEqual(card.entryCount, CanonicalJson.intValue(golden["entryCount"]), name)
        XCTAssertEqual(card.moodCounts, (golden["moodCounts"] as? [Any])?.compactMap { CanonicalJson.intValue($0) }, name)
        let days = (golden["days"] as? [Any])?.map { row in (row as? [Any] ?? []).compactMap { CanonicalJson.intValue($0) } }
        XCTAssertEqual(card.days, days, name)
        XCTAssertEqual(card.isLowWeek, golden["isLowWeek"] as? Bool, name)
        XCTAssertEqual(card.variant.rawValue, golden["variant"] as? String, name)
        XCTAssertEqual(card.maxItemCount, CanonicalJson.intValue(golden["maxItemCount"]), name)
        let items = (golden["items"] as? [[String: Any]]) ?? []
        XCTAssertEqual(card.items.map(\.patternID), items.compactMap { $0["patternID"] as? String }, name)
        XCTAssertEqual(card.items.map(\.libraryID), items.compactMap { $0["libraryID"] as? String }, name)
        let candidates = (golden["candidates"] as? [[String: Any]]) ?? []
        XCTAssertEqual(card.candidates.count, candidates.count, name)
        for (candidate, row) in zip(card.candidates, candidates) {
            XCTAssertEqual(candidate.id, row["id"] as? String, name)
            XCTAssertEqual(candidate.confidence.rawValue, row["confidence"] as? String, name)
            XCTAssertEqual(candidate.nSegment, CanonicalJson.intValue(row["nSegment"]), name)
            XCTAssertEqual(candidate.nRest, CanonicalJson.intValue(row["nRest"]), name)
            FixtureJSON.sameNumber(candidate.meanSegment, row["meanSegment"])
            FixtureJSON.sameNumber(candidate.meanRest, row["meanRest"])
            FixtureJSON.sameNumber(candidate.diff, row["diff"])
            FixtureJSON.sameNumber(candidate.z, row["z"])
            FixtureJSON.sameNumber(candidate.score, row["score"])
        }
    }

    private func fixtureNames(in directory: URL) throws -> [String] {
        try FileManager.default.contentsOfDirectory(atPath: directory.path).compactMap { name in
            guard name.hasSuffix(".input.json") else { return nil }
            return String(name.dropLast(".input.json".count))
        }
    }

    private func document(_ url: URL) throws -> String {
        var text = try String(contentsOf: url, encoding: .utf8)
        if text.hasSuffix("\n") { text.removeLast() }
        return text
    }
}
