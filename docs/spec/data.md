# C2 · `docs/spec/data.md`

#### Canonical data format
Used for export files, fixtures and the sealed database payloads.

```json
{
  "id": "3f0c2b9e-7d1a-4a52-9a8e-0c6f1d2e4b10",
  "createdAt": "2026-10-12T13:05:00.000Z",
  "updatedAt": "2026-10-12T13:06:10.000Z",
  "timeZoneID": "Europe/Rome",
  "mood": 2,
  "tags": ["anxious.stressed"],
  "comment": null,
  "location": {
    "latitude": 45.4642, "longitude": 9.1900, "horizontalAccuracy": 12.0,
    "capturedAt": "2026-10-12T13:05:03.000Z", "kind": "fresh"
  },
  "source": "notification"
}
```
```json
{ "id": "…", "name": "Office", "kind": "work", "anchorLatitude": 45.48, "anchorLongitude": 9.21, "createdAt": "…" }
```
- UUIDs lower-case. Timestamps UTC ISO 8601 with milliseconds and `Z`.
- **Weekdays are ISO: 1 = Monday … 7 = Sunday** (`DayOfWeek.value`).
- `mood` 1–3; `tags` max 3, ordered, ids from `emotion-tags.json`; `comment` trimmed, `null` if empty, max 1000 characters.
- `location` is `null` when location is off, permission is missing or no acceptable fix exists. The rating is saved either way.
- `location.kind`: `fresh` | `cached`. `source`: `app` | `notification`.
- `timeZoneID` is the IANA zone where the entry was made. Daypart, weekday and calendar date are always computed in **its own** zone (travel; DST: Europe's clocks go back on 25 Oct 2026).
- Place label `kind`: `home` | `work` | `other`.

```kotlin
// :core
@Serializable data class Entry(
    val id: String,
    @Serializable(with = IsoInstantSerializer::class) val createdAt: Instant,
    @Serializable(with = IsoInstantSerializer::class) val updatedAt: Instant,
    val timeZoneID: String, val mood: Mood, val tags: List<String>,
    val comment: String?, val location: LocationSample?, val source: EntrySource
)
```
**Type names:** `Mood`, `EntrySource`, `LocationSample`, `Entry`, `PlaceLabel`, `ClusterID`, `PlaceAssignment`, `PatternConfig`, `Detector`, `Family`, `Direction`, `Confidence`, `DetectedPattern`, `CardItem`, `WeeklyCard`, `Variant`, `WindowKind`, `PatternLibrary`, `LibraryMatcher`, `PlaceResolver`, `PatternEngine`, `WeeklyCardBuilder`, `LowMoodMonitor`, `CardPeriod`, `CheckInMachine`, `Sealer`, `ExportFormat`.

#### `content/emotion-tags.json` (ids and colours; labels in `strings.xml` as `tag_<core>_<fine>`)
| Core | Fine tags |
|---|---|
| `happy` | joyful, grateful, proud, hopeful, excited |
| `calm` | relaxed, content, peaceful, focused, safe |
| `sad` | down, lonely, disappointed, hurt, nostalgic |
| `anxious` | stressed, worried, overwhelmed, nervous, insecure |
| `angry` | frustrated, irritated, resentful, jealous, impatient |
| `tired` | drained, sleepy, bored, unmotivated, numb |
| `surprised` | amazed, confused, curious, startled, moved |

Fine ids are `<core>.<fine>` (e.g. `anxious.stressed`).
```json
{ "version": 1, "cores": [ { "id": "happy", "color": "#F2C14E", "fine": ["happy.joyful", "happy.grateful", "happy.proud", "happy.hopeful", "happy.excited"] } ] }
```
Choose a soft, distinguishable colour per core that meets contrast in light and dark mode.

#### `content/pattern-config.json`
Thresholds come only from this file; a test asserts every key is read.
```json
{
  "version": 1,
  "minSegment": 3, "minRest": 3, "patternMinSegment": 5, "patternMinRest": 5,
  "minAbsDiff": 0.4, "zHint": 1.5, "zPattern": 2.0, "sdFloor": 0.3,
  "weekdayMinSegment": 4, "weekdayMinDistinctDates": 2, "weekdayMinAbsDiff": 0.5, "weekdayMinDataDays": 14,
  "tagMinLowTagged": 4, "tagMinCount": 3, "tagMinShare": 0.5, "tagMinShareGap": 0.25, "tagPatternCount": 5,
  "tagAtPlaceMinShare": 0.4, "fineTagDominance": 0.7,
  "shortWeekThreshold": 10, "maxItems": 3, "maxItemsLowWeek": 2,
  "notEnoughDataBelow": 5, "firstWeekDays": 14, "firstCardMinEntries": 5,
  "lowWeekMinEntries": 4, "lowWeekShare": 0.6, "lowRunLength": 5, "lowRunMinDays": 2,
  "placeRadiusMeters": 100, "placeMaxAccuracyMeters": 150
}
```

#### `content/pattern-library.en.json`
Placeholders: `{segment}` (morning/afternoon/evening/night), `{weekday}`, `{place}` (current name or "an unnamed place"), `{tag}` (lower-case label). Ship exactly this seed; the human may extend it. `"weekday": "7"` = Sunday.

```json
[
  { "id": "afternoon-dip", "match": { "detector": "timeOfDay", "direction": "lower", "segment": "afternoon" },
    "title": "Afternoons run lower",
    "common": "Lots of people notice their mood and energy dip after lunch.",
    "tip": "Try a 10-minute walk or a few minutes of daylight shortly before the dip usually starts.",
    "question": "Where could a 10-minute break fit tomorrow afternoon?" },

  { "id": "sunday-evening", "match": { "detector": "weekdayDaypart", "direction": "lower", "weekday": "7", "segment": "evening" },
    "title": "Sunday evenings feel heavier",
    "common": "A dip before the week starts is something many people recognise.",
    "tip": "Plan one small thing to look forward to on Monday.",
    "question": "What's one small thing you could put into Monday?" },

  { "id": "out-lifts", "match": { "detector": "awayFromHome", "direction": "higher" },
    "title": "You feel better when you're out",
    "common": "Many people find a change of scene lifts their mood.",
    "tip": "Put one outdoor or out-of-the-house block into your week.",
    "question": "Which day has room for it?" },

  { "id": "stress-at-place", "match": { "detector": "tagAtPlace", "tag": "anxious.stressed" },
    "title": "Stress tends to show up at {place}",
    "common": "It's common for one place to carry most of a week's stress.",
    "tip": "Take a 2-minute pause when you arrive at {place}, or just before you leave.",
    "question": "Arriving or leaving — which pause is easier to try?" },

  { "id": "tired-on-low", "match": { "detector": "tagOnLow", "tag": "tired" },
    "title": "Tiredness shows up on most low check-ins",
    "common": "Low mood and tiredness often travel together.",
    "tip": "Look at sleep before anything else: pick one night this week to start winding down earlier.",
    "question": "Which night could start 30 minutes earlier?" },

  { "id": "time-lower", "match": { "detector": "timeOfDay", "direction": "lower" },
    "title": "Your {segment}s run lower",
    "common": "Many people have a time of day that's harder than the rest.",
    "tip": "Give your {segment}s one small buffer this week: a short break, something to eat, or a few minutes outside.",
    "question": "What would make the next {segment} a little easier?" },

  { "id": "time-higher", "match": { "detector": "timeOfDay", "direction": "higher" },
    "title": "Your best stretch is the {segment}",
    "common": "Most people have a time of day when they feel more like themselves.",
    "tip": "If you can, put something you care about into your {segment}s.",
    "question": "What could you move into that time?" },

  { "id": "weekday-lower", "match": { "detector": "weekday", "direction": "lower" },
    "title": "{weekday}s run lower",
    "common": "Many people have one day of the week that's harder than the others.",
    "tip": "Add one small, kind thing to your next {weekday}.",
    "question": "What would make next {weekday} lighter?" },

  { "id": "weekday-higher", "match": { "detector": "weekday", "direction": "higher" },
    "title": "{weekday}s go well for you",
    "common": "Noticing your good days is as useful as noticing the hard ones.",
    "tip": "Look at what's different about {weekday}s and borrow one piece of it for another day.",
    "question": "What makes {weekday}s work?" },

  { "id": "weekday-segment-lower", "match": { "detector": "weekdayDaypart", "direction": "lower" },
    "title": "{weekday} {segment}s run lower",
    "common": "Many people notice one stretch of the week that's harder.",
    "tip": "Plan something small and easy for your next {weekday} {segment}.",
    "question": "What could you plan for it?" },

  { "id": "weekday-segment-higher", "match": { "detector": "weekdayDaypart", "direction": "higher" },
    "title": "{weekday} {segment}s go well",
    "common": "Noticing what goes well is half of repeating it.",
    "tip": "Think about what makes that time work and borrow one piece for another day.",
    "question": "Which piece would you borrow?" },

  { "id": "place-lower", "match": { "detector": "place", "direction": "lower" },
    "title": "Check-ins at {place} run lower",
    "common": "Places often carry their own mood.",
    "tip": "Try one small change at {place}: a short pause, a different spot, or a moment outside.",
    "question": "Which change is easiest to try?" },

  { "id": "place-higher", "match": { "detector": "place", "direction": "higher" },
    "title": "You feel better at {place}",
    "common": "Many people have a place that steadies them.",
    "tip": "If you can, spend a little more time at {place} this week.",
    "question": "When could you get there?" },

  { "id": "home-higher", "match": { "detector": "awayFromHome", "direction": "lower" },
    "title": "Home feels better than being out",
    "common": "Being out takes more energy for a lot of people.",
    "tip": "Plan a few quiet minutes to recharge after you get back.",
    "question": "When do you usually need that most?" },

  { "id": "tag-on-low", "match": { "detector": "tagOnLow" },
    "title": "Feeling {tag} shows up on most low check-ins",
    "common": "Putting a name to what's behind a low moment is a useful step.",
    "tip": "Pick one small thing that would ease feeling {tag} this week.",
    "question": "What usually helps, even a little?" },

  { "id": "tag-at-place", "match": { "detector": "tagAtPlace" },
    "title": "Feeling {tag} tends to show up at {place}",
    "common": "It's common for one place to bring out one feeling.",
    "tip": "Take a 2-minute pause at {place} this week and notice what changes.",
    "question": "When would that pause fit?" }
]
```

#### `content/support.json` — human-owned
**Talk to someone** and **See support options** open the Support screen. Its gentle copy comes from `strings.xml`; optional contacts come from this file. The agent creates exactly this seed and **never adds names, numbers or links from memory** (the edit hook blocks changes to it):
```json
{ "version": 1, "entries": [] }
```
Entry shape, for the human: `{ "region": "IT", "name": "…", "phone": "…", "sms": "…", "url": "…", "verified": false }`. With no entries for the device's region, the screen shows only its copy. Entries with `"verified": false` never show in dogfood or release builds.

#### Strings
Every UI string lives in `app/src/main/res/values/strings.xml`: tag labels, face labels, notification text, observation lines (`week_observation_timeOfDay_lower`: "Your %1$s check-ins were lower than the rest of your day (%2$d check-ins).") and all copy in screens.md. Italian later goes into `values-it/`; library text gets a sibling `pattern-library.it.json`.

#### Fixtures
`tools/generate_fixtures.py` (Python 3 standard library only, fixed seeds) writes inputs and hand-checkable expectations; `:core` tests load them via a Gradle-provided path.

| Fixture set | Scenarios (each `name.input.json` + `name.expect.json`) |
|---|---|
| `patterns/` | `afternoon-dip`, `sunday-evening` (28 days), `tired-on-low`, `stressed-at-work` (with a Work label), `out-lifts` (with a Home label), `low-week`, `not-enough-data`, `first-week`, `dst-week` (across 25 Oct 2026, Europe/Rome), `travel` (some entries in America/New_York), `no-location`, `noise-1..3` |
| `places/` | `three-places-noise` (±40 m), `accuracy-filter`, `label-capture`, `label-deleted` |
| `periods/` | card-period boundaries for several "now" values, including the DST week |
| `export/` | `vector-1.json` (written in M9) |

`expect.json` assertions: `firstItemLibraryID`, `itemsIncludeLibraryIDs`, `variant`, `isLowWeek`, `maxItemCount`, `candidatesInclude` (`{id, confidence}`), `dayparts` (`{entryID: daypart}`), `sameCluster` / `differentCluster` / `unclustered`.

**Goldens:** once all `expect` files pass (M2), a `WRITE_GOLDENS=1` run writes `<name>.golden.json` (full sorted candidate list with `id`, `confidence`, `nSegment`, `nRest`, `diff`, `z`, `score` rounded to 6 decimals; selected items with library ids; `variant`, `isLowWeek`, `windowKind`, `entryCount`, `moodCounts`). The human reviews and accepts them. From then on tests reproduce every golden exactly. Goldens are regenerated only on the human's request.
