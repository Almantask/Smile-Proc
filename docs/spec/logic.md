# C3 · `docs/spec/logic.md`

#### Location acceptance
Platform code requests the fix; `:core` holds the rules.
- **In the app:** accept the first fix with accuracy ≤ 100 m (`fresh`). At the 10 s timeout, use the best fix seen if ≤ 200 m. Otherwise the last known location if ≤ 10 min old and ≤ 200 m (`cached`). Otherwise `null`.
- **From a notification tap:** last known location only, if ≤ 10 min old and ≤ 200 m (`cached`); otherwise `null`. Spike A2 may change this, only with the human's approval.
- Location off or permission missing → `null`, no request made.
- **In-app ratings are saved first with `location = null`, then updated when the fix arrives.**

#### Place clustering (`PlaceResolver`)
Places are **computed, not stored per entry**, so renames and new labels apply to all history.
```
resolve(entries, labels, config) -> PlaceAssignment
  // entryID -> ClusterID; ClusterID -> Cluster(centroid, memberIDs, label?)

1. eligible = entries with location.horizontalAccuracy <= placeMaxAccuracyMeters (150), sorted by (createdAt, id).
2. Named pass: assign each eligible entry to the nearest label anchor within placeRadiusMeters (100)
   → ClusterID "p:<labelID>".
3. Unnamed pass, in order: join the nearest unnamed cluster whose running centroid is within 100 m
   (centroid = arithmetic mean of members' lat/lon); otherwise start a new cluster "u:<firstEntryID>".
4. Stabilise once: recompute unnamed centroids, reassign each unnamed member to the nearest centroid
   within 100 m (or its own cluster), drop empty clusters, keep "u:<earliest member id>" as the id.
5. Entries without an eligible location have no cluster.
Distance: haversine, R = 6,371,000 m. Ties: lower ClusterID string wins.
```
- **Naming:** chips *Home*, *Work* and a free-text name. Creates a `PlaceLabel` with anchor = cluster centroid. If another label's anchor is within 100 m, ask "Is this {name}?" — if yes, don't create a new label; move that label's anchor to the combined centroid.
- **Unnamed place descriptions** (no maps): "Unnamed place · 8 check-ins · mostly weekday mornings".

#### Pattern engine
**Dayparts** (entry's local time): `morning` 05:00–11:59, `afternoon` 12:00–16:59, `evening` 17:00–21:59, `night` 22:00–04:59.

**Card period** (`CardPeriod`): `periodEnd` = the latest occurrence of the weekly card time (default Sunday 19:00, ISO weekday 7) that is ≤ now, in the device's current time zone; `periodStart` = the same local time 7 calendar days earlier (not 7 × 24 h).

**Windows**
- *Card window* = `[periodStart, periodEnd)`. Fewer than `shortWeekThreshold` entries → use the 14 calendar days ending at `periodEnd` and set `windowKind = twoWeeks`.
- *Weekday window* = the 28 calendar days ending at `periodEnd`. Weekday detectors are skipped if the first entry ever is less than `weekdayMinDataDays` before `periodEnd`.

**Statistics**
```
var(x)    = Σ(x − mean)² / (n − 1)        (0 when n = 1)
diff      = mean(seg) − mean(rest)
sdPooled  = sqrt(((nSeg−1)·var(seg) + (nRest−1)·var(rest)) / (nSeg + nRest − 2))
z         = diff / (max(sdPooled, sdFloor) · sqrt(1/nSeg + 1/nRest))
pattern   : nSeg ≥ patternMinSegment, nRest ≥ patternMinRest, |diff| ≥ minAbsDiff, |z| ≥ zPattern
earlyHint : nSeg ≥ minSegment,        nRest ≥ minRest,        |diff| ≥ minAbsDiff, |z| ≥ zHint
score     = |diff| · min(nSeg, 10)/10 + (pattern ? 0.2 : 0)
direction = diff < 0 ? lower : higher
```
Weekday detectors use `weekdayMinSegment`, `weekdayMinAbsDiff`, and require the segment to span ≥ `weekdayMinDistinctDates` local dates.

**Mean-comparison detectors**
| Detector | Family | Segment | Rest | Window | Pattern id |
|---|---|---|---|---|---|
| `timeOfDay` | time | one daypart | all other entries | card | `timeOfDay.<dir>.<daypart>` |
| `weekday` | weekday | one ISO weekday | other days | weekday | `weekday.<dir>.<1-7>` |
| `weekdayDaypart` | weekday | weekday × {morning, afternoon, evening} | all other entries | weekday | `weekdayDaypart.<dir>.<1-7>.<daypart>` |
| `place` | place | one cluster | other located entries | card | `place.<dir>.<clusterID>` |
| `awayFromHome` | place | located entries not in the Home label's cluster | entries in it | card | `awayFromHome.<dir>` |

`awayFromHome` needs a label with `kind == home`. Place detectors use located entries only; entries without location still count for every other detector.

**Tag detectors.** An entry "has core c" if any of its tags is c or starts with `c.`. Counts are at core level; if one fine tag appears in ≥ `fineTagDominance` of the segment hits, report that fine id instead.
| Detector | Family | Rule | Pattern when | Pattern id |
|---|---|---|---|---|
| `tagOnLow` | tag | Low entries with tags ≥ `tagMinLowTagged`; core appears in ≥ `tagMinShare` of them and ≥ `tagMinCount`; its share among Okay/Good tagged entries is ≥ `tagMinShareGap` lower | count ≥ `tagPatternCount` | `tagOnLow.<tag>` |
| `tagAtPlace` | tag | At one cluster: core appears in ≥ `tagMinCount` entries and ≥ `tagAtPlaceMinShare` of the cluster's tagged entries; its share elsewhere is ≥ `tagMinShareGap` lower | count ≥ `tagPatternCount` | `tagAtPlace.<tag>.<clusterID>` |

Tag score = (shareSegment − shareRest) · min(count, 10)/10 + (pattern ? 0.2 : 0). Tag patterns have no direction.

**Determinism:** sort by confidence (pattern first), then score **rounded to 9 decimals** descending, then id ascending.
```
DetectedPattern { id, detector, family, direction?, params {segment, weekday, clusterID, placeKind, tag},
                  nSegment, nRest, meanSegment?, meanRest?, diff?, z?, score, confidence, supportingEntryIDs }
```

#### Library matching and card building
**Matching** (`LibraryMatcher`): a library entry matches when its `detector` equals the pattern's and every other key it specifies (`direction`, `segment`, `weekday`, `placeKind`, `tag`) equals the pattern's param; `tag` also matches the pattern tag's core. Values are strings. Most matched keys wins; ties go to file order. A test enumerates every detector × direction × segment/weekday/placeKind/tag-core combination and asserts something matches.

**Variant and limits** (period entries = entries in `[periodStart, periodEnd)`):
- `notEnoughData`: period entries < `notEnoughDataBelow` → max 1 item (hints allowed), copy "patterns need a few more check-ins".
- else `firstWeek`: first entry ever within `firstWeekDays` of `periodEnd` → max 1 item, copy "patterns get clearer after two or three weeks".
- else `normal`: max `maxItems`.
- `isLowWeek` (independent): period entries ≥ `lowWeekMinEntries` and Low share ≥ `lowWeekShare` → max items `maxItemsLowWeek`, and the card **leads with acknowledgment and *Talk to someone* before any tips**.

**Selection** (`WeeklyCardBuilder`): deterministic order; pick greedily, **max one per family**, up to the limit; if limit ≥ 2 and nothing picked is `higher` but a `higher` candidate with `pattern` confidence exists, it replaces the last pick; early hints only fill slots patterns didn't (UI label "Early hint").
```
WeeklyCard { periodStart, periodEnd, windowKind, entryCount, moodCounts [low, okay, good],
             days [7 × counts per face], isLowWeek, variant, items [CardItem {patternID, libraryID, pattern}] }
```
- Observation lines come from strings (`week_observation_<detector>_<dir>`). Never show raw averages.
- Store the structured card; render text at display time so renamed places show current names. A cluster that no longer exists renders as "one of your places".
- **Timing:** the first card appears at the first card time with ≥ `firstCardMinEntries` entries in total. A card is built and stored the first time the app opens after the card time. The Week tab shows a dot until a new card is seen. **No notification.**

#### Low-mood support (`LowMoodMonitor`)
- **Low run (support card on Today):** the last `lowRunLength` entries are all Low **and** span ≥ `lowRunMinDays` local dates. Dismissing stores the latest entry id; the card re-arms only after a non-Low entry.
- **Low week:** see Library matching and card building.
- Never a notification, never in notification text, never in an export filename.

#### Check-in notification state machine (`CheckInMachine`)
A pure reducer: `reduce(state, settings, event, now) -> Transition(state, effects)`.

**Settings:** `standing` (Keep a check-in on the lock screen), `dismissed` (the person swiped the standing notification away; cleared by the next reminder).
**Resting state** = `Faces(silent)` if `standing && !dismissed`, else `Hidden`.

| State | What the one notification shows |
|---|---|
| `Hidden` | nothing |
| `Faces(alerting)` | "How are you right now?" + Low · Okay · Good, with sound/vibration per channel |
| `Faces(silent)` | the same, no sound, no vibration |
| `Saved` | "Saved ✓", no buttons, silent |
| `Failed` | "Couldn't save. Open SmileProc.", no buttons, silent |
| `Test` | the faces, alerting; buttons save nothing |
| `TestDone` | "It works ✓", no buttons, silent |

| Event | Next state | Effects |
|---|---|---|
| `ReminderFired` | `Faces(alerting)` | `dismissed = false`; cancel any restore timer |
| `Tap(mood)` on `Faces` | `Saved` | insert entry (`source = notification`); restore timer 60 s |
| save throws | `Failed` | restore timer 60 s |
| `Tap(mood)` on `Test` | `TestDone` | record test success; no entry; restore timer 60 s |
| `RestoreTimer` | resting state | — |
| `RatedInApp` while `Faces(alerting)` | resting state | — |
| `Dismissed` (swipe) | `Hidden` | `dismissed = true` if `standing` |
| `StandingTurnedOn` | resting state if `Hidden` | `dismissed = false` |
| `StandingTurnedOff` | `Hidden` if `Faces(silent)`; else unchanged | — |
| `TestRequested` | unchanged; after 10 s → `Test` | remember the resting state |
| `Booted` / `AppUpdated` | resting state | reschedule alarms |

Every state renders to **the same notification ID** (or a cancel). A property test runs random event sequences and asserts each effect list holds at most one post-or-cancel of `ID_CHECKIN` and never posts another ID.
