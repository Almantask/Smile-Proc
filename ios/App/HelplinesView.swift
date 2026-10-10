import SmileCore
import SwiftUI
import UIKit

struct HelplinesView: View {
    @Environment(AppEnvironment.self) private var environment

    var body: some View {
        List {
            if let regionName {
                Text(Copy.fill("helplines.region", ["region": regionName]))
            }
            if let emergency {
                Section(L10n.string("helplines.emergency")) {
                    Button(emergency) { openPhone(emergency) }
                        .accessibilityLabel("\(L10n.string("helplines.emergency")) \(emergency)")
                }
            }
            if !lines.isEmpty {
                Section {
                    ForEach(lines) { line in
                        VStack(alignment: .leading, spacing: 8) {
                            Text(line.name)
                            Button(L10n.string("helplines.call")) { openPhone(line.phone) }
                                .accessibilityLabel("\(L10n.string("helplines.call")) \(line.name)")
                            if let sms = line.sms, !sms.isEmpty {
                                Button(L10n.string("helplines.text")) { openSMS(sms) }
                                    .accessibilityLabel("\(L10n.string("helplines.text")) \(line.name)")
                            }
                            if !line.verified {
                                Text(L10n.string("helplines.unverified"))
                                    .font(.footnote)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                }
            }
            if let helplines = environment.helplines {
                Section {
                    Button(L10n.string("helplines.directory")) { openWeb(helplines.directoryURL) }
                    if !helplines.directoryVerified {
                        Text(L10n.string("helplines.unverified"))
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
            }
        }
        .navigationTitle(L10n.string("helplines.title"))
    }

    private var regionCode: String? {
        Locale.current.region?.identifier
    }

    private var regionName: String? {
        guard let regionCode else { return nil }
        return Locale.current.localizedString(forRegionCode: regionCode) ?? regionCode
    }

    private var lines: [Helpline] {
        guard let code = regionCode, let helplines = environment.helplines else { return [] }
        return helplines.regions[code] ?? []
    }

    private var emergency: String? {
        guard let helplines = environment.helplines else { return nil }
        if let code = regionCode, let number = helplines.emergency[code] { return number }
        return helplines.emergency["default"]
    }

    private func openPhone(_ number: String) {
        let digits = number.filter { $0.isNumber || $0 == "+" }
        guard let url = URL(string: "tel:\(digits)") else { return }
        UIApplication.shared.open(url)
    }

    private func openSMS(_ number: String) {
        let digits = number.filter { $0.isNumber || $0 == "+" }
        guard let url = URL(string: "sms:\(digits)") else { return }
        UIApplication.shared.open(url)
    }

    private func openWeb(_ text: String) {
        guard let url = URL(string: text) else { return }
        UIApplication.shared.open(url)
    }
}
