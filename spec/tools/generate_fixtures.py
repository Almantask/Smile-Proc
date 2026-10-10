#!/usr/bin/env python3
"""Write spec/fixtures from the reference engine. Stdlib only.

    python3 spec/tools/generate_fixtures.py
    python3 spec/tools/generate_fixtures.py --check
"""

from __future__ import annotations

import argparse
import json
import math
import random
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPEC = ROOT / "spec"
sys.path.insert(0, str(SPEC / "tools"))

from engine import (  # noqa: E402
    build_weekly_card,
    card_period,
    format_instant,
    haversine_m,
    library_coverage,
    low_run_armed,
    parse_instant,
    prepare_input_entries,
    resolve_places,
    r6,
    should_build_card,
    zone_of,
)

WEEKLY = {"weekday": 7, "hour": 19, "minute": 0}


def load(path: Path):
    return json.loads(path.read_text())


def dump(path: Path, value) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n")


def rome(year, month, day, hour, minute, second=0) -> str:
    dt = datetime(year, month, day, hour, minute, second, tzinfo=zone_of("Europe/Rome"))
    return format_instant(dt)


def entry(eid, created, mood, zone="Europe/Rome", tags=None, comment=None, location=None, source="app", updated=None):
    return {
        "id": eid,
        "createdAt": created,
        "updatedAt": updated or created,
        "timeZoneID": zone,
        "mood": mood,
        "tags": tags or [],
        "comment": comment,
        "location": location,
        "source": source,
    }


def location(lat, lon, accuracy=12.0, captured=None, kind="fresh"):
    return {
        "latitude": lat,
        "longitude": lon,
        "horizontalAccuracy": accuracy,
        "capturedAt": captured,
        "kind": kind,
    }


def place(pid, name, kind, lat, lon, created):
    return {
        "id": pid,
        "name": name,
        "kind": kind,
        "anchorLatitude": lat,
        "anchorLongitude": lon,
        "createdAt": created,
    }


def offset(lat, lon, north_m, east_m):
    dlat = north_m / 111_320.0
    dlon = east_m / (111_320.0 * math.cos(math.radians(lat)))
    return lat + dlat, lon + dlon


def uid(n: int) -> str:
    return f"00000000-0000-4000-8000-{n:012d}"


def run_card(entries, places, now, zone="Europe/Rome", weekly=None):
    config = load(SPEC / "content" / "pattern-config.json")
    library = load(SPEC / "content" / "pattern-library.en.json")
    weekly = weekly or WEEKLY
    now_dt = parse_instant(now) if isinstance(now, str) else now
    return build_weekly_card(
        entries,
        places,
        config,
        library,
        now_dt,
        zone,
        weekly["weekday"],
        weekly["hour"],
        weekly["minute"],
    )


def golden_card(card: dict) -> dict:
    return {
        "periodStart": card["periodStart"],
        "periodEnd": card["periodEnd"],
        "windowKind": card["windowKind"],
        "entryCount": card["entryCount"],
        "moodCounts": card["moodCounts"],
        "days": card["days"],
        "isLowWeek": card["isLowWeek"],
        "variant": card["variant"],
        "maxItemCount": card["maxItemCount"],
        "items": card["items"],
        "candidates": card["candidates"],
    }


def expect_card(card: dict, daypart_ids=None) -> dict:
    expect = {
        "variant": card["variant"],
        "isLowWeek": card["isLowWeek"],
        "windowKind": card["windowKind"],
        "maxItemCount": card["maxItemCount"],
        "itemCount": len(card["items"]),
        "firstItemLibraryID": card["items"][0]["libraryID"] if card["items"] else None,
        "itemsIncludeLibraryIDs": [item["libraryID"] for item in card["items"]],
        "candidatesInclude": [{"id": c["id"], "confidence": c["confidence"]} for c in card["candidates"]],
    }
    if daypart_ids:
        expect["dayparts"] = {eid: card["dayparts"][eid] for eid in daypart_ids}
    return expect


def write_pattern(name, entries, places, now, zone="Europe/Rome", daypart_ids=None):
    card = run_card(entries, places, now, zone)
    base = SPEC / "fixtures" / "patterns" / name
    dump(Path(str(base) + ".input.json"), {"now": now, "timeZoneID": zone, "weeklyCard": WEEKLY, "entries": entries, "places": places})
    dump(Path(str(base) + ".expect.json"), expect_card(card, daypart_ids))
    dump(Path(str(base) + ".golden.json"), golden_card(card))
    return card


def require(name, card, **expected):
    libs = [item["libraryID"] for item in card["items"]]
    for key, value in expected.items():
        if key == "firstLibrary":
            actual = libs[0] if libs else None
        elif key == "includes":
            missing = [item for item in value if item not in libs]
            if missing:
                raise SystemExit(f"{name}: missing libraries {missing}; items={card['items']}\ncandidates={card['candidates'][:12]}")
            continue
        elif key == "excludes":
            present = [item for item in value if item in libs]
            if present:
                raise SystemExit(f"{name}: unexpected libraries {present}; items={card['items']}")
            continue
        else:
            actual = card[key]
        if actual != value:
            raise SystemExit(
                f"{name}: {key}={actual!r} expected {value!r}\nitems={card['items']}\n"
                f"candidates={json.dumps(card['candidates'][:15], indent=2)}"
            )


def scenario_afternoon():
    # Six low afternoons against six good check-ins split across the other
    # dayparts, so no other daypart reaches a pattern and the balance step
    # has no higher pattern to swap in.
    now = rome(2026, 10, 18, 19, 0)
    entries = [entry(uid(1), rome(2026, 9, 1, 10, 0), 2)]
    n = 2
    for day in (12, 13, 14, 15, 16, 17):
        entries.append(entry(uid(n), rome(2026, 10, day, 14, 0), 1))
        n += 1
    for day, hour in ((12, 9), (13, 10), (14, 19), (15, 20), (16, 23), (17, 2)):
        entries.append(entry(uid(n), rome(2026, 10, day, hour, 0), 3))
        n += 1
    card = write_pattern("afternoon-dip", entries, [], now)
    require("afternoon-dip", card, variant="normal", isLowWeek=False, firstLibrary="afternoon-dip", includes=["afternoon-dip"])
    return card


def scenario_sunday():
    now = rome(2026, 11, 15, 19, 0)
    entries = []
    n = 100
    for month, days in ((10, (18, 25)), (11, (1, 8))):
        for day in days:
            entries.append(entry(uid(n), rome(2026, month, day, 20, 0), 1))
            n += 1
            for hour in (8, 9, 10, 11):
                entries.append(entry(uid(n), rome(2026, month, day, hour, 0), 3))
                n += 1
    start = datetime(2026, 10, 19, tzinfo=zone_of("Europe/Rome")).date()
    end = datetime(2026, 11, 15, tzinfo=zone_of("Europe/Rome")).date()
    day = start
    while day < end:
        if day.isoweekday() != 7:
            entries.append(entry(uid(n), rome(day.year, day.month, day.day, 9, 30), 3))
            n += 1
        day += timedelta(days=1)
    card = write_pattern("sunday-evening", entries, [], now)
    require("sunday-evening", card, variant="normal", isLowWeek=False, firstLibrary="sunday-evening", includes=["sunday-evening"])
    return card


def scenario_tired():
    now = rome(2026, 10, 18, 19, 0)
    entries = [entry(uid(200), rome(2026, 9, 20, 10, 0), 2, tags=["calm"])]
    plan = [
        (12, 8, 1, ["tired"]),
        (12, 14, 1, ["tired"]),
        (13, 18, 1, ["tired"]),
        (14, 23, 1, ["tired"]),
        (15, 10, 1, ["tired"]),
        (12, 9, 2, ["happy"]),
        (13, 15, 2, ["happy"]),
        (14, 19, 2, ["happy"]),
        (15, 6, 2, ["happy"]),
        (16, 13, 2, ["happy"]),
    ]
    for index, (day, hour, mood, tags) in enumerate(plan, start=201):
        entries.append(entry(uid(index), rome(2026, 10, day, hour, 0), mood, tags=tags))
    card = write_pattern("tired-on-low", entries, [], now)
    require("tired-on-low", card, variant="normal", isLowWeek=False, includes=["tired-on-low"])
    return card


WORK = (45.4642, 9.1900)
AWAY = (45.5000, 9.2800)


def scenario_stressed():
    now = rome(2026, 10, 18, 19, 0)
    label = place(uid(300), "Office", "work", WORK[0], WORK[1], rome(2026, 9, 1, 9, 0))
    entries = [entry(uid(301), rome(2026, 9, 1, 9, 0), 2)]
    for index, day in enumerate(range(12, 18)):
        lat, lon = offset(WORK[0], WORK[1], 8 + index, -6)
        entries.append(
            entry(
                uid(310 + index),
                rome(2026, 10, day, 11, 0),
                2,
                tags=["anxious.stressed"],
                location=location(lat, lon, 15.0, rome(2026, 10, day, 11, 0)),
            )
        )
        lat, lon = offset(AWAY[0], AWAY[1], index * 3, 4)
        entries.append(
            entry(
                uid(320 + index),
                rome(2026, 10, day, 16, 0),
                2,
                tags=["calm"],
                location=location(lat, lon, 18.0, rome(2026, 10, day, 16, 0)),
            )
        )
    card = write_pattern("stressed-at-work", entries, [label], now)
    require("stressed-at-work", card, variant="normal", includes=["stress-at-place"])
    return card


def scenario_out():
    now = rome(2026, 10, 18, 19, 0)
    label = place(uid(400), "Home", "home", WORK[0], WORK[1], rome(2026, 8, 1, 9, 0))
    entries = [entry(uid(401), rome(2026, 8, 2, 9, 0), 2)]
    for index, day in enumerate(range(12, 18)):
        lat, lon = offset(WORK[0], WORK[1], 5, index * 2)
        entries.append(
            entry(
                uid(410 + index),
                rome(2026, 10, day, 8, 30),
                1,
                location=location(lat, lon, 10.0, rome(2026, 10, day, 8, 30)),
            )
        )
        lat, lon = offset(AWAY[0], AWAY[1], -index * 4, 6)
        entries.append(
            entry(
                uid(420 + index),
                rome(2026, 10, day, 15, 0),
                3,
                location=location(lat, lon, 20.0, rome(2026, 10, day, 15, 0)),
            )
        )
    card = write_pattern("out-lifts", entries, [label], now)
    require("out-lifts", card, variant="normal", includes=["out-lifts"])
    return card


def scenario_low_week():
    now = rome(2026, 10, 18, 19, 0)
    entries = [entry(uid(500), rome(2026, 9, 10, 12, 0), 3)]
    moods = [1, 1, 1, 1, 1, 1, 3, 2]
    for index, mood in enumerate(moods):
        day = 12 + (index % 6)
        hour = 8 + (index % 5) * 2
        entries.append(entry(uid(510 + index), rome(2026, 10, day, hour, 0), mood))
    card = write_pattern("low-week", entries, [], now)
    require("low-week", card, variant="normal", isLowWeek=True)
    return card


def scenario_not_enough():
    now = rome(2026, 10, 18, 19, 0)
    entries = [
        entry(uid(600), rome(2026, 8, 1, 10, 0), 2),
        entry(uid(601), rome(2026, 10, 16, 9, 0), 2),
        entry(uid(602), rome(2026, 10, 17, 12, 0), 1),
        entry(uid(603), rome(2026, 10, 17, 18, 0), 3),
    ]
    card = write_pattern("not-enough-data", entries, [], now)
    require("not-enough-data", card, variant="notEnoughData", isLowWeek=False, maxItemCount=1)
    return card


def scenario_first_week():
    now = rome(2026, 10, 18, 19, 0)
    entries = []
    for index, (day, hour, mood) in enumerate(
        ((16, 9, 2), (16, 14, 3), (17, 8, 2), (17, 18, 1), (18, 11, 3), (18, 16, 2))
    ):
        entries.append(entry(uid(700 + index), rome(2026, 10, day, hour, 0), mood))
    card = write_pattern("first-week", entries, [], now)
    require("first-week", card, variant="firstWeek", maxItemCount=1)
    return card


def scenario_dst():
    now = "2026-10-25T18:00:00.000Z"
    specs = [
        ("dst-a", "2026-10-24T18:00:00.000Z", "evening"),
        ("dst-b", "2026-10-24T23:30:00.000Z", "night"),
        ("dst-c", "2026-10-25T01:30:00.000Z", "night"),
        ("dst-d", "2026-10-25T11:00:00.000Z", "afternoon"),
        ("dst-e", "2026-10-25T16:00:00.000Z", "evening"),
        ("dst-f", "2026-10-20T07:00:00.000Z", "morning"),
        ("dst-g", "2026-10-21T07:00:00.000Z", "morning"),
        ("dst-h", "2026-10-22T07:00:00.000Z", "morning"),
        ("dst-i", "2026-10-23T07:00:00.000Z", "morning"),
    ]
    entries = [entry(eid, created, 2 if eid != "dst-e" else 1) for eid, created, _part in specs]
    card = write_pattern("dst-week", entries, [], now, daypart_ids=[eid for eid, _, _ in specs])
    for eid, _created, part in specs:
        if card["dayparts"][eid] != part:
            raise SystemExit(f"dst daypart {eid}={card['dayparts'][eid]} expected {part}")
    if card["periodEnd"] != "2026-10-25T18:00:00.000Z":
        raise SystemExit(f"dst periodEnd {card['periodEnd']}")
    return card


def scenario_travel():
    now = rome(2026, 10, 18, 19, 0)
    entries = [
        entry("travel-ny", "2026-10-14T14:00:00.000Z", 3, zone="America/New_York"),
        entry("travel-rome", "2026-10-14T14:00:00.000Z", 2, zone="Europe/Rome"),
        entry(uid(800), rome(2026, 9, 1, 10, 0), 2),
    ]
    for index in range(8):
        entries.append(entry(uid(810 + index), rome(2026, 10, 12 + (index % 5), 9 + index, 0), 2))
    card = write_pattern("travel", entries, [], now, daypart_ids=["travel-ny", "travel-rome"])
    if card["dayparts"]["travel-ny"] != "morning" or card["dayparts"]["travel-rome"] != "afternoon":
        raise SystemExit(f"travel dayparts {card['dayparts']['travel-ny']} {card['dayparts']['travel-rome']}")
    return card


def scenario_noise():
    config_zone_now = rome(2026, 10, 18, 19, 0)
    for seed in (1, 2, 3):
        rng = random.Random(seed)
        entries = [entry(uid(900 + seed), rome(2026, 9, 1, 10, 0), 2)]
        for index in range(18):
            day = rng.randint(12, 17)
            hour = rng.randint(0, 23)
            minute = rng.randint(0, 59)
            mood = rng.randint(1, 3)
            entries.append(entry(uid(1000 * seed + index), rome(2026, 10, day, hour, minute), mood))
        write_pattern(f"noise-{seed}", entries, [], config_zone_now)


def write_places():
    config = load(SPEC / "content" / "pattern-config.json")
    origin_a = (45.4642, 9.1900)
    origin_b = (45.4700, 9.2100)
    origin_c = (45.4500, 9.1700)

    def cluster_entries(start, origin, count, accuracy=12.0, created_hour=10):
        made = []
        for index in range(count):
            lat, lon = offset(origin[0], origin[1], (index - 1) * 15, (index % 3) * 10)
            created = rome(2026, 10, 12 + index, created_hour, index)
            made.append(
                entry(
                    uid(start + index),
                    created,
                    2,
                    location=location(lat, lon, accuracy, created),
                )
            )
        return made

    groups = [
        cluster_entries(1100, origin_a, 4),
        cluster_entries(1200, origin_b, 4, created_hour=15),
        cluster_entries(1300, origin_c, 4, created_hour=20),
    ]
    entries = [e for group in groups for e in group]
    prepared = prepare_input_entries(entries)
    assignment = resolve_places(prepared, [], config)
    clusters = assignment["entryClusters"]
    ids = [[e["id"] for e in group] for group in groups]
    for group in ids:
        if len({clusters[eid] for eid in group}) != 1:
            raise SystemExit(f"three-places same cluster failed { {eid: clusters[eid] for eid in group} }")
    if len({clusters[group[0]] for group in ids}) != 3:
        raise SystemExit(f"expected 3 clusters, got {clusters}")
    # 500 m separation check
    if haversine_m(*origin_a, *origin_b) <= 100:
        raise SystemExit("fixture origins are too close")
    write_place_fixture(
        "three-places-noise",
        entries,
        [],
        {
            "sameCluster": ids,
            "differentCluster": [[ids[0][0], ids[1][0]], [ids[0][0], ids[2][0]], [ids[1][0], ids[2][0]]],
            "unclustered": [],
        },
        assignment,
    )

    good = cluster_entries(1400, origin_a, 3)
    created = rome(2026, 10, 16, 12, 0)
    coarse = entry(uid(1490), created, 2, location=location(origin_a[0], origin_a[1], 180.0, created))
    borderline = entry(
        uid(1491),
        rome(2026, 10, 16, 13, 0),
        2,
        location=location(*offset(origin_a[0], origin_a[1], 20, 0), 150.0, rome(2026, 10, 16, 13, 0)),
    )
    entries = good + [coarse, borderline]
    prepared = prepare_input_entries(entries)
    assignment = resolve_places(prepared, [], config)
    clusters = assignment["entryClusters"]
    if uid(1490) in clusters:
        raise SystemExit("accuracy 180 should be unclustered")
    if uid(1491) not in clusters:
        raise SystemExit("accuracy 150 should be clustered")
    write_place_fixture(
        "accuracy-filter",
        entries,
        [],
        {
            "sameCluster": [[e["id"] for e in good] + [uid(1491)]],
            "differentCluster": [],
            "unclustered": [uid(1490)],
        },
        assignment,
    )

    label = place(uid(1500), "Office", "work", origin_a[0], origin_a[1], rome(2026, 9, 1, 9, 0))
    entries = cluster_entries(1510, origin_a, 4)
    prepared = prepare_input_entries(entries)
    assignment = resolve_places(prepared, [label], config)
    clusters = assignment["entryClusters"]
    if any(clusters[e["id"]] != "p:" + label["id"] for e in entries):
        raise SystemExit(f"label capture failed {clusters}")
    write_place_fixture(
        "label-capture",
        entries,
        [label],
        {"sameCluster": [[e["id"] for e in entries]], "differentCluster": [], "unclustered": [], "clusterIDs": {"all": "p:" + label["id"]}},
        assignment,
    )

    prepared = prepare_input_entries(entries)
    assignment = resolve_places(prepared, [], config)
    clusters = assignment["entryClusters"]
    if any(not clusters[e["id"]].startswith("u:") for e in entries):
        raise SystemExit(f"label deleted should be unnamed {clusters}")
    write_place_fixture(
        "label-deleted",
        entries,
        [],
        {"sameCluster": [[e["id"] for e in entries]], "differentCluster": [], "unclustered": []},
        assignment,
    )


def write_place_fixture(name, entries, places, expect, assignment):
    base = SPEC / "fixtures" / "places" / name
    dump(Path(str(base) + ".input.json"), {"entries": entries, "places": places})
    dump(Path(str(base) + ".expect.json"), expect)
    clusters = []
    for cluster in assignment["clusters"]:
        clusters.append(
            {
                "id": cluster["id"],
                "centroidLatitude": r6(cluster["centroidLatitude"]),
                "centroidLongitude": r6(cluster["centroidLongitude"]),
                "memberIDs": cluster["memberIDs"],
                "labelID": cluster["labelID"],
            }
        )
    dump(
        Path(str(base) + ".golden.json"),
        {"entryClusters": assignment["entryClusters"], "clusters": clusters},
    )


def write_periods():
    cases = []

    def add(name, now, zone):
        period = card_period(parse_instant(now), zone, 7, 19, 0)
        cases.append(
            {
                "name": name,
                "now": now,
                "timeZoneID": zone,
                "weeklyCard": WEEKLY,
                "periodStart": format_instant(period["periodStart"]),
                "periodEnd": format_instant(period["periodEnd"]),
            }
        )

    add("exact-sunday", rome(2026, 10, 18, 19, 0), "Europe/Rome")
    add("one-minute-before", rome(2026, 10, 18, 18, 59), "Europe/Rome")
    add("monday-morning", rome(2026, 10, 19, 8, 0), "Europe/Rome")
    add("dst-sunday-after", "2026-10-25T18:30:00.000Z", "Europe/Rome")
    add("dst-sunday-just-before", "2026-10-25T17:59:00.000Z", "Europe/Rome")
    add("new-york", "2026-10-18T23:00:00.000Z", "America/New_York")
    # DST: Sunday 25 Oct 2026 19:00 CET is 18:00Z, and 7 calendar days earlier is
    # Sunday 18 Oct 19:00 CEST = 17:00Z. The gap is 169 hours, not 168.
    by_name = {case["name"]: case for case in cases}
    if by_name["dst-sunday-after"]["periodEnd"] != "2026-10-25T18:00:00.000Z":
        raise SystemExit(by_name["dst-sunday-after"])
    if by_name["dst-sunday-after"]["periodStart"] != "2026-10-18T17:00:00.000Z":
        raise SystemExit(by_name["dst-sunday-after"])
    if by_name["dst-sunday-just-before"]["periodEnd"] != "2026-10-18T17:00:00.000Z":
        raise SystemExit(by_name["dst-sunday-just-before"])
    if by_name["exact-sunday"]["periodEnd"] != rome(2026, 10, 18, 19, 0):
        raise SystemExit(by_name["exact-sunday"])
    dump(SPEC / "fixtures" / "periods" / "periods.json", {"cases": cases})


def write_low_mood():
    config = load(SPEC / "content" / "pattern-config.json")
    one_day = [entry(uid(1600 + i), rome(2026, 10, 12, 9 + i, 0), 1) for i in range(5)]
    two_days = [entry(uid(1700 + i), rome(2026, 10, 12 if i < 3 else 13, 9 + i, 0), 1) for i in range(5)]
    cases = [
        {"name": "same-day", "entries": one_day, "dismissedEntryID": None, "armed": False},
        {"name": "two-days", "entries": two_days, "dismissedEntryID": None, "armed": True},
        {"name": "dismissed", "entries": two_days, "dismissedEntryID": two_days[-1]["id"], "armed": False},
    ]
    continued = two_days + [entry(uid(1710), rome(2026, 10, 14, 9, 0), 1)]
    cases.append({"name": "still-low-after-dismiss", "entries": continued, "dismissedEntryID": two_days[-1]["id"], "armed": False})
    rearmed_prefix = two_days + [entry(uid(1720), rome(2026, 10, 14, 12, 0), 2)]
    fresh = [entry(uid(1730 + i), rome(2026, 10, 15 if i < 2 else 16, 10 + i, 0), 1) for i in range(5)]
    cases.append({"name": "rearmed", "entries": rearmed_prefix + fresh, "dismissedEntryID": two_days[-1]["id"], "armed": True})
    for case in cases:
        actual = low_run_armed(case["entries"], config, case["dismissedEntryID"])
        if actual != case["armed"]:
            raise SystemExit(f"low mood {case['name']} armed={actual}")
    dump(SPEC / "fixtures" / "lowmood" / "cases.json", {"cases": cases})


def write_build_gate():
    config = load(SPEC / "content" / "pattern-config.json")
    now = parse_instant(rome(2026, 10, 14, 12, 0))  # Wednesday
    early = [entry(uid(1800 + i), rome(2026, 10, 14, 9, i), 2) for i in range(5)]
    if should_build_card(early, config, now, "Europe/Rome", 7, 19, 0, []):
        raise SystemExit("should not build before the next card time")
    sunday_evening = parse_instant(rome(2026, 10, 18, 19, 5))
    if not should_build_card(early, config, sunday_evening, "Europe/Rome", 7, 19, 0, []):
        raise SystemExit("should build at the first card time after enough entries")
    period_end = rome(2026, 10, 18, 19, 0)
    if should_build_card(early, config, sunday_evening, "Europe/Rome", 7, 19, 0, [period_end]):
        raise SystemExit("should not rebuild an existing period")
    too_few = early[:3]
    if should_build_card(too_few, config, sunday_evening, "Europe/Rome", 7, 19, 0, []):
        raise SystemExit("five entries are required")
    dump(
        SPEC / "fixtures" / "periods" / "build-gate.json",
        {
            "cases": [
                {
                    "name": "before-card-time",
                    "now": rome(2026, 10, 14, 12, 0),
                    "entries": early,
                    "existingPeriodEnds": [],
                    "shouldBuild": False,
                },
                {
                    "name": "first-card-time",
                    "now": rome(2026, 10, 18, 19, 5),
                    "entries": early,
                    "existingPeriodEnds": [],
                    "shouldBuild": True,
                },
                {
                    "name": "already-built",
                    "now": rome(2026, 10, 18, 19, 5),
                    "entries": early,
                    "existingPeriodEnds": [period_end],
                    "shouldBuild": False,
                },
                {
                    "name": "too-few",
                    "now": rome(2026, 10, 18, 19, 5),
                    "entries": too_few,
                    "existingPeriodEnds": [],
                    "shouldBuild": False,
                },
            ]
        },
    )


def write_inbox():
    inbox = SPEC / "fixtures" / "inbox"
    valid = {
        "v": 1,
        "id": uid(1900),
        "createdAt": "2026-10-12T13:05:00.000Z",
        "timeZoneID": "Europe/Rome",
        "mood": 2,
        "source": "lockScreenWidget",
    }
    loc = {
        "v": 1,
        "id": uid(1900),
        "latitude": 45.4642,
        "longitude": 9.19,
        "horizontalAccuracy": 12.0,
        "capturedAt": "2026-10-12T13:05:03.000Z",
        "kind": "fresh",
    }
    partial = {"v": 1, "id": uid(1901), "mood": 1}
    dump(inbox / "valid.entry.json", valid)
    dump(inbox / "valid.loc.json", loc)
    dump(inbox / "partial.entry.json", partial)
    (inbox / "corrupt.entry.json").write_text("{this is not json\n")


def write_coverage():
    library = load(SPEC / "content" / "pattern-library.en.json")
    rows = library_coverage(library)
    missing = [row for row in rows if not row["libraryID"]]
    if missing:
        raise SystemExit(f"library coverage gaps: {missing[:5]}")
    dump(SPEC / "fixtures" / "library" / "coverage.json", {"cases": rows})


def write_canonical():
    sample = entry(
        "3f0c2b9e-7d1a-4a52-9a8e-0c6f1d2e4b10",
        "2026-10-12T13:05:00.000Z",
        2,
        tags=["anxious.stressed"],
        comment=None,
        location=location(45.4642, 9.19, 12.0, "2026-10-12T13:05:03.000Z", "fresh"),
        source="lockScreenWidget",
        updated="2026-10-12T13:06:10.000Z",
    )
    text = canonical_entry(sample)
    dump_text = SPEC / "fixtures" / "canonical" / "entry-1.json"
    dump_text.parent.mkdir(parents=True, exist_ok=True)
    dump_text.write_text(text + "\n")
    # The file is the exact canonical document plus a trailing newline for git.
    # Tests compare the bytes without the trailing newline.
    place_text = canonical_place(place(uid(1), "Office", "work", 45.48, 9.21, "2026-10-01T08:00:00.000Z"))
    (SPEC / "fixtures" / "canonical" / "place-1.json").write_text(place_text + "\n")


def canonical_number(value):
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, int) and not isinstance(value, bool):
        return str(value)
    if isinstance(value, float):
        text = json.dumps(value)
        return text
    raise TypeError(type(value))


def canonical_json(value) -> str:
    if value is None:
        return "null"
    if isinstance(value, str):
        return json.dumps(value, ensure_ascii=False)
    if isinstance(value, (int, float)) and not isinstance(value, bool):
        return canonical_number(value)
    if isinstance(value, list):
        return "[" + ",".join(canonical_json(item) for item in value) + "]"
    if isinstance(value, dict):
        return "{" + ",".join(json.dumps(k) + ":" + canonical_json(v) for k, v in value.items()) + "}"
    raise TypeError(type(value))


def canonical_entry(item: dict) -> str:
    location_value = None
    if item.get("location"):
        loc = item["location"]
        location_value = {
            "latitude": loc["latitude"],
            "longitude": loc["longitude"],
            "horizontalAccuracy": loc["horizontalAccuracy"],
            "capturedAt": loc["capturedAt"],
            "kind": loc["kind"],
        }
    payload = {
        "id": item["id"],
        "createdAt": item["createdAt"],
        "updatedAt": item["updatedAt"],
        "timeZoneID": item["timeZoneID"],
        "mood": item["mood"],
        "tags": item["tags"],
        "comment": item.get("comment"),
        "location": location_value,
        "source": item["source"],
    }
    return canonical_json(payload)


def canonical_place(item: dict) -> str:
    payload = {
        "id": item["id"],
        "name": item["name"],
        "kind": item["kind"],
        "anchorLatitude": item["anchorLatitude"],
        "anchorLongitude": item["anchorLongitude"],
        "createdAt": item["createdAt"],
    }
    return canonical_json(payload)


def noise_guard_report():
    config = load(SPEC / "content" / "pattern-config.json")
    library = load(SPEC / "content" / "pattern-library.en.json")
    rng = random.Random(20261010)
    now = parse_instant(rome(2026, 10, 18, 19, 0))
    end = now
    total = 0
    for _week in range(100):
        entries = []
        for index in range(25):
            mood = rng.randint(1, 3)
            day_offset = rng.randint(0, 6)
            hour = rng.randint(0, 23)
            minute = rng.randint(0, 59)
            created = end - timedelta(days=day_offset, hours=(end.astimezone(zone_of("Europe/Rome")).hour - hour) % 24)
            # Build a local time on the date `day_offset` days before the card, at hour:minute.
            local_end = end.astimezone(zone_of("Europe/Rome"))
            day = local_end.date() - timedelta(days=day_offset)
            created_dt = datetime(day.year, day.month, day.day, hour, minute, tzinfo=zone_of("Europe/Rome"))
            if created_dt >= local_end:
                created_dt = created_dt - timedelta(days=7)
            entries.append(
                entry(f"n{_week:03d}-{index:02d}", format_instant(created_dt), mood)
            )
        card = build_weekly_card(entries, [], config, library, now, "Europe/Rome", 7, 19, 0)
        by_id = {c["id"]: c for c in card["candidates"]}
        total += sum(1 for item in card["items"] if by_id[item["patternID"]]["confidence"] == "pattern")
    average = total / 100
    print(f"noise-guard reference average pattern items/card = {average:.3f}")
    if average > 0.3:
        print("WARNING: noise guard is above 0.3 with the reference RNG. Review pattern-config.json.")
    return average


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    if args.check:
        import tempfile
        import filecmp
        import shutil

        live = SPEC / "fixtures"
        with tempfile.TemporaryDirectory() as tmp:
            backup = Path(tmp) / "fixtures"
            shutil.copytree(live, backup)
            try:
                generate()
                mismatch = []
                for path in sorted(live.rglob("*")):
                    if path.is_dir():
                        continue
                    other = backup / path.relative_to(live)
                    if not other.exists() or path.read_bytes() != other.read_bytes():
                        mismatch.append(str(path.relative_to(ROOT)))
                for path in sorted(backup.rglob("*")):
                    if path.is_dir():
                        continue
                    if not (live / path.relative_to(backup)).exists():
                        mismatch.append("missing " + str(path.relative_to(backup)))
            finally:
                shutil.rmtree(live)
                shutil.copytree(backup, live)
        if mismatch:
            print("fixture drift:")
            for item in mismatch:
                print(" ", item)
            return 1
        print("fixtures match the generator")
        return 0
    generate()
    return 0


def generate():
    scenario_afternoon()
    scenario_sunday()
    scenario_tired()
    scenario_stressed()
    scenario_out()
    scenario_low_week()
    scenario_not_enough()
    scenario_first_week()
    scenario_dst()
    scenario_travel()
    scenario_noise()
    write_places()
    write_periods()
    write_low_mood()
    write_build_gate()
    write_inbox()
    write_coverage()
    write_canonical()
    noise_guard_report()
    print("fixtures generated")


if __name__ == "__main__":
    sys.exit(main())
