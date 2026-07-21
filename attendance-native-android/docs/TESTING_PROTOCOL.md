# Schoollog AI Attendance Testing Protocol

## Purpose

This protocol validates the Schoollog AI Gate Attendance app in realistic school-gate conditions before production rollout. It covers enrollment quality, live recognition accuracy, liveness behavior, offline-first attendance, sync, and performance on basic Android devices.

The app must be tested using live CameraX `ImageAnalysis` frames only. Do not use photo capture during attendance or enrollment testing.

## 1. Test Setup

### Android Device Requirements

Recommended minimum test devices:

- Android 8.0 or newer.
- Front camera with stable autofocus/exposure.
- 3 GB RAM minimum for baseline testing; include at least one low-end device.
- Battery connected or kiosk charging setup.
- Screen brightness fixed during tests.
- App installed as debug or staging build with the correct face model asset.

Record for every test device:

- Device brand and model.
- Android version.
- Available RAM/storage.
- App version.
- Face model version.

### Phone Stand Positioning

- Mount the phone firmly on a stand or wall bracket.
- Place the front camera roughly at student face height, or slightly below face height angled upward.
- Avoid hand-held testing for official results.
- Keep the phone stable; vibration can affect face stability and liveness timing.
- Ensure the student can see the preview and stand naturally.

### Lighting Setup

Test these lighting conditions:

- Normal daylight or classroom corridor light.
- Early morning low light.
- Bright backlight, such as a gate facing sunlight.
- Mixed indoor/outdoor lighting.
- Night or shaded gate area if the school will use the device at those times.

Avoid placing the phone directly against harsh sunlight. If unavoidable, record that condition clearly.

### Student Distance From Camera

Start with these distances:

- Ideal: 40-70 cm from the phone.
- Near boundary: 30 cm.
- Far boundary: 90-120 cm.

Students should approach one at a time, look at the live preview, and pause briefly until attendance is marked or the app shows a guard message.

### Internet On/Off Cases

Run tests in both modes:

- Online: Wi-Fi or mobile data enabled.
- Offline: internet disabled before marking attendance.

Attendance must be saved locally first. Recognition and attendance marking must not depend on internet availability.

## 2. Enrollment Test

Enrollment testing checks whether student records and face embeddings can be created reliably from live frames.

### 10-Student Enrollment

- Enroll 10 students with valid ERP Student ID, name, class, section, and roll number.
- Confirm each student appears in the local student list.
- Confirm each student has one active embedding for the current model version.
- Reopen the app and verify students remain available.

### 50-Student Enrollment

- Enroll 50 students in the same school ID.
- Confirm the app remains responsive during and after enrollment.
- Randomly select 10 enrolled students and run recognition tests.

### 100-Student Enrollment

- Enroll 100 students.
- Confirm local matching cache reload works from Recognition QA in debug builds.
- Measure recognition speed with the 100-student dataset.

### Duplicate Student Prevention

- Try saving a student with an existing `schoolId + ERP Student ID`.
- Expected result: duplicate creation is blocked or treated as update/re-enrollment flow, not uncontrolled duplication.

### Re-Enrollment

- Select an already enrolled student.
- Run re-enrollment.
- Expected result: previous active embedding is replaced or marked deleted according to app flow, and only the latest valid active embedding is used for matching.

## 3. Recognition Test

For each enrolled student, test the following conditions. Record the result for each attempt as `Accepted`, `Rejected`, `Manual Review`, or `Wrong Match`.

### Threshold Calibration Workflow

Use the debug-only Recognition QA screen during calibration. Production default remains `Strict`.

1. Open Recognition QA in a debug build.
2. Start a new test session. Record tester name and notes such as location, camera mount, lighting, and dataset size.
3. Before each attempt, enter the `Expected Student ID` for the person in front of the camera.
4. Select the lighting condition: `NORMAL`, `LOW_LIGHT`, `BRIGHT_LIGHT`, `OUTDOOR`, or `INDOOR`.
5. Select the face condition: `NORMAL`, `GLASSES`, `MASK`, `HAIR_CHANGE`, `SIDE_ANGLE`, or `FAST_MOVEMENT`.
6. Run the recognition attempt in Gate Mode.
7. Review top 1/top 2/top 3 scores, top1-top2 margin, decision, liveness score, quality score, inference time, matching time, and total decision time.
8. Export the calibration CSV at the end of the session.

The local summary separates genuine accepted, genuine rejected, wrong accepted, ambiguous rejected, low-confidence rejected, and average decision time. Any wrong accepted match means thresholds must be tightened before production rollout. CSV exports must not include face images or raw embeddings.

### Required Conditions Per Student

- Normal face, looking straight.
- With glasses, if the student has glasses available.
- Without glasses, if applicable.
- Different hairstyle, if practical.
- Slight left angle.
- Slight right angle.
- Low light.
- Bright light or backlight.
- Mask or partially covered face.
- Fast movement past the camera.

### Expected Behavior

- Normal genuine attempts should be recognized quickly.
- Slight left/right angles may pass if face quality remains acceptable.
- Low light, bright backlight, masks, covered faces, and fast movement may be rejected with a clear guard/student message.
- Wrong student attendance must never be accepted.
- Ambiguous or low-confidence matches should go to manual review or rejection.

## 4. Spoof And Liveness Test

These tests validate that obvious spoof attempts are not accepted. Current liveness v0 is a first live-frame layer and must not be treated as final anti-spoofing.

### Spoof Cases

- Printed photo of an enrolled student.
- Photo of an enrolled student displayed on another phone.
- Video of an enrolled student displayed on another phone.
- Replay attempt by the same student immediately after successful attendance.
- Multiple faces in the frame.

### Expected Behavior

- Printed photo should not mark attendance.
- Phone photo should not mark attendance.
- Phone video should not mark attendance.
- Immediate repeat scan should be rejected by duplicate cooldown.
- Multiple faces should show `Only one student at a time` and must not mark attendance.

Escalate any spoof case that marks attendance as a critical bug.

## 5. Performance Test

Measure performance on at least one low-end, one mid-range, and one target deployment device.

### Metrics To Record

- Average recognition time from face visible to final decision.
- Worst recognition time.
- Analyzer input FPS.
- Processed/analyzed FPS.
- Liveness time.
- Embedding inference time.
- Matching time.
- Total pipeline time from face detected to decision.
- Dropped/throttled frame count.

Use the debug metrics panel, Gate Mode benchmark panel, and Recognition QA screen in debug builds.

### 30-Second Device Benchmark

Use a debug build with debug metrics enabled in Settings. Run the benchmark from Gate Mode while the live camera preview is active.

1. Mount the phone in the intended gate position.
2. Open Gate Mode and confirm the live preview is smooth.
3. Ask an enrolled student to stand normally in front of the phone.
4. Tap `Run 30-second benchmark` in the debug panel.
5. Keep normal recognition attempts flowing during the 30-second window.
6. Review average, p95, and worst timings for analyzer FPS, face detection, face quality, stable tracking, liveness, crop, embedding inference, local matching, total decision time, memory estimate, dropped frames, success count, and failure count.
7. Export the benchmark CSV for the test record.

Acceptance target: total decision time should stay under 3 seconds, the app should not freeze, and dropped frames should not indicate analyzer backlog. Benchmark CSV exports must not contain face images or raw embeddings.

### Dataset Sizes

Measure local matching time with:

- 100 active embeddings.
- 1,000 active embeddings.
- 6,000 active embeddings.

Expected trend:

- Matching should not query the database on every frame.
- Embeddings should load into memory cache and refresh only when changed.
- Recognition should remain suitable for gate usage at school scale.

## 6. Offline Test

Offline-first behavior is mandatory.

### Steps

1. Enable airplane mode or disconnect Wi-Fi/mobile data.
2. Mark attendance for several enrolled students.
3. Verify attendance is marked locally.
4. Verify pending sync count increases.
5. Close and restart the app.
6. Verify pending events remain pending after restart.
7. Reconnect internet.
8. Tap `Sync Now` from Settings or wait for WorkManager sync.
9. Verify pending count decreases after successful mock sync.

### Expected Behavior

- Attendance should work offline.
- Events must persist locally after app restart.
- Sync should never be required before marking attendance.
- Failed sync should not delete local attendance events.

## 7. Acceptance Criteria

A test cycle is acceptable only when these conditions are met:

- No false attendance is accepted.
- Genuine recognition target is 95-96% or better under normal gate conditions.
- Average attendance time is under 3 seconds.
- Duplicate scans are rejected within the configured cooldown window.
- Multiple faces are rejected.
- Low-confidence and ambiguous matches do not mark attendance.
- Manual review path works for failed or uncertain recognition.
- Attendance is saved locally before sync.
- Offline attendance survives app restart.
- CSV/debug exports do not include face images or raw embeddings.

Any false attendance should block production rollout until investigated and fixed.

## 8. Bug Report Format

Use this format for every issue found during testing.

```text
Title:

Device model:
Android version:
App version:
Face model version:
School ID:
Gate ID:
Recognition mode:

Test condition:
Example: low light, glasses, printed photo, 1000 embeddings, offline sync

Expected result:

Actual result:

Student ERP ID, if relevant:
Expected student ID, if calibration mode was used:
Predicted student ID, if shown:
Top 1 score:
Top 2 score:
Top1-top2 margin:
Liveness score:
Quality score:
Inference time ms:
Matching time ms:
Total recognition time ms:

Steps to reproduce:

Screenshots or screen recording, if safe and allowed:

Notes:
```

Do not attach face crops, raw embeddings, or student biometric data to ordinary bug reports. Use anonymized Recognition QA exports whenever possible.

## Pilot Run Summary Template

```text
School:
Date:
Test lead:
Devices tested:
Students enrolled:
Recognition attempts:
Accepted genuine attempts:
Rejected genuine attempts:
False attendance count:
Manual review count:
Average attendance time:
Worst attendance time:
Offline events tested:
Sync result:
Production recommendation: Go / No-Go
Key blockers:
```
