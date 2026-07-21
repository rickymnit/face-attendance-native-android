# One-School Pilot Plan

This plan is for the first controlled rollout of the Schoollog AI Attendance gate app at one school and one gate. The pilot should start with 50 to 100 students or staff, expand to 500+ after the first results are reviewed, and only then move to full-school deployment.

## 1. Goals

- Validate that live recognition can mark genuine students quickly at the gate.
- Validate the liveness v0 flow and record where it rejects real students or spoof attempts.
- Validate offline-first attendance: local saves must work without internet and sync later.
- Validate guard workflow for failures, duplicates, manual review, and queue handling.
- Validate backend sync and downstream Schoollog ERP attendance updates.

## 2. Setup

- Use one Android phone approved for the pilot, mounted securely on a stable stand.
- Position the phone at student face height, facing the queue head-on.
- Mark the standing distance on the floor, typically 40 to 70 cm from the front camera.
- Keep the phone connected to a charger for the full gate session.
- Check lighting before arrival time; avoid strong backlight and direct glare.
- Confirm internet is available, then also test offline mode intentionally.
- Confirm the support team has setup token/admin access, backend URL, school code, gate ID, and rollback instructions.
- Keep manual attendance available during the pilot.

## 3. Day 0 Setup

- Register the device to the target school and gate.
- Confirm the binding in Settings: school ID/name, device ID, gate ID, app version, model version.
- Install the approved `face_embedding.tflite` model in the app build.
- Import or enroll the first 50 to 100 students/staff.
- Run the model smoke test from Recognition QA.
- Run the 30-second benchmark on the actual gate phone.
- Run Pilot Readiness and resolve all FAIL items before live use.
- Run test attendance for enrolled users in normal gate lighting.
- Train the guard/admin on success, failure, duplicate, offline, and manual-review messages.

## 4. Day 1 Live Pilot

- Start with one gate and one controlled queue.
- Monitor successful recognitions, failed recognitions, liveness failures, duplicates, and manual reviews.
- Watch pending sync count during internet changes.
- Watch device heat, battery, charging state, camera stability, and app responsiveness.
- Ask the guard to note confusing messages or workflow delays.
- Review false match reports immediately. Any wrong accepted match means thresholds must be tightened before expanding.

## 5. Success Criteria

- Zero false attendance during the pilot.
- Average attendance marking time below 3 seconds.
- Genuine recognition around 95%+ under normal conditions.
- Failed cases are clearly handled through manual review or fallback attendance.
- Duplicate scans are rejected within the configured cooldown window.
- Offline attendance remains pending after app restart and syncs after reconnecting.
- No device overheating, app freezes, or repeated camera pipeline failures.

## 6. Rollback Plan

- Switch to manual attendance immediately if false attendance, device instability, or backend outage affects operations.
- Disable AI gate attendance for that gate in the school process until the issue is reviewed.
- Export anonymized debug/performance/calibration logs from the debug QA tools when available.
- Do not export face images or raw embeddings.
- Replace the device if the phone overheats, camera fails, or battery/charging is unreliable.
- Keep all locally saved pending attendance events for later sync or admin reconciliation.

## 7. Daily Report Format

- School ID and gate ID
- Device model and Android version
- App version and model version
- Total scans
- Successful recognitions
- Failed recognitions
- Liveness failures
- Manual reviews
- Duplicate scans rejected
- Sync failures and current pending sync count
- Average decision time and worst decision time
- False match count
- Guard/admin feedback
- Action items before the next pilot day
