import CryptoKit
import Foundation
@preconcurrency import GRDB

public struct AppDatabase: Sendable {
    public let queue: DatabaseQueue
    public let directory: URL

    public init(queue: DatabaseQueue, directory: URL) {
        self.queue = queue
        self.directory = directory
    }

    public static func open(at directory: URL) throws -> AppDatabase {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        FileProtection.excludeFromBackup(directory)
        FileProtection.applyCompleteProtection(at: directory)
        let databaseURL = directory.appendingPathComponent("smileproc.sqlite")
        let queue = try DatabaseQueue(path: databaseURL.path)
        var migrator = DatabaseMigrator()
        migrator.registerMigration("v1") { db in
            try db.execute(sql: """
            CREATE TABLE entry (
                id TEXT PRIMARY KEY NOT NULL,
                createdAt DOUBLE NOT NULL,
                updatedAt DOUBLE NOT NULL,
                payload BLOB NOT NULL
            );
            CREATE INDEX entry_createdAt ON entry(createdAt);
            CREATE TABLE place (
                id TEXT PRIMARY KEY NOT NULL,
                payload BLOB NOT NULL
            );
            CREATE TABLE weekly_card (
                periodStart DOUBLE PRIMARY KEY NOT NULL,
                payload BLOB NOT NULL
            );
            """)
        }
        try migrator.migrate(queue)
        FileProtection.excludeFromBackup(databaseURL)
        FileProtection.applyCompleteProtection(at: databaseURL)
        return AppDatabase(queue: queue, directory: directory)
    }

    public static func databaseExists(at directory: URL) -> Bool {
        let url = directory.appendingPathComponent("smileproc.sqlite")
        return FileManager.default.fileExists(atPath: url.path)
    }

    public static func deleteFiles(at directory: URL) {
        let manager = FileManager.default
        let names = ["smileproc.sqlite", "smileproc.sqlite-wal", "smileproc.sqlite-shm"]
        for name in names {
            let url = directory.appendingPathComponent(name)
            if manager.fileExists(atPath: url.path) {
                try? manager.removeItem(at: url)
            }
        }
    }
}

public enum DatabaseBootstrap: Sendable {
    case ready(AppDatabase, SymmetricKey)
    case needsRecovery
}

public enum DatabaseOpener {
    public static func bootstrap(directory: URL, keyStore: any KeychainKeyStore) throws -> DatabaseBootstrap {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        FileProtection.excludeFromBackup(directory)
        let exists = AppDatabase.databaseExists(at: directory)
        if let key = try keyStore.loadMasterKey() {
            let database = try AppDatabase.open(at: directory)
            return .ready(database, key)
        }
        if exists {
            return .needsRecovery
        }
        let key = SymmetricKey(size: .bits256)
        try keyStore.saveMasterKey(key)
        let database = try AppDatabase.open(at: directory)
        return .ready(database, key)
    }
}
