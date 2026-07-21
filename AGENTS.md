# AGENTS.md - Developer Guide for Vesti Android App

## Project Overview

This is a web feed reader for Android.

## Build Commands

### Build Debug APK
```bash
./gradlew assembleDebug
```

### Build Release APK
```bash
./gradlew assembleRelease
```

### Run Unit Tests
```bash
./gradlew testDebugUnitTest
```

### Run a Single Unit Test
```bash
./gradlew testDebugUnitTest --tests "org.vestifeed.feeds.FeedsModelTest"
```

### Run All Tests (unit + instrumented)
```bash
./gradlew test
```

### Run Instrumented Tests (on device/emulator)
```bash
./gradlew connectedDebugAndroidTest
```
This runs the full instrumented suite without uninstalling the existing debug
app first, so tests may read or modify persistent emulator data. Do not use
this command when the user asks to run the E2E test.

### Run E2E Test (fresh install)
```bash
./devtools e2e
```
Always use `./devtools e2e` for E2E requests. It uninstalls the debug app before
running only `org.vestifeed.e2e.EmbeddedBackendTest`; never substitute
`./gradlew connectedDebugAndroidTest`.

### Clean Build
```bash
./gradlew clean
```

### Build with Info
```bash
./gradlew assembleDebug --info
```

## Code Style Guidelines

### Language & Version
- Kotlin 2.3.10
- Java 21 (JVM target)
- Android SDK 36 (compileSdk)

### Project Structure
```
org.vestifeed.app/src/main/kotlin/<package>/    # App code 
org.vestifeed.app/src/test/kotlin/<package>/    # Unit tests
org.vestifeed.app/src/androidTest/kotlin/       # Instrumented tests
```

### Package Organization
- Group by feature/domain (e.g., `org.vestifeed.feeds`, `org.vestifeed.entries`, `org.vestifeed.auth`, `org.vestifeed.sync`)
- Use lowercase with camelCase for file names

### Imports
- Fully qualified imports (no wildcard imports)
- Order: standard library -> Android -> external libraries -> project
- Example:
  ```kotlin
  package org.vestifeed.feeds

  import androidx.lifecycle.ViewModel
  import androidx.lifecycle.viewModelScope
  import co.appreactor.feedk.AtomLinkRel
  import org.vestifeed.conf.ConfRepo
  import kotlinx.coroutines.Dispatchers
  ```

### Naming Conventions
- **Classes**: PascalCase (e.g., `FeedsModel`, `EntriesAdapter`)
- **Functions**: camelCase (e.g., `addFeed`, `importOpml`)
- **Variables/Properties**: camelCase (e.g., `hasActionInProgress`, `error`)
- **Sealed class states**: PascalCase with object/data class (e.g., `State.Loading`, `State.ShowingFeeds`)
- **Test classes**: `<ClassName>Test` suffix (e.g., `FeedsModelTest`)
- **Test methods**: descriptive names, no prefix (e.g., `fun init()`)

### Types & Null Safety
- Use nullable types (`?`) when values can be null
- Avoid `!!` operator; prefer safe calls (`?.`) and elvis operator (`?:`)
- Use `runCatching` for exception handling instead of try-catch when appropriate

### Database
- Raw SQL and built-in helpers only, no external deps or ORMs
- Every table class has const val SCHEMA, check it before reasoning about the entities those tables represent
- Feed, Entry and Link table fields closely follow ATOM spec, extra/extenstion fields should be marked with an ext_ prefix

### Networking
- Retrofit + OkHttp for API calls
- MockWebServer for testing API integrations

### Testing
- JUnit 4
  ```

### Android-Specific
- ViewBinding enabled
- Use `Fragment` with `FragmentKtx` extensions
- Use `Dispatchers.setMain` / `resetMain` in `@Before` / `@After`

### Formatting
- 4 spaces for indentation (Kotlin default)
- No explicit line length limit (follow Android Studio defaults)
- Trailing commas for readability
- Single blank line between top-level declarations

### What NOT to Do
- Don't use `var` unless necessary (prefer `val`)
- Don't commit secrets or keys to the repository

## Emulator (debug device)

Use the Android emulator when you need a real Android runtime for crash
reproduction, log capture, or manual UI checks.

All emulator interaction goes through the `./devtools` CLI in the repo root.
Don't invoke `adb` or the `emulator` binary directly — the CLI encodes the
defaults the agent would otherwise have to memorise (AVD/device id, package
id, quickboot snapshot handling, the `monkey`-vs-`resolve-activity` launch
quirk). If a workflow you need isn't covered, add a subcommand to `devtools`
rather than reaching for `adb` directly — the CLI is the only supported
surface.

The CLI keeps all transient state (emulator logs, screen recordings, seed
SQLite databases, temporary build artefacts) inside the `.tmp/` directory at
the repo root — never use `/tmp` for dev workflow artefacts. The directory is
git-ignored.

```bash
./devtools emulator start                         # boot the AVD (loads default_boot snapshot if present)
./devtools emulator status                       # confirm the device is online and booted
./devtools emulator stop                         # shut down cleanly via `adb emu kill`
./devtools emulator doctor                       # diagnose Wayland / GPU / Qt-plugin freezes
./devtools emulator screencap .tmp/foo.png       # capture a screenshot from the device
./devtools emulator snapshot save                # freeze current state as the default_boot snapshot
./devtools emulator snapshot delete              # wipe a stale default_boot snapshot
./devtools run                                   # assemble debug APK, install it, launch it
./devtools login                                 # fresh-install debug APK and seed Miniflux credentials from .env
./devtools clean                                 # uninstall the debug APK from a running emulator
```

### Useful tunables
Set these env vars before `./devtools emulator start` or `./devtools run`:

- `VESTI_NO_WINDOW=1` — run headless; suppresses the Mutter ANR dialogs
  the wedged emulator Qt/XCB thread otherwise triggers under Wayland
- `VESTI_CORES=N` — cap QEMU cores when ANRs trip on overload

### Quickboot (snapshot) workflow
Cold boot is 12–30 s; quickboot from a saved `default_boot` snapshot is ~8 s.
On the first launch the emulator has no snapshot and cold-boots. Once boot
is clean and any first-run setup has finished, `./devtools emulator snapshot
save` freezes that state for every subsequent start. If the log shows
`Failed to load snapshot 'default_boot'` after an emulator or system-image
upgrade, run `./devtools emulator snapshot delete` and re-save after the
next cold boot. Logs land in `$VESTI_LOG_DIR/emulator.log` (default
`.tmp/`).

## Dependency Upgrades

Apply upgrades in small, isolatable steps; this repository pins everything in
`gradle/libs.versions.toml` and the `buildSrc`/wrapper flow below is the only
way changes should flow in.

### Recommended workflow
1. Pick one version line at a time. Update its `version.ref` in the catalog, then
   run `./gradlew check assembleRelease`. If it fails, revert and try the next
   stable before layering further changes.
2. Check `buildSrc`-equivalent or AGP-released compatibility tables (e.g.
   AGP 9.x ships with built-in Kotlin 2.2.x) **before** bumping Kotlin itself.
   Adding the `org.jetbrains.kotlin.android` plugin on AGP 9 is a non-default
   path that requires `android.newDsl=false` in `gradle.properties`, which is
   already deprecated in 9.0 and scheduled for removal in 10.0.
3. After each version bump, mirror it in any related code that imports a
   renamed, moved, or removed symbol. Run
   `./gradlew :app:dependencies --configuration releaseRuntimeClasspath`
   afterwards to confirm no transitive downgrade happened.
4. For libraries with breaking interop, leave a `Wait for ...` comment next to
   the version pin (see the Coil entry in `gradle/libs.versions.toml`) so the
   next person knows not to retry the bump until a compatible toolchain exists.
5. When AGP forces a build-toolchain change (Android 17, Compose 1.10, etc.),
   apply the SDK target at the same time as the build-tooling bump, not later.
6. For ad-hoc tooling files (`.github/workflows/*.yml`, scripts like
   `deploy`), keep workflow drift and library drift in the same PR so CI can
   validate the new release pipeline.

### Common gotchas observed in this repo
- **AGP vs Kotlin metadata**: dependencies compiled with Kotlin ≥2.4 metadata
  (Coil 3.5+, recent KSP, kotlinx-coroutines 1.11+) need AGP that bundles Kotlin
  ≥2.4. If a library refuses to build against the AGP-managed compiler, defer
  the upgrade instead of switching to the deprecated legacy DSL.
- **Android 17 / API 37 behavior changes** (`targetSdk = 37`): mandatory
  `ACCESS_LOCAL_NETWORK` runtime prompt for user-typed LAN URLs, Certificate
  Transparency enabled by default, ECH where supported, and new large-screen
  behavior. Touch
  `app/src/main/AndroidManifest.xml`, `app/src/main/kotlin/org/vestifeed/lan/`,
  and any cleartext/LAN path in `app/src/main/kotlin/org/vestifeed/api/`.
- **Stable jetifier/AGP interactions**: when `app/src/main/AndroidManifest.xml`
  uses `android:targetSdk` and `compileSdk` separately, the CI workflow’s
  `actions/setup-java` matrix must stay ahead of the catalog `agp` minimum.
- **R8 + new dependencies**: with `isMinifyEnabled = true` and no custom
  ProGuard rules, new reflection-based libraries may need consumer rules that
  ship inside the AAR. Run `assembleRelease` to surface keep-rule complaints
  in the R8 report under `app/build/outputs/mapping/release/`.
- **Locale strings**: any new or modified string in
  `app/src/main/res/values/strings.xml` must also be added to every other
  `app/src/main/res/values-*/strings.xml` in the locale that matches the
  surrounding translation, otherwise lint will fail the `MissingTranslation`
  check. Reuse the existing translated noun/verb from sibling keys (e.g.
  `bookmarks` for `bookmarks_n`) and keep the `%1$d` placeholder. Only fall
  back to English in a locale when no translation exists for the base term.
  Use `translatable="false"` only for non-user-facing values such as email
  addresses, channel IDs, or URL templates.

