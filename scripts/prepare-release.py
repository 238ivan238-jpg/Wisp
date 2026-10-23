#!/usr/bin/env python3
"""Prepare the tested, signed APK and update feed; never push or expose signing keys."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

ROOT = Path(__file__).resolve().parent.parent
REPOSITORY = "238ivan238-jpg/Wisp"
BRANCH = "codex/wisp-android-v1"
CERTIFICATE_SHA256 = "be3f6aa53a2dbf4948177bee7de42b976f363e2137ff8b95e141d5465086dd9e"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--notes", required=True, help="User-facing changes, without credentials")
    args = parser.parse_args()
    sdk = Path(os.environ.get("ANDROID_HOME", "/workspace/.wisp-tools/android")) / "build-tools/35.0.0"
    build = ROOT / "app/build/outputs/apk/release"
    metadata = json.loads((build / "output-metadata.json").read_text())
    if metadata["applicationId"] != "app.wisp.voice" or len(metadata["elements"]) != 1:
        raise SystemExit("Unexpected package or split APK build")
    element = metadata["elements"][0]
    version, code = element["versionName"], element["versionCode"]
    if not isinstance(code, int) or not 1 <= code <= 2147483647 or not re.fullmatch(r"[0-9]+(?:\.[0-9]+){1,3}(?:[-+][a-zA-Z0-9.-]+)?", version):
        raise SystemExit("Invalid release version")
    apk = (build / element["outputFile"]).resolve()
    if not apk.is_relative_to(build.resolve()) or not apk.is_file() or not 0 < apk.stat().st_size <= 100 * 1024 * 1024:
        raise SystemExit("APK missing, too large or outside build directory")
    verified = subprocess.check_output([str(sdk / "apksigner"), "verify", "--print-certs", str(apk)], text=True)
    certificates = re.findall(r"Signer #[0-9]+ certificate SHA-256 digest: ([0-9a-f]+)", verified)
    if certificates != [CERTIFICATE_SHA256]:
        raise SystemExit("Wrong signing certificate. Restore the existing private .local/debug.keystore; do not publish this build.")
    badging = subprocess.check_output([str(sdk / "aapt"), "dump", "badging", str(apk)], text=True)
    package = re.search(r"package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'", badging)
    minimum = re.search(r"sdkVersion:'([0-9]+)'", badging)
    if not package or package.groups() != ("app.wisp.voice", str(code), version) or not minimum:
        raise SystemExit("APK metadata does not match the declared release")
    data = apk.read_bytes()
    digest = hashlib.sha256(data).hexdigest()
    destination = ROOT / "downloads"
    destination.mkdir(exist_ok=True)
    feed = destination / "latest.json"
    if feed.exists():
        previous = json.loads(feed.read_text())
        if previous["versionCode"] > code or (previous["versionCode"] == code and previous["sha256"] != digest):
            raise SystemExit("Increment versionCode and versionName before publishing a changed APK")
    target = destination / f"Wisp-{version}.apk"
    if target.exists() and hashlib.sha256(target.read_bytes()).hexdigest() != digest:
        raise SystemExit("Versioned APK already exists with different bytes; increment the version")
    value = {
        "schemaVersion": 1,
        "packageName": "app.wisp.voice",
        "versionCode": code,
        "versionName": version,
        "minSdk": int(minimum.group(1)),
        "apkUrl": f"https://raw.githubusercontent.com/{REPOSITORY}/{BRANCH}/downloads/Wisp-{version}.apk",
        "sha256": digest,
        "size": len(data),
        "notes": args.notes[:2000],
    }
    shutil.copyfile(apk, target)
    temporary = feed.with_suffix(".json.part")
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n")
    temporary.replace(feed)
    print(f"Prepared {target.name}, versionCode {code}, {len(data)} bytes")
    print("Commit the APK and downloads/latest.json together; publish only after build/tests/lint pass.")
    print(f"Update feed stays on branch {BRANCH}; do not delete that branch.")


if __name__ == "__main__":
    main()
