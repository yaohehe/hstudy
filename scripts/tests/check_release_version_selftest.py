#!/usr/bin/env python3
"""Self-test for the blocking release tag/version consistency gate."""

from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "check_release_version.py"


def fixture(root: Path, *, version: str = "3.3.3", code: str = "30303") -> None:
    (root / "composeApp").mkdir(parents=True, exist_ok=True)
    (root / "iosApp").mkdir(exist_ok=True)
    (root / "composeApp" / "build.gradle.kts").write_text(
        f'defaultConfig {{\n        versionCode = {code}\n        versionName = "{version}"\n    }}\n',
        encoding="utf-8",
    )
    (root / "iosApp" / "Info.plist").write_text(
        "<plist><dict>"
        "<key>CFBundleShortVersionString</key><string>" + version + "</string>"
        "<key>CFBundleVersion</key><string>" + code + "</string>"
        "</dict></plist>",
        encoding="utf-8",
    )
    (root / "iosApp" / "iosApp.xcodeproj").mkdir(exist_ok=True)
    (root / "iosApp" / "iosApp.xcodeproj" / "project.pbxproj").write_text(
        f"CURRENT_PROJECT_VERSION = {code};\nMARKETING_VERSION = {version};\n"
        f"CURRENT_PROJECT_VERSION = {code};\nMARKETING_VERSION = {version};\n",
        encoding="utf-8",
    )


def run(root: Path, tag: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [sys.executable, str(SCRIPT), "--root", str(root), "--tag", tag],
        text=True,
        capture_output=True,
        check=False,
    )


def main() -> int:
    with tempfile.TemporaryDirectory(prefix="hstudy-release-version-") as temporary:
        root = Path(temporary)
        fixture(root)
        passed = run(root, "v3.3.3")
        assert passed.returncode == 0, passed.stdout + passed.stderr

        for tag in ("v3.3.2", "3.3.3", "v3.3.30"):
            failed = run(root, tag)
            assert failed.returncode != 0, f"tag mismatch accepted: {tag}"

        fixture(root, version="3.3.2")
        assert run(root, "v3.3.3").returncode != 0, "Android/iOS version mismatch accepted"
        fixture(root, code="30302")
        assert run(root, "v3.3.3").returncode != 0, "versionCode mismatch accepted"

        pbx = root / "iosApp" / "iosApp.xcodeproj" / "project.pbxproj"
        pbx.write_text(pbx.read_text(encoding="utf-8").replace("30303", "30302", 1), encoding="utf-8")
        assert run(root, "v3.3.3").returncode != 0, "partial Xcode stamp drift accepted"

    print("release version gate self-test: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
