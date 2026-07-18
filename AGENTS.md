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

### Android SDK tool locations
On this workstation, `adb` is installed at
`$HOME/Android/Sdk/platform-tools/adb`. It may not be on `PATH`, and
`ANDROID_HOME` may be unset. Either use the full path directly or initialize
the shell before running the commands below:

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
adb devices
```

### Listing available AVDs
```bash
$ANDROID_HOME/emulator/emulator -list-avds
# or, if `emulator` is on $PATH:
emulator -list-avds
```

### Cold-booting an AVD with a window
The first invocation must be detached from the opencode shell, otherwise the
shell wrapper times out and the emulator is killed when the call returns.
Use `nohup setsid` redirected away from the tool's stdout:

```bash
nohup setsid $ANDROID_HOME/emulator/emulator -avd Pixel_9_Pro -gpu host \
    > /tmp/opencode/pixel9-emulator.log 2>&1 < /dev/null &
disown
```

Always pass `-gpu host`. Without it, recent emulator builds (≥ 36.x) probe the
host GPU and self-disable hardware rendering on AMD/Intel drivers they don't
recognise, falling back to `lavapipe` (CPU Vulkan) + `swangle`. The QEMU
process then pegs multiple cores per frame, the guest's `99th gpu percentile`
climbs to several seconds, and Mutter starts reporting the AVD window as
"app is unresponsive" every few seconds. Verify the fix landed by checking
the log for `Selecting Vulkan device:` (should mention a real GPU, not
`llvmpipe`) and that `ps -o pcpu=` on `qemu-system-x86` stays under ~100%.

Headless mode (`-no-window`) is acceptable for adb-only flows but produces no
usable screenshot for visual verification. Drop `-no-window` when you need to
see the device.

### Quickboot (snapshot) workflow
Cold boot is 12–30 s; quickboot from a saved `default_boot` snapshot is ~8 s
(~1.8 s of which is loading the snapshot itself). On the **first** launch the
emulator has no snapshot and falls back to cold boot. After it has booted
cleanly once, save a snapshot and use that on every subsequent launch:

```bash
# 1. Boot once with no existing snapshot (cold). Wait for sys.boot_completed.
# 2. Install the debug build, launch it, and let any first-run setup finish.
# 3. Save the snapshot so the NEXT launch uses quickboot.
adb -s emulator-5554 emu avd snapshot save default_boot

# 4. From now on, the same launch command below loads the snapshot in ~2 s
#    instead of running a full boot. Do NOT pass -no-snapshot or -no-snapshot-load.
nohup setsid $ANDROID_HOME/emulator/emulator -avd Pixel_9_Pro -gpu host \
    > /tmp/opencode/pixel9-emulator.log 2>&1 < /dev/null &
disown
```

Stale snapshot symptoms — if the log shows `Failed to load snapshot
'default_boot'` / `Error -1 from the snapshot callback`, the saved image no
longer matches (typically after an emulator/system-image upgrade or a package
install that broke the saved memory state). To recover:

```bash
rm -rf ~/.android/avd/Pixel_9_Pro.avd/snapshots/default_boot
# then re-do steps 1–3 above.
```

Re-save the snapshot whenever you make persistent system-level changes inside
the AVD (install debug APKs that touch system state, change `adb shell pm`
defaults, etc.); a normal app `install -r` does not invalidate it.

Wait for boot completion before continuing:
```bash
for i in $(seq 1 30); do
    boot=$(adb -s emulator-5554 shell getprop sys.boot_completed | tr -d '\r\n')
    [ "$boot" = "1" ] && { echo "boot in ${i}*5s"; break; }
    sleep 5
done
adb devices  # expect `emulator-5554    device`
```

### Installing and launching the debug build
The debug variant has `applicationIdSuffix = ".debug"`, so the package id is
`org.vestifeed.debug` and the launchable component is
`org.vestifeed.navigation.Activity`. The launcher query
`cmd package resolve-activity --brief -c android.intent.category.LAUNCHER`
often returns "No activity found" for the suffixed id; launch via `monkey`
instead:

```bash
adb -s emulator-5554 install -r -t app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell monkey -p org.vestifeed.debug \
    -c android.intent.category.LAUNCHER 1
adb -s emulator-5554 shell ps -A | grep vesti   # confirm process is alive
```

### Capturing a screenshot
```bash
adb -s emulator-5554 shell screencap -p /sdcard/vesti.png
adb -s emulator-5554 pull /sdcard/vesti.png /tmp/opencode/vesti.png
```

### Stopping the emulator
Always shut down via adb so the AVD lock files and GRPC server are released
cleanly; raw `pkill` can leave the next boot slow:

```bash
adb -s emulator-5554 emu kill
```

If a previous launch was killed mid-startup, also clean the lock file under
`/run/user/$UID/avd/running/pid_<pid>.ini` so the next start is not blocked.

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

