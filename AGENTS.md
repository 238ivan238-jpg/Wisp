# Wisp development and delivery

The user installs Wisp on a Pixel 9 and expects future releases to appear inside the app.

User UI preferences: the input UI is only a movable floating microphone, without a keyboard panel, branding, language/mode chips, preview, ABC, undo, space/delete/Enter, or manual insert button. Tap starts/stops recording, hold opens settings, drag moves the icon. Insert processed text automatically into the current editor after recording stops; never send the message. Language/mode controls belong in settings. Use monochrome colors following the system light/dark theme. Reserve the system navigation area and keep touches outside the bubble available to the underlying app. Do not restore removed controls without the user's request.

The user explicitly approved a single microphone; do not add a separate gear to the floating UI. Settings remain available by holding the microphone or opening Wisp.

Dictation should produce edited written text, including grammar/explicit self-corrections and automatic lists, while preserving facts, negation and Russian/English technical terms. Keep the raw mode optional. Do not seed Whisper with generic language-description prose: it was echoed into a user's transcription. Without a user dictionary, omit its prompt field. Do not blacklist legitimate spoken English. Pass the dictionary as data to cleanup, never as system instructions. Mock transport tests check the data path; do not describe them as validation of live model language quality.

- Use the existing checkout. Do not create a worktree unless the user explicitly asks.
- Keep the update feed URL stable: branch `codex/wisp-android-v1`, file `downloads/latest.json`. Do not delete or repurpose this branch; installed apps depend on it.
- For Android behavior changes, increment `versionCode` and `versionName` in `app/build.gradle`. Never replace a published versioned APK with different bytes.
- Activate `source scripts/cloud-env.sh`; run `bash scripts/setup-cloud.sh`. Complete the relevant tests and lint before publishing. Preserve TLS, checksum and signing verification.
- Run `python3 scripts/prepare-release.py --notes '<brief user-facing changes>'` after a successful build. It checks the existing signing certificate, generates the versioned APK and updates the feed with its SHA-256/size/version. Run it again to check repeatability if the publisher changes.
- Deliver the new APK and `downloads/latest.json` in the same commit. When the user's task authorizes delivery, push the commit to `codex/wisp-android-v1` without force. Verify the public feed/download and actual client download checksum before reporting success. Preserve remote changes and stop to resolve divergence rather than force-pushing.
- After publishing, run `python3 scripts/verify-update-channel.py`. It exercises the actual Java update client against GitHub for both an older installed version and an already-current version, and compares the download to the local release APK. A completed Android install still needs a phone.
- Retain the private `.local/debug.keystore` for compatible updates. Never publish it, passwords, Groq keys, caches or `.local` contents. `downloads/Wisp-source.zip`, when refreshed, must exclude `downloads/`, build outputs and private files.
- Updater code must verify file size, SHA-256, package/version and the installed application's signing certificate before handing the APK to Android. It may not bypass the OS installation approval or attach Groq credentials to GitHub requests.
- Distinguish JVM/network checks from a completed installation on the physical phone. Do not claim a real Groq call or device installation passed unless observed.
