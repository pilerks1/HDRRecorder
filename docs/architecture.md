# Source organization

HDRRecorder remains one Android module. Main Kotlin source starts at `app/src/main/java/com/pilerks1/hdrrecorder/`, with three package roots: `ui`, `camera`, and `data`.

## Source map

| Responsibility | Location |
| --- | --- |
| Android activity, permissions, startup | Root MainActivity.kt |
| Process-wide initialization/crash handler | Root HdrRecorderApplication.kt |
| Measurement formatting/time units | Root MeasurementFormatting.kt |
| Root Compose navigation, private route enum, permission message | ui/AppContent.kt |
| Camera rendering and state/lifecycle integration | ui/camera/CameraScreen.kt and CameraRoute.kt |
| Window effects/runtime contract | Directly in ui/camera/ |
| ViewModel, shared UI state/actions, events, controllers | ui/camera/state/ |
| Control rail, panels, sliders | ui/camera/controls/, panels/, slider/ |
| Preview, geometry/placement, statistics | ui/camera/preview/, layout/, stats/ |
| Settings screen/sections | ui/camera/settings/ |
| Compatibility and diagnostics presentation | ui/compatibility/, ui/diagnostics/ |
| Dimensions and explicit typography | ui/style/UiDimensions.kt and UiTextStyles.kt |
| Shared capture/recording/control models | camera/model/ |
| Reusable Camera2 metadata/requests/results/policies | camera/camera2/ |
| CameraX sessions, controls, configuration, recording | camera/camerax/ |
| Document/output storage and preferences | data/storage/ |
| Preset schema/documents | data/presets/ |
| Telemetry sampling, aggregation, models | data/telemetry/ |
| Compatibility scanning and persisted reports | data/compatibility/ |
| Diagnostic records, formatting, JSON, persistence | data/diagnostics/ |

## Consolidation and ownership

AppContent is the root Compose function, while HdrRecorderApplication is Android's Application subclass. Activity/Application identities remain unchanged. AppContent owns its private navigation enum and permission-message component.

Related declarations share subject files: CameraUiState includes actions and manual-state aliases/helpers; ControlPanels includes availability and the panel enum; CameraLayoutGeometry includes layout specification types. VideoSettingsSection contains both video and color sections. CaptureSettings groups capture/preview/resolution/aspect types, RecordingSettings groups recording options/color/bitrate types, and CameraXSessionConfiguration groups existing construction helpers. TelemetryModels replaces a sparse model subfolder. Camera-sample mapping and FPS estimation are file-private helpers beside their sole coordinator consumer.

Screen controllers still share the ViewModel's state and scope. The ViewModel retains event gates, recording actions, collectors, and cleanup ordering. CameraControlCoordinator retains its serialized writer, revisions, cancellation, and recovery; exposure handoff remains under the original lock/timeout. CameraXSessionManager retains provider/lifecycle/observation ownership. Recording finalization retains its existing lifetime. No pipeline API or state-ownership redesign was introduced.

A folder should represent a substantial responsibility, and a file should provide a useful ownership or lookup boundary. Prefer 50-250 lines with no minimum; review functions above 50 lines without mechanically splitting them. The ViewModel, control coordinator, and session manager remain deliberate larger-file exceptions. Independently useful small files, including preferences and diagnostic JSON, remain separate.

Dimensions retain their named objects in UiDimensions. Statistics text metrics live directly in UiTextStyles. Current values and geometry formulas are unchanged; runtime measurements, insets, density conversion, weights, and algorithm thresholds remain local.

## Source sets and validation

Main contains ui/camera/CameraRuntime.kt. Debug contains CameraRuntimeProvider.kt and UiTestbedCameraRuntime.kt in that same package, with private simulated data in the runtime file. Release contains a provider that always returns RealCameraRuntime; no simulation data/classes are included. The runtime is screen-development infrastructure, not a backend interface.

Launch a fresh debug process using `adb shell am start -n com.pilerks1.hdrrecorder/.MainActivity --ez ui_testbed true`. A normal fresh launch uses hardware. The testbed renders the actual UI with the existing simulated values/events. Keep automated tests/frameworks removed and validate with debug builds/lint plus manual checks on Samsung S24 / Android 17.

## Compatibility and future pipelines

Automatic supported codec/bit-depth/encoding selection preserves the touched setting, then other settings by most recent use. Compatibility scanning remains advisory and does not gate startup or predictively reject frame-rate/resolution/stabilization/session combinations. Actual platform-operation failures retain diagnostics and cleanup. Numeric validation and recording/lifecycle gates remain.

Future code belongs in camera/direct/, camera/processing/gpu/, and camera/encoding/mediacodec/. These folders do not exist yet. Reusable Camera2 support imports no UI, ViewModels, or CameraX. Existing SurfaceRequest/VideoCapture/metering boundaries elsewhere remain unchanged.

There is one repository-owned .gitignore at project root. See [camera ownership](camera-architecture.md), [diagnostics history](error-reporting.md), and [consolidation validation](consolidation-validation.md).
