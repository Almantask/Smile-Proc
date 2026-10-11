---
paths:
  - "app/src/main/**/data/**"
  - "app/schemas/**"
  - "core/src/main/**/crypto/**"
---
# Storage rules
- Spec: docs/spec/android.md (Storage and encryption; Export and import).
- Every payload: IV(12) ‖ ciphertext ‖ tag, AES/GCM/NoPadding, AAD = row id bytes, over canonical JSON.
- Keystore key: no user-authentication or unlocked-device requirement (notification taps save while locked).
- After M5 (dogfood build): never edit an existing Room schema version; add a migration and a migration test.
- Missing key with an existing DB → recovery screen. Never crash, never wipe silently.
