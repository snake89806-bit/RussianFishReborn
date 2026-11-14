# Russian Fishing Reborn 0.29.0 — build fixes

This package is based on the repository snapshot `main` that was versioned 0.28.0.

## Changes

- Moved `frameTask` property initialization above the `init` block that posts it, fixing the initialization-order compiler error.
- Replaced the four-argument `Triple` daily quest entries with a dedicated `DailyTask` data class and updated all property references.
- Set Java source/target compatibility and Kotlin JVM target explicitly to 17, matching the JDK 17 configured in GitHub Actions.
- Bumped Android version to `versionCode 29` / `versionName 0.29.0` and updated the workflow artifact name and project docs.

## Verification

- `go test ./...` — passed.
- `go vet ./...` — passed.
- Go server build — passed.
- Kotlin parser check — no syntax/parser errors; unresolved Android references are expected without the Android SDK/classpath.
- ZIP integrity — passed.

The Android APK was **not** built in this environment because Android SDK / Gradle Android build tooling is unavailable here. Use the included GitHub Actions workflow to build the debug APK after uploading this source tree.
