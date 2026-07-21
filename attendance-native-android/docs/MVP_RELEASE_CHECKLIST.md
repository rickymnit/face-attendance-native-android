# Schoollog Attendance MVP Release Checklist

Use this checklist before releasing the Android Gate Attendance MVP to a pilot school. Every checked item should have an owner and evidence, such as a build link, screenshot, QA export, test report, or support sign-off.

## 1. Android Build

- [ ] Release build completes successfully with `assembleRelease`.
- [ ] Debug-only screens are not visible in release builds.
- [ ] Debug panels are hidden by default.
- [ ] Mock recognition is disabled in release builds.
- [ ] Mock sync is disabled in release builds.
- [ ] `API_BASE_URL` points to the correct backend environment.
- [ ] `ENVIRONMENT` value is correct for the build.
- [ ] App version name and version code are set correctly.
- [ ] Release APK/AAB is signed with the correct key.
- [ ] App installs cleanly on target Android devices.

## 2. Model

- [ ] `app/src/main/assets/models/face_embedding.tflite` is present in the release build.
- [ ] Model smoke test passes in a debug/staging build before release.
- [ ] Android `modelVersion` matches backend embedding packs.
- [ ] Embedding size matches backend embedding records.
- [ ] Strict/Balanced/Lenient thresholds are calibrated with real pilot data.
- [ ] Production default recognition mode is `Strict`.
- [ ] Average embedding inference time is acceptable on target devices.
- [ ] Model/embedding mismatch warning has been tested.

## 3. Attendance Flow

- [ ] Gate attendance uses live CameraX frames only.
- [ ] No `ImageCapture` usage exists in app source.
- [ ] No `takePicture()` usage exists in app source.
- [ ] No face photos are saved during attendance.
- [ ] Live front-camera preview works in Gate Mode.
- [ ] Face detection works for enrolled students.
- [ ] Face quality gate rejects poor framing, small face, tilted face, and multiple faces.
- [ ] Stable face tracking requires the student to hold still before liveness.
- [ ] Liveness v0 works for real live attempts.
- [ ] Obvious spoof attempts are rejected in pilot tests.
- [ ] Real embedding generation works.
- [ ] Local face matching works with current school embeddings.
- [ ] Low-confidence matches are rejected.
- [ ] Ambiguous matches go to manual review/rejection.
- [ ] Duplicate cooldown rejects repeated scans.
- [ ] Attendance saves locally before sync.
- [ ] Pending sync count updates correctly.
- [ ] Sync retry works after network returns.
- [ ] Failed recognition/liveness cases are logged locally.

## 4. Enrollment

- [ ] Live-frame enrollment works.
- [ ] Enrollment captures required live samples.
- [ ] Enrollment does not use photo capture.
- [ ] Enrollment does not save face photos.
- [ ] Re-enrollment replaces the active embedding correctly.
- [ ] Duplicate student creation is prevented for the same school and ERP student ID.
- [ ] Invalid face quality is rejected during enrollment.
- [ ] Missing model blocks production enrollment embedding creation.
- [ ] Newly enrolled embeddings sync or appear in local matcher as expected.

## 5. Privacy And Security

- [ ] Device registration works with school code, gate ID, device name, and setup token.
- [ ] Device is bound to the correct school and gate.
- [ ] Auth token is stored securely.
- [ ] Normal guard flow cannot casually switch school/device/gate.
- [ ] Cross-school data access is blocked by backend and local filtering.
- [ ] Attendance sync uses HTTPS backend API.
- [ ] No face images are exported in QA/debug logs.
- [ ] Raw embeddings are not exported in ordinary support flows.
- [ ] Local embeddings are protected according to deployment policy.
- [ ] Student deletion/deactivation flow is planned and tested through embedding delta delete.
- [ ] Audit logs are available for attendance sync, failed recognition, and device registration.
- [ ] Device unbind/reprovision flow is restricted to debug/admin/support flow.

## 6. Deployment

- [ ] Phone stand or wall mount is ready.
- [ ] Phone is positioned at suitable student face height.
- [ ] Charger is connected and stable.
- [ ] Battery warning has been tested.
- [ ] Lighting is checked for morning gate conditions.
- [ ] Internet connectivity is checked.
- [ ] Offline mode warning has been tested.
- [ ] Device is registered and visible in Settings.
- [ ] Student/embedding sync has completed before pilot start.
- [ ] Support team has setup token/admin access ready.
- [ ] School admin and gate guard are trained.
- [ ] Manual attendance fallback is ready.
- [ ] Android screen pinning or kiosk preparation is configured if required.
- [ ] Support contact and escalation process are shared with the school.

## 7. Pilot Success Criteria

- [ ] Genuine recognition reaches 95-96% or better in normal gate conditions.
- [ ] Zero false attendance occurs during pilot.
- [ ] Average attendance marking time is below 3 seconds.
- [ ] Worst-case marking time is recorded and reviewed.
- [ ] Offline attendance works and survives app restart.
- [ ] Offline sync succeeds after reconnecting internet.
- [ ] Duplicate scans are rejected within the configured cooldown.
- [ ] Multiple faces are rejected.
- [ ] Guard can handle failures and manual review cases.
- [ ] Support team can collect QA/calibration logs without exporting face images.

## Final Go/No-Go Sign-Off

```text
School:
Pilot date:
Build version:
API environment:
Model version:
Device IDs:
Gate IDs:

Android owner:
Backend owner:
Schoollog support owner:
School admin owner:

Open blockers:
Risk level: Low / Medium / High
Release decision: Go / No-Go
Approved by:
Approval date:
```
