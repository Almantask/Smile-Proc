#!/usr/bin/env python3
"""The rules of .claude/hooks/guard_edit.py, applied to the files in the tree (edits made through
Bash bypass the hook). Same regexes, matched against each whole file like the hook matches the
whole edit, so a call split over several lines is still caught. Prints `file:line rule` for each
hit (the line where the match starts) and exits 1 on any hit."""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCAN_DIRS = ("core", "app/src/main", "content")
SCAN_FILES = ("settings.gradle.kts", "build.gradle.kts", "app/build.gradle.kts", "gradle/libs.versions.toml")

# Kept identical to .claude/hooks/guard_edit.py.
FORBIDDEN = {
    r"okhttp3|retrofit2|io\.ktor": "HTTP clients",
    r"java\.net\.(URL|HttpURLConnection)\b|HttpsURLConnection": "network APIs",
    r"android\.webkit\.WebView|<WebView": "WebView",
    r"android\.location\.Geocoder": "Geocoder",
    r"com\.google\.android\.gms|com\.google\.firebase|firebase-": "Play Services and Firebase",
    r"android\.permission\.INTERNET": "the INTERNET permission",
    r"android\.appwidget|appwidget-provider|androidx\.glance": "widgets",
    r"android\.service\.quicksettings|TileService": "Quick Settings tiles",
    r"ShortcutManager|android\.app\.shortcuts": "app shortcuts",
    r"ACCESS_BACKGROUND_LOCATION|SCHEDULE_EXACT_ALARM|USE_EXACT_ALARM": "permissions outside the spec",
    r"FOREGROUND_SERVICE": "foreground services (only if the human approves spike A2)",
    r"showWhenLocked\s*=\s*\"true\"|setShowWhenLocked\(\s*true": "activities over the lock screen",
}
CORE_IMPORT = re.compile(r"^\s*import\s+(android|androidx)\.", re.M)
MAIN_LOGGING = re.compile(r"android\.util\.Log\b|\bLog\.[vdiwe]\(|\bprintln\(")
NOTIFY = re.compile(r"\.notify\(\s*[\w.]+\s*,")


def files():
    found = set()
    for d in SCAN_DIRS:
        base = ROOT / d
        if base.is_dir():
            found |= {p for p in base.rglob("*") if p.is_file() and "build" not in p.relative_to(ROOT).parts}
    found |= {ROOT / f for f in SCAN_FILES if (ROOT / f).is_file()}
    return sorted(found)


def main():
    hits = []
    scanned = files()
    for path in scanned:
        rel = path.relative_to(ROOT).as_posix()
        if not rel.endswith((".kt", ".kts", ".xml", ".toml")):
            continue
        text = path.read_text(encoding="utf-8", errors="replace")

        def report(match, rule):
            # The line of the match's first non-blank character (`^\s*` can start a line early).
            start = match.start() + len(match.group(0)) - len(match.group(0).lstrip())
            hits.append(f"{rel}:{text.count(chr(10), 0, start) + 1} {rule}")

        for pattern, what in FORBIDDEN.items():
            for m in re.finditer(pattern, text):
                report(m, f"not allowed: {what}")
        if rel.endswith(".kt"):
            if rel.startswith("core/src/"):
                for m in CORE_IMPORT.finditer(text):
                    report(m, ":core must stay pure Kotlin/JVM (no android.* or androidx.* imports)")
            if rel.startswith("app/src/main/") and not rel.endswith("/SafeLog.kt"):
                for m in MAIN_LOGGING.finditer(text):
                    report(m, "logging in main sources; use SafeLog with event names and exception types only")
            if not rel.endswith("/CheckInNotifier.kt"):
                for m in NOTIFY.finditer(text):
                    report(m, "only CheckInNotifier may post notifications (one notification, one ID)")
    for line in hits:
        print(line)
    if hits:
        print(f"privacy-guard: {len(hits)} hit(s). See docs/spec/product.md.", file=sys.stderr)
        return 1
    print(f"privacy-guard: OK ({len(scanned)} files)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
