#!/usr/bin/env bash
# Regenerate fixtures in a temp directory and parse every content/ and fixtures/ JSON file.
set -euo pipefail
cd "$(dirname "$0")/.."

python3 tools/generate_fixtures.py --check

python3 - <<'PY'
import json
import pathlib
import sys

root = pathlib.Path(".")
files = sorted((root / "content").rglob("*.json")) + sorted((root / "fixtures").rglob("*.json"))
for path in files:
    try:
        json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        print(f"invalid JSON {path}: {exc}", file=sys.stderr)
        sys.exit(1)
print(f"parsed {len(files)} json files")
PY
