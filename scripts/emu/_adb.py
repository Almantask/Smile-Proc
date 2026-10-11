"""adb for scripts/emu/*.py: one device ($SMILEPROC_SERIAL, else the first running emulator),
and never the human's daily app (com.example.smileproc)."""
import os
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PKG = "com.example.smileproc.debug"
DOGFOOD_PKG = "com.example.smileproc"


def _sdk_candidates():
    for var in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        if os.environ.get(var):
            yield Path(os.environ[var])
    props = ROOT / "local.properties"
    if props.exists():
        for line in props.read_text(encoding="utf-8").splitlines():
            if line.startswith("sdk.dir="):
                yield Path(line[len("sdk.dir="):].replace("\\:", ":").replace("\\\\", "\\"))
    if os.environ.get("LOCALAPPDATA"):
        yield Path(os.environ["LOCALAPPDATA"]) / "Android" / "Sdk"
    yield Path.home() / "Library" / "Android" / "sdk"
    yield Path.home() / "Android" / "Sdk"


def adb_path():
    exe = "adb.exe" if os.name == "nt" else "adb"
    for sdk in _sdk_candidates():
        candidate = sdk / "platform-tools" / exe
        if candidate.exists():
            return str(candidate)
    found = shutil.which("adb")
    if found:
        return found
    sys.exit("adb not found; set ANDROID_HOME")


ADB = adb_path()


def serial():
    if os.environ.get("SMILEPROC_SERIAL"):
        return os.environ["SMILEPROC_SERIAL"]
    out = subprocess.run([ADB, "devices"], capture_output=True, text=True).stdout
    for line in out.splitlines():
        parts = line.split()
        if len(parts) == 2 and parts[0].startswith("emulator-") and parts[1] == "device":
            return parts[0]
    sys.exit("No emulator running and SMILEPROC_SERIAL is not set. Start one: scripts/emu/start.sh sp36")


SERIAL = serial()


def adb(*args, check=True):
    """Runs adb against SERIAL and returns stdout as text (CR stripped)."""
    for arg in args:
        if arg == DOGFOOD_PKG or arg.startswith(DOGFOOD_PKG + "/"):
            sys.exit(f"Refusing to touch {DOGFOOD_PKG} (the human's daily app).")
    result = subprocess.run([ADB, "-s", SERIAL, *args], capture_output=True, text=True,
                            encoding="utf-8", errors="replace")
    if check and result.returncode != 0:
        sys.exit(f"adb {' '.join(args)} failed: {result.stderr.strip() or result.stdout.strip()}")
    return result.stdout.replace("\r", "")
