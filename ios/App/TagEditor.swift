import Darwin
import SmileCore
import SwiftUI

struct TagEditor: View {
    @Environment(AppEnvironment.self) private var environment
    @Environment(\.accessibilityVoiceOverEnabled) private var voiceOver
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Binding var selected: [String]
    @State private var showLimit = false

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            if !selected.isEmpty {
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 96), alignment: .leading)], alignment: .leading, spacing: 8) {
                    ForEach(selected, id: \.self) { id in
                        Button(Copy.tagName(id)) { toggle(id) }
                            .buttonStyle(.borderedProminent)
                            .accessibilityLabel(Copy.tagName(id))
                            .accessibilityAddTraits(.isSelected)
                    }
                }
            }
            if showLimit {
                Text(L10n.string("tags.limit"))
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            Picker(
                L10n.string("entry.tags"),
                selection: Binding(
                    get: { environment.tagPickerMode },
                    set: { environment.tagPickerMode = $0 }
                )
            ) {
                Text(L10n.string("tags.mode.gradual")).tag(TagPickerMode.gradual)
                Text(L10n.string("tags.mode.detailed")).tag(TagPickerMode.detailed)
            }
            .pickerStyle(.segmented)
            .accessibilityLabel(L10n.string("entry.tags"))
            if useList {
                TagListPicker(cores: environment.tags.cores, selected: selected, onToggle: toggle)
            } else if environment.tagPickerMode == .detailed {
                EmotionWheel(cores: environment.tags.cores, selected: selected, onToggle: toggle)
                    .frame(height: 380)
            } else {
                GradualTagPicker(cores: environment.tags.cores, selected: selected, onToggle: toggle)
            }
        }
    }

    private var useList: Bool {
        voiceOver || dynamicTypeSize >= .accessibility3
    }

    private func toggle(_ id: String) {
        if let index = selected.firstIndex(of: id) {
            selected.remove(at: index)
            showLimit = false
            return
        }
        guard selected.count < EntryValidator.maxTags else {
            showLimit = true
            return
        }
        selected.append(id)
        showLimit = false
    }
}

private struct GradualTagPicker: View {
    var cores: [EmotionCore]
    var selected: [String]
    var onToggle: (String) -> Void
    @State private var coreID: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if let coreID, let core = cores.first(where: { $0.id == coreID }) {
                Text(Copy.fill("tags.justCore", ["core": Copy.tagName(core.id)]))
                    .font(.headline)
                chip(id: core.id, title: Copy.fill("tags.justCore", ["core": Copy.tagName(core.id)]))
                ForEach(core.fine, id: \.self) { fine in
                    chip(id: fine, title: Copy.tagName(fine))
                }
                Button(L10n.string("common.back")) { self.coreID = nil }
            } else {
                Text(L10n.string("tags.closest"))
                    .font(.headline)
                ForEach(cores) { core in
                    Button(Copy.tagName(core.id)) { coreID = core.id }
                        .buttonStyle(.bordered)
                        .accessibilityLabel(Copy.tagName(core.id))
                }
            }
        }
        .onChange(of: selected) { old, new in
            if new.count > old.count { coreID = nil }
        }
    }

    private func chip(id: String, title: String) -> some View {
        let on = selected.contains(id)
        return Button(title) { onToggle(id) }
            .buttonStyle(.bordered)
            .accessibilityLabel(title)
            .accessibilityAddTraits(on ? .isSelected : [])
    }
}

private struct TagListPicker: View {
    var cores: [EmotionCore]
    var selected: [String]
    var onToggle: (String) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            ForEach(cores) { core in
                VStack(alignment: .leading, spacing: 8) {
                    Text(Copy.tagName(core.id))
                        .font(.headline)
                    row(core.id)
                    ForEach(core.fine, id: \.self) { fine in
                        row(fine)
                    }
                }
            }
        }
        .accessibilityElement(children: .contain)
    }

    private func row(_ id: String) -> some View {
        let on = selected.contains(id)
        return Button {
            onToggle(id)
        } label: {
            HStack {
                Text(Copy.tagName(id))
                Spacer()
                if on {
                    Image(systemName: "checkmark")
                        .accessibilityHidden(true)
                }
            }
        }
        .accessibilityLabel(Copy.tagName(id))
        .accessibilityAddTraits(on ? .isSelected : [])
    }
}

private struct WheelTag: Identifiable {
    var id: String
    var color: String
}

private struct EmotionWheel: View {
    var cores: [EmotionCore]
    var selected: [String]
    var onToggle: (String) -> Void

    var body: some View {
        GeometryReader { proxy in
            let diameter = min(max(proxy.size.width - 32, 0), 360)
            let center = CGPoint(x: proxy.size.width / 2, y: proxy.size.height / 2)
            let outer = diameter * 0.42
            let inner = diameter * 0.22
            let fines = fineTags
            ZStack {
                ForEach(Array(fines.enumerated()), id: \.element.id) { index, item in
                    let angle = angle(index: index, count: fines.count)
                    tagButton(item.id, color: item.color)
                        .position(point(center, radius: outer, angle: angle))
                }
                ForEach(Array(cores.enumerated()), id: \.element.id) { index, core in
                    let middle = index * 5 + 2
                    let angle = angle(index: middle, count: max(fines.count, 1))
                    tagButton(core.id, color: core.color)
                        .position(point(center, radius: inner, angle: angle))
                }
            }
        }
        .accessibilityElement(children: .contain)
    }

    private var fineTags: [WheelTag] {
        cores.flatMap { core in
            core.fine.map { WheelTag(id: $0, color: core.color) }
        }
    }

    private func angle(index: Int, count: Int) -> Double {
        let total = Double(max(count, 1))
        return (Double(index) / total) * 2 * Double.pi - Double.pi / 2
    }

    private func point(_ center: CGPoint, radius: CGFloat, angle: Double) -> CGPoint {
        CGPoint(x: center.x + CGFloat(cos(angle)) * radius, y: center.y + CGFloat(sin(angle)) * radius)
    }

    private func tagButton(_ id: String, color: String) -> some View {
        let on = selected.contains(id)
        return Button {
            onToggle(id)
        } label: {
            Circle()
                .fill(Copy.color(hex: color))
                .overlay {
                    Circle().strokeBorder(on ? Color.primary : Color.clear, lineWidth: 3)
                }
                .frame(width: 34, height: 34)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Copy.tagName(id))
        .accessibilityAddTraits(on ? .isSelected : [])
    }
}
