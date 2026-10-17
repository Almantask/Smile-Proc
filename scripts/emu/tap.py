#!/usr/bin/env python3
"""Taps the centre of the on-screen node whose text or content description is TEXT
(exact match first, then substring). Works on the lock screen.

  tap.py "<text>" [--shade] [--swipe]
    --shade   opens the notification shade first
    --swipe   swipes the node to the right edge instead of tapping it (dismissing a notification)
"""
import argparse
import re
import sys
import time
import xml.etree.ElementTree as ET

from _adb import adb

DUMP = "/sdcard/smileproc-window.xml"


def nodes():
    for _ in range(4):
        out = adb("shell", "uiautomator", "dump", DUMP, check=False)
        if "dumped to" in out:
            xml = adb("exec-out", "cat", DUMP)
            try:
                return list(ET.fromstring(xml).iter("node"))
            except ET.ParseError:
                pass
        time.sleep(1)
    sys.exit("uiautomator dump failed")


def centre(bounds):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", bounds))
    return (x1 + x2) // 2, (y1 + y2) // 2


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("text")
    parser.add_argument("--shade", action="store_true")
    parser.add_argument("--swipe", action="store_true")
    args = parser.parse_args()

    if args.shade:
        adb("shell", "cmd", "statusbar", "expand-notifications")
        time.sleep(1.5)

    found = nodes()
    labels = lambda n: (n.get("text") or "", n.get("content-desc") or "")
    match = next((n for n in found if args.text in labels(n)), None)
    if match is None:
        match = next((n for n in found if any(args.text in label for label in labels(n) if label)), None)
    if match is None:
        visible = sorted({label for n in found for label in labels(n) if label})
        print(f'"{args.text}" not found. Visible: {visible}', file=sys.stderr)
        return 1
    x, y = centre(match.get("bounds"))
    if args.swipe:
        width = int(re.search(r"(\d+)x(\d+)", adb("shell", "wm", "size")).group(1))
        adb("shell", "input", "swipe", str(x), str(y), str(width - 5), str(y), "300")
        print(f'swiped "{args.text}" from {x},{y} to the right edge')
        return 0
    adb("shell", "input", "tap", str(x), str(y))
    print(f'tapped "{args.text}" at {x},{y}')
    return 0


if __name__ == "__main__":
    sys.exit(main())
