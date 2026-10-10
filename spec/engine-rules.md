# SmileProc engine rules

`spec/tools/engine.py` is the reference. Swift `SmileCore` and Kotlin `:core` must reproduce every `expect.json` and `golden.json` under `spec/fixtures/`. If this file and the Python disagree, Python wins and this file should be corrected.

Do not read the system clock, time zone, or a random generator inside the engine. Tests pass them in.

## Rounding

Format the IEEE-754 double with 12 digits after the decimal, then round half away from zero.

- Sort key: confidence (`pattern` before `earlyHint`), then score rounded to 9 decimals descending, then `id` ascending.
- Golden numbers (`meanSegment`, `meanRest`, `diff`, `z`, `score`, place centroids) are rounded to 6 decimals. Tag patterns use null for the four mean fields.

## Card period

`periodEnd` is the latest local occurrence of the weekly card time (default Sunday 19:00, ISO weekday 7) that is `<= now`, in the device zone. `periodStart` is that same clock time 7 calendar days earlier, not `7 * 24h`. Fixture: `spec/fixtures/periods/periods.json`, including the Europe/Rome fallback on 25 Oct 2026.

`shouldBuildCard` is true only when all of these hold:

- total entries `>= firstCardMinEntries` (5)
- `periodEnd >=` the earliest entry's `createdAt`
- that `periodEnd` is not already stored

See `spec/fixtures/periods/build-gate.json`.

## Windows

Period entries are `createdAt >= periodStart` and `< periodEnd`.

- Card window starts as the period. If it has fewer than `shortWeekThreshold` entries, extend the start to 14 calendar days before `periodEnd` (same clock time) and set `windowKind = twoWeeks`. Otherwise `oneWeek`.
- Weekday window is 28 calendar days ending at `periodEnd`.
- Weekday and weekday-daypart detectors are skipped unless the earliest entry ever is at least `weekdayMinDataDays` (14) before `periodEnd`, measured in exact 86400-second days.

`entryCount`, `moodCounts`, `isLowWeek`, and the day strip use period entries. Detectors use the card window, except weekday detectors, which use the weekday window.

## Dayparts and dates

Computed in the entry's own `timeZoneID`.

- morning 05:00–11:59, afternoon 12:00–16:59, evening 17:00–21:59, night 22:00–04:59
- ISO weekday: Monday 1 … Sunday 7

The day strip is Monday→Sunday (indexes 0…6) of the ISO week that contains `periodEnd - 1ms` in the device zone. Each cell is `[low, okay, good]` for period entries whose own-zone local date is that date.

## Mean comparison

```
var(x)    = sum((x - mean)^2) / (n - 1)    # 0 when n = 1
diff      = mean(segment) - mean(rest)
sdPooled  = sqrt(((nSeg-1)*var(seg) + (nRest-1)*var(rest)) / (nSeg + nRest - 2))
z         = diff / (max(sdPooled, sdFloor) * sqrt(1/nSeg + 1/nRest))
score     = |diff| * min(nSeg, 10)/10 + (pattern ? 0.2 : 0)
direction = diff < 0 ? lower : higher
```

Skip a split when either side is empty.

Pattern vs early hint:

| | time, place, awayFromHome | weekday, weekdayDaypart |
|---|---|---|
| pattern nSeg | `patternMinSegment` | `weekdayMinSegment` |
| pattern \|diff\| | `minAbsDiff` | `weekdayMinAbsDiff` |
| pattern extra | — | distinct own-zone dates `>= weekdayMinDistinctDates` |
| pattern nRest / \|z\| | `patternMinRest` / `zPattern` | same |
| early hint | `minSegment`, `minRest`, `minAbsDiff`, `zHint` | same, plus at least 1 distinct date |

`weekdayDaypart` uses morning, afternoon, and evening only. Its rest is every other entry in the weekday window.

`awayFromHome` requires a `kind == home` label (lowest id if several). Segment = located card-window entries outside that label's cluster. Rest = entries in it.

`place` compares one cluster with other located card-window entries. Params include `clusterID` and, for a named cluster, `placeKind`.

Ids:

- `timeOfDay.<dir>.<daypart>`
- `weekday.<dir>.<1-7>`
- `weekdayDaypart.<dir>.<1-7>.<daypart>`
- `place.<dir>.<clusterID>`
- `awayFromHome.<dir>`

## Tags

An entry has core `c` when a tag equals `c` or starts with `c.`. Counts are at core level. If one fine tag is on at least `fineTagDominance` of the hits, the reported tag is that fine id (ties: higher count, then id ascending).

`tagOnLow` (card window): only if low entries that have tags `>= tagMinLowTagged`. A core qualifies when its count is `>= tagMinCount`, its share of those low tagged entries is `>= tagMinShare`, and that share is at least `tagMinShareGap` above its share of Okay/Good tagged entries (0 when there are none). Pattern confidence when count `>= tagPatternCount`, otherwise early hint.

`tagAtPlace`: per cluster, count `>= tagMinCount` and share of the cluster's tagged entries `>= tagAtPlaceMinShare`, and that share is at least `tagMinShareGap` above the share in other clustered tagged entries.

Tag score = `(shareSegment - shareRest) * min(count, 10)/10 + (pattern ? 0.2 : 0)`. `nSegment` is the hit count. `nRest` is the rest population size. No direction.

Ids: `tagOnLow.<tag>` and `tagAtPlace.<tag>.<clusterID>`.

## Selection

1. Walk candidates in the sort order above. Take patterns first, then early hints. At most one per family. Stop at the limit.
2. Families: `timeOfDay` → time; `weekday` and `weekdayDaypart` → weekday; `place` and `awayFromHome` → place; both tag detectors → tag.
3. Limit is `maxItems` (3). `notEnoughData` and `firstWeek` cap it at 1. `isLowWeek` caps it at `maxItemsLowWeek` (2). Apply the lowest cap.
4. Balance, only when the limit is at least 2: if no picked item has direction `higher` and some unpicked `pattern` with direction `higher` exists, replace the last pick with the first such candidate. A duplicate family is allowed only as the result of this replacement.
5. Drop a pick that matches no library row.

Variant, checked in this order: period count `< notEnoughDataBelow` → `notEnoughData`; else the earliest entry is within `firstWeekDays` of `periodEnd` (`ageDays <= firstWeekDays`) → `firstWeek`; else `normal`.

`isLowWeek`: period count `>= lowWeekMinEntries` and the Low share `>= lowWeekShare`. Independent of variant.

## Library matching

A library row matches when `detector` is equal and every other key it sets equals the pattern (`direction`, `segment`, `weekday`, `placeKind`). `tag` matches the pattern tag or that tag's core. The row with the most match keys wins. Ties keep the earlier row in `pattern-library.en.json`.

`spec/fixtures/library/coverage.json` enumerates every detector × direction × segment / weekday / place kind / tag core that the product can produce. Each row has a library id.

When substituting copy, `{tag}` is the string `tag.<id>` lowercased. `{segment}` is the lowercase daypart. `{weekday}` is `weekday.<n>`. `{place}` is the current label name, or "an unnamed place", or "one of your places" when the cluster is gone.

## Places

Haversine, R = 6,371,000 m. Eligible entries have `horizontalAccuracy <= placeMaxAccuracyMeters` and are processed in `(createdAt, id)` order.

1. Named pass: nearest label anchor within `placeRadiusMeters`. Cluster id `p:<labelID>`. Distance ties: lower cluster id.
2. Unnamed pass: join the nearest unnamed cluster whose running centroid (arithmetic mean of member lat/lon) is within the radius, else start `u:<entryID>`. Ties: lower cluster id.
3. Stabilise once from those centroids. Reassign each unnamed member to the nearest centroid within the radius, or leave it in its own cluster. Drop empty clusters. Rename to `u:<earliest member id>` by `(createdAt, id)`.
4. Everyone else is unclustered.

"Within" means `<=`. Naming UI: if another label's anchor is within the radius of the cluster centroid, ask "Is this {name}?" and on yes move that label's anchor to the combined centroid instead of creating a label.

## Low-mood run

Armed when the last `lowRunLength` entries are all Low and span at least `lowRunMinDays` own-zone dates. Dismissal stores that latest entry id. The card stays off while every entry after the dismissed one is still Low. A later non-Low entry clears the dismissal, and the tail rule applies again. Cases: `spec/fixtures/lowmood/cases.json`.

## Canonical JSON

UTF-8, compact, no extra whitespace. Key order:

- Entry: `id`, `createdAt`, `updatedAt`, `timeZoneID`, `mood`, `tags`, `comment`, `location`, `source`
- Location: `latitude`, `longitude`, `horizontalAccuracy`, `capturedAt`, `kind`
- Place: `id`, `name`, `kind`, `anchorLatitude`, `anchorLongitude`, `createdAt`

UUIDs are lowercase. Timestamps are UTC `yyyy-MM-ddTHH:mm:ss.SSSZ`. `comment` is null when missing or blank, never `""`. Numbers use Python `json.dumps` formatting (`12.0` stays `12.0`, `9.19` stays `9.19`). The files in `spec/fixtures/canonical/` are exact documents; compare them without a trailing newline.

Mood is the integer 1, 2, or 3. Tags: at most 3, ids from `emotion-tags.json`, order preserved. Comment max 1000 characters after trim. Sources: `app`, `lockScreenWidget`, `homeScreenWidget`, `notification`. Location kind: `fresh` or `cached`.

## Export

```
key = PBKDF2-HMAC-SHA256(password UTF-8, salt, iterations, 32 bytes)
payload = base64(nonce(12) ‖ ciphertext ‖ tag(16))
AES-256-GCM, no AAD
```

Production iterations: 600000. The test vector uses 1000. Plaintext is canonical JSON with keys `exportedAt`, `app` (`platform`, `version`), `entries`, `places`, `settings`. Settings keys: `reminders` (each `{id, hour, minute, weekdays}`), `weeklyCard` (`{weekday, hour, minute}`), `tagPickerMode` (`gradual` or `detailed`). Weekly cards are not exported. Import merges by id; an existing row is replaced only when the imported `updatedAt` is newer.

`spec/fixtures/export/vector-1.json` has the password, salt, nonce, iterations, plaintext base64, and the expected envelope. Both platforms must produce that envelope and decrypt it back to the same plaintext.

Database payloads use the same AES-GCM layout with AAD = the row id's UTF-8 bytes. `spec/fixtures/export/seal-vector.json` locks that layout for a software key.

## Inbox

Entry file keys: `v`, `id`, `createdAt`, `timeZoneID`, `mood`, `source`. Location file keys: `v`, `id`, `latitude`, `longitude`, `horizontalAccuracy`, `capturedAt`, `kind`. Samples live in `spec/fixtures/inbox/`.

## Noise guard

Seed `20261010` with the platform RNG. 100 weeks, 25 entries each, no tags and no locations, zone `Europe/Rome`, card time Sunday 19:00, `now` = 2026-10-18 19:00 in that zone. For each entry draw mood in 1…3, `dayOffset` in 0…6, hour in 0…23, minute in 0…59, and place it on that local day at that time. If the stamp is not before `now`, subtract 7 days. The average count of selected items whose confidence is `pattern` must be `<= 0.3`. The reference RNG lands at 0.080, so `pattern-config.json` stays as written.

## Logic files

Use the same names on both platforms: `Mood`, `EntrySource`, `LocationSample`, `Entry`, `PlaceLabel`, `PlaceAssignment`, `PatternConfig`, `DetectedPattern`, `PatternLibrary`, `LibraryMatcher`, `Geo`, `PlaceResolver`, `CardPeriod`, `PatternEngine`, `WeeklyCardBuilder`, `LowMoodMonitor`, `CanonicalJson`, `ContentLoader`, `ExportFormat`, `Sealer`.
