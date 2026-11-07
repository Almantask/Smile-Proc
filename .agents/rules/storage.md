---
trigger: model_decision
description: Apply when working on Room database entities, DAOs, migrations, schemas, encryption/Keystore sealing, or .smileproc export/import (app/src/main/**/data/**, app/schemas/**, core/src/main/**/crypto/**).
---

# Storage & Crypto Rules (`data/**`, `app/schemas/**`, `crypto/**`)

- Spec: `docs/spec/android.md` (*Storage and encryption*; *Export and import*).
- Every stored payload is `IV(12) || ciphertext || tag` using `AES/GCM/NoPadding`, with `AAD = row id UTF-8 bytes`, encrypting canonical JSON.
- Keystore key must **not** set user-authentication or unlocked-device requirements (notification face taps must seal and save while the phone is locked).
- After M5 (dogfood build 1): never edit an existing Room schema version — always add an explicit `Migration` and a migration test.
- Missing Keystore key with an existing DB routes to the recovery screen; never crash and never wipe data silently.
