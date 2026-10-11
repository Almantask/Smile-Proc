#!/usr/bin/env python3
"""Checks a dogfood/release manifest (apkanalyzer manifest print) against
docs/spec/android.md › Manifest: no debug components, no app-widget provider, no TileService,
no shortcuts, no activity over the lock screen, backups off, and SystemEventsReceiver as the
only exported receiver. Prints each problem; exits 1 if there is any."""
import sys
import xml.etree.ElementTree as ET

A = "{http://schemas.android.com/apk/res/android}"
COMPONENTS = ("activity", "activity-alias", "service", "receiver", "provider")


def main(path):
    root = ET.parse(path).getroot()
    app = root.find("application")
    problems = []
    if app is None:
        print("no <application> element")
        return 1

    if app.get(A + "allowBackup") != "false":
        problems.append('application: allowBackup is not "false"')
    if not app.get(A + "dataExtractionRules"):
        problems.append("application: no dataExtractionRules")

    for node in app.iter("meta-data"):
        if node.get(A + "name") == "android.app.shortcuts":
            problems.append("app shortcuts declared")

    exported_receivers = []
    for kind in COMPONENTS:
        for node in app.iter(kind):
            name = node.get(A + "name") or ""
            actions = {a.get(A + "name") for a in node.iter("action")}
            meta = {m.get(A + "name") for m in node.iter("meta-data")}
            if ".debug." in name or name.endswith(("ProbeReceiver", "DebugEventsReceiver")):
                problems.append(f"debug component {name}")
            if kind == "receiver" and ("android.appwidget.provider" in meta
                                       or "android.appwidget.action.APPWIDGET_UPDATE" in actions):
                problems.append(f"app-widget provider {name}")
            if kind == "service" and (node.get(A + "permission") == "android.permission.BIND_QUICK_SETTINGS_TILE"
                                      or "android.service.quicksettings.action.QS_TILE" in actions):
                problems.append(f"TileService {name}")
            if kind in ("activity", "activity-alias") and node.get(A + "showWhenLocked") == "true":
                problems.append(f"{name} shows over the lock screen")
            if kind == "receiver" and node.get(A + "exported") == "true":
                exported_receivers.append(name)

    others = [n for n in exported_receivers if not n.endswith(".SystemEventsReceiver")]
    for name in others:
        problems.append(f"exported receiver {name} (only SystemEventsReceiver may be exported)")

    for p in problems:
        print(p)
    if problems:
        return 1
    print(f"manifest OK; exported receivers: {exported_receivers or 'none'}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
