#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"
python3 spec/tools/generate_fixtures.py --check
python3 spec/tools/gen_strings.py --check
python3 - <<'PY'
import json
from pathlib import Path
root = Path("spec")
errors = []
for path in root.rglob("*.json"):
    if "fixtures/inbox/corrupt" in path.as_posix():
        continue
    try:
        json.loads(path.read_text())
    except Exception as exc:
        errors.append(f"{path}: {exc}")
if errors:
    print("\n".join(errors))
    raise SystemExit(1)
print(f"json ok ({sum(1 for _ in root.rglob('*.json'))} files)")
PY
