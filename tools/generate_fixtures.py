#!/usr/bin/env python3
"""Generate SmileProc fixtures from the plan's rules (Python 3 stdlib only).

The expectations are produced by this file's implementation of §6, with fixed
seeds. `python3 tools/generate_fixtures.py --check` regenerates into a temp
directory and fails if the committed fixtures differ.

M2 tests should treat every key in an expect file as exact:

- patterns: periodStart, periodEnd, windowKind, variant, isLowWeek,
  maxItemCount, entryCount, moodCounts, firstItemLibraryID,
  itemsIncludeLibraryIDs (the selected items, in order), candidatesInclude
  (every candidate, in deterministic order), dayparts (every entry).
- places: sameCluster (one sorted id list per cluster), differentCluster
  (one pair of representatives per cluster pair), unclustered, clusterOf.
- periods: the cases in periods.json.

Choices recorded for the Kotlin port (also in docs/progress.md):

- Score sort key uses Java Math.round semantics on score * 1e9:
  floor(score * 1e9 + 0.5) / 1e9. Scores themselves are not pre-rounded.
- Weekday and weekdayDaypart use weekdayMinSegment / weekdayMinAbsDiff /
  weekdayMinDistinctDates for a pattern, and minSegment / weekdayMinAbsDiff /
  weekdayMinDistinctDates for an early hint. nRest thresholds stay
  patternMinRest and minRest. z uses zPattern / zHint and sdFloor.
- "Less than weekdayMinDataDays before periodEnd" is strict: detectors run
  when the first entry is exactly that many calendar days earlier.
- "Within firstWeekDays" includes the boundary instant.
- Tag detectors and place detectors use the card window (the 7-day period,
  or 14 calendar days when the period is short). Weekday detectors use the
  28-day window.
- Place detectors ignore entries that did not join a cluster.
- Tag fine-tag dominance looks at entries in the segment that have the core.
  The most common fine tag wins a tie by id. Otherwise the id stays the core.
- Low-run "span" is the number of distinct local dates.
- windowKind is "week" or "twoWeeks".
- Balance replaces the last pick with the first higher-confidence pattern
  that was not already picked, even when families then collide.
"""

from __future__ import annotations

import argparse
import json
import math
import random
import tempfile
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path
from zoneinfo import ZoneInfo

ROOT = Path(__file__).resolve().parents[1]
CONTENT = ROOT / "content"
FIXTURES = ROOT / "fixtures"
NS = uuid.NAMESPACE_URL
EARTH_RADIUS_M = 6_371_000
CARD_WEEKDAY = 7
CARD_HOUR = 19
CARD_MINUTE = 0
CLINICAL = ("depression", "disorder", "symptom", "diagnosis", "treatment")


class Config(dict):
    def __init__(self, raw: dict):
        super().__init__(raw)
        self.used: set[str] = set()

    def __getitem__(self, key):
        self.used.add(key)
        return super().__getitem__(key)


def load_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def dump(obj) -> str:
    return json.dumps(obj, indent=2, ensure_ascii=False) + "\n"


def sid(name: str) -> str:
    return str(uuid.uuid5(NS, "smileproc:" + name))


def fmt(moment: datetime) -> str:
    utc = moment.astimezone(timezone.utc)
    millis = utc.microsecond // 1000
    return utc.strftime("%Y-%m-%dT%H:%M:%S.") + f"{millis:03d}Z"


def parse_iso(text: str) -> datetime:
    return datetime.strptime(text, "%Y-%m-%dT%H:%M:%S.%fZ").replace(tzinfo=timezone.utc)


def local_at(zone_id: str, year: int, month: int, day: int, hour: int, minute: int) -> datetime:
    return datetime(year, month, day, hour, minute, tzinfo=ZoneInfo(zone_id))


def shift_calendar_days(moment: datetime, days: int, zone: ZoneInfo) -> datetime:
    local = moment.astimezone(zone).replace(tzinfo=None)
    shifted = local - timedelta(days=days)
    return shifted.replace(tzinfo=zone)


def card_period(now: datetime, zone: ZoneInfo, weekday: int, hour: int, minute: int):
    local = now.astimezone(zone)
    days_back = (local.isoweekday() - weekday) % 7
    end_date = local.date() - timedelta(days=days_back)
    end_local = datetime(end_date.year, end_date.month, end_date.day, hour, minute, tzinfo=zone)
    if end_local > local:
        end_date = end_date - timedelta(days=7)
        end_local = datetime(end_date.year, end_date.month, end_date.day, hour, minute, tzinfo=zone)
    start_date = end_date - timedelta(days=7)
    start_local = datetime(start_date.year, start_date.month, start_date.day, hour, minute, tzinfo=zone)
    return start_local, end_local


def haversine(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    d_phi = math.radians(lat2 - lat1)
    d_lon = math.radians(lon2 - lon1)
    a = math.sin(d_phi / 2) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(d_lon / 2) ** 2
    return 2 * EARTH_RADIUS_M * math.asin(math.sqrt(a))


def offset_meters(lat: float, lon: float, north_m: float, east_m: float):
    d_lat = north_m / 111_320
    d_lon = east_m / (111_320 * math.cos(math.radians(lat)))
    return lat + d_lat, lon + d_lon


def jitter(rng: random.Random, lat: float, lon: float, max_m: float):
    angle = rng.random() * 2 * math.pi
    radius = max_m * math.sqrt(rng.random())
    j_lat, j_lon = offset_meters(lat, lon, radius * math.cos(angle), radius * math.sin(angle))
    return round(j_lat, 6), round(j_lon, 6)


def entry(name, when, zone, mood, tags=None, comment=None, location=None, source="app"):
    created = fmt(when)
    return {
        "id": sid(name),
        "createdAt": created,
        "updatedAt": created,
        "timeZoneID": zone,
        "mood": mood,
        "tags": list(tags or []),
        "comment": comment,
        "location": location,
        "source": source,
    }


def sample(lat, lon, accuracy, when, kind="fresh"):
    return {
        "latitude": round(float(lat), 6),
        "longitude": round(float(lon), 6),
        "horizontalAccuracy": float(accuracy),
        "capturedAt": fmt(when),
        "kind": kind,
    }


def place_label(name, label, kind, lat, lon, when):
    return {
        "id": sid("place:" + name),
        "name": label,
        "kind": kind,
        "anchorLatitude": round(float(lat), 6),
        "anchorLongitude": round(float(lon), 6),
        "createdAt": fmt(when),
    }


def anchor(zone: str):
    return entry("anchor", local_at(zone, 2026, 9, 20, 10, 0), zone, 2)


def daypart(entry_obj) -> str:
    local = parse_iso(entry_obj["createdAt"]).astimezone(ZoneInfo(entry_obj["timeZoneID"]))
    minutes = local.hour * 60 + local.minute
    if 5 * 60 <= minutes <= 11 * 60 + 59:
        return "morning"
    if 12 * 60 <= minutes <= 16 * 60 + 59:
        return "afternoon"
    if 17 * 60 <= minutes <= 21 * 60 + 59:
        return "evening"
    return "night"


def local_date(entry_obj):
    return parse_iso(entry_obj["createdAt"]).astimezone(ZoneInfo(entry_obj["timeZoneID"])).date()


def iso_weekday(entry_obj) -> int:
    return parse_iso(entry_obj["createdAt"]).astimezone(ZoneInfo(entry_obj["timeZoneID"])).isoweekday()


def has_core(entry_obj, core: str) -> bool:
    prefix = core + "."
    return any(tag == core or tag.startswith(prefix) for tag in entry_obj["tags"])


def cores_in(entries) -> list[str]:
    found = set()
    for entry_obj in entries:
        for tag in entry_obj["tags"]:
            found.add(tag.split(".", 1)[0])
    return sorted(found)


def report_tag(hits, core: str, dominance: float) -> str:
    counts: dict[str, int] = {}
    for entry_obj in hits:
        for tag in entry_obj["tags"]:
            if tag == core or tag.startswith(core + "."):
                if tag != core:
                    counts[tag] = counts.get(tag, 0) + 1
    best_tag = None
    best_n = -1
    for tag, count in counts.items():
        if count > best_n or (count == best_n and (best_tag is None or tag < best_tag)):
            best_tag = tag
            best_n = count
    if best_tag is not None and hits and best_n / len(hits) >= dominance:
        return best_tag
    return core


def round_score(score: float) -> float:
    return math.floor(score * 1_000_000_000 + 0.5) / 1_000_000_000


def sample_var(values, mean: float) -> float:
    if len(values) <= 1:
        return 0.0
    return sum((value - mean) ** 2 for value in values) / (len(values) - 1)


def compare_stats(segment, rest, sd_floor: float):
    n_seg, n_rest = len(segment), len(rest)
    if n_seg == 0 or n_rest == 0:
        return None
    mean_seg = sum(segment) / n_seg
    mean_rest = sum(rest) / n_rest
    diff = mean_seg - mean_rest
    var_seg = sample_var(segment, mean_seg)
    var_rest = sample_var(rest, mean_rest)
    degrees = n_seg + n_rest - 2
    if degrees <= 0:
        sd_pooled = 0.0
    else:
        sd_pooled = math.sqrt(((n_seg - 1) * var_seg + (n_rest - 1) * var_rest) / degrees)
    spread = max(sd_pooled, sd_floor) * math.sqrt(1 / n_seg + 1 / n_rest)
    z_value = diff / spread if spread else 0.0
    return diff, z_value


def nearer(distance: float, cluster_id: str, best_distance, best_id) -> bool:
    if best_id is None:
        return True
    if distance < best_distance - 1e-4:
        return True
    if abs(distance - best_distance) <= 1e-4 and cluster_id < best_id:
        return True
    return False


def resolve_places(entries, labels, config: Config):
    radius = config["placeRadiusMeters"]
    max_accuracy = config["placeMaxAccuracyMeters"]
    eligible = []
    for entry_obj in entries:
        location = entry_obj.get("location")
        if not location:
            continue
        if location["horizontalAccuracy"] > max_accuracy:
            continue
        eligible.append(entry_obj)
    eligible.sort(key=lambda item: (item["createdAt"], item["id"]))

    clusters: dict[str, dict] = {}
    assigned: dict[str, str] = {}

    def add_member(cluster_id: str, entry_obj, label_id=None):
        cluster = clusters.setdefault(
            cluster_id,
            {"lats": [], "lons": [], "members": [], "labelID": label_id},
        )
        cluster["lats"].append(entry_obj["location"]["latitude"])
        cluster["lons"].append(entry_obj["location"]["longitude"])
        cluster["members"].append(entry_obj)
        assigned[entry_obj["id"]] = cluster_id

    for entry_obj in eligible:
        best_id = None
        best_distance = None
        best_label = None
        for label in labels:
            distance = haversine(
                entry_obj["location"]["latitude"],
                entry_obj["location"]["longitude"],
                label["anchorLatitude"],
                label["anchorLongitude"],
            )
            cluster_id = "p:" + label["id"]
            if distance <= radius and nearer(distance, cluster_id, best_distance, best_id):
                best_id = cluster_id
                best_distance = distance
                best_label = label["id"]
        if best_id is not None:
            add_member(best_id, entry_obj, best_label)

    unnamed_ids: list[str] = []
    for entry_obj in eligible:
        if entry_obj["id"] in assigned:
            continue
        best_id = None
        best_distance = None
        for cluster_id in unnamed_ids:
            cluster = clusters[cluster_id]
            count = len(cluster["lats"])
            centroid_lat = sum(cluster["lats"]) / count
            centroid_lon = sum(cluster["lons"]) / count
            distance = haversine(
                entry_obj["location"]["latitude"],
                entry_obj["location"]["longitude"],
                centroid_lat,
                centroid_lon,
            )
            if distance <= radius and nearer(distance, cluster_id, best_distance, best_id):
                best_id = cluster_id
                best_distance = distance
        if best_id is None:
            best_id = "u:" + entry_obj["id"]
            unnamed_ids.append(best_id)
        add_member(best_id, entry_obj)

    frozen = {}
    for cluster_id in unnamed_ids:
        cluster = clusters[cluster_id]
        count = len(cluster["lats"])
        frozen[cluster_id] = (sum(cluster["lats"]) / count, sum(cluster["lons"]) / count)
    regrouped: dict[str, list] = {cluster_id: [] for cluster_id in unnamed_ids}
    unnamed_members = [entry_obj for entry_obj in eligible if assigned.get(entry_obj["id"], "").startswith("u:")]
    for entry_obj in unnamed_members:
        own = assigned[entry_obj["id"]]
        best_id = None
        best_distance = None
        for cluster_id, (centroid_lat, centroid_lon) in frozen.items():
            distance = haversine(
                entry_obj["location"]["latitude"],
                entry_obj["location"]["longitude"],
                centroid_lat,
                centroid_lon,
            )
            if distance <= radius and nearer(distance, cluster_id, best_distance, best_id):
                best_id = cluster_id
                best_distance = distance
        regrouped[best_id or own].append(entry_obj)

    for cluster_id in unnamed_ids:
        clusters.pop(cluster_id, None)
        for entry_obj in list(assigned):
            if assigned[entry_obj] == cluster_id:
                del assigned[entry_obj]
    for members in regrouped.values():
        if not members:
            continue
        earliest = min(members, key=lambda item: (item["createdAt"], item["id"]))
        cluster_id = "u:" + earliest["id"]
        for entry_obj in members:
            add_member(cluster_id, entry_obj)

    return assigned


def confidence_for(config: Config, n_seg: int, n_rest: int, diff: float, z_value: float, distinct: int, family: str):
    if family == "weekday":
        pattern_n = config["weekdayMinSegment"]
        hint_n = config["minSegment"]
        min_diff = config["weekdayMinAbsDiff"]
        dates_ok = distinct >= config["weekdayMinDistinctDates"]
    else:
        pattern_n = config["patternMinSegment"]
        hint_n = config["minSegment"]
        min_diff = config["minAbsDiff"]
        dates_ok = True
    if (
        dates_ok
        and n_seg >= pattern_n
        and n_rest >= config["patternMinRest"]
        and abs(diff) >= min_diff
        and abs(z_value) >= config["zPattern"]
    ):
        return "pattern"
    if (
        dates_ok
        and n_seg >= hint_n
        and n_rest >= config["minRest"]
        and abs(diff) >= min_diff
        and abs(z_value) >= config["zHint"]
    ):
        return "earlyHint"
    return None


def mean_candidate(config, segment_entries, rest_entries, detector, family, params, id_builder):
    moods_seg = [entry_obj["mood"] for entry_obj in segment_entries]
    moods_rest = [entry_obj["mood"] for entry_obj in rest_entries]
    compared = compare_stats(moods_seg, moods_rest, config["sdFloor"])
    if compared is None:
        return None
    diff, z_value = compared
    distinct = len({local_date(entry_obj) for entry_obj in segment_entries})
    level = confidence_for(config, len(segment_entries), len(rest_entries), diff, z_value, distinct, family)
    if level is None:
        return None
    direction = "lower" if diff < 0 else "higher"
    full_params = dict(params)
    full_params["direction"] = direction
    score = abs(diff) * min(len(segment_entries), 10) / 10
    if level == "pattern":
        score += 0.2
    return {
        "id": id_builder(direction),
        "detector": detector,
        "family": family,
        "direction": direction,
        "params": full_params,
        "nSegment": len(segment_entries),
        "score": score,
        "confidence": level,
    }


def tag_candidate(config, detector, tag, cluster_id, count, share_seg, share_rest, n_rest):
    gap = share_seg - share_rest
    level = "pattern" if count >= config["tagPatternCount"] else "earlyHint"
    score = gap * min(count, 10) / 10
    if level == "pattern":
        score += 0.2
    params = {"tag": tag}
    if cluster_id is not None:
        params["clusterID"] = cluster_id
        pattern_id = f"tagAtPlace.{tag}.{cluster_id}"
    else:
        pattern_id = f"tagOnLow.{tag}"
    return {
        "id": pattern_id,
        "detector": detector,
        "family": "tag",
        "direction": None,
        "params": params,
        "nSegment": count,
        "score": score,
        "confidence": level,
        "nRest": n_rest,
    }


def detect(entries, labels, window, config: Config, assigned):
    candidates = []
    for segment_name in ("morning", "afternoon", "evening", "night"):
        segment = [entry_obj for entry_obj in window if daypart(entry_obj) == segment_name]
        rest = [entry_obj for entry_obj in window if daypart(entry_obj) != segment_name]
        candidate = mean_candidate(
            config,
            segment,
            rest,
            "timeOfDay",
            "time",
            {"segment": segment_name},
            lambda direction, name=segment_name: f"timeOfDay.{direction}.{name}",
        )
        if candidate:
            candidates.append(candidate)

    located = [entry_obj for entry_obj in window if entry_obj["id"] in assigned]
    cluster_ids = sorted({assigned[entry_obj["id"]] for entry_obj in located})
    label_by_id = {label["id"]: label for label in labels}
    for cluster_id in cluster_ids:
        segment = [entry_obj for entry_obj in located if assigned[entry_obj["id"]] == cluster_id]
        rest = [entry_obj for entry_obj in located if assigned[entry_obj["id"]] != cluster_id]
        params = {"clusterID": cluster_id}
        if cluster_id.startswith("p:"):
            label = label_by_id.get(cluster_id[2:])
            if label:
                params["placeKind"] = label["kind"]
        candidate = mean_candidate(
            config,
            segment,
            rest,
            "place",
            "place",
            params,
            lambda direction, cid=cluster_id: f"place.{direction}.{cid}",
        )
        if candidate:
            candidates.append(candidate)

    for label in labels:
        if label["kind"] != "home":
            continue
        home_id = "p:" + label["id"]
        segment = [entry_obj for entry_obj in located if assigned[entry_obj["id"]] != home_id]
        rest = [entry_obj for entry_obj in located if assigned[entry_obj["id"]] == home_id]
        candidate = mean_candidate(
            config,
            segment,
            rest,
            "awayFromHome",
            "place",
            {"placeKind": "home"},
            lambda direction: f"awayFromHome.{direction}",
        )
        if candidate:
            candidates.append(candidate)

    low_tagged = [entry_obj for entry_obj in window if entry_obj["mood"] == 1 and entry_obj["tags"]]
    if len(low_tagged) >= config["tagMinLowTagged"]:
        okay_good = [entry_obj for entry_obj in window if entry_obj["mood"] in (2, 3) and entry_obj["tags"]]
        for core in cores_in(low_tagged):
            hits = [entry_obj for entry_obj in low_tagged if has_core(entry_obj, core)]
            count = len(hits)
            share_seg = count / len(low_tagged)
            if count < config["tagMinCount"] or share_seg < config["tagMinShare"]:
                continue
            if okay_good:
                share_rest = sum(1 for entry_obj in okay_good if has_core(entry_obj, core)) / len(okay_good)
            else:
                share_rest = 0.0
            if share_seg - share_rest < config["tagMinShareGap"]:
                continue
            tag = report_tag(hits, core, config["fineTagDominance"])
            candidates.append(tag_candidate(config, "tagOnLow", tag, None, count, share_seg, share_rest, len(okay_good)))

    tagged_window = [entry_obj for entry_obj in window if entry_obj["tags"]]
    for cluster_id in cluster_ids:
        members = [entry_obj for entry_obj in tagged_window if assigned.get(entry_obj["id"]) == cluster_id]
        if not members:
            continue
        elsewhere = [entry_obj for entry_obj in tagged_window if assigned.get(entry_obj["id"]) != cluster_id]
        for core in cores_in(members):
            hits = [entry_obj for entry_obj in members if has_core(entry_obj, core)]
            count = len(hits)
            share_seg = count / len(members)
            if count < config["tagMinCount"] or share_seg < config["tagAtPlaceMinShare"]:
                continue
            if elsewhere:
                share_rest = sum(1 for entry_obj in elsewhere if has_core(entry_obj, core)) / len(elsewhere)
            else:
                share_rest = 0.0
            if share_seg - share_rest < config["tagMinShareGap"]:
                continue
            tag = report_tag(hits, core, config["fineTagDominance"])
            candidates.append(
                tag_candidate(config, "tagAtPlace", tag, cluster_id, count, share_seg, share_rest, len(elsewhere))
            )
    return candidates


def weekday_detect(entries, window, config: Config):
    if not entries:
        return []
    candidates = []
    for weekday in range(1, 8):
        segment = [entry_obj for entry_obj in window if iso_weekday(entry_obj) == weekday]
        rest = [entry_obj for entry_obj in window if iso_weekday(entry_obj) != weekday]
        candidate = mean_candidate(
            config,
            segment,
            rest,
            "weekday",
            "weekday",
            {"weekday": str(weekday)},
            lambda direction, day=weekday: f"weekday.{direction}.{day}",
        )
        if candidate:
            candidates.append(candidate)
    for weekday in range(1, 8):
        for segment_name in ("morning", "afternoon", "evening"):
            segment = [
                entry_obj
                for entry_obj in window
                if iso_weekday(entry_obj) == weekday and daypart(entry_obj) == segment_name
            ]
            rest = [entry_obj for entry_obj in window if entry_obj not in segment]
            candidate = mean_candidate(
                config,
                segment,
                rest,
                "weekdayDaypart",
                "weekday",
                {"weekday": str(weekday), "segment": segment_name},
                lambda direction, day=weekday, name=segment_name: f"weekdayDaypart.{direction}.{day}.{name}",
            )
            if candidate:
                candidates.append(candidate)
    return candidates


def sort_candidates(candidates):
    return sorted(
        candidates,
        key=lambda item: (
            0 if item["confidence"] == "pattern" else 1,
            -round_score(item["score"]),
            item["id"],
        ),
    )


def library_match_count(library_entry, pattern):
    spec = library_entry["match"]
    if spec["detector"] != pattern["detector"]:
        return None
    for key, value in spec.items():
        if key == "detector":
            continue
        actual = pattern["params"].get(key)
        if key == "tag":
            core = (actual or "").split(".", 1)[0]
            if value != actual and value != core:
                return None
        elif actual != value:
            return None
    return len(spec)


def match_library(pattern, library):
    best = None
    best_count = -1
    best_index = None
    for index, library_entry in enumerate(library):
        count = library_match_count(library_entry, pattern)
        if count is None:
            continue
        if count > best_count or (count == best_count and (best_index is None or index < best_index)):
            best = library_entry
            best_count = count
            best_index = index
    if best is None:
        raise RuntimeError("no library row matches " + pattern["id"])
    return best["id"]


def build_card(entries, labels, now: datetime, zone_id: str, config: Config, library):
    zone = ZoneInfo(zone_id)
    period_start, period_end = card_period(now, zone, CARD_WEEKDAY, CARD_HOUR, CARD_MINUTE)
    start_text, end_text = fmt(period_start), fmt(period_end)

    def in_range(entry_obj, start, end):
        return start <= entry_obj["createdAt"] < end

    period_entries = [entry_obj for entry_obj in entries if in_range(entry_obj, start_text, end_text)]
    window_entries = list(period_entries)
    window_kind = "week"
    if len(window_entries) < config["shortWeekThreshold"]:
        window_kind = "twoWeeks"
        two_week_start = fmt(shift_calendar_days(period_end, 14, zone))
        window_entries = [entry_obj for entry_obj in entries if in_range(entry_obj, two_week_start, end_text)]

    weekday_start = fmt(shift_calendar_days(period_end, 28, zone))
    weekday_entries = [entry_obj for entry_obj in entries if in_range(entry_obj, weekday_start, end_text)]
    first_at = min((entry_obj["createdAt"] for entry_obj in entries), default=None)
    data_cutoff = fmt(shift_calendar_days(period_end, config["weekdayMinDataDays"], zone))
    weekday_ok = first_at is not None and first_at <= data_cutoff

    assigned = resolve_places(entries, labels, config)
    candidates = detect(entries, labels, window_entries, config, assigned)
    if weekday_ok:
        candidates.extend(weekday_detect(entries, weekday_entries, config))
    ordered = sort_candidates(candidates)

    count = len(period_entries)
    low_count = sum(1 for entry_obj in period_entries if entry_obj["mood"] == 1)
    okay_count = sum(1 for entry_obj in period_entries if entry_obj["mood"] == 2)
    good_count = sum(1 for entry_obj in period_entries if entry_obj["mood"] == 3)
    is_low_week = count >= config["lowWeekMinEntries"] and low_count / count >= config["lowWeekShare"]
    first_week_cutoff = fmt(shift_calendar_days(period_end, config["firstWeekDays"], zone))
    if count < config["notEnoughDataBelow"]:
        variant = "notEnoughData"
        limit = 1
    elif first_at is not None and first_at >= first_week_cutoff:
        variant = "firstWeek"
        limit = 1
    else:
        variant = "normal"
        limit = config["maxItems"]
    if is_low_week:
        limit = min(limit, config["maxItemsLowWeek"])

    picked = []
    families = set()
    for candidate in ordered:
        if len(picked) >= limit:
            break
        if candidate["family"] in families:
            continue
        picked.append(candidate)
        families.add(candidate["family"])
    if limit >= 2 and picked and not any(item["direction"] == "higher" for item in picked):
        picked_ids = {id(item) for item in picked}
        for candidate in ordered:
            if candidate["confidence"] == "pattern" and candidate["direction"] == "higher" and id(candidate) not in picked_ids:
                picked[-1] = candidate
                break

    items = [{"patternID": item["id"], "libraryID": match_library(item, library)} for item in picked]
    return {
        "periodStart": start_text,
        "periodEnd": end_text,
        "windowKind": window_kind,
        "variant": variant,
        "isLowWeek": is_low_week,
        "maxItemCount": limit,
        "entryCount": count,
        "moodCounts": [low_count, okay_count, good_count],
        "firstItemLibraryID": items[0]["libraryID"] if items else None,
        "itemsIncludeLibraryIDs": [item["libraryID"] for item in items],
        "candidatesInclude": [{"id": item["id"], "confidence": item["confidence"]} for item in ordered],
        "dayparts": {entry_obj["id"]: daypart(entry_obj) for entry_obj in entries},
    }


def low_run_active(entries, config: Config) -> bool:
    length = config["lowRunLength"]
    if len(entries) < length:
        return False
    last = sorted(entries, key=lambda item: (item["createdAt"], item["id"]))[-length:]
    if any(item["mood"] != 1 for item in last):
        return False
    return len({local_date(item) for item in last}) >= config["lowRunMinDays"]


def require(condition: bool, message: str):
    if not condition:
        raise SystemExit("fixture intent failed: " + message)


def pattern_input(now: str, zone: str, entries, places=None):
    return {
        "now": now,
        "timeZoneID": zone,
        "cardTime": {"weekday": CARD_WEEKDAY, "hour": CARD_HOUR, "minute": CARD_MINUTE},
        "entries": entries,
        "places": places or [],
    }


def afternoon_dip_entries():
    """Six low afternoons. The other dayparts stay under the pattern threshold.

    A higher pattern would replace this dip: the card keeps at most one item
    per family, then swaps the last pick for a higher pattern when the card
    would otherwise have none.
    """
    zone = "Europe/Rome"
    rows = [anchor(zone)]
    for day in range(5, 11):
        rows.append(entry(f"dip-afternoon-{day}", local_at(zone, 2026, 10, day, 15, 0), zone, 1))
    for day in (5, 6, 7):
        rows.append(entry(f"dip-morning-{day}", local_at(zone, 2026, 10, day, 9, 0), zone, 3))
    for day in (8, 9):
        rows.append(entry(f"dip-evening-{day}", local_at(zone, 2026, 10, day, 18, 30), zone, 3))
    return rows


def sunday_evening_entries():
    zone = "Europe/Rome"
    rows = []
    day = datetime(2026, 9, 14).date()
    last = datetime(2026, 10, 11).date()
    while day <= last:
        when_zone = ZoneInfo(zone)
        morning = datetime(day.year, day.month, day.day, 9, 0, tzinfo=when_zone)
        afternoon = datetime(day.year, day.month, day.day, 15, 0, tzinfo=when_zone)
        evening = datetime(day.year, day.month, day.day, 18, 30, tzinfo=when_zone)
        evening_mood = 1 if day.isoweekday() == 7 else 3
        stamp = day.isoformat()
        rows.append(entry(f"sun-m-{stamp}", morning, zone, 3))
        rows.append(entry(f"sun-a-{stamp}", afternoon, zone, 3))
        rows.append(entry(f"sun-e-{stamp}", evening, zone, evening_mood))
        day += timedelta(days=1)
    return rows


def tired_entries():
    zone = "Europe/Rome"
    rows = [anchor(zone)]
    pairs = [(1, 3), (3, 1), (1, 3), (3, 1), (1, 3), (3, 1), (1, 3)]
    for offset, (morning_mood, afternoon_mood) in enumerate(pairs):
        day = 5 + offset
        morning = entry(
            f"tired-m-{day}",
            local_at(zone, 2026, 10, day, 9, 30),
            zone,
            morning_mood,
            ["tired.drained"] if morning_mood == 1 else ["happy.grateful"],
        )
        afternoon = entry(
            f"tired-a-{day}",
            local_at(zone, 2026, 10, day, 15, 30),
            zone,
            afternoon_mood,
            ["tired.drained"] if afternoon_mood == 1 else ["happy.grateful"],
        )
        rows.extend([morning, afternoon])
    return rows


def spread_times(index: int):
    return [(9, 10), (15, 10), (18, 10), (23, 10), (8, 40), (13, 20)][index % 6]


def stressed_scenario():
    zone = "Europe/Rome"
    work_lat, work_lon = 45.4642, 9.1900
    else_lat, else_lon = offset_meters(work_lat, work_lon, 2200, 0)
    created = local_at(zone, 2026, 9, 1, 12, 0)
    work = place_label("work", "Work", "work", work_lat, work_lon, created)
    rows = [anchor(zone)]
    for index in range(6):
        hour, minute = spread_times(index)
        day = 5 + index
        when = local_at(zone, 2026, 10, day, hour, minute)
        rows.append(
            entry(
                f"work-{index}",
                when,
                zone,
                2,
                ["anxious.stressed"],
                location=sample(work_lat + index * 0.00001, work_lon, 12, when),
            )
        )
        hour, minute = spread_times(index + 1)
        when = local_at(zone, 2026, 10, day, hour, minute)
        rows.append(
            entry(
                f"else-{index}",
                when,
                zone,
                2,
                ["calm.relaxed"],
                location=sample(else_lat, else_lon + index * 0.00001, 12, when),
            )
        )
    return rows, [work]


def out_lifts_scenario():
    zone = "Europe/Rome"
    home_lat, home_lon = 45.4642, 9.1900
    away_lat, away_lon = offset_meters(home_lat, home_lon, 2000, 400)
    created = local_at(zone, 2026, 9, 1, 12, 0)
    home = place_label("home", "Home", "home", home_lat, home_lon, created)
    rows = [anchor(zone)]
    for index in range(6):
        hour, minute = spread_times(index)
        day = 5 + index
        when = local_at(zone, 2026, 10, day, hour, minute)
        rows.append(
            entry(
                f"home-{index}",
                when,
                zone,
                1,
                location=sample(home_lat, home_lon + index * 0.00002, 15, when),
            )
        )
        when = local_at(zone, 2026, 10, day, hour, min(minute + 20, 59))
        rows.append(
            entry(
                f"away-{index}",
                when,
                zone,
                3,
                location=sample(away_lat, away_lon, 15, when),
            )
        )
    return rows, [home]


def low_week_entries():
    zone = "Europe/Rome"
    rows = [anchor(zone)]
    hours = [6, 9, 12, 15, 18, 23, 8, 14, 17, 22]
    for index, hour in enumerate(hours):
        day = 5 + (index % 7)
        if day == 11 and hour >= 19:
            hour = 16
        rows.append(entry(f"low-{index}", local_at(zone, 2026, 10, day, hour, 5), zone, 1))
    return rows


def not_enough_entries():
    zone = "Europe/Rome"
    return [
        entry(f"few-{day}", local_at(zone, 2026, 10, day, 10, 0), zone, 2)
        for day in (8, 9, 10)
    ]


def first_week_entries():
    zone = "Europe/Rome"
    return [
        entry(f"first-{day}", local_at(zone, 2026, 10, day, 11, 0), zone, 2)
        for day in range(6, 12)
    ]


def dst_entries():
    zone = "Europe/Rome"
    rows = [anchor(zone)]
    traps = [
        ("dst-before-midnight", "2026-10-24T22:30:00.000Z", "night"),
        ("dst-before-fallback", "2026-10-25T00:30:00.000Z", "night"),
        ("dst-after-fallback", "2026-10-25T01:30:00.000Z", "night"),
        ("dst-trap-0430", "2026-10-25T03:30:00.000Z", "night"),
        ("dst-morning-edge", "2026-10-25T04:00:00.000Z", "morning"),
        ("dst-afternoon", "2026-10-25T13:00:00.000Z", "afternoon"),
        ("dst-evening", "2026-10-25T16:30:00.000Z", "evening"),
    ]
    expected = {}
    for name, instant, part in traps:
        when = parse_iso(instant)
        made = entry(name, when, zone, 2)
        rows.append(made)
        expected[made["id"]] = part
    for day in range(19, 25):
        rows.append(entry(f"dst-fill-{day}", local_at(zone, 2026, 10, day, 10, 15), zone, 2))
    rows.append(entry("dst-fill-25", local_at(zone, 2026, 10, 25, 10, 15), zone, 2))
    return rows, expected


def travel_entries():
    rome = "Europe/Rome"
    york = "America/New_York"
    instant = parse_iso("2026-10-08T14:00:00.000Z")
    rome_entry = entry("travel-rome", instant, rome, 2)
    york_entry = entry("travel-york", instant, york, 2)
    rows = [anchor(rome), rome_entry, york_entry]
    for day in range(6, 11):
        rows.append(entry(f"travel-fill-{day}", local_at(rome, 2026, 10, day, 12, 0), rome, 2))
    return rows, rome_entry["id"], york_entry["id"]


def noise_entries(seed: int):
    zone = "Europe/Rome"
    rng = random.Random(seed)
    rows = [anchor(zone)]
    for index in range(21):
        day = 5 + (index % 7)
        hour = rng.choice([6, 9, 12, 15, 18, 23])
        if day == 11 and hour >= 19:
            hour = 16
        minute = rng.randrange(0, 60)
        mood = rng.choice([1, 2, 3])
        rows.append(entry(f"noise-{seed}-{index}", local_at(zone, 2026, 10, day, hour, minute), zone, mood))
    return rows


def three_places():
    zone = "Europe/Rome"
    rng = random.Random(7)
    base_lat, base_lon = 45.4642, 9.1900
    centers = [
        (base_lat, base_lon),
        offset_meters(base_lat, base_lon, 500, 0),
        offset_meters(base_lat, base_lon, 0, 500),
    ]
    rows = []
    for center_index, (lat, lon) in enumerate(centers):
        for point in range(5):
            j_lat, j_lon = jitter(rng, lat, lon, 40)
            when = local_at(zone, 2026, 10, 5, 9, center_index * 10 + point)
            rows.append(entry(f"cluster-{center_index}-{point}", when, zone, 2, location=sample(j_lat, j_lon, 8, when)))
    return rows


def accuracy_entries():
    zone = "Europe/Rome"
    lat, lon = 45.4642, 9.1900
    rows = []
    for index, accuracy in enumerate((10, 20, 30, 150.0, 150.1, 200)):
        when = local_at(zone, 2026, 10, 6, 11, index)
        rows.append(entry(f"acc-{accuracy}", when, zone, 2, location=sample(lat, lon, accuracy, when)))
    return rows


def label_points(with_label: bool):
    zone = "Europe/Rome"
    lat, lon = 45.4700, 9.2000
    created = local_at(zone, 2026, 9, 1, 8, 0)
    rows = []
    for index in range(4):
        j_lat, j_lon = offset_meters(lat, lon, 10 * index, 5)
        when = local_at(zone, 2026, 10, 7, 9, index * 5)
        rows.append(entry(f"near-{index}", when, zone, 2, location=sample(j_lat, j_lon, 10, when)))
    far_lat, far_lon = offset_meters(lat, lon, 250, 0)
    far_when = local_at(zone, 2026, 10, 7, 12, 0)
    rows.append(entry("far", far_when, zone, 2, location=sample(far_lat, far_lon, 10, far_when)))
    labels = [place_label("captured-home", "Home", "home", lat, lon, created)] if with_label else []
    return rows, labels


def place_expect(entries, assigned):
    grouped: dict[str, list] = {}
    for entry_id, cluster_id in assigned.items():
        grouped.setdefault(cluster_id, []).append(entry_id)
    groups = [sorted(grouped[cluster_id]) for cluster_id in sorted(grouped)]
    reps = [group[0] for group in groups]
    pairs = []
    for left in range(len(reps)):
        for right in range(left + 1, len(reps)):
            pairs.append([reps[left], reps[right]])
    unclustered = sorted(entry_obj["id"] for entry_obj in entries if entry_obj["id"] not in assigned)
    return {
        "sameCluster": groups,
        "differentCluster": pairs,
        "unclustered": unclustered,
        "clusterOf": {entry_id: assigned[entry_id] for entry_id in sorted(assigned)},
    }


def period_cases():
    raw = [
        ("sunday-after", "2026-10-11T18:00:00.000Z", "Europe/Rome", "2026-10-04T17:00:00.000Z", "2026-10-11T17:00:00.000Z"),
        ("sunday-before", "2026-10-11T16:00:00.000Z", "Europe/Rome", "2026-09-27T17:00:00.000Z", "2026-10-04T17:00:00.000Z"),
        ("sunday-exact", "2026-10-11T17:00:00.000Z", "Europe/Rome", "2026-10-04T17:00:00.000Z", "2026-10-11T17:00:00.000Z"),
        ("monday-morning", "2026-10-12T06:00:00.000Z", "Europe/Rome", "2026-10-04T17:00:00.000Z", "2026-10-11T17:00:00.000Z"),
        ("saturday-night", "2026-10-10T21:00:00.000Z", "Europe/Rome", "2026-09-27T17:00:00.000Z", "2026-10-04T17:00:00.000Z"),
        ("dst-sunday-after", "2026-10-25T19:00:00.000Z", "Europe/Rome", "2026-10-18T17:00:00.000Z", "2026-10-25T18:00:00.000Z"),
        ("dst-sunday-before", "2026-10-25T16:30:00.000Z", "Europe/Rome", "2026-10-11T17:00:00.000Z", "2026-10-18T17:00:00.000Z"),
        ("dst-early-morning", "2026-10-25T01:30:00.000Z", "Europe/Rome", "2026-10-11T17:00:00.000Z", "2026-10-18T17:00:00.000Z"),
        ("new-york-sunday-after", "2026-10-12T00:00:00.000Z", "America/New_York", "2026-10-04T23:00:00.000Z", "2026-10-11T23:00:00.000Z"),
    ]
    cases = []
    for name, now, zone_id, expected_start, expected_end in raw:
        start, end = card_period(parse_iso(now), ZoneInfo(zone_id), CARD_WEEKDAY, CARD_HOUR, CARD_MINUTE)
        require(fmt(start) == expected_start and fmt(end) == expected_end, name + " period boundary")
        cases.append(
            {
                "name": name,
                "now": now,
                "timeZoneID": zone_id,
                "periodStart": fmt(start),
                "periodEnd": fmt(end),
            }
        )
    dst_start = parse_iso(cases[5]["periodStart"])
    dst_end = parse_iso(cases[5]["periodEnd"])
    require(int((dst_end - dst_start).total_seconds()) == 169 * 3600, "DST week is 169 hours, not 168")
    return {
        "version": 1,
        "cardTime": {"weekday": CARD_WEEKDAY, "hour": CARD_HOUR, "minute": CARD_MINUTE},
        "cases": cases,
    }


def self_check(config: Config, library, tags):
    require(config["version"] == 1, "config version")
    require(config["firstCardMinEntries"] == 5, "first card minimum")
    zone = "Europe/Rome"
    one_day = [
        entry(f"run-same-{index}", local_at(zone, 2026, 10, 8, 9 + index, 0), zone, 1)
        for index in range(5)
    ]
    require(not low_run_active(one_day, config), "five Low ratings on one day do not arm support")
    two_days = one_day[:3] + [
        entry("run-next-0", local_at(zone, 2026, 10, 9, 9, 0), zone, 1),
        entry("run-next-1", local_at(zone, 2026, 10, 9, 18, 0), zone, 1),
    ]
    require(low_run_active(two_days, config), "five Low ratings across two days arm support")
    broken = two_days[:-1] + [entry("run-okay", local_at(zone, 2026, 10, 9, 20, 0), zone, 2)]
    require(not low_run_active(broken, config), "a non-Low rating at the end does not arm support")
    for word in CLINICAL:
        for row in library:
            blob = json.dumps(row).lower()
            require(word not in blob, f"library contains {word}")
    known = set()
    for core in tags["cores"]:
        known.add(core["id"])
        for fine in core["fine"]:
            require(fine.startswith(core["id"] + "."), fine)
            known.add(fine)
    for row in library:
        tag = row["match"].get("tag")
        if tag:
            core = tag.split(".", 1)[0]
            require(core in known, "library tag core " + tag)
    require(haversine(45.0, 9.0, 45.0 + 100 / 111_320, 9.0) == pytest_approx_100(), "haversine scale")


def pytest_approx_100() -> float:
    distance = haversine(45.0, 9.0, 45.0 + 100 / 111_320, 9.0)
    require(99 < distance < 101, f"haversine 100 m got {distance}")
    return distance


def build_pattern_scenarios(config, library):
    scenarios = []

    def add(name, now, zone, entries, places=None, check=None):
        payload = pattern_input(now, zone, entries, places)
        result = build_card(entries, places or [], parse_iso(now), zone, config, library)
        if check:
            check(result)
        scenarios.append((name, payload, {key: result[key] for key in (
            "periodStart",
            "periodEnd",
            "windowKind",
            "variant",
            "isLowWeek",
            "maxItemCount",
            "entryCount",
            "moodCounts",
            "firstItemLibraryID",
            "itemsIncludeLibraryIDs",
            "candidatesInclude",
            "dayparts",
        )}))

    add(
        "afternoon-dip",
        "2026-10-11T18:00:00.000Z",
        "Europe/Rome",
        afternoon_dip_entries(),
        check=lambda result: require(
            result["firstItemLibraryID"] == "afternoon-dip"
            and result["variant"] == "normal"
            and result["isLowWeek"] is False
            and result["windowKind"] == "week",
            "afternoon-dip shape " + json.dumps(result["candidatesInclude"]),
        ),
    )
    add(
        "sunday-evening",
        "2026-10-11T18:00:00.000Z",
        "Europe/Rome",
        sunday_evening_entries(),
        check=lambda result: require(
            result["firstItemLibraryID"] == "sunday-evening" and result["variant"] == "normal",
            "sunday-evening shape " + json.dumps(
                {"first": result["firstItemLibraryID"], "items": result["itemsIncludeLibraryIDs"], "candidates": result["candidatesInclude"][:8]}
            ),
        ),
    )
    add(
        "tired-on-low",
        "2026-10-11T18:00:00.000Z",
        "Europe/Rome",
        tired_entries(),
        check=lambda result: require(
            result["firstItemLibraryID"] == "tired-on-low" and result["isLowWeek"] is False and result["variant"] == "normal",
            "tired-on-low shape " + json.dumps(result["itemsIncludeLibraryIDs"]) + json.dumps(result["candidatesInclude"]),
        ),
    )
    stressed_rows, stressed_places = stressed_scenario()
    add(
        "stressed-at-work",
        "2026-10-11T18:00:00.000Z",
        "Europe/Rome",
        stressed_rows,
        stressed_places,
        check=lambda result: require(
            "stress-at-place" in result["itemsIncludeLibraryIDs"] and result["variant"] == "normal",
            "stressed-at-work shape " + json.dumps(result["itemsIncludeLibraryIDs"]) + json.dumps(result["candidatesInclude"]),
        ),
    )
    out_rows, out_places = out_lifts_scenario()
    add(
        "out-lifts",
        "2026-10-11T18:00:00.000Z",
        "Europe/Rome",
        out_rows,
        out_places,
        check=lambda result: require(
            result["firstItemLibraryID"] == "out-lifts" and result["isLowWeek"] is False,
            "out-lifts shape " + json.dumps(result["itemsIncludeLibraryIDs"]) + json.dumps(result["candidatesInclude"]),
        ),
    )
    add(
        "low-week",
        "2026-10-11T18:00:00.000Z",
        "Europe/Rome",
        low_week_entries(),
        check=lambda result: require(
            result["isLowWeek"] is True and result["variant"] == "normal" and result["maxItemCount"] == 2,
            "low-week shape " + json.dumps({k: result[k] for k in ("variant", "isLowWeek", "maxItemCount", "moodCounts")}),
        ),
    )
    add(
        "not-enough-data",
        "2026-10-11T18:00:00.000Z",
        "Europe/Rome",
        not_enough_entries(),
        check=lambda result: require(
            result["variant"] == "notEnoughData" and result["maxItemCount"] == 1 and result["entryCount"] == 3,
            "not-enough-data",
        ),
    )
    add(
        "first-week",
        "2026-10-11T18:00:00.000Z",
        "Europe/Rome",
        first_week_entries(),
        check=lambda result: require(
            result["variant"] == "firstWeek" and result["maxItemCount"] == 1 and result["isLowWeek"] is False,
            "first-week",
        ),
    )
    dst_rows, dst_expected = dst_entries()
    add(
        "dst-week",
        "2026-10-25T19:00:00.000Z",
        "Europe/Rome",
        dst_rows,
        check=lambda result: require(
            all(result["dayparts"][entry_id] == part for entry_id, part in dst_expected.items()),
            "dst dayparts " + json.dumps({entry_id: result["dayparts"][entry_id] for entry_id in dst_expected}),
        ),
    )
    travel_rows, rome_id, york_id = travel_entries()
    add(
        "travel",
        "2026-10-11T18:00:00.000Z",
        "Europe/Rome",
        travel_rows,
        check=lambda result: require(
            result["dayparts"][rome_id] == "afternoon" and result["dayparts"][york_id] == "morning",
            "travel dayparts",
        ),
    )
    add(
        "no-location",
        "2026-10-11T18:00:00.000Z",
        "Europe/Rome",
        afternoon_dip_entries(),
        check=lambda result: require(
            result["firstItemLibraryID"] == "afternoon-dip"
            and all(
                not item["id"].startswith("place.")
                and not item["id"].startswith("awayFromHome")
                and not item["id"].startswith("tagAtPlace")
                for item in result["candidatesInclude"]
            ),
            "no-location candidates " + json.dumps(result["candidatesInclude"]),
        ),
    )
    for seed, name in ((1001, "noise-1"), (1002, "noise-2"), (1003, "noise-3")):
        add(
            name,
            "2026-10-11T18:00:00.000Z",
            "Europe/Rome",
            noise_entries(seed),
            check=lambda result: require(result["variant"] == "normal" and result["entryCount"] == 21, "noise week"),
        )
    return scenarios


def build_place_scenarios(config):
    scenarios = []
    triples = three_places()
    assigned = resolve_places(triples, [], config)
    expect = place_expect(triples, assigned)
    require(len(expect["sameCluster"]) == 3 and all(len(group) == 5 for group in expect["sameCluster"]), "three places")
    require(expect["unclustered"] == [], "three places noise stays clustered")
    scenarios.append(("three-places-noise", {"entries": triples, "places": []}, expect))

    accurate = accuracy_entries()
    assigned = resolve_places(accurate, [], config)
    expect = place_expect(accurate, assigned)
    require(len(expect["unclustered"]) == 2, "accuracy filter drops two fixes")
    require(len(expect["sameCluster"]) == 1 and len(expect["sameCluster"][0]) == 4, "accuracy filter keeps four")
    scenarios.append(("accuracy-filter", {"entries": accurate, "places": []}, expect))

    captured, labels = label_points(True)
    assigned = resolve_places(captured, labels, config)
    expect = place_expect(captured, assigned)
    named = [cluster_id for cluster_id in expect["clusterOf"].values() if cluster_id.startswith("p:")]
    unnamed = [cluster_id for cluster_id in expect["clusterOf"].values() if cluster_id.startswith("u:")]
    require(len(set(named)) == 1 and named.count(named[0]) == 4, "label captures the near points")
    require(len(set(unnamed)) == 1, "the far point stays unnamed")
    scenarios.append(("label-capture", {"entries": captured, "places": labels}, expect))

    deleted_rows, _ = label_points(False)
    near_only = deleted_rows[:-1]
    assigned = resolve_places(near_only, [], config)
    expect = place_expect(near_only, assigned)
    require(
        len(expect["sameCluster"]) == 1 and next(iter(expect["clusterOf"].values())).startswith("u:"),
        "deleted label falls back to an unnamed cluster",
    )
    scenarios.append(("label-deleted", {"entries": near_only, "places": []}, expect))
    return scenarios


def generate(target: Path, config: Config, library, tags):
    self_check(config, library, tags)
    patterns = target / "patterns"
    places = target / "places"
    periods = target / "periods"
    for directory in (patterns, places, periods):
        directory.mkdir(parents=True, exist_ok=True)
    written = []
    for name, payload, expect in build_pattern_scenarios(config, library):
        input_path = patterns / f"{name}.input.json"
        expect_path = patterns / f"{name}.expect.json"
        input_path.write_text(dump(payload), encoding="utf-8")
        expect_path.write_text(dump(expect), encoding="utf-8")
        written.extend([input_path, expect_path])
    for name, payload, expect in build_place_scenarios(config):
        input_path = places / f"{name}.input.json"
        expect_path = places / f"{name}.expect.json"
        input_path.write_text(dump(payload), encoding="utf-8")
        expect_path.write_text(dump(expect), encoding="utf-8")
        written.extend([input_path, expect_path])
    period_path = periods / "periods.json"
    period_path.write_text(dump(period_cases()), encoding="utf-8")
    written.append(period_path)
    missing = set(config) - config.used
    require(not missing, "config keys not read: " + ", ".join(sorted(missing)))
    return written


def compare(generated: Path, committed: Path):
    def files(root: Path):
        return {path.relative_to(root).as_posix(): path.read_text(encoding="utf-8") for path in sorted(root.rglob("*.json"))}

    left = files(generated)
    right = files(committed)
    if set(left) != set(right):
        raise SystemExit(
            "fixture set differs\nmissing: "
            + ", ".join(sorted(set(right) - set(left)))
            + "\nextra: "
            + ", ".join(sorted(set(left) - set(right)))
        )
    changed = [name for name in sorted(left) if left[name] != right[name]]
    if changed:
        raise SystemExit("fixtures out of date: " + ", ".join(changed) + "\nRun python3 tools/generate_fixtures.py")


def main():
    parser = argparse.ArgumentParser(description="Generate or check SmileProc fixtures")
    parser.add_argument("--check", action="store_true", help="fail if committed fixtures differ")
    args = parser.parse_args()
    config = Config(load_json(CONTENT / "pattern-config.json"))
    library = load_json(CONTENT / "pattern-library.en.json")
    tags = load_json(CONTENT / "emotion-tags.json")
    if args.check:
        with tempfile.TemporaryDirectory() as temp:
            generate(Path(temp), config, library, tags)
            compare(Path(temp), FIXTURES)
        print("fixtures match")
    else:
        generate(FIXTURES, config, library, tags)
        print("wrote fixtures")


if __name__ == "__main__":
    main()
