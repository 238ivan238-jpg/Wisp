#!/usr/bin/env python3
"""Verify the published update using the actual Java client, without a Groq key."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parent.parent


def main():
    tools = Path(os.environ.get("WISP_TOOLS_ROOT", "/workspace/.wisp-tools"))
    jdk = Path(os.environ.get("JAVA_HOME", str(tools / "jdk"))) / "bin"
    cache = Path(os.environ.get("GRADLE_USER_HOME", str(tools / "gradle-user")))
    jars = list((cache / "caches/modules-2/files-2.1/org.json/json/20240303").glob("*/json-20240303.jar"))
    if len(jars) != 1:
        raise SystemExit("Run setup-cloud.sh first to install the pinned org.json test dependency")
    metadata = json.loads((ROOT / "app/build/outputs/apk/release/output-metadata.json").read_text())
    element = metadata["elements"][0]
    code = element["versionCode"]
    classes = ROOT / ".local/update-smoke-classes"
    output = ROOT / ".local/update-channel-download"
    classes.mkdir(parents=True, exist_ok=True)
    source = ROOT / "app/src/main/java/app/wisp/voice"
    subprocess.run([str(jdk / "javac"), "--release", "17", "-cp", str(jars[0]), "-d", str(classes),
                    str(source / "UpdateInfo.java"), str(source / "UpdateClient.java"),
                    str(ROOT / "scripts/UpdateChannelSmoke.java")], check=True)
    properties = []
    proxy = os.environ.get("HTTPS_PROXY", "")
    if proxy:
        parsed = urlsplit(proxy)
        if parsed.username or parsed.password:
            raise SystemExit("Authenticated proxy needs secure supported JVM propagation")
        properties += [f"-Dhttps.proxyHost={parsed.hostname}", f"-Dhttps.proxyPort={parsed.port or 80}"]
    trust = Path("/etc/ssl/certs/java/cacerts")
    if trust.is_file():
        properties.append(f"-Djavax.net.ssl.trustStore={trust}")
    command = [str(jdk / "java"), *properties, "-cp", f"{classes}{os.pathsep}{jars[0]}", "app.wisp.voice.UpdateChannelSmoke"]
    subprocess.run([*command, str(code), str(code - 1), str(output)], check=True)
    built = ROOT / "app/build/outputs/apk/release" / element["outputFile"]
    downloaded = output / f"Wisp-{code}.apk"
    if hashlib.sha256(built.read_bytes()).digest() != hashlib.sha256(downloaded.read_bytes()).digest():
        raise SystemExit("Published APK differs from the local verified build")
    subprocess.run([*command, str(code), str(code), str(output)], check=True)
    print("Live update channel verified: newer-version download, checksum/cache, and already-current version.")


if __name__ == "__main__":
    main()
