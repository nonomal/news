# AGENTS.md - Developer Guide for Vesti Android App

## Project Overview

This is a web feed reader for Android.

It has two backends/modes

- Miniflux (syncs with a server)
- Embedded (uses in-app parser)

## Build Commands

### Build Debug APK
```bash
./gradlew assembleDebug
```

### Build Release APK
```bash
./gradlew assembleRelease
```

### Run All Unit Tests (Local JVM Only)
```bash
./gradlew test
```

### Run a Single Unit Test
```bash
./gradlew testDebugUnitTest --tests "org.vestifeed.backend.BackendTest"
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

### Project Structure
```
app/src/main/kotlin/<package>/    # App code 
app/src/test/kotlin/<package>/    # Unit tests
app/src/androidTest/kotlin/       # Instrumented tests
```

### Imports
- Fully qualified imports (no wildcard imports)

### Types & Null Safety
- Use nullable types (`?`) when values can be null
- Avoid `!!` operator; prefer safe calls (`?.`) and elvis operator (`?:`)

### Database
- Raw SQL and built-in helpers only, no new external deps or ORMs
- Feed, Entry and Link table fields closely follow ATOM spec, extra/extenstion fields should be marked with an ext_ prefix

### Networking
- OkHttp for network calls
- MockWebServer for testing external calls

### Testing
- JUnit 4

### Android-Specific
- ViewBinding enabled
- Use `Fragment` with `FragmentKtx` extensions

## Emulator (debug device)

Use the Android emulator when you need a real Android runtime for crash
reproduction, log capture, or manual UI checks.

Use `adb` or `devtools` to interact with a running emulator.

```bash
./devtools emulator start     # boot the AVD (loads default_boot snapshot if present)
./devtools emulator status    # confirm the device is online and booted
./devtools emulator stop      # shut down cleanly via `adb emu kill`
./devtools run                # assemble debug APK, install it, launch it
./devtools clean              # uninstall the debug APK from a running emulator
```
