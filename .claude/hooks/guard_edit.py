#!/usr/bin/env python3
"""PreToolUse guard for Edit/Write/MultiEdit. Exit 2 = block, reason on stderr."""
import json
import re
import sys

data = json.load(sys.stdin)
ti = data.get("tool_input") or {}
path = (ti.get("file_path") or "").replace("\\", "/")
parts = [ti.get("content") or "", ti.get("new_string") or ""]
parts += [e.get("new_string") or "" for e in ti.get("edits") or [] if isinstance(e, dict)]
text = "\n".join(parts)


def block(reason):
    print(f"Blocked by .claude/hooks/guard_edit.py: {reason}. See docs/spec/product.md.", file=sys.stderr)
    sys.exit(2)


# Human-owned files
for owned in (".claude/settings.json", ".claude/hooks/", "content/support.json"):
    if owned in path:
        block(f"{owned} is human-owned; ask the human to change it")
if re.search(r"fixtures/.+\.golden\.json$", path):
    block("goldens are written only by a WRITE_GOLDENS=1 test run the human asked for")

# The M1 spike app is throwaway and exempt from product rules
if "/spikes/" in "/" + path:
    sys.exit(0)

if path.endswith((".kt", ".kts", ".xml", ".toml")):
    forbidden = {
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
    for pattern, what in forbidden.items():
        if re.search(pattern, text):
            block(f"not allowed: {what}")

if path.endswith(".kt"):
    if "/core/src/" in "/" + path and re.search(r"^\s*import\s+(android|androidx)\.", text, re.M):
        block(":core must stay pure Kotlin/JVM (no android.* or androidx.* imports)")
    if "/app/src/main/" in "/" + path and not path.endswith("/SafeLog.kt"):
        if re.search(r"android\.util\.Log\b|\bLog\.[vdiwe]\(|\bprintln\(", text):
            block("logging in main sources; use SafeLog with event names and exception types only")
    if not path.endswith("/CheckInNotifier.kt") and re.search(r"\.notify\(\s*[\w.]+\s*,", text):
        block("only CheckInNotifier may post notifications (one notification, one ID)")

sys.exit(0)
