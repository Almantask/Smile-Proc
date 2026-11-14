#!/usr/bin/env python3
"""Version-bump and APK update-compatibility checks for scripts/publish-apk.sh."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ANDROID_MAX_VERSION_CODE = 2_147_483_647
CERT_RE = re.compile(
    r"Signer #\d+ certificate SHA-256 digest:\s*([0-9a-fA-F]{64})"
)


def parse_properties(text: str) -> dict[str, str]:
    result: dict[str, str] = {}
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, val = line.split("=", 1)
        result[key.strip()] = val.strip()
    return result


def compute_next_version_name(
    current_name: str, current_code: int, next_code: int
) -> str:
    simple = re.fullmatch(r"(\d+)\.(\d+)\.(\d+)", current_name)
    if simple:
        maj, minor, patch = (int(simple.group(i)) for i in (1, 2, 3))
        if maj == 0 and minor == 0 and (patch == current_code or patch <= 1):
            return f"0.0.{next_code}"
        return f"{maj}.{minor}.{patch + 1}"

    semver_plus = re.fullmatch(r"(\d+\.\d+\.\d+)(?:\+.*)?", current_name)
    if semver_plus:
        return f"{semver_plus.group(1)}+{next_code}"

    return f"0.0.{next_code}"


def extract_cert_digests(apksigner_output: str) -> tuple[str, ...]:
    digests = sorted({m.lower() for m in CERT_RE.findall(apksigner_output)})
    return tuple(digests)


def cmd_bump_version(args: argparse.Namespace) -> int:
    props_path = Path(args.props)
    props: dict[str, str] = {}
    if props_path.exists():
        props = parse_properties(props_path.read_text(encoding="utf-8"))

    current_code = int(props.get("versionCode", "0") or 0)
    current_name = props.get("versionName", "0.0.0") or "0.0.0"

    candidates = [current_code, args.git_count, *args.existing_vc]
    next_code = max(candidates) + 1
    if not (1 <= next_code <= ANDROID_MAX_VERSION_CODE):
        print(
            f"Error: computed versionCode {next_code} outside valid Android range 1..{ANDROID_MAX_VERSION_CODE}",
            file=sys.stderr,
        )
        return 1

    next_name = compute_next_version_name(current_name, current_code, next_code)
    content = (
        "# Monotonically increasing Android app version for in-place updates.\n"
        "# Bumped automatically by scripts/publish-apk.sh before publishing APKs.\n"
        f"versionCode={next_code}\n"
        f"versionName={next_name}\n"
    )
    props_path.parent.mkdir(parents=True, exist_ok=True)
    props_path.write_text(content, encoding="utf-8", newline="\n")
    print(f"Bumped {props_path.name}: versionCode={next_code}, versionName={next_name}")
    return 0


def cmd_verify_upgrade(args: argparse.Namespace) -> int:
    new_certs = extract_cert_digests(Path(args.new_certs_file).read_text(encoding="utf-8"))
    if not new_certs:
        print(
            f"Error [{args.variant}]: no valid APK signing certificate digest found in apksigner output.",
            file=sys.stderr,
        )
        return 1

    if args.new_vc < 1:
        print(
            f"Error [{args.variant}]: versionCode must be >= 1, got {args.new_vc}.",
            file=sys.stderr,
        )
        return 1

    if args.expected_id and not args.allow_package_change and args.new_id != args.expected_id:
        print(
            f"Error [{args.variant}]: built applicationId '{args.new_id}' != expected '{args.expected_id}'.\n"
            "Installing an APK with a different applicationId creates a separate app instead of updating in place.\n"
            "If this package change is intentional (e.g., Appendix 7), export a .smileproc backup first and re-run with --allow-package-change.",
            file=sys.stderr,
        )
        return 1

    if args.prev_certs_file:
        prev_certs = extract_cert_digests(
            Path(args.prev_certs_file).read_text(encoding="utf-8")
        )
        if not args.allow_package_change and args.prev_id and args.new_id != args.prev_id:
            print(
                f"Error [{args.variant}]: applicationId changed ('{args.prev_id}' -> '{args.new_id}').\n"
                "Android would install this as a whole new app rather than updating the existing install.\n"
                "Export a .smileproc backup first or pass --allow-package-change if intentional.",
                file=sys.stderr,
            )
            return 1

        if not prev_certs:
            print(
                f"Error [{args.variant}]: could not read signing certificate from previously published APK.",
                file=sys.stderr,
            )
            return 1

        if not args.allow_signing_change and new_certs != prev_certs:
            print(
                f"Error [{args.variant}]: signing certificate SHA-256 changed!\n"
                f"  previous APK cert: {', '.join(prev_certs)}\n"
                f"  new APK cert:      {', '.join(new_certs)}\n"
                "Android will reject an in-place update (INSTALL_FAILED_UPDATE_INCOMPATIBLE) and uninstalling wipes all user data.\n"
                "Build with the matching signing certificate, or export a .smileproc backup in the app first and re-run with --allow-signing-change.",
                file=sys.stderr,
            )
            return 1

        if args.new_vc <= args.prev_vc:
            print(
                f"Error [{args.variant}]: new versionCode ({args.new_vc}) did not strictly increase over published APK ({args.prev_vc}).\n"
                "Non-monotonic versions can fail to update or cause version downgrades.",
                file=sys.stderr,
            )
            return 1

    short_cert = new_certs[0][:12]
    print(
        f"[OK] {args.variant}: id={args.new_id}, versionCode={args.new_vc}, versionName={args.new_vn}, cert={short_cert}..."
    )
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="SmileProc APK versioning and upgrade verifier")
    sub = parser.add_subparsers(dest="cmd", required=True)

    bump = sub.add_parser("bump-version")
    bump.add_argument("--props", required=True)
    bump.add_argument("--git-count", type=int, default=0)
    bump.add_argument("--existing-vc", type=int, action="append", default=[])
    bump.set_defaults(func=cmd_bump_version)

    verify = sub.add_parser("verify-upgrade")
    verify.add_argument("--variant", required=True)
    verify.add_argument("--new-id", required=True)
    verify.add_argument("--new-vc", type=int, required=True)
    verify.add_argument("--new-vn", required=True)
    verify.add_argument("--new-certs-file", required=True)
    verify.add_argument("--expected-id", default="")
    verify.add_argument("--prev-id", default="")
    verify.add_argument("--prev-vc", type=int, default=0)
    verify.add_argument("--prev-certs-file", default="")
    verify.add_argument("--allow-package-change", action="store_true")
    verify.add_argument("--allow-signing-change", action="store_true")
    verify.set_defaults(func=cmd_verify_upgrade)

    args = parser.parse_args()
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
