import CryptoKit
import Foundation
import Security

public protocol KeychainKeyStore: Sendable {
    func loadMasterKey() throws -> SymmetricKey?
    func saveMasterKey(_ key: SymmetricKey) throws
    func deleteMasterKey() throws
}

public struct KeychainMasterKeyStore: KeychainKeyStore {
    public static let service = "smileproc.db"
    public static let account = "master-v1"

    public init() {}

    public func loadMasterKey() throws -> SymmetricKey? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: Self.service,
            kSecAttrAccount as String: Self.account,
            kSecAttrSynchronizable as String: kCFBooleanFalse as Any,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess, let data = item as? Data else {
            throw SmileError.databaseFailure
        }
        return SymmetricKey(data: data)
    }

    public func saveMasterKey(_ key: SymmetricKey) throws {
        try deleteMasterKey()
        let data = key.withUnsafeBytes { Data($0) }
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: Self.service,
            kSecAttrAccount as String: Self.account,
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
            kSecAttrSynchronizable as String: kCFBooleanFalse as Any,
            kSecValueData as String: data,
        ]
        let status = SecItemAdd(query as CFDictionary, nil)
        guard status == errSecSuccess else { throw SmileError.databaseFailure }
    }

    public func deleteMasterKey() throws {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: Self.service,
            kSecAttrAccount as String: Self.account,
            kSecAttrSynchronizable as String: kCFBooleanFalse as Any,
        ]
        let status = SecItemDelete(query as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw SmileError.databaseFailure
        }
    }
}
