import Foundation

enum L10n {
    static func string(_ key: String) -> String {
        Bundle.main.localizedString(forKey: key, value: key, table: "Localizable")
    }

    static func format(_ key: String, _ args: CVarArg...) -> String {
        let template = string(key)
        return String(format: template, locale: Locale.current, arguments: args)
    }
}
