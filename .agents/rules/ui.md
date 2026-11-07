---
trigger: model_decision
description: Apply when working on Jetpack Compose screens, themes, accessibility, Android string resources, or pattern library copy (app/src/main/**/ui/**, app/src/main/res/**, content/pattern-library*.json).
---

# UI & Copy Rules (`ui/**`, `res/**`, `content/pattern-library*.json`)

- Spec: `docs/spec/screens.md`.
- Every user-facing string lives in `app/src/main/res/values/strings.xml`. Every tappable element provides an accessible content description.
- Layouts must work cleanly at 200% font scale and in dark mode; colour is never the sole signal; respect system reduced-motion preferences.
- Copy voice: plain, warm, short. Prefer `"Many people notice…"`, `"Try…"`, `"If you can…"`. Never write `"You should…"`.
- No clinical words (`depression`, `disorder`, `symptom`, `diagnosis`, `treatment`), no numbers/streaks/scores that grade the user, and no exclamation marks on low-mood screens.
- Faces are `Low` / `Okay` / `Good`, and every face is presented as an equally valid answer.
