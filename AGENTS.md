# Repository Guidelines

## Project Structure & Architecture

HDRRecorder is a single Android application module built with Kotlin, Jetpack Compose, CameraX, and MVVM. Production code is in `app/src/main/java/com/pilerks1/hdrrecorder/`:

- `ui/AppContent.kt` contains root Compose navigation, its private screen enum, and permission-message component. Activity and Application types stay in the root package.
- `ui/camera/` contains the route/screen, window effects, runtime contract, and substantial `state/`, `controls/`, `preview/`, `layout/`, `stats/`, and `settings/` responsibilities.
- `ui/compatibility/` and `ui/diagnostics/` contain those screens and their presentation state.
- `camera/model/` contains shared camera models. `camera/camera2/` contains reusable Camera2 metadata, request, and result code; it must not import CameraX, Compose, or ViewModels. `camera/camerax/` owns CameraX sessions and controls.
- `camera/camerax/` also owns recording. Shared capture/recording settings and bitrate validation live in `camera/model/`.
- `data/storage/`, `data/presets/`, `data/telemetry/`, `data/compatibility/`, and `data/diagnostics/` own their platform/data responsibilities and associated models. Preferences live beside storage; telemetry models stay in the telemetry package.
- Root `MeasurementFormatting.kt` contains measurement formatting and shared time units.
- `ui/style/UiDimensions.kt` and `UiTextStyles.kt` centralize dimensions and explicit text styles in named groups. Keep runtime measurements, insets, and density conversions beside layout code.
- Resources and the manifest are in `app/src/main/res/` and `app/src/main/AndroidManifest.xml`.

Keep responsibilities narrow: UI renders state, ViewModels coordinate work, and shared engines own platform APIs. Screen controllers share the ViewModel's state and scope; do not add parallel state stores or coroutine scopes.

See `docs/` for more information.

## Build and Manual Validation

Run from the repository root (use Android Studio's bundled JDK if Java is not configured):

```powershell
.\gradlew.bat :app:assembleDebug       # build a debug APK
.\gradlew.bat :app:lintDebug           # run Android lint
```

The development reference device is a Samsung S24 on Android 17. Do not introduce undocumented or deprecated CameraX APIs.

The UI testbed is a debug-only development tool at `app/src/debug/java/com/pilerks1/hdrrecorder/ui/camera/UiTestbedCameraRuntime.kt`, including its private simulated data. It renders the real UI using simulated capabilities. Build-specific `CameraRuntimeProvider` files live under `ui/camera/` in debug/release; the shared contract lives in main. Keep simulation code out of main/release and retain the existing `ui_testbed` intent flag:

```powershell
adb shell am start -n com.pilerks1.hdrrecorder/.MainActivity --ez ui_testbed true
```

Launch a fresh app process when changing that flag. A normal launch uses hardware. Validate layout with the testbed and actual camera behavior on a physical device; report pending device checks explicitly.

## Coding Style & Naming

Use idiomatic Kotlin with four-space indentation. Types, composables, and Kotlin files use `PascalCase`; functions/properties use `camelCase`; packages use lowercase without underscores. Match filenames to their primary type/composable or a clearly scoped responsibility. Use `Route` for ViewModel/lifecycle integration, `Screen` for rendering, and `UiState`/`UiEvent` for presentation contracts. Use `CameraX`/`Camera2` names when backend ownership matters. Retain standard camera abbreviations and existing serialized enum identifiers. Avoid new generic `Utils`, `Helpers`, `Shared`, or `Wrapper` files.

Prefer immutable state, small focused functions, and descriptive names over clever abstractions. Keep Compose styling consistent with existing controls and avoid changing established UI structure unless requested. Preserve existing dimension values; equal numeric values do not imply a shared design decision.

Prefer files around 50-250 lines, with no minimum. Review functions above 50 lines; extract named responsibilities rather than meeting a hard limit. Longer cohesive Compose declarations and ordered camera transactions are acceptable. CameraViewModel wiring, CameraControlCoordinator serialization/recovery, and CameraXSessionManager lifecycle/observation ownership are deliberate larger-file exceptions; do not scatter their coupled state solely to reduce line counts.

Keep dependency versions in `gradle/libs.versions.toml`.

A folder should represent a substantial responsibility, and a separate file should provide a useful ownership or lookup boundary. Keep related companion declarations together; avoid single-file wrapper folders and numerical minimums for files per folder. Small independent contracts may remain separate.

Make sure to clean up previous code after new edits. Maximize code reuse with minimal duplication.

If an unstated fallback is added, be explicit in what you added and what it does.

Keep code edits and additions simple, concise, and to the point. Do not over-engineer. Do not deviate from plans or add excess "nice to haves."

## Automated Tests

Do not add automated tests, test fixtures, or test-framework dependencies. Keep production calculations that the app uses and keep the debug UI testbed. Use debug builds, lint, and manual validation; do not use release builds for testing.
