#!/usr/bin/env python3
"""Prints SmileProc's posted notifications as JSON (id, channel, ongoing, title, text, actions)
from `dumpsys notification --noredact`.

  notifs.py [--expect-max N]   exits 1 if more than N SmileProc notifications are posted
"""
import argparse
import json
import re
import sys

from _adb import PKG, adb

FLAG_ONGOING_EVENT = 0x2


def section(lines, header):
    """Lines of the dumpsys section that starts with `header`, up to the next line at the same indent."""
    for i, line in enumerate(lines):
        if line.strip() == header:
            indent = len(line) - len(line.lstrip())
            out = []
            for following in lines[i + 1:]:
                if following.strip() and len(following) - len(following.lstrip()) <= indent:
                    break
                out.append(following)
            return out
    return []


def ongoing(flags):
    """Notification flags as dumpsys prints them: hex up to Android 15 (flags=0x62),
    names from Android 16 (flags=ONGOING_EVENT|ONLY_ALERT_ONCE)."""
    if flags.startswith("0x"):
        return bool(int(flags, 16) & FLAG_ONGOING_EVENT)
    return "ONGOING_EVENT" in flags.split("|")


def parse(dump):
    records, current = [], None
    lines = section(dump.splitlines(), "Notification List:")
    for line in lines:
        head = re.search(r"NotificationRecord\(0x[0-9a-f]+: pkg=(\S+) user=\S+ id=(-?\d+) .*?"
                         r"Notification\(channel=(\S+) .*?flags=([\w|]+)", line)
        if head:
            current = {"pkg": head.group(1), "id": int(head.group(2)), "channel": head.group(3),
                       "ongoing": ongoing(head.group(4)), "title": None, "text": None, "actions": []}
            records.append(current)
            continue
        if current is None:
            continue
        m = re.match(r"\s*android\.title=\w+ \((.*)\)\s*$", line)
        if m:
            current["title"] = m.group(1)
        m = re.match(r"\s*android\.text=\w+ \((.*)\)\s*$", line)
        if m:
            current["text"] = m.group(1)
        m = re.match(r'\s*\[\d+\] "(.*)" -> ', line)
        if m:
            current["actions"].append(m.group(1))
    found = [r for r in records if r["pkg"] == PKG]
    # A dumpsys format this parser doesn't know must fail, not read as "no notifications".
    listed = sum(1 for line in lines if re.search(rf"NotificationRecord\(.*\bpkg={re.escape(PKG)} ", line))
    if listed != len(found):
        sys.exit(f"notifs.py: dumpsys lists {listed} {PKG} notification(s) but only {len(found)} parsed; "
                 "update the parser for this Android version")
    return found


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--expect-max", type=int)
    args = parser.parse_args()
    found = parse(adb("shell", "dumpsys", "notification", "--noredact"))
    for record in found:
        record.pop("pkg")
    print(json.dumps(found, indent=2, ensure_ascii=False))
    if args.expect_max is not None and len(found) > args.expect_max:
        print(f"{len(found)} SmileProc notifications, expected at most {args.expect_max}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
