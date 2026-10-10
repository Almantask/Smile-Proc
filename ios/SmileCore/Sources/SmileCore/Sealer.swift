import CryptoKit
import Foundation

public struct Sealer: Sendable {
    public var key: SymmetricKey

    public init(key: SymmetricKey) {
        self.key = key
    }

    public func seal(_ plaintext: Data, id: String, nonce: Data? = nil) throws -> Data {
        let authenticating = Data(id.utf8)
        let sealed: AES.GCM.SealedBox
        if let nonce {
            let gcmNonce = try AES.GCM.Nonce(data: nonce)
            sealed = try AES.GCM.seal(plaintext, using: key, nonce: gcmNonce, authenticating: authenticating)
        } else {
            sealed = try AES.GCM.seal(plaintext, using: key, authenticating: authenticating)
        }
        guard let combined = sealed.combined else { throw SmileError.cryptoFailure }
        return combined
    }

    public func open(_ combined: Data, id: String) throws -> Data {
        do {
            let box = try AES.GCM.SealedBox(combined: combined)
            return try AES.GCM.open(box, using: key, authenticating: Data(id.utf8))
        } catch {
            throw SmileError.cryptoFailure
        }
    }
}
