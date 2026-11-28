#!/usr/bin/env python3
"""Block release unless tag, Android, and every iOS version stamp agree."""

from __future__ import annotations

import argparse
from pathlib import Path
import plistlib
import re
import sys


class GateError(RuntimeError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise GateError(message)


def parse_tag(tag: str) -> tuple[str, str]:
    match = re.fullmatch(r"v(\d+)\.(\d+)\.(\d+)", tag)
    require(match is not None, f"invalid release tag {tag!r}; expected vMAJOR.MINOR.PATCH")
    major, minor, patch = (int(value) for value in match.groups())
    version = f"{major}.{minor}.{patch}"
    code = major * 10000 + minor * 100 + patch
    return version, str(code)


def check(root: Path, tag: str) -> None:
    version, code = parse_tag(tag)

    gradle = (root / "composeApp" / "build.gradle.kts").read_text(encoding="utf-8")
    android_name = re.search(r'versionName\s*=\s*"([^"]+)"', gradle)
    android_code = re.search(r"versionCode\s*=\s*(\d+)", gradle)
    require(android_name is not None and android_name.group(1) == version,
            f"Android versionName must be {version}: {android_name.group(1) if android_name else 'missing'}")
    require(android_code is not None and android_code.group(1) == code,
            f"Android versionCode must be {code}: {android_code.group(1) if android_code else 'missing'}")

    with (root / "iosApp" / "Info.plist").open("rb") as stream:
        info = plistlib.load(stream)
    require(str(info.get("CFBundleShortVersionString")) == version,
            f"Info.plist CFBundleShortVersionString must be {version}")
    require(str(info.get("CFBundleVersion")) == code,
            f"Info.plist CFBundleVersion must be {code}")

    pbx = (root / "iosApp" / "iosApp.xcodeproj" / "project.pbxproj").read_text(encoding="utf-8")
    marketing = re.findall(r"MARKETING_VERSION\s*=\s*([^;]+);", pbx)
    current = re.findall(r"CURRENT_PROJECT_VERSION\s*=\s*([^;]+);", pbx)
    require(len(marketing) >= 2 and set(marketing) == {version},
            f"all Xcode MARKETING_VERSION stamps must be {version}: {marketing}")
    require(len(current) >= 2 and set(current) == {code},
            f"all Xcode CURRENT_PROJECT_VERSION stamps must be {code}: {current}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path("."))
    parser.add_argument("--tag", required=True)
    args = parser.parse_args()
    try:
        check(args.root, args.tag)
    except (GateError, OSError, plistlib.InvalidFileException) as error:
        print(f"release version gate: FAIL: {error}", file=sys.stderr)
        return 1
    print(f"release version gate: PASS ({args.tag})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
