#!/usr/bin/env bash
set -euo pipefail
WISP_REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
source "$WISP_REPO_ROOT/scripts/cloud-env.sh"
mkdir -p "$WISP_TOOLS_ROOT/downloads" "$GRADLE_USER_HOME" "$ANDROID_USER_HOME" "$WISP_REPO_ROOT/.local"
download() {
    local url="$1" file="$2"
    if [[ ! -f "$file" ]]; then
        curl --fail --location --retry 2 --connect-timeout 20 --max-time 240 "$url" -o "$file.part"
        mv "$file.part" "$file"
    fi
}
if [[ ! -x "$JAVA_HOME/bin/javac" ]]; then
    download 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.8%2B9/OpenJDK21U-jdk_x64_linux_hotspot_21.0.8_9.tar.gz' "$WISP_TOOLS_ROOT/downloads/jdk.tar.gz"
    printf '%s  %s\n' f2dc5418092c43003db8f9005c4a286e1c0104fea96ccdd49e8ebd037cac9219 "$WISP_TOOLS_ROOT/downloads/jdk.tar.gz" | sha256sum -c -
    mkdir -p "$JAVA_HOME"
    tar -xzf "$WISP_TOOLS_ROOT/downloads/jdk.tar.gz" -C "$JAVA_HOME" --strip-components=1
fi
if [[ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]]; then
    download 'https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip' "$WISP_TOOLS_ROOT/downloads/android-tools.zip"
    # Published checksum in Google's repository2-1.xml for this exact archive.
    printf '%s  %s\n' e025545c62a8e64c7559119566a569fb1dec5f60 "$WISP_TOOLS_ROOT/downloads/android-tools.zip" | sha1sum -c -
    mkdir -p "$ANDROID_HOME/cmdline-tools"
    unzip -q "$WISP_TOOLS_ROOT/downloads/android-tools.zip" -d "$ANDROID_HOME/cmdline-tools"
    mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
fi
# Gradle's JVM does not automatically honor HTTPS_PROXY. Use the injected proxy
# and system CA store without disabling TLS or writing credentials to the project.
python3 - <<'PY'
from pathlib import Path
from urllib.parse import urlsplit
import os
props=Path(os.environ['GRADLE_USER_HOME'])/'gradle.properties'
prefixes=('systemProp.https.proxyHost=','systemProp.https.proxyPort=','systemProp.http.proxyHost=','systemProp.http.proxyPort=','systemProp.javax.net.ssl.trustStore=')
lines=props.read_text().splitlines() if props.exists() else []
lines=[line for line in lines if not line.startswith(prefixes)]
proxy=os.environ.get('HTTPS_PROXY',os.environ.get('https_proxy',''))
if proxy:
    u=urlsplit(proxy)
    if u.username or u.password: raise SystemExit('Authenticated proxy needs supported JVM credential propagation; credentials must not be saved here.')
    if not u.hostname: raise SystemExit('Invalid HTTPS proxy configuration')
    for protocol in ('http','https'):
        lines += [f'systemProp.{protocol}.proxyHost={u.hostname}',f'systemProp.{protocol}.proxyPort={u.port or 80}']
trust=Path('/etc/ssl/certs/java/cacerts')
if trust.is_file(): lines.append(f'systemProp.javax.net.ssl.trustStore={trust}')
props.write_text('\n'.join(lines)+'\n')
PY
if [[ ! -f "$ANDROID_HOME/platforms/android-35/android.jar" || ! -x "$ANDROID_HOME/build-tools/35.0.0/apksigner" || ! -x "$ANDROID_HOME/platform-tools/adb" ]]; then
    # Capture sdkmanager's status; the producer may receive SIGPIPE after acceptance.
    set +o pipefail
    yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" --licenses > "$WISP_TOOLS_ROOT/licenses.log" 2>&1
    sdk_status="${PIPESTATUS[1]}"
    set -o pipefail
    [[ "$sdk_status" -eq 0 ]]
    "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools'
fi
if [[ ! -f "$WISP_REPO_ROOT/.local/debug.keystore" ]]; then
    keytool -genkeypair -keystore "$WISP_REPO_ROOT/.local/debug.keystore" -alias androiddebugkey -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Wisp Personal Development,O=Wisp,C=US'
fi
cd "$WISP_REPO_ROOT"
./gradlew --console=plain assembleRelease testDebugUnitTest lintDebug lintRelease
mkdir -p dist
cp app/build/outputs/apk/release/app-release.apk dist/Wisp-1.0.0.apk
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose dist/Wisp-1.0.0.apk
sha256sum dist/Wisp-1.0.0.apk > dist/Wisp-1.0.0.apk.sha256
