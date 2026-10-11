#!/usr/bin/env python3
"""Writes SmileProc's shared test fixtures (docs/spec/data.md › Fixtures).

Standard library only, fixed seeds: two runs write byte-identical files.
  generate_fixtures.py           write fixtures/
  generate_fixtures.py --check   regenerate in memory; exit 1 if fixtures/ differs

How the expectations are made
- Each scenario is built so its signal is overwhelming, and every expect.json value
  follows by hand from docs/spec/logic.md. The "notes" list in each expect file shows
  that hand check; tests ignore "notes".
- Scenarios stay clear of spec ambiguities: no 14-day boundary cases, no competing
  `higher` pattern where the balance rule could swap a pick, no tag detector that
  depends on which window is used (all tagged entries sit in the card week).
- Thresholds are the ones in content/pattern-config.json; the numbers in the notes use
  them. If the config changes, re-check the notes by hand.
- The generator only builds entries. It doesn't run the pattern engine; the asserts
  below only count entries, to catch typos in the scenario descriptions.
- Goldens (*.golden.json) are written by the :core tests in M2 (WRITE_GOLDENS=1),
  never here. export/vector-1.json is written in M9.

Time zones: Python's zoneinfo has no database on Windows without the tzdata package,
so the two zones the fixtures use carry their 2026 rules here (Europe/Rome: CET/CEST,
change 29 Mar and 25 Oct at 01:00 UTC; America/New_York: EST/EDT, change 8 Mar 07:00
and 1 Nov 06:00 UTC). Instants outside 2026-01-01..2027-02-28 are refused.
"""
import json
import math
import random
import sys
import uuid
import zlib
from datetime import date, datetime, timedelta, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FIXTURES = ROOT / "fixtures"
CONFIG = json.loads((ROOT / "content" / "pattern-config.json").read_text(encoding="utf-8"))

UTC = timezone.utc
ROME = "Europe/Rome"
NEW_YORK = "America/New_York"
SUNDAY_1900 = {"weekday": 7, "hour": 19, "minute": 0}

# zone -> (DST start, DST end, standard offset min, DST offset min)
_ZONES = {
    ROME: (datetime(2026, 3, 29, 1, tzinfo=UTC), datetime(2026, 10, 25, 1, tzinfo=UTC), 60, 120),
    NEW_YORK: (datetime(2026, 3, 8, 7, tzinfo=UTC), datetime(2026, 11, 1, 6, tzinfo=UTC), -300, -240),
}

# Places (lat, lon). Each pair is more than 1.5 km apart.
MILAN_HOME = (45.4642, 9.1900)
MILAN_WORK = (45.4850, 9.2050)
MILAN_A = (45.4500, 9.1650)
MILAN_B = (45.4800, 9.1600)
NEW_YORK_HOTEL = (40.7128, -74.0060)

EARTH_RADIUS_M = 6371000.0


# ---------------------------------------------------------------- time

def utc_offset(zone, instant):
    if not datetime(2026, 1, 1, tzinfo=UTC) <= instant < datetime(2027, 3, 1, tzinfo=UTC):
        raise ValueError(f"{instant} is outside the years this generator knows")
    start, end, standard, dst = _ZONES[zone]
    return timedelta(minutes=dst if start <= instant < end else standard)


def at(zone, y, mo, d, h, mi=0, s=0, ms=0):
    """Wall-clock time in `zone` -> UTC instant. Refuses times in a DST gap or overlap."""
    wall = datetime(y, mo, d, h, mi, s, ms * 1000, tzinfo=UTC)
    _, _, standard, dst = _ZONES[zone]
    hits = [wall - timedelta(minutes=m) for m in (standard, dst)
            if utc_offset(zone, wall - timedelta(minutes=m)) == timedelta(minutes=m)]
    if len(hits) != 1:
        raise ValueError(f"{wall:%Y-%m-%d %H:%M} is not a unique local time in {zone}")
    return hits[0]


def local(zone, instant):
    return (instant + utc_offset(zone, instant)).replace(tzinfo=None)


def iso(instant):
    return instant.strftime("%Y-%m-%dT%H:%M:%S.") + f"{instant.microsecond // 1000:03d}Z"


def daypart(zone, instant):
    h = local(zone, instant).hour
    if 5 <= h < 12:
        return "morning"
    if 12 <= h < 17:
        return "afternoon"
    if 17 <= h < 22:
        return "evening"
    return "night"


def card_period(zone, now, card_time):
    """(periodStart, periodEnd) per logic.md › Card period. Used only for count checks."""
    now_local = local(zone, now)
    back = (now_local.isoweekday() - card_time["weekday"]) % 7
    day = now_local.date() - timedelta(days=back)
    end = at(zone, day.year, day.month, day.day, card_time["hour"], card_time["minute"])
    if end > now:
        day -= timedelta(days=7)
        end = at(zone, day.year, day.month, day.day, card_time["hour"], card_time["minute"])
    start_day = day - timedelta(days=7)
    start = at(zone, start_day.year, start_day.month, start_day.day, card_time["hour"], card_time["minute"])
    return start, end


# ---------------------------------------------------------------- scenarios

def offset_point(point, metres, bearing_deg):
    lat, lon = point
    b = math.radians(bearing_deg)
    dlat = metres * math.cos(b) / EARTH_RADIUS_M
    dlon = metres * math.sin(b) / (EARTH_RADIUS_M * math.cos(math.radians(lat)))
    return lat + math.degrees(dlat), lon + math.degrees(dlon)


class Scenario:
    def __init__(self, name, description):
        self.name = name
        self.description = description
        self.rng = random.Random(zlib.crc32(name.encode("utf-8")))
        self.entries = []
        self.labels = []
        self.ids = {}

    def new_id(self):
        return str(uuid.UUID(int=self.rng.getrandbits(128), version=4))

    def label(self, handle, name, kind, point, created):
        label_id = self.new_id()
        self.ids[handle] = label_id
        self.labels.append({
            "id": label_id, "name": name, "kind": kind,
            "anchorLatitude": point[0], "anchorLongitude": point[1],
            "createdAt": iso(created),
        })
        return label_id

    def add(self, created, zone, mood, place=None, tags=(), handle=None,
            jitter_m=10.0, accuracy=None, exact=False):
        entry_id = self.new_id()
        if handle:
            self.ids[handle] = entry_id
        source = "notification" if self.rng.random() < 0.5 else "app"
        location = None
        if place is not None:
            lat, lon = place
            if not exact:
                lat, lon = offset_point(place, jitter_m * math.sqrt(self.rng.random()), self.rng.uniform(0, 360))
            acc = accuracy if accuracy is not None else self.rng.randint(8, 25)
            if source == "notification":
                kind, captured = "cached", created - timedelta(minutes=4)
            else:
                kind, captured = "fresh", created + timedelta(seconds=3)
            location = {
                "latitude": round(lat, 6), "longitude": round(lon, 6),
                "horizontalAccuracy": float(acc),
                "capturedAt": iso(captured), "kind": kind,
            }
        updated = created + timedelta(seconds=70) if tags else created
        self.entries.append({
            "id": entry_id,
            "createdAt": iso(created),
            "updatedAt": iso(updated),
            "timeZoneID": zone,
            "mood": mood,
            "tags": list(tags),
            "comment": None,
            "location": location,
            "source": source,
        })
        return entry_id

    def sorted_entries(self):
        return sorted(self.entries, key=lambda e: (e["createdAt"], e["id"]))


def parse(ts):
    return datetime.strptime(ts, "%Y-%m-%dT%H:%M:%S.%fZ").replace(tzinfo=UTC)


def period_counts(scn, zone, now, card_time=SUNDAY_1900):
    start, end = card_period(zone, now, card_time)
    inside = [e for e in scn.entries if start <= parse(e["createdAt"]) < end]
    return len(inside), sum(1 for e in inside if e["mood"] == 1)


def pattern_files(scn, now, zone, expect, card_time=SUNDAY_1900):
    assert all(parse(e["createdAt"]) < now for e in scn.entries), f"{scn.name}: entry after now"
    data = {
        "scenario": scn.name,
        "description": scn.description,
        "now": iso(now),
        "timeZoneID": zone,
        "cardTime": card_time,
        "labels": scn.labels,
        "entries": scn.sorted_entries(),
    }
    return {
        f"patterns/{scn.name}.input.json": data,
        f"patterns/{scn.name}.expect.json": expect,
    }


# Standard card for most scenarios: now = Sun 18 Oct 2026 20:00 in Rome.
# periodEnd = Sun 18 Oct 19:00 CEST (17:00Z), periodStart = Sun 11 Oct 19:00 CEST.
# Weekday window = 28 days = [Sun 20 Sep 19:00, Sun 18 Oct 19:00).
NOW_OCT18 = at(ROME, 2026, 10, 18, 20, 0)


def rome(d, h, mi=0):
    """Wall time in Rome, October 2026 unless `d` is a (month, day) tuple."""
    month, day = (10, d) if isinstance(d, int) else d
    return at(ROME, 2026, month, day, h, mi)


def add_history(scn, days, place=MILAN_HOME):
    """Okay entries well before the 28-day weekday window, so the first entry ever is
    more than 14 days before periodEnd (normal variant, weekday detectors on) without
    adding anything to the card or weekday windows."""
    for d in days:
        scn.add(rome((9, d), 10, 0), ROME, 2, place)


def afternoon_dip_week(scn, place=MILAN_HOME):
    """The card week of afternoon-dip: 7 Low afternoons (Mon 12 - Sun 18 at 14:30) and
    7 Good entries, one per day, alternating morning 09:30 / evening 20:00
    (4 mornings: Mon, Wed, Fri, Sun; 3 evenings: Tue, Thu, Sat)."""
    for d in range(12, 19):
        scn.add(rome(d, 14, 30), ROME, 1, place, handle=f"low{d}")
        if d in (12, 14, 16, 18):
            scn.add(rome(d, 9, 30), ROME, 3, place, handle=f"good{d}")
        else:
            scn.add(rome(d, 20, 0), ROME, 3, place, handle=f"good{d}")


AFTERNOON_NOTES = [
    "Card window [Sun 11 Oct 19:00, Sun 18 Oct 19:00) Rome: 14 entries (>= shortWeekThreshold 10, week window).",
    "afternoon: nSeg 7 all Low; rest 7 all Good. diff = 1 - 3 = -2; both variances 0 -> sdFloor 0.3;"
    " z = -2 / (0.3 * sqrt(1/7 + 1/7)) = -12.5. nSeg, nRest >= 5 -> pattern. score = 2 * 0.7 + 0.2 = 1.6.",
    "morning: nSeg 4 Good vs rest 10 (7 Low, 3 Good): diff 1.4, z = 2.83 -> earlyHint only (nSeg 4 < patternMinSegment 5).",
    "evening: nSeg 3 Good vs rest 11 (7 Low, 4 Good): diff 1.27, z = 2.12 -> earlyHint only.",
    "No competing higher *pattern*, so the balance rule doesn't apply. The hints are family time, already used.",
    "Weekday detectors: the 28-day window holds only the card week; every weekday has 1 Low + 1 Good -> mean 2, diff 0."
    " weekdayDaypart segments have 1 entry each.",
    "Library: afternoon-dip (detector, direction, segment = 3 keys) beats time-lower (2 keys).",
    "Low share 7/14 = 0.5 < lowWeekShare 0.6 -> not a low week.",
]


def build_patterns():
    files = {}

    # afternoon-dip ---------------------------------------------------------
    scn = Scenario("afternoon-dip", "Afternoons are Low every day of the card week; all entries at one place.")
    add_history(scn, [1, 8])
    afternoon_dip_week(scn)
    assert period_counts(scn, ROME, NOW_OCT18) == (14, 7)
    files |= pattern_files(scn, NOW_OCT18, ROME, {
        "variant": "normal",
        "isLowWeek": False,
        "maxItemCount": 1,
        "firstItemLibraryID": "afternoon-dip",
        "itemsIncludeLibraryIDs": ["afternoon-dip"],
        "candidatesInclude": [
            {"id": "timeOfDay.lower.afternoon", "confidence": "pattern"},
            {"id": "timeOfDay.higher.morning", "confidence": "earlyHint"},
            {"id": "timeOfDay.higher.evening", "confidence": "earlyHint"},
        ],
        "dayparts": {scn.ids["low12"]: "afternoon", scn.ids["good12"]: "morning", scn.ids["good13"]: "evening"},
        "notes": AFTERNOON_NOTES + [
            "First entry 1 Sep (47 days before periodEnd) -> normal variant.",
            "All located entries share one cluster: place detectors have no rest group (nRest 0).",
            "Items: one per family -> only afternoon-dip.",
        ],
    })

    # no-location -----------------------------------------------------------
    scn = Scenario("no-location", "afternoon-dip without any location: every other detector still counts every entry.")
    add_history(scn, [1, 8], place=None)
    afternoon_dip_week(scn, place=None)
    assert period_counts(scn, ROME, NOW_OCT18) == (14, 7)
    files |= pattern_files(scn, NOW_OCT18, ROME, {
        "variant": "normal",
        "isLowWeek": False,
        "maxItemCount": 1,
        "firstItemLibraryID": "afternoon-dip",
        "itemsIncludeLibraryIDs": ["afternoon-dip"],
        "candidatesInclude": [{"id": "timeOfDay.lower.afternoon", "confidence": "pattern"}],
        "notes": AFTERNOON_NOTES + [
            "Every entry has location null: no clusters, no place candidates, nothing else changes.",
        ],
    })

    # first-week ------------------------------------------------------------
    scn = Scenario("first-week", "afternoon-dip, but the first entry ever is 9 days before periodEnd.")
    scn.add(rome(9, 10, 0), ROME, 3, MILAN_HOME)
    scn.add(rome(10, 10, 0), ROME, 3, MILAN_HOME)
    afternoon_dip_week(scn)
    assert period_counts(scn, ROME, NOW_OCT18) == (14, 7)
    files |= pattern_files(scn, NOW_OCT18, ROME, {
        "variant": "firstWeek",
        "isLowWeek": False,
        "maxItemCount": 1,
        "firstItemLibraryID": "afternoon-dip",
        "candidatesInclude": [{"id": "timeOfDay.lower.afternoon", "confidence": "pattern"}],
        "notes": AFTERNOON_NOTES[:5] + [
            "First entry Fri 9 Oct 10:00, 9 days before periodEnd (< firstWeekDays 14) and"
            " period entries 14 >= notEnoughDataBelow 5 -> firstWeek, max 1 item.",
            "Weekday detectors are skipped (first entry < weekdayMinDataDays 14 before periodEnd).",
        ],
    })

    # sunday-evening --------------------------------------------------------
    scn = Scenario("sunday-evening", "28 days: Sunday evenings are Low, everything else is Okay.")
    start = date(2026, 9, 20)
    for i in range(29):
        d = start + timedelta(days=i)
        sunday = d.isoweekday() == 7

        def wall(h, mi=0, d=d):
            return at(ROME, d.year, d.month, d.day, h, mi)

        if d == date(2026, 9, 20):
            scn.add(wall(20), ROME, 1, MILAN_HOME, handle="firstSundayEvening")
        elif d == date(2026, 10, 18):
            scn.add(wall(10), ROME, 2, MILAN_HOME)
            scn.add(wall(15), ROME, 2, MILAN_HOME)
        elif sunday:
            scn.add(wall(10), ROME, 2, MILAN_HOME)
            scn.add(wall(15), ROME, 2, MILAN_HOME)
            scn.add(wall(20), ROME, 1, MILAN_HOME)
        else:
            scn.add(wall(9), ROME, 2, MILAN_HOME)
            scn.add(wall(20), ROME, 2, MILAN_HOME)
    assert len(scn.entries) == 60
    assert period_counts(scn, ROME, NOW_OCT18) == (15, 1)
    files |= pattern_files(scn, NOW_OCT18, ROME, {
        "variant": "normal",
        "isLowWeek": False,
        "maxItemCount": 1,
        "firstItemLibraryID": "sunday-evening",
        "itemsIncludeLibraryIDs": ["sunday-evening"],
        "candidatesInclude": [{"id": "weekdayDaypart.lower.7.evening", "confidence": "pattern"}],
        "dayparts": {scn.ids["firstSundayEvening"]: "evening"},
        "notes": [
            "Weekday window [Sun 20 Sep 19:00, Sun 18 Oct 19:00): 60 entries. Sunday evenings 20 Sep, 27 Sep,"
            " 4 Oct, 11 Oct are Low (4); every other entry is Okay.",
            "weekdayDaypart Sunday x evening: nSeg 4 (= weekdayMinSegment), 4 distinct dates, rest 56 all Okay."
            " diff -1 (>= weekdayMinAbsDiff 0.5); variances 0 -> sdFloor; z = -1 / (0.3 * sqrt(1/4 + 1/56)) = -6.4 -> pattern."
            " score = 1 * 0.4 + 0.2 = 0.6.",
            "weekday Sunday: 12 entries (4 Low, 8 Okay), mean 1.67 vs rest 2.0 -> diff -0.33 < 0.5, no candidate.",
            "Card window: 15 entries; evenings mean 1.86 vs rest 2.0 -> diff -0.14 < minAbsDiff 0.4, no time candidate.",
            "First entry 20 Sep, 28 days before periodEnd -> normal, weekday detectors on.",
            "Library: sunday-evening (detector, direction, weekday, segment) beats weekday-segment-lower.",
            "Low share 1/15 -> not a low week.",
        ],
    })

    # tired-on-low ----------------------------------------------------------
    scn = Scenario("tired-on-low", "Most Low check-ins in the card week are tagged with a tired feeling.")
    add_history(scn, [3, 10], place=None)
    low_tags = {12: ["tired.drained"], 13: ["tired.sleepy"], 14: ["tired"], 15: ["tired.drained"],
                16: ["tired.sleepy"], 17: ["sad.down"]}
    good_tags = {12: ["happy.grateful"], 13: ["calm.relaxed"], 14: ["happy.proud"], 15: ["calm.content"],
                 16: ["happy.grateful"], 17: ["calm.peaceful"]}
    for d in range(12, 18):
        low_morning = d in (12, 14, 16)
        scn.add(rome(d, 9, 0) if low_morning else rome(d, 20, 0), ROME, 1, None, tags=low_tags[d])
        scn.add(rome(d, 20, 0) if low_morning else rome(d, 9, 0), ROME, 3, None, tags=good_tags[d])
    scn.add(rome(18, 9, 0), ROME, 2, None)
    assert period_counts(scn, ROME, NOW_OCT18) == (13, 6)
    files |= pattern_files(scn, NOW_OCT18, ROME, {
        "variant": "normal",
        "isLowWeek": False,
        "maxItemCount": 1,
        "firstItemLibraryID": "tired-on-low",
        "itemsIncludeLibraryIDs": ["tired-on-low"],
        "candidatesInclude": [{"id": "tagOnLow.tired", "confidence": "pattern"}],
        "notes": [
            "Card week Mon 12 - Sat 17: each day one Low and one Good, swapping morning 09:00 / evening 20:00;"
            " plus an untagged Okay on Sun 18 at 09:00. 13 entries.",
            "Low tagged entries: 6 (>= tagMinLowTagged 4). Core tired on 5 of them (drained x2, sleepy x2, tired x1):"
            " share 0.83 >= 0.5, count 5 >= tagMinCount 3. Among Okay/Good tagged entries (6) tired never appears:"
            " share 0, gap 0.83 >= 0.25. count 5 >= tagPatternCount 5 -> pattern.",
            "No fine tag reaches fineTagDominance 0.7 of the tired hits (2/5 max) -> reported as core 'tired'.",
            "sad appears once among Low (< tagMinCount) -> no candidate.",
            "Mornings and evenings each hold 3 Low + 3 Good (+1 Okay morning) -> means 2.0 / 2.0, no time candidate.",
            "Every weekday has 1 Low + 1 Good -> no weekday candidate. No location -> no place or tagAtPlace candidate.",
            "All tagged entries are in the card week, so the result doesn't depend on which window tag detectors read.",
            "Library: tired-on-low (detector, tag) beats tag-on-low (detector).",
            "Low share 6/13 = 0.46 -> not a low week.",
        ],
    })

    # stressed-at-work ------------------------------------------------------
    scn = Scenario("stressed-at-work", "Stress shows up at the place labelled Work; every mood is Okay.")
    work_id = scn.label("work", "Office", "work", MILAN_WORK, at(ROME, 2026, 9, 1, 12, 0))
    add_history(scn, [2, 9])
    work_tags = ["anxious.stressed", "anxious.stressed", "happy.proud", "anxious.stressed", "calm.focused",
                 "anxious.stressed", "anxious.stressed", "happy.proud", "anxious.stressed", "calm.focused"]
    i = 0
    for d in range(12, 17):
        for h in (11, 15):
            scn.add(rome(d, h), ROME, 2, MILAN_WORK, tags=[work_tags[i]])
            i += 1
    home_tags = ["calm.relaxed", "happy.grateful", "calm.relaxed", "surprised.curious", "anxious.worried",
                 "happy.grateful", "tired.sleepy"]
    for d, tag in zip(range(12, 19), home_tags):
        scn.add(rome(d, 20) if d != 18 else rome(d, 17, 30), ROME, 2, MILAN_HOME, tags=[tag])
    assert period_counts(scn, ROME, NOW_OCT18) == (17, 0)
    files |= pattern_files(scn, NOW_OCT18, ROME, {
        "variant": "normal",
        "isLowWeek": False,
        "maxItemCount": 1,
        "firstItemLibraryID": "stress-at-place",
        "itemsIncludeLibraryIDs": ["stress-at-place"],
        "candidatesInclude": [{"id": f"tagAtPlace.anxious.stressed.p:{work_id}", "confidence": "pattern"}],
        "notes": [
            "Work label 'Office' (kind work) anchored at the work point; work entries are within 10 m -> cluster p:<label id>.",
            "Card week: 10 work entries (Mon-Fri 11:00 and 15:00), 7 home entries (evenings), all Okay -> every mean-comparison"
            " diff is 0, no mean candidates, no Low entries.",
            "At work: anxious on 6 of 10 tagged entries (all anxious.stressed): count 6 >= 3, share 0.6 >= tagAtPlaceMinShare 0.4."
            " Elsewhere: anxious 1/7 = 0.14 -> gap 0.46 >= 0.25. count 6 >= tagPatternCount 5 -> pattern.",
            "All 6 anxious hits are anxious.stressed (100% >= fineTagDominance 0.7) -> reported as the fine tag.",
            "Every other core stays below tagMinCount 3 at each place (work: happy 2, calm 2; home: calm 2, happy 2,"
            " anxious, surprised, tired 1 each) -> no other tag candidate.",
            "Library: stress-at-place (detector, tag = anxious.stressed) beats tag-at-place (detector).",
        ],
    })

    # out-lifts -------------------------------------------------------------
    scn = Scenario("out-lifts", "Check-ins away from the place labelled Home are Good; at home they're Okay.")
    home_id = scn.label("home", "Home", "home", MILAN_HOME, at(ROME, 2026, 9, 1, 12, 0))
    add_history(scn, [4, 11])
    grid = {  # day -> (morning, afternoon, evening); A and B are unnamed places, H is Home
        12: "AHB", 13: "BHH", 14: "AHH", 15: "BAH", 16: "HBA", 17: "HAB",
    }
    points = {"A": MILAN_A, "B": MILAN_B, "H": MILAN_HOME}
    for d, row in grid.items():
        for (h, mi), where in zip(((9, 30), (14, 30), (20, 0)), row):
            scn.add(rome(d, h, mi), ROME, 2 if where == "H" else 3, points[where])
    assert period_counts(scn, ROME, NOW_OCT18) == (18, 0)
    files |= pattern_files(scn, NOW_OCT18, ROME, {
        "variant": "normal",
        "isLowWeek": False,
        "maxItemCount": 1,
        "firstItemLibraryID": "out-lifts",
        "itemsIncludeLibraryIDs": ["out-lifts"],
        "candidatesInclude": [
            {"id": "awayFromHome.higher", "confidence": "pattern"},
            {"id": f"place.lower.p:{home_id}", "confidence": "pattern"},
        ],
        "notes": [
            "Card week Mon 12 - Sat 17, 3 entries a day: 10 away (5 at A, 5 at B, all Good), 8 at Home (all Okay).",
            "awayFromHome: seg = 10 away, rest = 8 at Home. diff +1, variances 0 -> sdFloor;"
            " z = 1 / (0.3 * sqrt(1/10 + 1/8)) = 7.0 -> pattern. score = 1 * 1.0 + 0.2 = 1.2.",
            "place.lower.p:<home>: seg 8 vs rest 10 -> diff -1, pattern, score 1 * 0.8 + 0.2 = 1.0 (same family, lower score).",
            "place.higher at A (or B): seg 5 Good vs rest 13 (mean 2.38) -> diff 0.62, z = 2.67 -> pattern, score 0.51.",
            "Time of day: mornings 4 away + 2 home (mean 2.67), afternoons and evenings 3 + 3 (2.5) -> |diff| 0.17 < 0.4.",
            "Weekdays: each day has 3 entries (nSeg 3 < weekdayMinSegment 4) and |diff| <= 0.27 -> nothing.",
            "Items: one per family -> only out-lifts. It is `higher`, so the balance rule changes nothing.",
            "Library: out-lifts (awayFromHome, higher) beats home-higher (awayFromHome, lower), which doesn't match.",
        ],
    })

    # low-week --------------------------------------------------------------
    scn = Scenario("low-week", "7 of 10 check-ins in the card week are Low; patterns in three families compete for two slots.")
    add_history(scn, [1])
    morning_tags = ["tired.drained", "tired.sleepy", "tired", "tired.drained", "tired.sleepy"]
    for d, tag in zip(range(12, 17), morning_tags):  # mornings at place A: all Low, tagged tired
        scn.add(rome(d, 9), ROME, 1, MILAN_A, tags=[tag], handle=f"morning{d}")
    evenings = [(1, []), (2, ["calm.relaxed"]), (1, []), (2, ["calm.content"]), (2, ["calm.peaceful"])]
    for d, (mood, tags) in zip(range(12, 17), evenings):  # evenings at place B: 2 Low, 3 Okay
        scn.add(rome(d, 20), ROME, mood, MILAN_B, tags=tags, handle=f"evening{d}")
    assert period_counts(scn, ROME, NOW_OCT18) == (10, 7)
    files |= pattern_files(scn, NOW_OCT18, ROME, {
        "variant": "normal",
        "isLowWeek": True,
        "maxItemCount": CONFIG["maxItemsLowWeek"],
        "firstItemLibraryID": "tag-at-place",
        "itemsIncludeLibraryIDs": ["tag-at-place", "place-higher"],
        "candidatesInclude": [
            {"id": f"tagAtPlace.tired.u:{scn.ids['morning12']}", "confidence": "pattern"},
            {"id": "tagOnLow.tired", "confidence": "pattern"},
            {"id": f"place.higher.u:{scn.ids['evening12']}", "confidence": "pattern"},
            {"id": "timeOfDay.lower.morning", "confidence": "pattern"},
        ],
        "notes": [
            "Card week Mon 12 - Fri 16: mornings 09:00 at place A (all Low, tagged tired: drained x2, sleepy x2, tired),"
            " evenings 20:00 at place B (Low, Okay, Low, Okay, Okay; the Okays tagged calm). 10 entries, 7 Low.",
            "Period entries 10 >= lowWeekMinEntries 4 and Low share 0.7 >= lowWeekShare 0.6 -> isLowWeek, limit maxItemsLowWeek 2.",
            "10 entries is not fewer than shortWeekThreshold 10 -> week window (the 14-day window holds the same 10 anyway).",
            "time and place split the entries the same way (A = mornings, B = evenings): seg 5 Low vs rest mean 1.6;"
            " diff -0.6, pooled sd 0.387, z = -2.45 -> patterns timeOfDay.lower.morning / timeOfDay.higher.evening"
            " and place.lower.u:<A> / place.higher.u:<B>, each score 0.6 * 0.5 + 0.2 = 0.5.",
            "tagOnLow.tired: 5 tagged Low entries, tired on all 5 (share 1.0, count 5); never on the 3 tagged Okays (gap 1.0)"
            " -> pattern, score 1.0 * 0.5 + 0.2 = 0.7. No fine tag reaches 0.7 of the hits -> core 'tired'.",
            "tagAtPlace.tired.u:<A>: tired on 5/5 tagged entries at A, 0/3 at B -> pattern, score 0.7.",
            "tagAtPlace.calm.u:<B>: 3/3 at B, 0/5 at A, count 3 < tagPatternCount -> earlyHint only.",
            "Order: score 0.7 tie -> id ascending: 'tagAtPlace...' < 'tagOnLow...'. Score 0.5 tie: 'place.higher' < 'place.lower'"
            " < 'timeOfDay.higher' < 'timeOfDay.lower'.",
            "Picks, one per family, limit 2: tagAtPlace.tired (tag), place.higher.u:<B> (place). It is `higher`, so no balance swap.",
            "Library: tag-at-place (stress-at-place needs anxious.stressed); place-higher.",
        ],
    })

    # not-enough-data -------------------------------------------------------
    scn = Scenario("not-enough-data", "Only 4 check-ins in the card week; the detectors read the 14 days before periodEnd.")
    add_history(scn, [1, 5])
    for d in (5, 7, 9, 12, 15):  # Low afternoons, 14:30
        scn.add(rome(d, 14, 30), ROME, 1, MILAN_HOME)
    for d in (6, 8, 10, 13, 16):  # Good mornings, 09:30
        scn.add(rome(d, 9, 30), ROME, 3, MILAN_HOME)
    assert period_counts(scn, ROME, NOW_OCT18) == (4, 2)
    files |= pattern_files(scn, NOW_OCT18, ROME, {
        "variant": "notEnoughData",
        "isLowWeek": False,
        "maxItemCount": 1,
        "firstItemLibraryID": "time-higher",
        "itemsIncludeLibraryIDs": ["time-higher"],
        "candidatesInclude": [
            {"id": "timeOfDay.higher.morning", "confidence": "pattern"},
            {"id": "timeOfDay.lower.afternoon", "confidence": "pattern"},
        ],
        "notes": [
            "Card week: 4 entries (Mon 12 and Thu 15 Low afternoons, Tue 13 and Fri 16 Good mornings).",
            "Period entries 4 < notEnoughDataBelow 5 -> notEnoughData, max 1 item (hints allowed).",
            "Low share 2/4 = 0.5 < 0.6 -> not a low week (4 >= lowWeekMinEntries, but the share is too small).",
            "4 < shortWeekThreshold 10 -> detectors read the 14 days [Sun 4 Oct 19:00, Sun 18 Oct 19:00): 10 entries,"
            " 5 Low afternoons and 5 Good mornings. In the card week alone each segment has 2 entries -> no candidate,"
            " so these candidates only exist with the two-week window.",
            "afternoon vs morning: diff -2 / +2, variances 0 -> sdFloor, |z| = 2 / (0.3 * sqrt(1/5 + 1/5)) = 10.5,"
            " nSeg = nRest = 5 -> both patterns, both score 2 * 0.5 + 0.2 = 1.2.",
            "Equal confidence and score -> id ascending: 'timeOfDay.higher.morning' < 'timeOfDay.lower.afternoon'."
            " The one slot goes to the morning pattern; limit 1 < 2, so the balance rule doesn't apply.",
            "Weekday window: every weekday segment has <= 2 entries -> no weekday candidate.",
            "Library: time-higher is the only timeOfDay + higher entry.",
        ],
    })

    # dst-week --------------------------------------------------------------
    now = at(ROME, 2026, 10, 25, 20, 0)  # Sun 25 Oct 20:00 CET, after the clocks went back
    scn = Scenario("dst-week", "Card week across the end of summer time in Rome (Sun 25 Oct 2026, 03:00 CEST -> 02:00 CET).")
    scn.add(at(ROME, 2026, 9, 10, 10), ROME, 2, MILAN_HOME)
    for d in range(18, 25):
        scn.add(rome(d, 20), ROME, 1, MILAN_HOME)
    scn.add(rome(19, 9, 30), ROME, 3, MILAN_HOME)
    scn.add(rome(20, 14, 30), ROME, 3, MILAN_HOME)
    scn.add(rome(21, 9, 30), ROME, 3, MILAN_HOME)
    scn.add(rome(22, 14, 30), ROME, 3, MILAN_HOME)
    scn.add(rome(23, 9, 30), ROME, 3, MILAN_HOME)
    oct24 = scn.add(rome(24, 12, 30), ROME, 3, MILAN_HOME)   # 10:30Z, CEST
    oct25m = scn.add(rome(25, 11, 30), ROME, 3, MILAN_HOME)  # 10:30Z, CET
    night = scn.add(rome(25, 4, 30), ROME, 2, MILAN_HOME)    # 03:30Z, CET
    late = scn.add(rome(25, 18, 30), ROME, 2, MILAN_HOME)    # 17:30Z, CET
    assert [daypart(ROME, parse(e["createdAt"])) for e in scn.entries if e["id"] in (oct24, oct25m, night, late)] == \
        ["afternoon", "morning", "night", "evening"]
    assert period_counts(scn, ROME, now) == (16, 7)
    files |= pattern_files(scn, now, ROME, {
        "variant": "normal",
        "isLowWeek": False,
        "maxItemCount": 1,
        "firstItemLibraryID": "time-lower",
        "itemsIncludeLibraryIDs": ["time-lower"],
        "candidatesInclude": [{"id": "timeOfDay.lower.evening", "confidence": "pattern"}],
        "dayparts": {oct24: "afternoon", oct25m: "morning", night: "night", late: "evening"},
        "notes": [
            "periodEnd = Sun 25 Oct 19:00 CET (18:00Z); periodStart = Sun 18 Oct 19:00 CEST (17:00Z): 7 calendar days = 169 h.",
            "Dayparts in the entry's own zone: 24 Oct 10:30Z = 12:30 CEST (afternoon); 25 Oct 10:30Z = 11:30 CET (morning);"
            " 25 Oct 03:30Z = 04:30 CET (night); 25 Oct 17:30Z = 18:30 CET (evening). A fixed +2 h offset would get the"
            " last three wrong (12:30, 05:30, 19:30 -> afternoon, morning, evening) or put 18:30 outside a 168 h window.",
            "Card window: 16 entries. Evenings: 7 Low (18-24 Oct, 20:00) + 1 Okay (25 Oct 18:30) -> mean 1.125;"
            " rest: 4 Good mornings, 3 Good afternoons, 1 Okay night -> mean 2.875. diff -1.75 -> pattern."
            " score = 1.75 * 0.8 + 0.2 = 1.6.",
            "Mornings (4) and afternoons (3) are higher but below patternMinSegment -> hints at most, family time already used.",
            "Every ISO weekday has mean 2.0: Mon-Sat one Low evening + one Good daytime entry; Sunday = 18 Oct Low evening"
            " + 25 Oct Okay night, Good morning, Okay evening -> no weekday candidate; weekday x daypart segments hold <= 2 entries.",
            "There is no evening-specific library entry -> time-lower ('Your {segment}s run lower').",
        ],
    })

    # travel ----------------------------------------------------------------
    scn = Scenario("travel", "afternoon-dip with Wed-Fri spent in New York: dayparts and dates come from each entry's own zone.")
    add_history(scn, [2])

    def ny(d, h, mi=0):
        return at(NEW_YORK, 2026, 10, d, h, mi)

    scn.add(rome(12, 9, 30), ROME, 3, MILAN_HOME)
    scn.add(rome(12, 14, 30), ROME, 1, MILAN_HOME)
    scn.add(rome(13, 14, 30), ROME, 1, MILAN_HOME)
    scn.add(rome(13, 20, 0), ROME, 3, MILAN_HOME)
    wed_low = scn.add(ny(14, 14, 30), NEW_YORK, 1, NEW_YORK_HOTEL)
    wed_eve = scn.add(ny(14, 20, 30), NEW_YORK, 3, NEW_YORK_HOTEL)
    thu_morn = scn.add(ny(15, 9, 0), NEW_YORK, 3, NEW_YORK_HOTEL)
    scn.add(ny(15, 14, 30), NEW_YORK, 1, NEW_YORK_HOTEL)
    scn.add(ny(16, 14, 30), NEW_YORK, 1, NEW_YORK_HOTEL)
    scn.add(ny(16, 20, 0), NEW_YORK, 3, NEW_YORK_HOTEL)
    scn.add(rome(17, 9, 30), ROME, 3, MILAN_HOME)
    scn.add(rome(17, 14, 30), ROME, 1, MILAN_HOME)
    scn.add(rome(18, 9, 30), ROME, 3, MILAN_HOME)
    scn.add(rome(18, 14, 30), ROME, 1, MILAN_HOME)
    assert period_counts(scn, ROME, NOW_OCT18) == (14, 7)
    files |= pattern_files(scn, NOW_OCT18, ROME, {
        "variant": "normal",
        "isLowWeek": False,
        "maxItemCount": 1,
        "firstItemLibraryID": "afternoon-dip",
        "itemsIncludeLibraryIDs": ["afternoon-dip"],
        "candidatesInclude": [{"id": "timeOfDay.lower.afternoon", "confidence": "pattern"}],
        "dayparts": {wed_low: "afternoon", wed_eve: "evening", thu_morn: "morning"},
        "notes": [
            "Wed 14 - Fri 16 entries carry timeZoneID America/New_York (EDT, UTC-4); the device zone is Europe/Rome.",
            "In their own zone: 14 Oct 14:30 EDT = afternoon; 14 Oct 20:30 EDT (15 Oct 00:30Z) = evening;"
            " 15 Oct 09:00 EDT (13:00Z) = morning. In Rome time they would be evening, night and afternoon.",
            "Same shape as afternoon-dip in local time: 7 Low afternoons, 4 Good mornings, 3 Good evenings -> same numbers.",
            "Every local date has 1 Low + 1 Good -> no weekday candidate (dates in each entry's own zone).",
            "Two clusters (Milan 8 entries, New York 6), both mean 2.0 -> no place candidate.",
        ],
    })

    # noise-1..3 -------------------------------------------------------------
    for n in (1, 2, 3):
        scn = Scenario(f"noise-{n}", "4 weeks of random check-ins (25 a week): moods, times, places and tags uniform at random.")
        cores = json.loads((ROOT / "content" / "emotion-tags.json").read_text(encoding="utf-8"))["cores"]
        week_start = date(2026, 9, 21)
        for week in range(4):
            for _ in range(25):
                d = week_start + timedelta(days=7 * week + scn.rng.randrange(7))
                last_hour = 19 if d.isoweekday() == 7 else 23  # Sundays stay before the card time
                h, mi = scn.rng.randrange(7, last_hour), scn.rng.randrange(0, 60, 5)
                created = at(ROME, d.year, d.month, d.day, h, mi)
                roll = scn.rng.random()
                place = MILAN_HOME if roll < 0.6 else MILAN_WORK if roll < 0.9 else None
                tags = []
                for _ in range(scn.rng.choice((0, 0, 1, 2))):
                    core = scn.rng.choice(cores)
                    tag = scn.rng.choice([core["id"]] + core["fine"])
                    if tag not in tags:
                        tags.append(tag)
                scn.add(created, ROME, scn.rng.randint(1, 3), place, tags=tags)
        count, lows = period_counts(scn, ROME, NOW_OCT18)
        low_week = count >= CONFIG["lowWeekMinEntries"] and lows / count >= CONFIG["lowWeekShare"]
        files |= pattern_files(scn, NOW_OCT18, ROME, {
            "variant": "normal",
            "isLowWeek": low_week,
            "maxItemCount": CONFIG["maxItemsLowWeek"] if low_week else CONFIG["maxItems"],
            "notes": [
                f"Seeded noise, entries between Mon 21 Sep and Sun 18 Oct. Card week: {count} entries, {lows} Low"
                f" -> share {lows / count:.2f} {'>=' if low_week else '<'} lowWeekShare 0.6.",
                "First entry is in the week of 21 Sep, more than 14 days before periodEnd -> normal variant.",
                "No pattern is designed in; the golden (M2) pins the exact candidate list.",
            ],
        })
    return files


# ---------------------------------------------------------------- places

def build_places():
    files = {}

    # three-places-noise ----------------------------------------------------
    scn = Scenario("three-places-noise", "Three places about 2-3 km apart, 8 check-ins each, scattered within 40 m of each centre.")
    groups = {"A": [], "B": [], "C": []}
    centres = {"A": MILAN_HOME, "B": MILAN_A, "C": MILAN_B}
    order = list("ABC" * 8)
    scn.rng.shuffle(order)
    t = at(ROME, 2026, 10, 5, 8, 0)
    for where in order:
        t += timedelta(minutes=scn.rng.randrange(180, 900))
        groups[where].append(scn.add(t, ROME, scn.rng.randint(1, 3), centres[where], jitter_m=40.0))
    files["places/three-places-noise.input.json"] = {
        "scenario": scn.name, "description": scn.description, "labels": [], "entries": scn.sorted_entries()}
    files["places/three-places-noise.expect.json"] = {
        "sameCluster": [groups["A"], groups["B"], groups["C"]],
        "differentCluster": [[groups["A"][0], groups["B"][0]], [groups["B"][0], groups["C"][0]],
                             [groups["A"][0], groups["C"][0]]],
        "unclustered": [],
        "notes": [
            "Every point is within 40 m of its centre, so every running centroid is too: any member is within 80 m"
            " (< placeRadiusMeters 100) of its group's centroid, and the centres are > 2 km apart.",
        ],
    }

    # accuracy-filter -------------------------------------------------------
    scn = Scenario("accuracy-filter", "One place; only fixes with horizontalAccuracy <= 150 m join a cluster.")
    t = at(ROME, 2026, 10, 5, 9, 0)
    ids = {}
    for handle, acc in (("acc10", 10), ("acc50", 50), ("acc150", 150), ("acc151", 151), ("acc400", 400)):
        t += timedelta(hours=5)
        ids[handle] = scn.add(t, ROME, 2, MILAN_HOME, accuracy=acc)
    t += timedelta(hours=5)
    ids["none"] = scn.add(t, ROME, 2, None)
    files["places/accuracy-filter.input.json"] = {
        "scenario": scn.name, "description": scn.description, "labels": [], "entries": scn.sorted_entries()}
    files["places/accuracy-filter.expect.json"] = {
        "sameCluster": [[ids["acc10"], ids["acc50"], ids["acc150"]]],
        "unclustered": [ids["acc151"], ids["acc400"], ids["none"]],
        "notes": [
            "placeMaxAccuracyMeters 150 is inclusive: 10, 50 and 150 m are eligible; 151 and 400 m are not;"
            " an entry without location never has a cluster.",
        ],
    }

    # label-capture / label-deleted ----------------------------------------
    def label_entries(scn):
        t = at(ROME, 2026, 10, 5, 9, 0)
        out = {}
        for handle, metres, bearing in (("centre", 0, 0), ("east90", 90, 90), ("west90", 90, 270),
                                        ("north130", 130, 0)):
            t += timedelta(hours=6)
            out[handle] = scn.add(t, ROME, 2, offset_point(MILAN_HOME, metres, bearing), exact=True)
        t += timedelta(hours=6)
        out["far"] = scn.add(t, ROME, 2, MILAN_A, exact=True)
        return out

    scn = Scenario("label-capture", "A Home label captures every eligible entry within 100 m of its anchor, even ones 180 m apart.")
    scn.label("home", "Home", "home", MILAN_HOME, at(ROME, 2026, 10, 1, 12, 0))
    ids = label_entries(scn)
    files["places/label-capture.input.json"] = {
        "scenario": scn.name, "description": scn.description, "labels": scn.labels, "entries": scn.sorted_entries()}
    files["places/label-capture.expect.json"] = {
        "sameCluster": [[ids["centre"], ids["east90"], ids["west90"]]],
        "differentCluster": [[ids["centre"], ids["north130"]], [ids["east90"], ids["north130"]],
                             [ids["centre"], ids["far"]], [ids["north130"], ids["far"]]],
        "unclustered": [],
        "notes": [
            "Named pass: centre (0 m), east90 and west90 (90 m) are within 100 m of the Home anchor -> one cluster p:<home id>,"
            " although east90 and west90 are 180 m apart.",
            "north130 is 130 m from the anchor -> unnamed pass, its own cluster. far is 2.5 km away -> its own cluster.",
        ],
    }

    scn = Scenario("label-deleted", "label-capture after the Home label was deleted: the same points fall back to unnamed clusters.")
    ids = label_entries(scn)
    files["places/label-deleted.input.json"] = {
        "scenario": scn.name, "description": scn.description, "labels": [], "entries": scn.sorted_entries()}
    files["places/label-deleted.expect.json"] = {
        "sameCluster": [[ids["centre"], ids["east90"]]],
        "differentCluster": [[ids["east90"], ids["west90"]], [ids["centre"], ids["west90"]],
                             [ids["centre"], ids["north130"]], [ids["centre"], ids["far"]]],
        "unclustered": [],
        "notes": [
            "Order by createdAt: centre, east90, west90, north130, far.",
            "centre starts u:centre. east90 is 90 m from its centroid -> joins; centroid moves 45 m east.",
            "west90 is 135 m from that centroid -> new cluster. north130 is 137 m and 158 m from the two centroids -> new cluster.",
            "Stabilise: centre is 45 m from its own centroid and 90 m from west90's -> stays. Nothing moves.",
        ],
    }

    # centroid-drift --------------------------------------------------------
    scn = Scenario("centroid-drift", "Points on a north-south line; the stabilise step moves one point to the cluster it is nearest to.")
    t = at(ROME, 2026, 10, 5, 9, 0)
    ids = {}
    for handle, metres in (("a", 0), ("b", 85), ("c", 140), ("d", 215), ("e", 185)):
        t += timedelta(hours=6)
        ids[handle] = scn.add(t, ROME, 2, offset_point(MILAN_HOME, metres, 0), exact=True)
    files["places/centroid-drift.input.json"] = {
        "scenario": scn.name, "description": scn.description, "labels": [], "entries": scn.sorted_entries()}
    files["places/centroid-drift.expect.json"] = {
        "sameCluster": [[ids["a"], ids["b"]], [ids["c"], ids["d"], ids["e"]]],
        "differentCluster": [[ids["b"], ids["c"]], [ids["a"], ids["c"]]],
        "unclustered": [],
        "notes": [
            "Positions are metres north of one point, in createdAt order: a 0, b 85, c 140, d 215, e 185.",
            "Unnamed pass: a starts u:a. b is 85 m from centroid 0 -> joins (centroid 42.5). c is 97.5 m away -> joins"
            " (centroid 75). d is 140 m away -> new cluster u:d. e is 110 m from u:a and 30 m from u:d -> joins u:d (centroid 200).",
            "Stabilise: c is 65 m from u:a's centroid (75) but 60 m from u:d's (200) -> c moves. Result {a, b} and {c, d, e};"
            " the second keeps the id of its earliest member, u:<c>.",
            "Without the stabilise step c would stay with a and b.",
        ],
    }
    return files


# ---------------------------------------------------------------- periods

def build_periods():
    rome, ny = ROME, NEW_YORK
    monday_0800 = {"weekday": 1, "hour": 8, "minute": 0}
    saturday_0930 = {"weekday": 6, "hour": 9, "minute": 30}
    # (name, now, zone, card time, periodStart, periodEnd) — expected values worked out by hand.
    cases = [
        ("midweek", "2026-10-14T08:00:00.000Z", rome, SUNDAY_1900, "2026-10-04T17:00:00.000Z", "2026-10-11T17:00:00.000Z"),
        ("exactly-at-card-time", "2026-10-18T17:00:00.000Z", rome, SUNDAY_1900, "2026-10-11T17:00:00.000Z", "2026-10-18T17:00:00.000Z"),
        ("one-ms-before-card-time", "2026-10-18T16:59:59.999Z", rome, SUNDAY_1900, "2026-10-04T17:00:00.000Z", "2026-10-11T17:00:00.000Z"),
        ("dst-autumn-card-evening", "2026-10-25T19:00:00.000Z", rome, SUNDAY_1900, "2026-10-18T17:00:00.000Z", "2026-10-25T18:00:00.000Z"),
        ("dst-autumn-midweek", "2026-10-28T12:00:00.000Z", rome, SUNDAY_1900, "2026-10-18T17:00:00.000Z", "2026-10-25T18:00:00.000Z"),
        ("dst-spring-card-evening", "2026-03-29T18:00:00.000Z", rome, SUNDAY_1900, "2026-03-22T18:00:00.000Z", "2026-03-29T17:00:00.000Z"),
        ("monday-morning-card-time", "2026-10-12T05:59:00.000Z", rome, monday_0800, "2026-09-28T06:00:00.000Z", "2026-10-05T06:00:00.000Z"),
        ("saturday-card-time-exact", "2026-10-17T07:30:00.000Z", rome, saturday_0930, "2026-10-10T07:30:00.000Z", "2026-10-17T07:30:00.000Z"),
        ("new-york-dst-end", "2026-11-02T01:00:00.000Z", ny, SUNDAY_1900, "2026-10-25T23:00:00.000Z", "2026-11-02T00:00:00.000Z"),
        ("year-boundary", "2027-01-01T11:00:00.000Z", rome, SUNDAY_1900, "2026-12-20T18:00:00.000Z", "2026-12-27T18:00:00.000Z"),
    ]
    inputs, expects = [], []
    for name, now, zone, card_time, start, end in cases:
        computed = card_period(zone, parse(now), card_time)
        assert (iso(computed[0]), iso(computed[1])) == (start, end), (name, computed)
        inputs.append({"name": name, "now": now, "timeZoneID": zone, "cardTime": card_time})
        expects.append({"name": name, "periodStart": start, "periodEnd": end})
    return {
        "periods/card-periods.input.json": {
            "description": "CardPeriod: periodEnd = latest card time <= now in the device zone;"
                           " periodStart = the same local time 7 calendar days earlier.",
            "cases": inputs,
        },
        "periods/card-periods.expect.json": {
            "cases": expects,
            "notes": [
                "dst-autumn: 18 Oct 19:00 CEST -> 25 Oct 19:00 CET is 169 h; dst-spring: 22 Mar 19:00 CET -> 29 Mar 19:00 CEST is 167 h.",
                "exactly-at-card-time: a card time equal to now counts (<= now).",
                "new-york-dst-end: 25 Oct 19:00 EDT (23:00Z) -> 1 Nov 19:00 EST (00:00Z on 2 Nov).",
            ],
        },
    }


# ---------------------------------------------------------------- output

def render(obj):
    return json.dumps(obj, indent=2, ensure_ascii=False) + "\n"


def generate():
    files = {}
    files |= build_patterns()
    files |= build_places()
    files |= build_periods()
    return {path: render(obj) for path, obj in sorted(files.items())}


def main(argv):
    check = "--check" in argv
    wanted = generate()
    managed = ("patterns", "places", "periods")
    existing = {
        p.relative_to(FIXTURES).as_posix()
        for d in managed if (FIXTURES / d).exists()
        for p in (FIXTURES / d).glob("*.json") if not p.name.endswith(".golden.json")
    }
    stale = sorted(existing - set(wanted))
    if check:
        problems = [f"missing: {p}" for p in wanted if p not in existing]
        problems += [f"differs: {p}" for p, text in wanted.items()
                     if p in existing and (FIXTURES / p).read_text(encoding="utf-8") != text]
        problems += [f"not generated: {p}" for p in stale]
        for line in problems:
            print(line, file=sys.stderr)
        if problems:
            print("fixtures are out of date: run tools/generate_fixtures.py", file=sys.stderr)
            return 1
        print(f"fixtures up to date ({len(wanted)} files)")
        return 0
    for path, text in wanted.items():
        target = FIXTURES / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text, encoding="utf-8", newline="\n")
    for path in stale:
        (FIXTURES / path).unlink()
    print(f"wrote {len(wanted)} files" + (f", removed {len(stale)} stale" if stale else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
