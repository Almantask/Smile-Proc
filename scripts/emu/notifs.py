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


def parse(dump):
    records, current = [], None
    for line in section(dump.splitlines(), "Notification List:"):
        head = re.search(r"NotificationRecord\(0x[0-9a-f]+: pkg=(\S+) user=\S+ id=(-?\d+) .*?"
                         r"Notification\(channel=(\S+) .*?flags=(0x[0-9a-f]+)", line)
        if head:
            current = {"pkg": head.group(1), "id": int(head.group(2)), "channel": head.group(3),
                       "ongoing": bool(int(head.group(4), 16) & FLAG_ONGOING_EVENT),
                       "title": None, "text": None, "actions": []}
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
    return [r for r in records if r["pkg"] == PKG]


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
