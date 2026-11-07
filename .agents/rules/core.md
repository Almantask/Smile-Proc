---
trigger: model_decision
description: Apply when reading, creating, or modifying pure Kotlin/JVM logic in core/**, shared test fixtures in fixtures/**, or runtime configuration in content/**.
---

# Core Rules (`core/**`, `fixtures/**`, `content/**`)

- Spec: `docs/spec/logic.md` and `docs/spec/data.md`.
- Pure Kotlin/JVM only (`no android.*` or `androidx.*` imports). Inject `Clock`, `ZoneId`, `Random`, and `Sealer`; never read the system clock directly in core logic.
- Day parts, weekdays, and dates are computed in each entry's own `timeZoneID`. Weekdays follow ISO-8601 (`1 = Monday`).
- Sort pattern candidates by confidence, then `score` rounded to 9 decimals descending, then `id` ascending.
- Thresholds come exclusively from `content/pattern-config.json`; never hard-code fallback defaults in code.
- Fixture expectations are hand-checkable. Never hand-edit a `.golden.json` file or regenerate goldens to make a failing test pass.
- `CheckInMachine` is a pure reducer: every returned effect list holds at most one post-or-cancel of `ID_CHECKIN`.
