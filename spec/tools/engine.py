"""SmileProc reference engine.

Both the Swift SmileCore package and the Kotlin :core module must match this
module's results on every file under spec/fixtures/. Clarifications that the
implementation plan leaves open are locked here and in spec/engine-rules.md.
"""

from __future__ import annotations

import json
import math
from datetime import datetime, timedelta, timezone
from decimal import Decimal, ROUND_HALF_UP
from zoneinfo import ZoneInfo

EARTH_RADIUS_M = 6_371_000.0
DAYPARTS = ("morning", "afternoon", "evening", "night")
WEEKDAY_DAYPARTS = ("morning", "afternoon", "evening")
CORE_IDS = ("happy", "calm", "sad", "anxious", "angry", "tired", "surprised")
PLACE_KINDS = ("home", "work", "other")

CONFIG_KEYS = (
    "version",
    "minSegment",
    "minRest",
    "patternMinSegment",
    "patternMinRest",
    "minAbsDiff",
    "zHint",
    "zPattern",
    "sdFloor",
    "weekdayMinSegment",
    "weekdayMinDistinctDates",
    "weekdayMinAbsDiff",
    "weekdayMinDataDays",
    "tagMinLowTagged",
    "tagMinCount",
    "tagMinShare",
    "tagMinShareGap",
    "tagPatternCount",
    "tagAtPlaceMinShare",
    "fineTagDominance",
    "shortWeekThreshold",
    "maxItems",
    "maxItemsLowWeek",
    "notEnoughDataBelow",
    "firstWeekDays",
    "firstCardMinEntries",
    "lowWeekMinEntries",
    "lowWeekShare",
    "lowRunLength",
    "lowRunMinDays",
    "placeRadiusMeters",
    "placeMaxAccuracyMeters",
)


def round_half_away(value: float, places: int) -> float:
    """Half-away-from-zero after formatting the double to 12 decimal places.

    Swift and Kotlin must use the same bridge: format the IEEE-754 double with
    12 digits after the decimal, then round half away from zero to `places`.
    """
    quant = Decimal("1").scaleb(-places)
    decimal_value = Decimal(format(float(value), ".12f"))
    return float(decimal_value.quantize(quant, rounding=ROUND_HALF_UP))


def r6(value):
    if value is None:
        return None
    return round_half_away(float(value), 6)


def r9(value: float) -> float:
    return round_half_away(float(value), 9)


def parse_instant(text: str) -> datetime:
    if text.endswith("Z"):
        text = text[:-1] + "+00:00"
    dt = datetime.fromisoformat(text)
    if dt.tzinfo is None:
        raise ValueError(f"timestamp needs a zone: {text}")
    return dt.astimezone(timezone.utc)


def format_instant(dt: datetime) -> str:
    dt = dt.astimezone(timezone.utc)
    millis = dt.microsecond // 1000
    return dt.strftime("%Y-%m-%dT%H:%M:%S.") + f"{millis:03d}Z"


def zone_of(name: str) -> ZoneInfo:
    return ZoneInfo(name)


def local_dt(instant: datetime, zone_name: str) -> datetime:
    return instant.astimezone(zone_of(zone_name))


def make_local(year: int, month: int, day: int, hour: int, minute: int, zone_name: str) -> datetime:
    return datetime(year, month, day, hour, minute, tzinfo=zone_of(zone_name))


def daypart_of(instant: datetime, zone_name: str) -> str:
    hour = local_dt(instant, zone_name).hour
    if 5 <= hour <= 11:
        return "morning"
    if 12 <= hour <= 16:
        return "afternoon"
    if 17 <= hour <= 21:
        return "evening"
    return "night"


def iso_weekday(instant: datetime, zone_name: str) -> int:
    return local_dt(instant, zone_name).isoweekday()


def local_date(instant: datetime, zone_name: str):
    return local_dt(instant, zone_name).date()


def haversine_m(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    p1 = math.radians(lat1)
    p2 = math.radians(lat2)
    dlat = math.radians(lat2 - lat1)
    dlon = math.radians(lon2 - lon1)
    a = math.sin(dlat / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dlon / 2) ** 2
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(max(0.0, 1 - a)))
    return EARTH_RADIUS_M * c


def mean(values: list[float]) -> float:
    return sum(values) / len(values)


def sample_variance(values: list[float]) -> float:
    n = len(values)
    if n <= 1:
        return 0.0
    m = mean(values)
    return sum((v - m) ** 2 for v in values) / (n - 1)


def compare_moods(segment: list[float], rest: list[float], sd_floor: float) -> dict:
    n_seg = len(segment)
    n_rest = len(rest)
    mean_seg = mean(segment)
    mean_rest = mean(rest)
    diff = mean_seg - mean_rest
    var_seg = sample_variance(segment)
    var_rest = sample_variance(rest)
    denom_n = n_seg + n_rest - 2
    if denom_n <= 0:
        sd_pooled = 0.0
    else:
        sd_pooled = math.sqrt(((n_seg - 1) * var_seg + (n_rest - 1) * var_rest) / denom_n)
    z = diff / (max(sd_pooled, sd_floor) * math.sqrt(1 / n_seg + 1 / n_rest))
    return {
        "nSegment": n_seg,
        "nRest": n_rest,
        "meanSegment": mean_seg,
        "meanRest": mean_rest,
        "diff": diff,
        "z": z,
    }


def card_period(now: datetime, zone_name: str, weekday: int, hour: int, minute: int) -> dict:
    """Latest local card time <= now, and the same clock time 7 calendar days earlier."""
    zone = zone_of(zone_name)
    local = now.astimezone(zone)
    delta_days = (local.isoweekday() - weekday) % 7
    end_date = local.date() - timedelta(days=delta_days)
    end_local = datetime(end_date.year, end_date.month, end_date.day, hour, minute, tzinfo=zone)
    if end_local > local:
        end_date = end_date - timedelta(days=7)
        end_local = datetime(end_date.year, end_date.month, end_date.day, hour, minute, tzinfo=zone)
    start_date = end_date - timedelta(days=7)
    start_local = datetime(start_date.year, start_date.month, start_date.day, hour, minute, tzinfo=zone)
    return {
        "periodStart": start_local.astimezone(timezone.utc),
        "periodEnd": end_local.astimezone(timezone.utc),
        "zone": zone_name,
        "hour": hour,
        "minute": minute,
    }


def shift_local(instant: datetime, zone_name: str, days: int, hour: int, minute: int) -> datetime:
    zone = zone_of(zone_name)
    shifted = instant.astimezone(zone).date() - timedelta(days=days)
    return datetime(shifted.year, shifted.month, shifted.day, hour, minute, tzinfo=zone).astimezone(timezone.utc)


def sort_entries(entries: list[dict]) -> list[dict]:
    return sorted(entries, key=lambda e: (e["_created"], e["id"]))


def core_of(tag: str) -> str:
    return tag.split(".", 1)[0]


def entry_has_core(entry: dict, core: str) -> bool:
    return any(tag == core or tag.startswith(core + ".") for tag in entry["tags"])


def resolve_places(entries: list[dict], labels: list[dict], config: dict) -> dict:
    radius = float(config["placeRadiusMeters"])
    max_acc = float(config["placeMaxAccuracyMeters"])
    eligible = []
    for entry in sort_entries(entries):
        loc = entry.get("location")
        if not loc:
            continue
        if float(loc["horizontalAccuracy"]) <= max_acc:
            eligible.append(entry)

    assignment: dict[str, str] = {}
    named_members: dict[str, list[dict]] = {}
    labels_by_id = {label["id"]: label for label in labels}

    for entry in eligible:
        loc = entry["location"]
        best_id = None
        best_dist = None
        for label in labels:
            dist = haversine_m(
                loc["latitude"], loc["longitude"], label["anchorLatitude"], label["anchorLongitude"]
            )
            cluster_id = "p:" + label["id"]
            if dist <= radius and (best_dist is None or dist < best_dist or (dist == best_dist and cluster_id < best_id)):
                best_dist = dist
                best_id = cluster_id
        if best_id is not None:
            assignment[entry["id"]] = best_id
            named_members.setdefault(best_id, []).append(entry)

    unnamed: dict[str, list[dict]] = {}
    for entry in eligible:
        if entry["id"] in assignment:
            continue
        loc = entry["location"]
        best_id = None
        best_dist = None
        for cluster_id, members in unnamed.items():
            lat = mean([m["location"]["latitude"] for m in members])
            lon = mean([m["location"]["longitude"] for m in members])
            dist = haversine_m(loc["latitude"], loc["longitude"], lat, lon)
            if dist <= radius and (best_dist is None or dist < best_dist or (dist == best_dist and cluster_id < best_id)):
                best_dist = dist
                best_id = cluster_id
        if best_id is None:
            best_id = "u:" + entry["id"]
            unnamed[best_id] = [entry]
        else:
            unnamed[best_id].append(entry)
        assignment[entry["id"]] = best_id

    if unnamed:
        centroids = {}
        for cluster_id, members in unnamed.items():
            centroids[cluster_id] = (
                mean([m["location"]["latitude"] for m in members]),
                mean([m["location"]["longitude"] for m in members]),
            )
        rebuilt: dict[str, list[dict]] = {cid: [] for cid in unnamed}
        for entry in eligible:
            current = assignment.get(entry["id"])
            if current is None or not current.startswith("u:"):
                continue
            loc = entry["location"]
            best_id = None
            best_dist = None
            for cluster_id, (lat, lon) in centroids.items():
                dist = haversine_m(loc["latitude"], loc["longitude"], lat, lon)
                if dist <= radius and (
                    best_dist is None or dist < best_dist or (dist == best_dist and cluster_id < best_id)
                ):
                    best_dist = dist
                    best_id = cluster_id
            if best_id is None:
                best_id = current
            rebuilt.setdefault(best_id, []).append(entry)
        final_unnamed: dict[str, list[dict]] = {}
        for members in rebuilt.values():
            if not members:
                continue
            earliest = min(members, key=lambda m: (m["_created"], m["id"]))
            final_id = "u:" + earliest["id"]
            final_unnamed.setdefault(final_id, []).extend(members)
        # If two rebuilt groups collapse to the same earliest id, merge them.
        for cluster_id, members in final_unnamed.items():
            for member in members:
                assignment[member["id"]] = cluster_id
        unnamed = final_unnamed

    clusters = []
    seen = []
    for entry in eligible:
        cid = assignment.get(entry["id"])
        if cid and cid not in seen:
            seen.append(cid)
    for cid in sorted(set(assignment.values())):
        members = [e for e in eligible if assignment.get(e["id"]) == cid]
        if not members:
            continue
        lat = mean([m["location"]["latitude"] for m in members])
        lon = mean([m["location"]["longitude"] for m in members])
        label = None
        if cid.startswith("p:"):
            label = labels_by_id.get(cid[2:])
        clusters.append(
            {
                "id": cid,
                "centroidLatitude": lat,
                "centroidLongitude": lon,
                "memberIDs": [m["id"] for m in sorted(members, key=lambda m: (m["_created"], m["id"]))],
                "labelID": label["id"] if label else None,
            }
        )
    return {"entryClusters": {eid: assignment[eid] for eid in sorted(assignment)}, "clusters": clusters}


def cluster_map(assignment: dict) -> dict[str, str]:
    return assignment["entryClusters"]


def _moods(entries: list[dict]) -> list[float]:
    return [float(e["mood"]) for e in entries]


def _distinct_dates(entries: list[dict]) -> int:
    return len({local_date(e["_created"], e["timeZoneID"]) for e in entries})


def _family(detector: str) -> str:
    return {
        "timeOfDay": "time",
        "weekday": "weekday",
        "weekdayDaypart": "weekday",
        "place": "place",
        "awayFromHome": "place",
        "tagOnLow": "tag",
        "tagAtPlace": "tag",
    }[detector]


def _candidate_mean(detector, ident, params, stats, supporting, config, weekday_rules: bool) -> dict | None:
    diff = stats["diff"]
    z = stats["z"]
    n_seg = stats["nSegment"]
    n_rest = stats["nRest"]
    distinct_ok_pattern = (not weekday_rules) or _distinct_dates(supporting) >= int(config["weekdayMinDistinctDates"])
    distinct_ok_hint = (not weekday_rules) or _distinct_dates(supporting) >= 1
    if weekday_rules:
        pattern_n = int(config["weekdayMinSegment"])
        pattern_diff = float(config["weekdayMinAbsDiff"])
    else:
        pattern_n = int(config["patternMinSegment"])
        pattern_diff = float(config["minAbsDiff"])
    is_pattern = (
        n_seg >= pattern_n
        and n_rest >= int(config["patternMinRest"])
        and abs(diff) >= pattern_diff
        and abs(z) >= float(config["zPattern"])
        and distinct_ok_pattern
    )
    is_hint = (
        n_seg >= int(config["minSegment"])
        and n_rest >= int(config["minRest"])
        and abs(diff) >= float(config["minAbsDiff"])
        and abs(z) >= float(config["zHint"])
        and distinct_ok_hint
    )
    if not is_pattern and not is_hint:
        return None
    confidence = "pattern" if is_pattern else "earlyHint"
    direction = "lower" if diff < 0 else "higher"
    score = abs(diff) * min(n_seg, 10) / 10 + (0.2 if confidence == "pattern" else 0.0)
    full_params = dict(params)
    if "segment" in full_params and detector == "timeOfDay":
        pass
    ident = ident.format(dir=direction)
    return {
        "id": ident,
        "detector": detector,
        "family": _family(detector),
        "direction": direction,
        "params": full_params,
        "nSegment": n_seg,
        "nRest": n_rest,
        "meanSegment": stats["meanSegment"],
        "meanRest": stats["meanRest"],
        "diff": diff,
        "z": z,
        "score": score,
        "confidence": confidence,
        "supportingEntryIDs": [e["id"] for e in supporting],
    }


def _candidate_tag(detector, tag, params, count, share_seg, share_rest, n_rest, supporting, config) -> dict | None:
    if count < int(config["tagMinCount"]):
        return None
    confidence = "pattern" if count >= int(config["tagPatternCount"]) else "earlyHint"
    score = (share_seg - share_rest) * min(count, 10) / 10 + (0.2 if confidence == "pattern" else 0.0)
    return {
        "id": f"{detector}.{tag}" + (f".{params['clusterID']}" if "clusterID" in params else ""),
        "detector": detector,
        "family": _family(detector),
        "direction": None,
        "params": {"tag": tag, **params},
        "nSegment": count,
        "nRest": n_rest,
        "meanSegment": None,
        "meanRest": None,
        "diff": None,
        "z": None,
        "score": score,
        "confidence": confidence,
        "supportingEntryIDs": [e["id"] for e in supporting],
    }


def dominant_tag(core: str, hits: list[dict], config: dict) -> str:
    if not hits:
        return core
    counts: dict[str, int] = {}
    for entry in hits:
        for tag in entry["tags"]:
            if tag.startswith(core + "."):
                counts[tag] = counts.get(tag, 0) + 1
    if not counts:
        return core
    best = sorted(counts.items(), key=lambda item: (-item[1], item[0]))[0]
    if best[1] / len(hits) >= float(config["fineTagDominance"]):
        return best[0]
    return core


def detect(entries: list[dict], labels: list[dict], config: dict, period: dict) -> list[dict]:
    zone = period["zone"]
    start = period["periodStart"]
    end = period["periodEnd"]
    hour = period["hour"]
    minute = period["minute"]
    ordered = sort_entries(entries)
    period_entries = [e for e in ordered if start <= e["_created"] < end]
    card_entries = period_entries
    window_kind = "oneWeek"
    if len(period_entries) < int(config["shortWeekThreshold"]):
        window_start = shift_local(end, zone, 14, hour, minute)
        card_entries = [e for e in ordered if window_start <= e["_created"] < end]
        window_kind = "twoWeeks"
    weekday_start = shift_local(end, zone, 28, hour, minute)
    weekday_entries = [e for e in ordered if weekday_start <= e["_created"] < end]
    first = ordered[0] if ordered else None
    weekday_ok = False
    if first is not None:
        age_days = (end - first["_created"]).total_seconds() / 86400
        weekday_ok = age_days >= float(config["weekdayMinDataDays"])

    places = resolve_places(ordered, labels, config)
    clusters = cluster_map(places)
    candidates = []

    def add_mean(detector, ident, params, segment, rest, weekday_rules=False):
        if not segment or not rest:
            return
        stats = compare_moods(_moods(segment), _moods(rest), float(config["sdFloor"]))
        found = _candidate_mean(detector, ident, params, stats, segment, config, weekday_rules)
        if found:
            candidates.append(found)

    for part in DAYPARTS:
        seg = [e for e in card_entries if daypart_of(e["_created"], e["timeZoneID"]) == part]
        rest = [e for e in card_entries if daypart_of(e["_created"], e["timeZoneID"]) != part]
        add_mean("timeOfDay", "timeOfDay.{dir}." + part, {"segment": part}, seg, rest)

    if weekday_ok:
        for day in range(1, 8):
            seg = [e for e in weekday_entries if iso_weekday(e["_created"], e["timeZoneID"]) == day]
            rest = [e for e in weekday_entries if iso_weekday(e["_created"], e["timeZoneID"]) != day]
            add_mean("weekday", "weekday.{dir}." + str(day), {"weekday": str(day)}, seg, rest, True)
            for part in WEEKDAY_DAYPARTS:
                seg = [
                    e
                    for e in weekday_entries
                    if iso_weekday(e["_created"], e["timeZoneID"]) == day
                    and daypart_of(e["_created"], e["timeZoneID"]) == part
                ]
                rest = [e for e in weekday_entries if e not in seg]
                add_mean(
                    "weekdayDaypart",
                    f"weekdayDaypart.{{dir}}.{day}.{part}",
                    {"weekday": str(day), "segment": part},
                    seg,
                    rest,
                    True,
                )

    cluster_ids = sorted({clusters[e["id"]] for e in card_entries if e["id"] in clusters})
    for cid in cluster_ids:
        seg = [e for e in card_entries if clusters.get(e["id"]) == cid]
        rest = [e for e in card_entries if e["id"] in clusters and clusters[e["id"]] != cid]
        label = next((item for item in labels if cid == "p:" + item["id"]), None)
        params = {"clusterID": cid}
        if label:
            params["placeKind"] = label["kind"]
        add_mean("place", "place.{dir}." + cid, params, seg, rest)

    home_labels = sorted((label for label in labels if label["kind"] == "home"), key=lambda label: label["id"])
    if home_labels:
        home_id = "p:" + home_labels[0]["id"]
        seg = [e for e in card_entries if e["id"] in clusters and clusters[e["id"]] != home_id]
        rest = [e for e in card_entries if clusters.get(e["id"]) == home_id]
        add_mean("awayFromHome", "awayFromHome.{dir}", {}, seg, rest)

    low_tagged = [e for e in card_entries if e["mood"] == 1 and e["tags"]]
    if len(low_tagged) >= int(config["tagMinLowTagged"]):
        okay_good = [e for e in card_entries if e["mood"] in (2, 3) and e["tags"]]
        for core in CORE_IDS:
            hits = [e for e in low_tagged if entry_has_core(e, core)]
            count = len(hits)
            share_seg = count / len(low_tagged)
            if okay_good:
                share_rest = len([e for e in okay_good if entry_has_core(e, core)]) / len(okay_good)
            else:
                share_rest = 0.0
            if count < int(config["tagMinCount"]) or share_seg < float(config["tagMinShare"]):
                continue
            if share_seg - share_rest < float(config["tagMinShareGap"]):
                continue
            tag = dominant_tag(core, hits, config)
            found = _candidate_tag("tagOnLow", tag, {}, count, share_seg, share_rest, len(okay_good), hits, config)
            if found:
                candidates.append(found)

    for cid in cluster_ids:
        in_cluster = [e for e in card_entries if clusters.get(e["id"]) == cid]
        tagged = [e for e in in_cluster if e["tags"]]
        if not tagged:
            continue
        elsewhere = [e for e in card_entries if e["id"] in clusters and clusters[e["id"]] != cid and e["tags"]]
        for core in CORE_IDS:
            hits = [e for e in tagged if entry_has_core(e, core)]
            count = len(hits)
            share_seg = count / len(tagged)
            if count < int(config["tagMinCount"]) or share_seg < float(config["tagAtPlaceMinShare"]):
                continue
            if elsewhere:
                share_rest = len([e for e in elsewhere if entry_has_core(e, core)]) / len(elsewhere)
            else:
                share_rest = 0.0
            if share_seg - share_rest < float(config["tagMinShareGap"]):
                continue
            tag = dominant_tag(core, hits, config)
            found = _candidate_tag(
                "tagAtPlace",
                tag,
                {"clusterID": cid},
                count,
                share_seg,
                share_rest,
                len(elsewhere),
                hits,
                config,
            )
            if found:
                candidates.append(found)

    def sort_key(item: dict):
        rank = 0 if item["confidence"] == "pattern" else 1
        return (rank, -r9(item["score"]), item["id"])

    candidates.sort(key=sort_key)
    return candidates, window_kind, period_entries, card_entries, places


def match_library(pattern: dict, library: list[dict]) -> str | None:
    best = None
    best_keys = -1
    for index, entry in enumerate(library):
        match = entry["match"]
        if match.get("detector") != pattern["detector"]:
            continue
        ok = True
        for key, value in match.items():
            if key == "detector":
                continue
            if key == "tag":
                tag = pattern["params"].get("tag")
                if tag is None or not (value == tag or value == core_of(tag)):
                    ok = False
                    break
            else:
                actual = pattern["params"].get(key) if key != "direction" else pattern.get("direction")
                if key == "direction":
                    actual = pattern.get("direction")
                elif key in pattern["params"]:
                    actual = pattern["params"][key]
                else:
                    actual = None
                if str(actual) != str(value):
                    ok = False
                    break
        if not ok:
            continue
        key_count = len(match)
        if key_count > best_keys:
            best = entry["id"]
            best_keys = key_count
    return best


def variant_for(period_entries: list[dict], all_entries: list[dict], period_end: datetime, config: dict) -> str:
    if len(period_entries) < int(config["notEnoughDataBelow"]):
        return "notEnoughData"
    if not all_entries:
        return "notEnoughData"
    first = sort_entries(all_entries)[0]
    age_days = (period_end - first["_created"]).total_seconds() / 86400
    if age_days <= float(config["firstWeekDays"]):
        return "firstWeek"
    return "normal"


def item_limit(variant: str, is_low_week: bool, config: dict) -> int:
    if variant == "notEnoughData" or variant == "firstWeek":
        limit = 1
    else:
        limit = int(config["maxItems"])
    if is_low_week:
        limit = min(limit, int(config["maxItemsLowWeek"]))
    return limit


def is_low_week(period_entries: list[dict], config: dict) -> bool:
    if len(period_entries) < int(config["lowWeekMinEntries"]):
        return False
    lows = sum(1 for e in period_entries if e["mood"] == 1)
    return lows / len(period_entries) >= float(config["lowWeekShare"])


def select_items(candidates: list[dict], limit: int) -> list[dict]:
    patterns = [c for c in candidates if c["confidence"] == "pattern"]
    hints = [c for c in candidates if c["confidence"] == "earlyHint"]
    picked: list[dict] = []
    families: set[str] = set()

    def take(pool):
        for candidate in pool:
            if len(picked) >= limit:
                return
            if candidate["family"] in families:
                continue
            picked.append(candidate)
            families.add(candidate["family"])

    take(patterns)
    take(hints)
    if limit >= 2 and picked and not any(item.get("direction") == "higher" for item in picked):
        replacement = next((c for c in patterns if c.get("direction") == "higher" and c not in picked), None)
        if replacement is not None:
            picked[-1] = replacement
    return picked


def mood_counts(entries: list[dict]) -> list[int]:
    return [sum(1 for e in entries if e["mood"] == mood) for mood in (1, 2, 3)]


def week_strip(period_entries: list[dict], period_end: datetime, zone_name: str) -> list[list[int]]:
    end_local = period_end.astimezone(zone_of(zone_name)) - timedelta(milliseconds=1)
    monday = end_local.date() - timedelta(days=end_local.isoweekday() - 1)
    days = []
    for index in range(7):
        day = monday + timedelta(days=index)
        on_day = [e for e in period_entries if local_date(e["_created"], e["timeZoneID"]) == day]
        days.append(mood_counts(on_day))
    return days


def build_weekly_card(entries: list[dict], labels: list[dict], config: dict, library: list[dict], now: datetime, zone_name: str, weekday: int, hour: int, minute: int) -> dict:
    prepared = []
    for entry in entries:
        copied = dict(entry)
        copied["_created"] = parse_instant(entry["createdAt"])
        copied["tags"] = list(entry.get("tags") or [])
        if copied.get("comment") == "":
            copied["comment"] = None
        prepared.append(copied)
    period = card_period(now, zone_name, weekday, hour, minute)
    candidates, window_kind, period_entries, _card_entries, places = detect(prepared, labels, config, period)
    variant = variant_for(period_entries, prepared, period["periodEnd"], config)
    low = is_low_week(period_entries, config)
    limit = item_limit(variant, low, config)
    picked = select_items(candidates, limit)
    items = []
    for candidate in picked:
        library_id = match_library(candidate, library)
        if library_id is None:
            continue
        items.append({"patternID": candidate["id"], "libraryID": library_id})
    return {
        "periodStart": format_instant(period["periodStart"]),
        "periodEnd": format_instant(period["periodEnd"]),
        "windowKind": window_kind,
        "entryCount": len(period_entries),
        "moodCounts": mood_counts(period_entries),
        "days": week_strip(period_entries, period["periodEnd"], zone_name),
        "isLowWeek": low,
        "variant": variant,
        "maxItemCount": limit,
        "items": items,
        "candidates": [_public_candidate(c) for c in candidates],
        "dayparts": {e["id"]: daypart_of(e["_created"], e["timeZoneID"]) for e in prepared},
        "places": places,
    }


def _public_candidate(candidate: dict) -> dict:
    return {
        "id": candidate["id"],
        "confidence": candidate["confidence"],
        "nSegment": candidate["nSegment"],
        "nRest": candidate["nRest"],
        "meanSegment": r6(candidate["meanSegment"]),
        "meanRest": r6(candidate["meanRest"]),
        "diff": r6(candidate["diff"]),
        "z": r6(candidate["z"]),
        "score": r6(candidate["score"]),
    }


def low_run_armed(entries: list[dict], config: dict, dismissed_entry_id: str | None) -> bool:
    prepared = []
    for entry in entries:
        copied = dict(entry)
        copied["_created"] = parse_instant(entry["createdAt"])
        prepared.append(copied)
    ordered = sort_entries(prepared)
    if dismissed_entry_id:
        index = next((i for i, e in enumerate(ordered) if e["id"] == dismissed_entry_id), None)
        if index is not None and all(e["mood"] == 1 for e in ordered[index + 1 :]):
            return False
    length = int(config["lowRunLength"])
    if len(ordered) < length:
        return False
    tail = ordered[-length:]
    if any(e["mood"] != 1 for e in tail):
        return False
    dates = {local_date(e["_created"], e["timeZoneID"]) for e in tail}
    return len(dates) >= int(config["lowRunMinDays"])


def should_build_card(entries: list[dict], config: dict, now: datetime, zone_name: str, weekday: int, hour: int, minute: int, existing_period_ends: list[str]) -> bool:
    if len(entries) < int(config["firstCardMinEntries"]):
        return False
    prepared = [(e["id"], parse_instant(e["createdAt"])) for e in entries]
    prepared.sort(key=lambda item: (item[1], item[0]))
    period = card_period(now, zone_name, weekday, hour, minute)
    if period["periodEnd"] < prepared[0][1]:
        return False
    return format_instant(period["periodEnd"]) not in existing_period_ends


def library_coverage(library: list[dict]) -> list[dict]:
    rows = []
    detectors = {
        "timeOfDay": {"directions": ("lower", "higher"), "segments": DAYPARTS, "weekdays": (None,), "placeKinds": (None,), "tags": (None,)},
        "weekday": {"directions": ("lower", "higher"), "segments": (None,), "weekdays": tuple(str(d) for d in range(1, 8)), "placeKinds": (None,), "tags": (None,)},
        "weekdayDaypart": {"directions": ("lower", "higher"), "segments": WEEKDAY_DAYPARTS, "weekdays": tuple(str(d) for d in range(1, 8)), "placeKinds": (None,), "tags": (None,)},
        "place": {"directions": ("lower", "higher"), "segments": (None,), "weekdays": (None,), "placeKinds": PLACE_KINDS, "tags": (None,)},
        "awayFromHome": {"directions": ("lower", "higher"), "segments": (None,), "weekdays": (None,), "placeKinds": (None,), "tags": (None,)},
        "tagOnLow": {"directions": (None,), "segments": (None,), "weekdays": (None,), "placeKinds": (None,), "tags": CORE_IDS},
        "tagAtPlace": {"directions": (None,), "segments": (None,), "weekdays": (None,), "placeKinds": (None,), "tags": CORE_IDS},
    }
    for detector, axes in detectors.items():
        for direction in axes["directions"]:
            for segment in axes["segments"]:
                for weekday in axes["weekdays"]:
                    for place_kind in axes["placeKinds"]:
                        for tag in axes["tags"]:
                            pattern = {
                                "detector": detector,
                                "direction": direction,
                                "params": {},
                            }
                            if segment:
                                pattern["params"]["segment"] = segment
                            if weekday:
                                pattern["params"]["weekday"] = weekday
                            if place_kind:
                                pattern["params"]["placeKind"] = place_kind
                            if tag:
                                pattern["params"]["tag"] = tag
                            library_id = match_library(pattern, library)
                            rows.append(
                                {
                                    "detector": detector,
                                    "direction": direction,
                                    "segment": segment,
                                    "weekday": weekday,
                                    "placeKind": place_kind,
                                    "tag": tag,
                                    "libraryID": library_id,
                                }
                            )
    return rows


def prepare_input_entries(entries: list[dict]) -> list[dict]:
    prepared = []
    for entry in entries:
        copied = dict(entry)
        copied["_created"] = parse_instant(entry["createdAt"])
        copied["tags"] = list(entry.get("tags") or [])
        prepared.append(copied)
    return prepared
