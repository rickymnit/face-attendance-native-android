# Schoollog Attendance

Schoollog Attendance is a native Android kiosk app for school gate attendance. An Android phone can be mounted at a school gate, run in Gate Mode, analyze live camera frames, and mark attendance locally first so the gate keeps working even when internet is slow or unavailable.

The current app is a production-oriented foundation. ML Kit face detection, TensorFlow Lite embedding loading, and local cosine matching are implemented on live CameraX frames; liveness v0 and ERP networking remain replaceable implementations.

## Product Purpose

- Mark student attendance at the school gate using live camera frames.
- Keep attendance offline-first by saving every attendance event locally before sync.
- Run on-device face detection from live camera frames and prepare the app for liveness detection, face embeddings, and local matching on basic Android devices.
- Support school/device/gate settings, enrollment data, pending sync visibility, and manual sync.

## Tech Stack

- Kotlin
- Native Android, single `:app` module
- Jetpack Compose
- CameraX `Preview` + `ImageAnalysis`
- Room for local offline storage
- DataStore Preferences for attendance rules/settings
- WorkManager for background sync
- Gradle Kotlin DSL
- Clean architecture package structure, ready for future multi-module extraction

## Architecture Overview

Main package: `com.schoollog.attendance`

- `app`: application class, app container, settings screen/view model
- `core`: common models, DataStore settings, permissions, navigation, theme/UI helpers
- `camera`: CameraX preview, ImageAnalysis analyzer, live frame pipeline
- `attendance`: gate state machine, attendance ViewModel, Room-backed attendance repositories
- `enrollment`: student enrollment form, live-frame face enrollment, Room-backed student and embedding persistence
- `sync`: mock ERP API, sync repository, WorkManager worker/scheduler
- `ml`: ML Kit face detection, TensorFlow Lite embedding generation, local matching, and replaceable liveness/recognition interfaces

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the detailed system design.

## Current Implemented Features

- First-run device registration screen that binds the gate phone to one school and one gate before Gate Mode is allowed.
- Home screen with navigation to Gate Mode, Enrollment, and Settings after registration.
- Gate Mode front-camera live preview using CameraX.
- Live frame analyzer using `ImageAnalysis` only, throttled to roughly 5-10 FPS.
- No `ImageCapture`, no photo capture, and no image saving.
- ML Kit live-frame face detection with face count, bounding box, pose, eye, and smile metadata.
- Stable face tracking gate before liveness/recognition.
- Liveness v0 heuristic over a short live-frame sequence; ready to replace with a future TFLite anti-spoof model.
- In-memory face crop/alignment placeholder after quality and stable tracking pass.
- TensorFlow Lite face embedding model loader infrastructure with a compatible FaceNet 512 model bundled at `app/src/main/assets/models/face_embedding.tflite`.
- Missing model handling still fails safely with `MODEL_NOT_FOUND` / `Face model not installed`; debug mock recognition is disabled by default.
- Product state machine connected to real face detection, face quality, stable tracking, liveness gates, real embedding generation, and local matcher decisions.
- Optional Gate Mode debug metrics panel for analyzer FPS, ML timing, dropped frames, decision latency, last failure reason, and live in-memory face crop preview.
- Room database for students, face embeddings, attendance events, and failed recognitions.
- Attendance events saved locally first with `PENDING` sync status.
- WorkManager sync layer with a Retrofit backend API client; debug builds can use the mock client when `MOCK_SYNC_ENABLED` is true.
- Embedding delta sync fetches added/updated/deleted student embeddings by local `embeddingSyncVersion` and refreshes the in-memory matcher cache.
- Enrollment form with live camera enrollment that captures 3 real live-frame embeddings, averages/L2-normalizes them, and saves an ACTIVE local embedding.
- DataStore-backed attendance settings/rules, including a debug metrics panel toggle.
- Pending sync count and last sync status in Gate Mode.
- Manual `Sync Now` and `Sync Students/Embeddings Now` actions in Settings.
- Debug-only Recognition QA screen for support/deployment testing of local recognition metrics and safe debug cleanup actions.
- Debug-only Pilot Readiness screen for device, model, embeddings, sync, charger, permission, and kiosk-mode checks before rollout.


## Device Registration

On first launch, the app shows `Bind Gate Device`. Schoollog support should enter:

- School code
- Gate name / Gate ID
- Device name
- Setup token or admin-login placeholder

The app calls the backend device registration API with Android device info and app version. On success it stores the returned school/device/gate binding locally, encrypts the auth token with Android Keystore-backed AES/GCM, saves returned attendance config, and unlocks the normal Home/Gate Mode flow.

Gate Mode is not available until registration succeeds. Settings shows the current school/device/gate binding, last heartbeat, app version, and model version. Debug builds expose an `Unbind Device` action for support/admin testing; normal guard flow should not switch schools casually.

## Debug Recognition QA

Debug builds show a `Recognition QA` entry on the home screen. Use it before deployment to inspect recognition readiness without server calls.

The QA screen shows enrolled student count, model status, model version, input size, embedding size, current recognition mode, active thresholds, average matching time, last top 3 match scores, last recognition top-3 student IDs/scores, top1-top2 margin, final decision, and failure reason. Testers can enter an `Expected Student ID` for calibration runs. Debug actions can run a model smoke test, reload the embedding cache, clear local attendance events, clear local embeddings after confirmation, export recognition calibration CSV logs, and export anonymized performance logs.

The model smoke test creates an in-memory dummy tensor, runs TFLite inference, validates output size and finite values, L2-normalizes the result, and reports success/failure. The dummy result is never used for attendance.

QA exports do not include face images or raw embeddings. The QA route is gated by `BuildConfig.DEBUG` and is not exposed in release builds.

Debug builds also show `Pilot Readiness`. Run it before a school pilot to check registration, school/gate binding, model install and smoke test, active embeddings, threshold mode, sync API reachability, pending sync count, charger state, camera permission, kiosk-mode recommendation, heartbeat, and last sync health.


## Environment Setup

The sync layer reads these Gradle/build config values:

- `API_BASE_URL`: backend base URL baked into the current build variant.
- `ENVIRONMENT`: build environment label. Debug builds use `debug`, staging builds use `staging`, and release builds use `production`.
- `MOCK_SYNC_ENABLED`: available only in debug builds. It defaults to `false`; release and staging builds force `false`.

Set backend URLs with Gradle properties or environment variables:

```bash
# Debug/emulator default is http://10.0.2.2:3000/
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew assembleDebug \
  -PSCHOOLLOG_DEBUG_API_BASE_URL=http://10.0.2.2:3000/

JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew assembleStaging \
  -PSCHOOLLOG_STAGING_API_BASE_URL=https://staging-api.schoollog.example.com/

JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew assembleRelease \
  -PSCHOOLLOG_API_BASE_URL=https://api.schoollog.example.com/
```

To use the debug mock API explicitly, build with `-PSCHOOLLOG_MOCK_SYNC_ENABLED=true`. Release and staging builds always use the real Retrofit API client. Attendance marking remains offline-first: network failures do not block local attendance saves, and unsynced events stay local for later retry or review.

## Build

This environment has a full Java 17 JDK at `/usr/lib/jvm/java-17-openjdk-amd64`. Use it if your default Java install does not include `javac`.

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew test
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --no-daemon --no-configuration-cache assembleDebug
```

Place the face embedding model at:

```text
app/src/main/assets/models/face_embedding.tflite
```

The current bundled model is a compatible FaceNet 512 TensorFlow Lite asset used to exercise the live-frame embedding pipeline. Replace it with a Schoollog-approved production model before rollout.

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Run

Open the project in Android Studio and run the `app` configuration on a physical Android device or emulator with a camera. A physical phone is recommended for Gate Mode camera testing.

From command line:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew installDebug
```

## Next Milestones

- Tune ML Kit face quality thresholds and benchmark on target low-end Android devices.
- Add real liveness detection using a short sequence of live frames.
- Calibrate enrollment thresholds on school devices, add guided bulk import, and harden model upgrade/re-enrollment workflows.
- Integrate real Schoollog ERP sync API and authentication.
- Add device provisioning, monitoring, logs, and production kiosk hardening.

See [docs/ROADMAP.md](docs/ROADMAP.md) for phased delivery.
