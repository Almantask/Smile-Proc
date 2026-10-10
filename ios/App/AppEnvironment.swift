import CryptoKit
import Foundation
import LocalAuthentication
import SmileCore
import UIKit
import WidgetKit

enum AppPhase: Equatable {
    case loading
    case waitingForData
    case onboarding
    case recovery
    case ready
}

struct LocationOutcome: Equatable {
    var kind: String
    var accuracy: Double
    var age: TimeInterval
}

@MainActor
@Observable
final class AppEnvironment {
    var phase: AppPhase = .loading
    var isLocked = false
    var showPrivacyCover = false
    var entryList: [Entry] = []
    var placeList: [PlaceLabel] = []
    var cardList: [WeeklyCard] = []
    var statusMessage: String?
    var followUpID: String?
    var lastLocation: LocationOutcome?
    var corruptDeleted = 0

    private(set) var config: PatternConfig?
    private(set) var library: [LibraryEntry] = []
    private(set) var tags = EmotionTagCatalog(version: 1, cores: [])
    private(set) var helplines: Helplines?
    private var database: AppDatabase?
    private var key: SymmetricKey?
    private var keyStore: any KeychainKeyStore = KeychainMasterKeyStore()
    private var backgroundedAt: Date?
    private var directory: URL?
    private var didBootstrap = false
    var openLatest = false
    var supportRevision = 0
    let uiTesting: Bool

    init() {
        uiTesting = ProcessInfo.processInfo.arguments.contains("-uiTesting")
    }

    func bootstrap() {
        guard !didBootstrap else { return }
        didBootstrap = true
        loadContent()
        corruptDeleted = AppGroup.defaults().integer(forKey: AppGroup.Key.corruptDeleted)
        if uiTesting {
            openTestingStore()
            phase = .ready
            isLocked = false
            reload()
            return
        }
        guard UIApplication.shared.isProtectedDataAvailable else {
            phase = .waitingForData
            return
        }
        openStore()
    }

    func protectedDataBecameAvailable() {
        guard phase == .waitingForData || database == nil else {
            ingest()
            return
        }
        openStore()
    }

    func sceneBecameInactive() {
        showPrivacyCover = true
        backgroundedAt = Date()
    }

    func sceneBecameActive() {
        showPrivacyCover = false
        if let backgroundedAt, Date().timeIntervalSince(backgroundedAt) >= 60, appLockEnabled, !uiTesting {
            isLocked = true
        }
        self.backgroundedAt = nil
        ingest()
        buildCardIfNeeded()
        Task { await CheckInNotifications.reschedule(reminders: reminders, weekly: weeklySchedule) }
    }

    func handleURL(_ url: URL) {
        guard url.scheme == "smileproc", url.host == "entry" else { return }
        openLatest = true
    }

    func log(mood: Mood) {
        guard let repository = entryRepository else {
            statusMessage = L10n.string("error.writeFailed")
            return
        }
        let now = Date()
        let entry = Entry(
            id: Identifiers.newID(),
            createdAt: now,
            updatedAt: now,
            timeZoneID: TimeZone.current.identifier,
            mood: mood,
            tags: [],
            comment: nil,
            location: nil,
            source: .app
        )
        do {
            try repository.insert(entry)
            followUpID = entry.id
            reload()
            Task { await attachLocation(to: entry.id) }
        } catch {
            statusMessage = L10n.string("error.writeFailed")
        }
    }

    func updateFollowUp(id: String, tags: [String], comment: String) {
        guard var entry = entryList.first(where: { $0.id == id }) ?? (try? entryRepository?.entry(id: id)) else { return }
        entry.tags = EntryValidator.normalizedTags(tags, allowed: self.tags.allowedIDs)
        entry.comment = EntryValidator.normalizedComment(comment)
        entry.updatedAt = Date()
        try? entryRepository?.update(entry)
        reload()
    }

    func updateEntry(_ entry: Entry) {
        try? entryRepository?.update(entry)
        reload()
        buildCardIfNeeded()
    }

    func deleteEntry(id: String) {
        try? entryRepository?.delete(id: id)
        reload()
    }

    func dismissLowRun() {
        let ordered = entryList.sorted { lhs, rhs in
            if lhs.createdAt != rhs.createdAt { return lhs.createdAt < rhs.createdAt }
            return lhs.id < rhs.id
        }
        guard let latest = ordered.last else { return }
        AppGroup.defaults().set(latest.id, forKey: AppGroup.Key.lowRunDismissed)
        supportRevision += 1
    }

    func namePlace(cluster: PlaceCluster, name: String, kind: PlaceKind, merge: Bool) {
        guard let places = placeRepository else { return }
        if merge, let existing = nearestLabel(for: cluster), existing.id != cluster.labelID {
            let combined = PlaceResolver.combinedAnchor(
                anchorLatitude: existing.anchorLatitude,
                anchorLongitude: existing.anchorLongitude,
                centroidLatitude: cluster.centroidLatitude,
                centroidLongitude: cluster.centroidLongitude,
                memberCount: cluster.memberIDs.count
            )
            var moved = existing
            moved.anchorLatitude = combined.latitude
            moved.anchorLongitude = combined.longitude
            try? places.upsert(moved)
        } else {
            let label = PlaceLabel(
                id: Identifiers.newID(),
                name: name.trimmingCharacters(in: .whitespacesAndNewlines),
                kind: kind,
                anchorLatitude: cluster.centroidLatitude,
                anchorLongitude: cluster.centroidLongitude,
                createdAt: Date()
            )
            if !label.name.isEmpty {
                try? places.upsert(label)
            }
        }
        reload()
    }

    func renamePlace(id: String, name: String) {
        guard var place = placeList.first(where: { $0.id == id }) else { return }
        place.name = name.trimmingCharacters(in: .whitespacesAndNewlines)
        try? placeRepository?.upsert(place)
        reload()
    }

    func changeKind(id: String, kind: PlaceKind) {
        guard var place = placeList.first(where: { $0.id == id }) else { return }
        place.kind = kind
        try? placeRepository?.upsert(place)
        reload()
    }

    func deleteLabel(id: String) {
        try? placeRepository?.delete(id: id)
        reload()
    }

    func removeAllLocations() {
        try? entryRepository?.removeAllLocations(now: Date())
        try? placeRepository?.deleteAll()
        reload()
    }

    func exportFile(password: String) async throws -> URL {
        guard password.count >= ExportFormat.minimumPasswordLength else { throw SmileError.validation }
        let document = ExportDocument(
            exportedAt: Date(),
            platform: "ios",
            version: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0",
            entries: entryList,
            places: placeList,
            settings: ExportSettings(reminders: reminders, weeklyCard: weeklySchedule, tagPickerMode: tagPickerMode)
        )
        let salt = ExportFormat.randomSalt()
        let nonce = ExportFormat.randomNonce()
        let data = try await Task.detached(priority: .userInitiated) {
            try ExportFormat.encrypt(
                document: document,
                password: password,
                salt: salt,
                nonce: nonce,
                iterations: ExportFormat.productionIterations
            )
        }.value
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("SmileProc.smileproc")
        try data.write(to: url, options: .atomic)
        return url
    }

    func importFile(url: URL, password: String) async -> Bool {
        let accessing = url.startAccessingSecurityScopedResource()
        defer { if accessing { url.stopAccessingSecurityScopedResource() } }
        do {
            let data = try Data(contentsOf: url)
            let document = try await Task.detached(priority: .userInitiated) {
                try ExportFormat.decrypt(envelopeData: data, password: password)
            }.value
            guard let entries = entryRepository, let places = placeRepository else { return false }
            for entry in document.entries { try entries.upsertImported(entry) }
            for place in document.places { try places.upsert(place) }
            save(reminders: document.settings.reminders)
            save(weekly: document.settings.weeklyCard)
            tagPickerMode = document.settings.tagPickerMode
            onboardingComplete = true
            reload()
            buildCardIfNeeded()
            await CheckInNotifications.reschedule(reminders: reminders, weekly: weeklySchedule)
            if phase == .recovery { phase = .ready }
            return true
        } catch {
            return false
        }
    }

    func startFresh() {
        closeDatabase()
        if let directory {
            AppDatabase.deleteFiles(at: directory)
        }
        try? keyStore.deleteMasterKey()
        clearDefaults()
        CheckInNotifications.removeAll()
        WidgetCenter.shared.reloadAllTimelines()
        openStore()
        phase = .onboarding
        isLocked = false
        openLatest = false
        followUpID = nil
    }

    func deleteAll() {
        closeDatabase()
        if let directory {
            AppDatabase.deleteFiles(at: directory)
        }
        if let inbox = try? AppGroup.inboxURL() {
            try? FileManager.default.removeItem(at: inbox)
        }
        try? keyStore.deleteMasterKey()
        clearDefaults()
        CheckInNotifications.removeAll()
        WidgetCenter.shared.reloadAllTimelines()
        openStore()
        onboardingComplete = false
        phase = .onboarding
        isLocked = false
        entryList = []
        placeList = []
        cardList = []
        followUpID = nil
        openLatest = false
    }

    func unlock() async {
        guard appLockEnabled, !uiTesting else {
            isLocked = false
            return
        }
        let context = LAContext()
        var error: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &error) else {
            isLocked = false
            return
        }
        do {
            let ok = try await context.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: L10n.string("lock.reason"))
            isLocked = !ok
        } catch {
            isLocked = true
        }
    }

    func canUseAppLock() -> Bool {
        LAContext().canEvaluatePolicy(.deviceOwnerAuthentication, error: nil)
    }

    var lowRunArmed: Bool {
        _ = supportRevision
        guard let config else { return false }
        let dismissed = AppGroup.defaults().string(forKey: AppGroup.Key.lowRunDismissed)
        return LowMoodMonitor.lowRunArmed(entries: entryList, config: config, dismissedEntryID: dismissed)
    }

    var assignment: PlaceAssignment {
        guard let config else { return PlaceAssignment(entryClusters: [:], clusters: []) }
        return PlaceResolver(config: config).resolve(entries: entryList, labels: placeList)
    }

    func placeName(for entryID: String) -> String? {
        guard let clusterID = assignment.entryClusters[entryID], let cluster = assignment.cluster(id: clusterID) else {
            return nil
        }
        if let labelID = cluster.labelID, let label = placeList.first(where: { $0.id == labelID }) {
            return label.name
        }
        return L10n.string("history.place.unnamed")
    }

    func libraryPlaceName(clusterID: String?) -> String {
        guard let clusterID, let cluster = assignment.cluster(id: clusterID) else {
            return L10n.string("history.place.gone")
        }
        if let labelID = cluster.labelID, let label = placeList.first(where: { $0.id == labelID }) {
            return label.name
        }
        return "an unnamed place"
    }

    func nearestLabel(for cluster: PlaceCluster) -> PlaceLabel? {
        guard let config else { return nil }
        return PlaceResolver(config: config).nearestLabel(
            latitude: cluster.centroidLatitude,
            longitude: cluster.centroidLongitude,
            labels: placeList,
            excluding: cluster.labelID
        )
    }

    var onboardingComplete: Bool {
        get { AppGroup.defaults().bool(forKey: AppGroup.Key.onboardingComplete) }
        set { AppGroup.defaults().set(newValue, forKey: AppGroup.Key.onboardingComplete) }
    }

    var captureLocation: Bool {
        get { AppGroup.defaults().bool(forKey: AppGroup.Key.captureLocation) }
        set { AppGroup.defaults().set(newValue, forKey: AppGroup.Key.captureLocation) }
    }

    var appLockEnabled: Bool {
        get { AppGroup.defaults().bool(forKey: AppGroup.Key.appLockEnabled) }
        set { AppGroup.defaults().set(newValue, forKey: AppGroup.Key.appLockEnabled) }
    }

    var tagPickerMode: TagPickerMode {
        get { TagPickerMode(rawValue: AppGroup.defaults().string(forKey: AppGroup.Key.tagPickerMode) ?? "") ?? .gradual }
        set { AppGroup.defaults().set(newValue.rawValue, forKey: AppGroup.Key.tagPickerMode) }
    }

    var reminders: [Reminder] {
        guard let data = AppGroup.defaults().data(forKey: AppGroup.Key.reminders),
              let rows = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]]
        else { return [] }
        return rows.compactMap { row in
            guard let id = row["id"] as? String,
                  let hour = CanonicalJson.intValue(row["hour"]),
                  let minute = CanonicalJson.intValue(row["minute"])
            else { return nil }
            let days = (row["weekdays"] as? [Any] ?? []).compactMap { CanonicalJson.intValue($0) }
            return Reminder(id: id, hour: hour, minute: minute, weekdays: days)
        }
    }

    var weeklySchedule: WeeklyCardSchedule {
        let defaults = AppGroup.defaults()
        guard defaults.object(forKey: AppGroup.Key.weeklyHour) != nil else { return .default }
        return WeeklyCardSchedule(
            weekday: defaults.integer(forKey: AppGroup.Key.weeklyWeekday),
            hour: defaults.integer(forKey: AppGroup.Key.weeklyHour),
            minute: defaults.integer(forKey: AppGroup.Key.weeklyMinute)
        )
    }

    func save(reminders: [Reminder]) {
        let rows: [[String: Any]] = reminders.map { reminder in
            ["id": reminder.id, "hour": reminder.hour, "minute": reminder.minute, "weekdays": reminder.weekdays]
        }
        if let data = try? JSONSerialization.data(withJSONObject: rows) {
            AppGroup.defaults().set(data, forKey: AppGroup.Key.reminders)
        }
        Task { await CheckInNotifications.reschedule(reminders: reminders, weekly: weeklySchedule) }
    }

    func save(weekly: WeeklyCardSchedule) {
        let defaults = AppGroup.defaults()
        defaults.set(weekly.weekday, forKey: AppGroup.Key.weeklyWeekday)
        defaults.set(weekly.hour, forKey: AppGroup.Key.weeklyHour)
        defaults.set(weekly.minute, forKey: AppGroup.Key.weeklyMinute)
        Task { await CheckInNotifications.reschedule(reminders: reminders, weekly: weekly) }
    }

    func finishOnboarding(capture: Bool, lock: Bool, reminders: [Reminder]) {
        captureLocation = capture
        appLockEnabled = lock
        onboardingComplete = true
        save(reminders: reminders)
        phase = .ready
        if lock { isLocked = true }
    }

    func buildCardIfNeeded() {
        guard let config, let cards = cardRepository else { return }
        let schedule = weeklySchedule
        let zone = TimeZone.current.identifier
        let now = Date()
        do {
            let existing = try cards.periodEnds()
            let builder = WeeklyCardBuilder(config: config, library: library)
            guard try builder.shouldBuildCard(
                entries: entryList,
                now: now,
                zoneID: zone,
                weekday: schedule.weekday,
                hour: schedule.hour,
                minute: schedule.minute,
                existingPeriodEnds: existing
            ) else { return }
            let card = try builder.build(
                entries: entryList,
                labels: placeList,
                now: now,
                zoneID: zone,
                weekday: schedule.weekday,
                hour: schedule.hour,
                minute: schedule.minute
            )
            try cards.insert(card)
            reload()
        } catch {
            statusMessage = L10n.string("error.generic")
        }
    }

    func reload() {
        entryList = (try? entryRepository?.all()) ?? []
        placeList = (try? placeRepository?.all()) ?? []
        cardList = (try? cardRepository?.all()) ?? []
    }

    private var entryRepository: EntryRepository? {
        guard let database, let key else { return nil }
        return EntryRepository(database: database, key: key)
    }

    private var placeRepository: PlaceRepository? {
        guard let database, let key else { return nil }
        return PlaceRepository(database: database, key: key)
    }

    private var cardRepository: WeeklyCardRepository? {
        guard let database, let key else { return nil }
        return WeeklyCardRepository(database: database, key: key)
    }

    private func attachLocation(to id: String) async {
        let sample = await QuickLocation.capture(timeout: LockedCheckIn.appTimeout, enabled: captureLocation, context: .app)
        if let sample {
            lastLocation = LocationOutcome(
                kind: sample.kind.rawValue,
                accuracy: sample.horizontalAccuracy,
                age: Date().timeIntervalSince(sample.capturedAt)
            )
        }
        guard let sample, var entry = try? entryRepository?.entry(id: id), entry.location == nil else { return }
        entry.location = sample
        entry.updatedAt = Date()
        try? entryRepository?.update(entry)
        reload()
    }

    private func ingest() {
        guard let repository = entryRepository, let inbox = try? AppGroup.inboxURL() else { return }
        if let report = try? InboxIngestor().ingest(directory: inbox, repository: repository, now: Date()) {
            corruptDeleted += report.deletedCorrupt
            AppGroup.defaults().set(corruptDeleted, forKey: AppGroup.Key.corruptDeleted)
            if report.inserted > 0 || report.locationsAttached > 0 {
                reload()
            }
        }
    }

    private func loadContent() {
        func data(_ name: String) -> Data? {
            guard let url = Bundle.main.url(forResource: name, withExtension: "json") else { return nil }
            return try? Data(contentsOf: url)
        }
        if let data = data("pattern-config") { config = try? ContentLoader.loadPatternConfig(data) }
        if let data = data("pattern-library.en") { library = (try? ContentLoader.loadLibrary(data)) ?? [] }
        if let data = data("emotion-tags") { tags = (try? ContentLoader.loadEmotionTags(data)) ?? tags }
        if let data = data("helplines") { helplines = try? ContentLoader.loadHelplines(data) }
    }

    private func openTestingStore() {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent("SmileProcUITest", isDirectory: true)
        try? FileManager.default.removeItem(at: directory)
        self.directory = directory
        keyStore = InMemoryKeyStore()
        openStore()
    }

    private func openStore() {
        do {
            let directory: URL
            if let existing = self.directory {
                directory = existing
            } else {
                let support = try FileManager.default.url(
                    for: .applicationSupportDirectory,
                    in: .userDomainMask,
                    appropriateFor: nil,
                    create: true
                )
                directory = support.appendingPathComponent("SmileProc", isDirectory: true)
                self.directory = directory
            }
            switch try DatabaseOpener.bootstrap(directory: directory, keyStore: keyStore) {
            case let .ready(database, key):
                self.database = database
                self.key = key
                reload()
                phase = onboardingComplete ? .ready : .onboarding
                if appLockEnabled && onboardingComplete && !uiTesting {
                    isLocked = true
                }
                ingest()
                buildCardIfNeeded()
            case .needsRecovery:
                phase = .recovery
            }
        } catch {
            statusMessage = L10n.string("error.generic")
            phase = .onboarding
        }
    }

    private func closeDatabase() {
        database = nil
        key = nil
    }

    #if DEBUG
    func seedDemoData() {
        guard let repository = entryRepository else {
            statusMessage = L10n.string("error.writeFailed")
            return
        }
        var calendar = Calendar(identifier: .gregorian)
        let zone = TimeZone.current
        calendar.timeZone = zone
        let hours: [(Int, Mood)] = [(9, .good), (15, .low), (20, .okay)]
        for offset in stride(from: 27, through: 0, by: -1) {
            guard let day = calendar.date(byAdding: .day, value: -offset, to: Date()) else { continue }
            let parts = calendar.dateComponents([.year, .month, .day], from: day)
            for (hour, mood) in hours {
                var stamp = DateComponents()
                stamp.year = parts.year
                stamp.month = parts.month
                stamp.day = parts.day
                stamp.hour = hour
                stamp.minute = 0
                stamp.second = 0
                stamp.timeZone = zone
                guard let created = calendar.date(from: stamp) else { continue }
                let entry = Entry(
                    id: Identifiers.newID(),
                    createdAt: created,
                    updatedAt: created,
                    timeZoneID: zone.identifier,
                    mood: mood,
                    tags: mood == .low ? ["tired"] : [],
                    comment: nil,
                    location: nil,
                    source: .app
                )
                try? repository.insert(entry)
            }
        }
        reload()
        buildCardIfNeeded()
    }

    func debugDetection() -> Detection? {
        guard let config else { return nil }
        let schedule = weeklySchedule
        let zone = TimeZone.current.identifier
        guard let period = try? CardPeriod.resolve(
            now: Date(),
            zoneID: zone,
            weekday: schedule.weekday,
            hour: schedule.hour,
            minute: schedule.minute
        ) else { return nil }
        return try? PatternEngine(config: config).detect(entries: entryList, labels: placeList, period: period)
    }

    func inboxFileCount() -> Int {
        guard let inbox = try? AppGroup.inboxURL() else { return 0 }
        let files = try? FileManager.default.contentsOfDirectory(at: inbox, includingPropertiesForKeys: nil)
        return files?.count ?? 0
    }
    #endif

    private func clearDefaults() {
        if let domain = Bundle.main.bundleIdentifier {
            UserDefaults.standard.removePersistentDomain(forName: domain)
        }
        if let suite = UserDefaults(suiteName: AppGroup.identifier) {
            suite.removePersistentDomain(forName: AppGroup.identifier)
        }
    }
}
