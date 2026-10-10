import SmileCore
import SwiftUI
import UniformTypeIdentifiers

struct SettingsView: View {
    @Environment(AppEnvironment.self) private var environment
    @State private var confirmDelete = false

    var body: some View {
        NavigationStack {
            List {
                NavigationLink(L10n.string("settings.reminders")) { RemindersView() }
                NavigationLink(L10n.string("settings.weeklyCardTime")) { WeeklyTimeView() }
                NavigationLink(L10n.string("settings.places")) { PlacesView() }
                Section {
                    Toggle(L10n.string("settings.location"), isOn: locationBinding)
                    Text(L10n.string("settings.location.detail"))
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                Section {
                    Toggle(L10n.string("settings.appLock"), isOn: lockBinding)
                        .disabled(!environment.canUseAppLock())
                    if !environment.canUseAppLock() {
                        Text(L10n.string("settings.appLock.unavailable"))
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
                NavigationLink(L10n.string("settings.export")) { ExportView() }
                NavigationLink(L10n.string("settings.import")) { ImportView() }
                NavigationLink(L10n.string("settings.privacy")) { PrivacyView() }
                Button(L10n.string("settings.deleteAll"), role: .destructive) {
                    confirmDelete = true
                }
                NavigationLink(L10n.string("settings.about")) { AboutView() }
                #if DEBUG
                NavigationLink(L10n.string("debug.title")) { DebugView() }
                #endif
            }
            .navigationTitle(L10n.string("tab.settings"))
            .confirmationDialog(L10n.string("deleteAll.confirm"), isPresented: $confirmDelete, titleVisibility: .visible) {
                Button(L10n.string("deleteAll.action"), role: .destructive) {
                    environment.deleteAll()
                }
                Button(L10n.string("entry.cancel"), role: .cancel) {}
            } message: {
                Text(L10n.string("deleteAll.body"))
            }
        }
    }

    private var locationBinding: Binding<Bool> {
        Binding(
            get: { environment.captureLocation },
            set: { newValue in
                environment.captureLocation = newValue
                if newValue, !LocationPermission.shared.requestWhenInUse() {
                    environment.statusMessage = L10n.string("error.locationDenied")
                }
            }
        )
    }

    private var lockBinding: Binding<Bool> {
        Binding(
            get: { environment.appLockEnabled },
            set: { newValue in
                environment.appLockEnabled = newValue
                if !newValue { environment.isLocked = false }
            }
        )
    }
}

private struct RemindersView: View {
    @Environment(AppEnvironment.self) private var environment
    @State private var rows: [Reminder] = []

    var body: some View {
        List {
            if rows.isEmpty {
                Text(L10n.string("reminders.empty"))
                    .foregroundStyle(.secondary)
            }
            ForEach(rows) { reminder in
                Section {
                    DatePicker(
                        L10n.string("reminders.time"),
                        selection: timeBinding(reminder),
                        displayedComponents: .hourAndMinute
                    )
                    ForEach(1...7, id: \.self) { day in
                        Toggle(Copy.weekdayName(day), isOn: dayBinding(reminder, day: day))
                    }
                    Button(L10n.string("reminders.remove"), role: .destructive) {
                        rows.removeAll { $0.id == reminder.id }
                        environment.save(reminders: rows)
                    }
                }
            }
            if rows.count >= 6 {
                Text(L10n.string("reminders.limit"))
                    .foregroundStyle(.secondary)
            }
            Button(L10n.string("reminders.add")) { add() }
                .disabled(rows.count >= 6)
        }
        .navigationTitle(L10n.string("reminders.title"))
        .onAppear { rows = environment.reminders }
    }

    private func add() {
        guard rows.count < 6 else { return }
        rows.append(Reminder(id: Identifiers.newID(), hour: 9, minute: 0, weekdays: [1, 2, 3, 4, 5]))
        environment.save(reminders: rows)
    }

    private func timeBinding(_ reminder: Reminder) -> Binding<Date> {
        Binding(
            get: { date(hour: reminder.hour, minute: reminder.minute) },
            set: { newDate in
                let parts = Calendar.current.dateComponents([.hour, .minute], from: newDate)
                replace(reminder.id) { item in
                    var copy = item
                    copy.hour = parts.hour ?? item.hour
                    copy.minute = parts.minute ?? item.minute
                    return copy
                }
            }
        )
    }

    private func dayBinding(_ reminder: Reminder, day: Int) -> Binding<Bool> {
        Binding(
            get: { reminder.weekdays.contains(day) },
            set: { on in
                replace(reminder.id) { item in
                    var copy = item
                    if on {
                        if !copy.weekdays.contains(day) { copy.weekdays.append(day) }
                    } else {
                        copy.weekdays.removeAll { $0 == day }
                    }
                    copy.weekdays.sort()
                    return copy
                }
            }
        )
    }

    private func replace(_ id: String, change: (Reminder) -> Reminder) {
        guard let index = rows.firstIndex(where: { $0.id == id }) else { return }
        rows[index] = change(rows[index])
        environment.save(reminders: rows)
    }

    private func date(hour: Int, minute: Int) -> Date {
        var parts = DateComponents()
        parts.hour = hour
        parts.minute = minute
        return Calendar.current.date(from: parts) ?? Date()
    }
}

private struct WeeklyTimeView: View {
    @Environment(AppEnvironment.self) private var environment
    @State private var weekday = WeeklyCardSchedule.default.weekday
    @State private var time = Date()
    @State private var ready = false

    var body: some View {
        Form {
            Text(L10n.string("weeklyCard.time.detail"))
            Picker(L10n.string("reminders.days"), selection: $weekday) {
                ForEach(1...7, id: \.self) { day in
                    Text(Copy.weekdayName(day)).tag(day)
                }
            }
            DatePicker(L10n.string("reminders.time"), selection: $time, displayedComponents: .hourAndMinute)
        }
        .navigationTitle(L10n.string("weeklyCard.time.title"))
        .onAppear {
            let schedule = environment.weeklySchedule
            weekday = schedule.weekday
            time = date(hour: schedule.hour, minute: schedule.minute)
            ready = true
        }
        .onChange(of: weekday) { _, _ in if ready { save() } }
        .onChange(of: time) { _, _ in if ready { save() } }
    }

    private func save() {
        let parts = Calendar.current.dateComponents([.hour, .minute], from: time)
        environment.save(weekly: WeeklyCardSchedule(
            weekday: weekday,
            hour: parts.hour ?? 19,
            minute: parts.minute ?? 0
        ))
    }

    private func date(hour: Int, minute: Int) -> Date {
        var parts = DateComponents()
        parts.hour = hour
        parts.minute = minute
        return Calendar.current.date(from: parts) ?? Date()
    }
}

struct ExportView: View {
    @Environment(AppEnvironment.self) private var environment
    @State private var password = ""
    @State private var confirm = ""
    @State private var message: String?
    @State private var busy = false
    @State private var fileURL: URL?

    var body: some View {
        Form {
            SecureField(L10n.string("export.password"), text: $password)
                .textContentType(.newPassword)
                .accessibilityLabel(L10n.string("export.password"))
            SecureField(L10n.string("export.confirm"), text: $confirm)
                .textContentType(.newPassword)
                .accessibilityLabel(L10n.string("export.confirm"))
            Text(L10n.string("export.warning"))
                .font(.footnote)
                .foregroundStyle(.secondary)
            if busy {
                ProgressView()
                Text(L10n.string("export.progress"))
            }
            if let message {
                Text(message)
            }
            Button(L10n.string("export.action")) { Task { await create() } }
                .disabled(busy)
            if let fileURL {
                ShareLink(item: fileURL) {
                    Text(L10n.string("export.action"))
                }
            }
        }
        .navigationTitle(L10n.string("export.title"))
    }

    private func create() async {
        message = nil
        fileURL = nil
        guard password.count >= ExportFormat.minimumPasswordLength else {
            message = L10n.string("export.tooShort")
            return
        }
        guard password == confirm else {
            message = L10n.string("export.mismatch")
            return
        }
        busy = true
        defer { busy = false }
        do {
            fileURL = try await environment.exportFile(password: password)
        } catch {
            message = L10n.string("error.generic")
        }
    }
}

struct ImportView: View {
    @Environment(AppEnvironment.self) private var environment
    @State private var password = ""
    @State private var picking = false
    @State private var fileURL: URL?
    @State private var message: String?
    @State private var busy = false

    var body: some View {
        Form {
            Button(L10n.string("settings.import")) { picking = true }
            if fileURL != nil {
                SecureField(L10n.string("import.password"), text: $password)
                    .textContentType(.password)
                    .accessibilityLabel(L10n.string("import.password"))
            }
            if busy {
                ProgressView()
                Text(L10n.string("import.progress"))
            }
            if let message {
                Text(message)
            }
            Button(L10n.string("import.action")) { Task { await run() } }
                .disabled(busy || fileURL == nil)
        }
        .navigationTitle(L10n.string("import.title"))
        .fileImporter(isPresented: $picking, allowedContentTypes: [Self.exportType, .data]) { result in
            if case let .success(url) = result {
                fileURL = url
                message = nil
            }
        }
    }

    private func run() async {
        guard let fileURL else { return }
        message = nil
        busy = true
        defer { busy = false }
        let ok = await environment.importFile(url: fileURL, password: password)
        message = L10n.string(ok ? "import.done" : "import.wrongPassword")
    }

    static let exportType = UTType(exportedAs: "com.example.smileproc.export", conformingTo: .data)
}

private struct AboutView: View {
    @Environment(AppEnvironment.self) private var environment

    var body: some View {
        List {
            Text(Copy.fill("about.version", [
                "version": Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0",
            ]))
            Text(L10n.string("about.body"))
            if unverified {
                Text(L10n.string("about.pending"))
            }
        }
        .navigationTitle(L10n.string("settings.about"))
    }

    private var unverified: Bool {
        guard let helplines = environment.helplines else { return false }
        if !helplines.directoryVerified { return true }
        return helplines.regions.values.contains { lines in lines.contains { !$0.verified } }
    }
}
