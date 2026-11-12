# Camera2 framework and CameraX adapter

The app uses CameraX for preview and recording. The shared Camera2 code is
inside `camera/camera2`; it depends on Android Camera2 and domain models, not
CameraX or UI state. `camera/camerax` owns CameraX-specific submission.

## Three separate sources of truth

- `CameraCatalog` reads requested camera IDs' `CameraCharacteristics` without
  opening a camera. It caches successful/failed descriptors and invalidates that
  cache when CAMERA permission changes. `DefaultBackCameraRepository` resolves
  the default CameraX camera and uses catalog metadata for zoom inventory.
- `CameraRequestOverrides` contains application-owned requests, not measurements or
  a complete CameraX request. Removing a key releases this application's override.
- `CameraFrameMetadata` reads a completed capture's request and reported result.
  Reported values may differ from desired values. The current wrapper exposes the
  logical TotalCaptureResult; it does not expose separate physical-camera results.

`CameraCapabilities` is the existing app-control projection. Camera2 characteristic
queries supply hardware limits; the CameraX adapter separately supplies CameraX
low-light availability. A raw key's presence does not prove that an arbitrary
combination of controls, streams, encoders, and dynamic ranges will work.

Sensor timestamps and local receipt timestamps are different clock domains unless
the device explicitly guarantees otherwise. Frame numbers are meaningful only
within their capture session/generation.

## Request ownership and updates

`CameraRequestCompiler` contains the existing control policies (exposure, focus,
white balance, FPS, noise reduction/sharpening and tonemapping). This list does not
limit the framework. Storage, diffing, and submission accept any public typed
`CaptureRequest.Key<T>`.

The coordinator currently compiles one complete desired control state. There is
no owner/contribution registry or public `contribute` API. Future controls must
evaluate capabilities and documented mode requirements. This structural refactor
adds no support gating, substitutions, or request contributors.

The override map is copied, while the current values are immutable Camera2
parameters (scalars, Range, and cached TonemapCurve instances). It does not
defensively copy arbitrary mutable arrays, Rect, or Location values. Such values
need an explicit copying policy before future features can safely add them.

`CameraControlCoordinator` serializes override writes and CameraX-native zoom, metering and
low-light operations. It retains desired state while a submission is in flight,
coalesces drag updates at the existing 33 ms cadence, and flushes the final value.
`CameraXRequestBackend` submits only added/changed keys and explicitly clears removed
keys in one `applyCamera2Interop` block. Unchanged settings cause no interop call;
they remain in CameraX's repeating requests without per-frame resubmission.

Successful submission and observed camera behavior remain separate. Failed or
canceled submissions invalidate the submission cache; the next explicit operation
reconciles the complete desired set and clears possibly submitted obsolete keys.
There is no unbounded retry loop. Generation checks prevent old completions or
results from updating a new attachment.

## Exposure handoffs

The coordinator owns the writer for the duration of each handoff:

1. From automatic/priority AE, request AE lock without changing EV.
2. Observe a newer same-generation result with the corresponding lock request and
   locked AE state. Use its exposure values, not a previously displayed sample.
3. Copy sensitivity, exposure time, frame duration and available post-RAW boost
   into a manual handoff; disable AE and release the temporary lock atomically.
   Priority handoffs keep AE enabled with the appropriate priority mode.
4. Observe the corresponding request/result mode before applying any different
   explicit user target and confirming the transition.
5. Returning to Auto releases the manual and priority overrides and observes
   automatic, unlocked operation. It does not attempt to lock AE while AE is OFF.

Without advertised AE-lock support, the existing best-effort path uses the latest
valid same-generation exposure result. That path does not establish locked
continuity or add a separate degraded-continuity notification. Missing required exposure metadata fails the transition. Handoffs have
a 2-second timeout. Failure attempts to restore Auto and reports whether that
restoration was actually observed; it never claims confirmed recovery solely from
an interop future completing.

Focus and CCT/tint retain their existing policies. They do not use the AE sequence.
A future gains/transform white-balance handoff must implement Android's AWB-lock
and result-copy sequence; gains must not be reinterpreted as CCT/tint.

## Results, lifecycle, and backend boundaries

One CameraX session repeating callback feeds `CameraResultReader` and the existing
telemetry presentation. The reader deduplicates frames and keeps only the latest
frame for the active generation. Exposure handoffs consume unthrottled latest results;
sliders publish every 250 ms and statistics every 500 ms. Camera timestamps estimate
capture cadence, not the number of encoded frames or verified encoder drops.

Stopping the lifecycle or rebinding invalidates the attachment and clears live
results. Starting again binds a fresh generation. CameraX owns its CameraDevice,
session, surfaces, templates and repeating submission; callbacks must never close
or submit requests directly on those raw objects.

Future direct Camera2 device/session/output ownership belongs in `camera/direct/`.
GPU processing belongs in `camera/processing/gpu/`, and direct MediaCodec encoding in
`camera/encoding/mediacodec/`. These packages and a shared backend interface are not yet
implemented. The direct pipeline can reuse the catalog, typed key snapshots,
control policies, and frame reader. CameraX-native control effects will need the
direct pipeline's own implementation. Releasing an override on direct Camera2
requires restoring the template/baseline request rather than leaving a stale
builder value behind. Repeating overrides are not a one-shot trigger API;
session/output changes need actual session/output configuration.

The Device Compatibility screen now shares metadata discovery. Its existing HDR/FPS
table algorithm remains unchanged, including its VideoCapture-only queries. The
planned complete-session parity fix and a detailed capability browser are deferred.

## Refactored ownership

CameraXSessionManager retains provider readiness, binding, observations, generation
invalidation, and shutdown. CameraXSessionPreparation constructs requested outputs without compatibility
validation or preview substitution.

CameraControlCoordinator retains its mutex, submission channel, attachment,
revision/generation checks, low-light reconciliation, and recovery. ExposureHandoff
runs the ordered handoff under the original lock and timeout;
ExposureTransitionSupport contains result matching and exposure/FPS calculations.
The four screen controllers in `ui/camera/state/` share the existing ViewModel
state/scope. They do not create additional camera writers or state stores.

## Physical-device acceptance

Validate on the Samsung S24 / Android 16: camera inventory, reported telemetry,
Auto/manual/priority handoffs, Auto restoration, focus/CCT, EV/metering/low light,
processing controls, rapid slider changes, recording, rotation, background/resume,
and changed settings during rebinding. Confirm that unchanged snapshots cause no
interop writes and that late results never repopulate an inactive generation.
Desktop builds and lint cannot establish that a device honors the requested modes.

For the source map and debug-only UI testbed, see [source organization](architecture.md). Current refactor validation is recorded separately in [refactor validation](refactor-validation.md).

## Advisory compatibility

Compatibility scanning runs when its screen is opened and does not gate camera entry. Session setup attempts the requested configuration without predictive frame-rate, resolution, stabilization, codec, or color-combination rejection. Codec, bit-depth, and encoding use the restored automatic supported-combination selection: preserve the touched setting, then preserve the other settings in most-recently-touched order. Offered color modes come from reported combinations. This selection does not display a predictive error. Advertised compatibility may gray an option as information, but does not disable selection. Preview settings are not preflight-rejected or silently replaced. Actual platform construction, binding, and recording failures retain diagnostics and cleanup. Numeric input checks and recording/lifecycle concurrency gates remain. No intentional crash or new fallback was added.
