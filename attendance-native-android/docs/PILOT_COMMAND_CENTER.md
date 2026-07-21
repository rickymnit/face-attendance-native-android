# Pilot Command Center

This guide is the day-by-day operating plan for a one-school Schoollog AI Attendance pilot. It is written for Schoollog support, school managers, and the technical team running the Android gate device, backend, AI worker, and support dashboard.

## Command Center Roles

- Schoollog support lead: owns setup, daily checks, issue triage, and school communication.
- Backend/operator: keeps `attendance-sync-backend`, database, ERP adapter, and AI worker healthy.
- Android/device operator: manages gate phone, camera placement, charging, model install, and kiosk mode.
- School admin/guard: validates student flow, handles manual review, and confirms attendance fallback.

## Day -2: Approval And Data Preparation

### School Approval

- Confirm school management approval for the pilot scope.
- Confirm the first gate and the test population.
- Confirm pilot timings, expected arrival window, and fallback attendance method.
- Confirm who can approve device setup, student import, and manual review.

### Student Data

- Collect the student CSV.
- Collect ZIP photos mapped by ERP student ID or admission number.
- Verify CSV columns:
  - `erpStudentId`
  - `name`
  - `className`
  - `section`
  - `rollNumber`
  - `photoFileName`
- Verify ERP student IDs match the ERP exactly.
- Remove duplicate ERP student IDs before import.
- Confirm no real student photos are retained outside the approved import flow.

### Device And Gate Preparation

- Prepare Android phone with supported Android version.
- Install latest debug/staging APK for pilot setup or release APK for live pilot.
- Place `face_embedding.tflite` in the Android app assets before release build.
- Prepare phone stand at gate height.
- Prepare charger and continuous power.
- Confirm internet at gate, plus offline fallback expectation.
- Mark student standing distance at the gate.
- Check lighting during expected morning arrival time.

## Day -1: Import, Enrollment, And Device Setup

### Backend And AI Worker

- Start PostgreSQL.
- Run backend migrations.
- Start `attendance-sync-backend` with staging configuration.
- Start `attendance-ai-worker` with the correct embedding model path.
- Confirm backend health and support APIs.
- Confirm mock ERP or staging ERP adapter is configured as intended.

### Student Import And Embeddings

- Import student CSV + ZIP photos.
- Review import status.
- Fix row-level errors:
  - missing photos
  - invalid CSV data
  - duplicate ERP student IDs
  - bad photo file names
- Generate embeddings through the AI worker.
- Confirm embedding model version matches Android model version.
- Confirm embedding delta version increased.
- Confirm no raw face photos are retained after processing.

### Android Device Binding

- Register device with school code, gate ID, and setup token.
- Confirm backend returns:
  - `schoolId`
  - `deviceId`
  - auth token
  - config
  - embedding sync version
- Sync embeddings to the phone.
- Confirm local embedding count on Pilot Readiness screen.
- Confirm Gate Mode is blocked until device registration is complete.

### Model And Performance Checks

- Run model smoke test.
- Run 30-second benchmark.
- Run Android stress screen if time permits.
- Confirm average decision time is below 3 seconds.
- Confirm no analyzer backlog.
- Confirm no device overheating.

### Manual Enrollment

- Identify students whose photo import failed or embedding quality was poor.
- Enroll failed students manually through live-frame enrollment.
- Prevent duplicate enrollment unless using explicit re-enrollment.
- Sync embeddings again after enrollment.

### Guard/Admin Training

- Train guard on student positioning:
  - one student at a time
  - face centered
  - look straight
  - move closer if prompted
- Train admin on manual review and fallback attendance.
- Show support dashboard basics.
- Confirm escalation channel for Day 0 and Day 1.

## Day 0: Dry Run

### Test 50 Students

- Test at least 50 students through the actual gate flow.
- Include normal arrival movement and realistic queue behavior.
- Record recognition success, failed recognition, liveness failure, and manual review.

### Offline Mode Test

- Turn off internet on the gate phone.
- Mark several attendance events.
- Restart the app.
- Confirm pending events remain locally.
- Restore internet.
- Confirm pending events sync to backend.

### Duplicate Scan Test

- Scan the same student twice within cooldown.
- Confirm duplicate scan is rejected locally.
- Confirm no second attendance is created.

### Manual Review Test

- Test low confidence or ambiguous match case.
- Confirm attendance is not marked automatically.
- Confirm guard/admin can handle the case using the agreed manual process.

### ERP Sync Test

- Confirm backend receives attendance events.
- Confirm `AttendanceDailySummary` is created.
- Confirm ERP sync status moves to pending/synced, or failed with retry if ERP is unavailable.
- Confirm duplicate backend event sync remains idempotent.

### Record Issues

For each issue, capture:

- device model
- Android version
- app version
- model version
- school ID
- gate ID
- student condition
- expected result
- actual result
- screenshot only if it does not expose sensitive data

## Day 1: Live Pilot

### Gate Monitoring

- Start phone on charger before arrival window.
- Confirm gate camera preview is active.
- Confirm network status.
- Confirm pending sync count starts at zero or known value.
- Keep guard focused on queue flow and manual review.

### Support Dashboard Monitoring

Monitor these screens throughout the arrival window:

- Schools health list
- Devices list by school
- Today attendance summary
- Failed recognitions
- ERP sync status
- Device detail/log summary

Watch for:

- offline device
- missing heartbeat
- growing pending ERP sync
- repeated failed recognitions
- liveness failure spike
- unusual queue delay
- old app/model version

### Recognition Quality Tracking

Track and report immediately:

- false accepted match
- ambiguous match
- low confidence rejection
- liveness failure
- multiple-face rejection
- students who repeatedly fail

Any false attendance must be treated as a pilot blocker and thresholds should be tightened before continuing expansion.

### Sync Tracking

- Confirm Android attendance marking continues offline if internet drops.
- Confirm backend receives events after internet returns.
- Confirm ERP sync retry works.
- Confirm duplicate events are accepted as duplicates, not failures.

### Guard Feedback

Collect short feedback from the guard:

- Was the message clear?
- Did students know where to stand?
- Did queue movement feel fast enough?
- Which failures were hard to handle?
- Was manual fallback clear?

## Daily Report

Send one report at the end of each pilot day.

### Summary Metrics

- total scans
- attendance marked
- failed recognition
- liveness failed
- manual review count
- duplicate scans
- average decision time
- false attendance count
- ERP sync pending
- device uptime

### Operational Notes

- gate/device used
- app version
- model version
- support dashboard observations
- internet issues
- device heating/battery issues
- guard/admin feedback

### Action Items

- students to re-enroll
- threshold changes to consider
- lighting/stand changes
- backend/ERP issues
- Android app issues
- training gaps

## Rollback Plan

Use rollback if false attendance is detected, device stability is poor, or the school requests fallback.

### Immediate Actions

- Disable AI Gate Mode operationally.
- Switch to manual attendance.
- Keep the Android app installed unless data export is required.
- Preserve local pending events.
- Do not clear local data until support confirms sync/export status.
- Notify school admin and Schoollog pilot owner.

### Data And Logs

- Export support dashboard summaries.
- Export Android debug/stress/calibration logs only if in debug/staging and approved.
- Do not export face images.
- Do not export raw embeddings unless explicitly approved for protected debug handling.
- Record final pending sync count.

### Recovery

- Fix root cause.
- Re-run Day 0 dry run checks.
- Confirm no false attendance in dry run.
- Resume live pilot only after school admin approval.

## Pilot Expansion Gate

Do not expand beyond the first gate until:

- false attendance count is zero
- average attendance marking is below 3 seconds
- genuine recognition is around 95% or better
- duplicate scan rejection is reliable
- offline sync and ERP retry are proven
- guard can handle failed cases confidently
- support dashboard gives enough visibility for live monitoring
