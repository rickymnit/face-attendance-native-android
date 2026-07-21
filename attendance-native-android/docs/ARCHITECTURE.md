# Architecture

## Overview

Schoollog Attendance is currently a single-module Android app organized with clean architecture packages so each area can later become its own Gradle module. The app keeps gate attendance offline-first: camera analysis and local attendance marking do not depend on internet access.

Primary areas:

- `camera`: live CameraX frame acquisition and frame pipeline entry point
- `ml`: ML Kit face detection, TFLite embedding generation, local matching, and replaceable ML interfaces
- `attendance`: gate state machine, ViewModel, and attendance repositories
- `enrollment`: student profile management and live-frame face embedding enrollment
- `sync`: mock ERP sync and WorkManager scheduling
- `core`: settings, navigation, permissions, and shared UI/common models

## Live Camera Frame Pipeline

Gate Mode uses CameraX with exactly two use cases:

- `Preview` for the live front-camera preview
- `ImageAnalysis` for live frame face detection and pipeline analysis

The analyzer uses `STRATEGY_KEEP_ONLY_LATEST` so slow processing does not build a queue of stale frames. `CameraFrameAnalyzer` throttles processing to roughly 5-10 FPS and closes each processed `ImageProxy` after ML Kit processing completes.

Current flow:

1. `GateCameraScreen` requests camera permission.
2. `CameraPreview` binds CameraX to the screen lifecycle.
3. CameraX sends frames to `CameraFrameAnalyzer` through `ImageAnalysis`.
4. The analyzer creates `CameraFrameInfo` from live frame metadata.
5. `MlKitFaceDetectorEngine` processes the live `ImageProxy` as an `InputImage` and returns face count, bounding box, pose, and quality metadata.
6. `LiveFramePipeline` receives frame info plus face detection output, waits for stable face tracking, then runs liveness v0 before TensorFlow Lite embedding generation and local matching gates. The analyzer also creates an in-memory face crop from the same open live frame once quality and stability pass.
7. `GateAttendanceViewModel` receives pipeline output and advances the gate state machine.
8. UI displays camera status, analyzer FPS, face status, gate state, pending sync count, and last sync status. When enabled in Settings, Gate Mode also shows a debug metrics panel.

No normal attendance path uses photo capture.

## Offline-First Attendance Flow

Attendance is saved locally before any sync attempt.

1. A live frame reaches ML Kit face detection.
2. Face count/quality gates run from ML Kit output; liveness v0, embedding, and local matching gates run after face quality passes.
3. `AttendanceGateStateMachine` moves through `IdleWaitingForFace -> FaceDetected -> FaceQualityChecking -> HoldStill -> CheckingLiveness -> GeneratingEmbeddingPlaceholder -> MatchingStudentPlaceholder -> AttendanceMarked` or `ManualReviewRequired`.
4. On successful match, `GateAttendanceViewModel` calls `AttendanceEventRepository.saveAttendanceEvent()`.
5. `RoomAttendanceEventRepository` inserts an `AttendanceEventEntity` with `syncStatus = PENDING`.
6. Only after local insert succeeds does the UI show attendance marked.
7. Liveness, recognition, and local-save failures are stored in `failed_recognitions` when manual review is appropriate.
8. WorkManager sync later sends pending events to the ERP mock API.

Duplicate scans are prevented using the DataStore setting `duplicateScanCooldownMinutes`.

## Room Database Design

`AppDatabase` stores the offline gate data.

Tables:

- `students`: local student roster/enrollment basics
- `face_embeddings`: binary face embedding vectors with model version, source, status, quality, and timestamps
- `attendance_events`: offline-first attendance event queue
- `failed_recognitions`: local audit trail for liveness, recognition, and local-save failures that require manual review

Important fields:

- `AttendanceEventEntity.syncStatus`: `PENDING`, `SYNCED`, or `FAILED`
- `StudentEntity.schoolId + erpStudentId`: unique index to prevent duplicate students for a school
- `FaceEmbeddingEntity.status`: `ACTIVE`, `PENDING_APPROVAL`, or `DELETED`; only one active student/model embedding is allowed

DAOs:

- `StudentDao`
- `FaceEmbeddingDao`
- `AttendanceEventDao`
- `FailedRecognitionDao`

Repositories keep UI/ViewModels away from Room details.

## Sync Architecture

Sync is intentionally decoupled from attendance marking.

Components:

- `SyncApi`: interface for future ERP API calls
- `MockSyncApi`: mock implementation that does not call a real server
- `RoomAttendanceSyncRepository`: reads pending Room events and updates sync status
- `SyncWorker`: WorkManager worker that performs background sync
- `AttendanceSyncScheduler`: schedules periodic sync and manual one-time sync

Current sync behavior:

1. Worker reads `PENDING` events from Room.
2. Repository maps each row to `AttendanceEventSyncRequest`.
3. `MockSyncApi` returns success.
4. Successful rows are marked `SYNCED`.
5. Errors mark rows `FAILED` and worker returns retry.

The request model is shaped for Schoollog ERP:

- `eventId`
- `schoolId`
- `studentId`
- `deviceId`
- `gateId`
- `eventType`
- `timestamp`
- `matchScore`
- `livenessScore`
- `qualityScore`

## Settings Architecture

Attendance rules are stored locally with DataStore Preferences through `SettingsRepository` and `DataStoreSettingsRepository`.

Rules include:

- school/device/gate IDs
- start, late, and half-day times
- duplicate scan cooldown
- require out-time flag
- recognition mode: Strict, Balanced, Lenient

Gate Mode reads these rules so attendance event metadata and duplicate cooldown are configurable without changing code.

## Performance Metrics

`PerformanceMonitor` tracks lightweight live-pipeline metrics without changing product flow:

- analyzer input FPS
- processed FPS
- average ML Kit face detection time
- average face quality evaluation time
- average liveness v0 time
- average time from face detection to pipeline decision
- dropped/throttled frame count
- last failure reason

The metrics are exposed as a `StateFlow` and displayed in Gate Mode only when `showDebugMetricsPanel` is enabled in DataStore-backed settings.

## Future ML Integration Points

Current and future ML classes remain behind interfaces. `FaceDetectorEngine` has an ML Kit implementation, `FaceEmbeddingEngine` has TensorFlow Lite infrastructure, and `FaceMatcher` has a Room-backed local cosine matcher. Remaining production-hardening areas include:

- replacing or strengthening `LivenessEngineV0` with a production anti-spoof model
- calibrating face quality, liveness, and matcher thresholds on target devices

The rest of the app should not need to know which model implementation is active. Real implementations should still consume live frame data from `ImageAnalysis` and preserve offline-first behavior. The face embedding model must be supplied at `app/src/main/assets/models/face_embedding.tflite`.
