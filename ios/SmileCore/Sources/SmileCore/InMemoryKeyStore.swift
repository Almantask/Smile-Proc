import CryptoKit
import Foundation

public final class InMemoryKeyStore: KeychainKeyStore, @unchecked Sendable {
    private let lock = NSLock()
    private var stored: Data?

    public init() {}

    public func loadMasterKey() throws -> SymmetricKey? {
        lock.lock()
        defer { lock.unlock() }
        guard let stored else { return nil }
        return SymmetricKey(data: stored)
    }

    public func saveMasterKey(_ key: SymmetricKey) throws {
        let data = key.withUnsafeBytes { Data($0) }
        lock.lock()
        stored = data
        lock.unlock()
    }

    public func deleteMasterKey() throws {
        lock.lock()
        stored = nil
        lock.unlock()
    }
}
