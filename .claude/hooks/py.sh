#!/usr/bin/env bash
# Runs a hook's Python script with the first interpreter that works: python3, then the Windows
# launcher (py -3), then python. On Windows, python3 and python can be Microsoft Store stubs
# that fail without running anything, which would let the guard fail open.
# No working Python -> exit 2, so the PreToolUse guard blocks instead of silently allowing.
for candidate in "python3" "py -3" "python"; do
  if $candidate -c 'import sys; sys.exit(0 if sys.version_info >= (3, 8) else 1)' >/dev/null 2>&1; then
    exec $candidate "$@"
  fi
done
echo "Blocked by .claude/hooks/py.sh: no working Python 3 (tried python3, py -3, python), so the edit guard can't run." >&2
exit 2
