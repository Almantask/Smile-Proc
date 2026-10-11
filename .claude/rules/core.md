---
paths:
  - "core/**"
  - "fixtures/**"
  - "content/**"
---
# Core rules
- Spec: docs/spec/logic.md and docs/spec/data.md.
- Pure Kotlin/JVM. Inject Clock, ZoneId, Random and Sealer; never read the system clock in core logic.
- Day parts, weekdays and dates are computed in each entry's own timeZoneID. Weekdays are ISO (1 = Monday).
- Sort candidates by confidence, then score rounded to 9 decimals descending, then id ascending.
- Thresholds come only from content/pattern-config.json; no defaults in code.
- Fixture expectations are hand-checkable. Never edit a golden by hand; never regenerate one to make a test pass.
- CheckInMachine is a pure reducer: every effect list holds at most one post-or-cancel of ID_CHECKIN.
