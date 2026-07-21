# Schoollog Gate App API Contract

## Purpose

This document defines the API contract between the Android Gate Attendance app and the Schoollog backend/sync layer. The Android app is offline-first: it marks attendance locally, stores events in Room, and syncs later. The app should not directly perform complex ERP writes or business reconciliation.

## Why Backend Sync Layer Exists Instead Of Direct ERP Writes

The backend sync layer protects both the Android app and the ERP from unstable gate conditions.

Reasons:

- Gate devices may be offline, restarted, or on weak school Wi-Fi.
- Attendance events must be accepted idempotently without duplicates.
- ERP business rules can change without forcing Android app releases.
- Backend can validate school, device, gate, student, model version, and event timestamps.
- Backend can queue, retry, audit, and reconcile ERP writes safely.
- Backend can revoke devices, rotate credentials, and manage embedding packs centrally.
- Android stays focused on local recognition, local attendance save, and reliable sync.

The Android app sends normalized attendance facts. The backend decides how those facts are written into ERP.

## Common Terms

### `deviceId`

Stable unique identifier assigned to one physical Android gate device during registration. It should survive app restarts and should not change unless the device is re-provisioned.

Example: `GATE-DEVICE-00042`

### `schoolId`

School identifier assigned by Schoollog backend/ERP.

Example: `school-demo`

### `gateId`

Identifier for the physical gate or entry point.

Example: `main-gate`

### `erpStudentId`

Student identifier from Schoollog ERP. Android uses this as the student identity key for attendance and embeddings.

Example: `STU-2026-0001`

### `modelVersion`

Version of the face embedding model used to create and compare embeddings. Backend must not send embeddings for a model version incompatible with the installed Android model.

Example: `facenet512-v1`

### `eventId`

Client-generated UUID for each attendance event. This is the primary idempotency key for attendance sync.

Example: `1f4f53de-0d43-4df6-bc79-bfaef802d49b`

### `syncStatus`

Local Android sync state for outbound data.

Values:

- `PENDING`: saved locally and waiting to sync.
- `SYNCED`: accepted by backend.
- `FAILED`: attempted but failed with retryable or review-needed error.

## Common API Rules

### Base URL

```text
https://api.schoollog.example.com
```

Final production host will be provided per environment.

### Content Type

All requests and responses use JSON.

```http
Content-Type: application/json
Accept: application/json
```

### Authentication

All APIs require device authentication except initial registration if registration uses a one-time provisioning code.

Recommended headers:

```http
Authorization: Bearer <device_access_token>
X-Schoollog-Device-Id: <deviceId>
X-Schoollog-School-Id: <schoolId>
X-Request-Id: <uuid>
```

For idempotent write APIs, also send:

```http
Idempotency-Key: <stable-key>
```

### Standard Error Response

```json
{
  "error": {
    "code": "DEVICE_NOT_AUTHORIZED",
    "message": "Device is not authorized for this school",
    "retryable": false,
    "details": {}
  }
}
```

Common error codes:

- `BAD_REQUEST`
- `UNAUTHORIZED`
- `DEVICE_NOT_AUTHORIZED`
- `SCHOOL_NOT_FOUND`
- `GATE_NOT_FOUND`
- `STUDENT_NOT_FOUND`
- `MODEL_VERSION_UNSUPPORTED`
- `CONFLICT`
- `RATE_LIMITED`
- `SERVER_ERROR`
- `ERP_UNAVAILABLE`

### Offline Retry Behavior

Android should retry `PENDING` local records when internet is available. Retry policy should use exponential backoff and must not delete local records until the backend accepts them.

Recommended handling:

- HTTP `200`, `201`, `202`: mark accepted records `SYNCED`.
- HTTP `400`: mark affected record `FAILED` unless backend marks it retryable.
- HTTP `401`/`403`: stop sync and require device re-auth/provisioning.
- HTTP `404`: mark affected object `FAILED` or require config refresh.
- HTTP `409`: treat as idempotency conflict; resolve using response body.
- HTTP `429`: retry later with backoff.
- HTTP `500`/`503`: keep `PENDING` and retry later.

## 1. Device Registration

```http
POST /api/devices/register
```

### Purpose

Register a physical Android gate device and receive credentials/config binding. This should normally happen during installation or provisioning by an authorized admin.

### Auth Requirement

Requires a one-time setup token or admin-authenticated setup token. Does not require existing device token.

### Request JSON

```json
{
  "schoolCode": "SCHOOL-DEMO",
  "gateId": "main-gate",
  "deviceName": "Main Gate Phone 1",
  "setupToken": "ABCD-1234",
  "deviceInfo": {
    "manufacturer": "Samsung",
    "model": "SM-A145F",
    "androidVersion": "14",
    "appVersion": "0.1.0"
  }
}
```

### Response JSON

```json
{
  "deviceId": "GATE-DEVICE-00042",
  "schoolId": "school-demo",
  "schoolCode": "SCHOOL-DEMO",
  "schoolName": "Schoollog Demo School",
  "gateId": "main-gate",
  "deviceAccessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "configVersion": 12,
  "embeddingSyncVersion": 245,
  "config": null,
  "serverTime": "2026-07-18T09:10:00Z"
}
```

### Error Responses

- `400 BAD_REQUEST`: missing school code, gate ID, setup token, or invalid device info.
- `401 UNAUTHORIZED`: invalid setup token.
- `409 CONFLICT`: setup token already used or device already registered.
- `500 SERVER_ERROR`: retry later.

### Idempotency Rules

Use `Idempotency-Key` with a setup UUID generated by the installer. Repeating the same registration request should return the same `deviceId` and credentials if already created.

### Offline Retry Behavior

Device registration requires internet. If registration fails due network/server error, Android should retry or ask the installer to try again. Attendance mode should not start until the device is registered or explicitly configured for a local test environment.

## 2. Device Heartbeat

```http
POST /api/devices/heartbeat
```

### Purpose

Tell backend the device is alive, app is running, local queue size, model version, and basic health metrics.

### Auth Requirement

Requires device bearer token.

### Request JSON

```json
{
  "deviceId": "GATE-DEVICE-00042",
  "schoolId": "school-demo",
  "gateId": "main-gate",
  "timestamp": "2026-07-18T09:15:00Z",
  "appVersion": "0.1.0",
  "modelVersion": "facenet512-v1",
  "pendingAttendanceCount": 8,
  "pendingFailedRecognitionCount": 2,
  "batteryPercent": 87,
  "isCharging": true,
  "networkType": "WIFI"
}
```

### Response JSON

```json
{
  "accepted": true,
  "serverTime": "2026-07-18T09:15:01Z",
  "configVersion": 13,
  "requiresConfigRefresh": true,
  "requiresEmbeddingDeltaRefresh": false
}
```

### Error Responses

- `401 UNAUTHORIZED`: token expired or invalid.
- `403 DEVICE_NOT_AUTHORIZED`: device disabled or not allowed for school.
- `429 RATE_LIMITED`: heartbeat too frequent.
- `500 SERVER_ERROR`: retry later.

### Idempotency Rules

Heartbeat is naturally repeatable. `X-Request-Id` is enough; backend should not create duplicate business records from heartbeats.

### Offline Retry Behavior

Do not queue every missed heartbeat. Send the next heartbeat when online. Attendance must continue offline.

## 3. Fetch School/Device Config

```http
GET /api/devices/{deviceId}/config
```

### Purpose

Fetch school attendance rules and device configuration.

### Auth Requirement

Requires device bearer token. The token must belong to `{deviceId}`.

### Request Example

```http
GET /api/devices/GATE-DEVICE-00042/config
```

Optional query:

```text
?knownConfigVersion=12
```

### Response JSON

```json
{
  "deviceId": "GATE-DEVICE-00042",
  "schoolId": "school-demo",
  "gateId": "main-gate",
  "configVersion": 13,
  "attendanceRules": {
    "schoolStartTime": "08:00",
    "lateAfterTime": "08:15",
    "halfDayAfterTime": "10:30",
    "duplicateScanCooldownMinutes": 10,
    "requireOutTime": false,
    "recognitionMode": "Strict"
  },
  "syncRules": {
    "attendanceBatchSize": 100,
    "failedRecognitionBatchSize": 100,
    "heartbeatIntervalMinutes": 15
  },
  "model": {
    "modelVersion": "facenet512-v1",
    "embeddingSize": 512,
    "distanceMetric": "COSINE"
  }
}
```

### Error Responses

- `401 UNAUTHORIZED`
- `403 DEVICE_NOT_AUTHORIZED`
- `404 DEVICE_NOT_FOUND`
- `500 SERVER_ERROR`

### Idempotency Rules

GET is safe and repeatable. Backend should return the current config or `304 Not Modified` if HTTP caching is later added.

### Offline Retry Behavior

Use last saved config while offline. Retry config refresh when online.

## 4. Fetch Embedding Delta Pack

```http
GET /api/schools/{schoolId}/embeddings/delta?sinceVersion=
```

### Purpose

Fetch changed student embeddings for local recognition. Android should apply deltas locally and match without server calls. The response supports added, updated, and deleted embeddings. Added and updated records are applied as active local embeddings; deleted records are marked `DELETED` locally.

### Auth Requirement

Requires device bearer token authorized for `{schoolId}`.

### Request Example

```http
GET /api/schools/school-demo/embeddings/delta?sinceVersion=245&modelVersion=facenet512-v1
```

### Response JSON

```json
{
  "schoolId": "school-demo",
  "modelVersion": "facenet512-v1",
  "fromVersion": 245,
  "toVersion": 246,
  "hasMore": false,
  "embeddings": [
    {
      "erpStudentId": "STU-2026-0001",
      "changeType": "ADDED",
      "student": {
        "name": "Asha Kumar",
        "className": "7",
        "section": "A",
        "rollNumber": "12",
        "status": "ACTIVE"
      },
      "embeddingBase64": "AAAA...",
      "embeddingSize": 512,
      "qualityScore": 0.92,
      "status": "ACTIVE",
      "updatedAt": "2026-07-18T08:00:00Z"
    }
  ],
  "deletedEmbeddings": [
    {
      "erpStudentId": "STU-2026-0099",
      "modelVersion": "facenet512-v1",
      "deletedAt": "2026-07-18T08:02:00Z"
    }
  ]
}
```

### Error Responses

- `400 BAD_REQUEST`: unsupported or missing `modelVersion`.
- `401 UNAUTHORIZED`
- `403 DEVICE_NOT_AUTHORIZED`
- `404 SCHOOL_NOT_FOUND`
- `409 MODEL_VERSION_UNSUPPORTED`
- `500 SERVER_ERROR`

### Idempotency Rules

Embedding deltas are versioned. Applying the same delta more than once must be safe. Android should upsert active embeddings by `schoolId + erpStudentId + modelVersion`, replace updated active embeddings, and mark deleted records locally when instructed. Android advances its local `embeddingSyncVersion` only after the full delta applies successfully.

If `modelVersion` does not match the locally installed face embedding model, Android must reject the pack, keep the previous local embeddings, show `Model/embedding version mismatch`, and retry only after model/config remediation.

### Offline Retry Behavior

Use existing local embeddings while offline. Retry delta fetch later. If no embeddings exist locally, recognition should show `No enrolled students found` and must not call backend for live matching.

## 5. Sync Attendance Events

```http
POST /api/attendance/events/sync
```

### Purpose

Upload locally saved attendance events to backend. Backend validates and forwards/reconciles with ERP.

### Auth Requirement

Requires device bearer token.

### Request JSON

```json
{
  "deviceId": "GATE-DEVICE-00042",
  "schoolId": "school-demo",
  "gateId": "main-gate",
  "events": [
    {
      "eventId": "1f4f53de-0d43-4df6-bc79-bfaef802d49b",
      "erpStudentId": "STU-2026-0001",
      "eventType": "PRESENT",
      "attendanceDate": "2026-07-18",
      "timestampLocal": "2026-07-18T08:03:20+05:30",
      "timestampUtc": "2026-07-18T02:33:20Z",
      "matchScore": 0.91,
      "livenessScore": 0.82,
      "qualityScore": 0.88,
      "modelVersion": "facenet512-v1",
      "recognitionMode": "Strict"
    }
  ]
}
```

### Response JSON

```json
{
  "accepted": [
    {
      "eventId": "1f4f53de-0d43-4df6-bc79-bfaef802d49b",
      "status": "ACCEPTED",
      "erpReferenceId": "ERP-ATT-884221"
    }
  ],
  "rejected": [],
  "serverTime": "2026-07-18T02:34:00Z"
}
```

Partial rejection example:

```json
{
  "accepted": [],
  "rejected": [
    {
      "eventId": "1f4f53de-0d43-4df6-bc79-bfaef802d49b",
      "status": "REJECTED",
      "code": "STUDENT_NOT_FOUND",
      "message": "Student does not exist or is inactive",
      "retryable": false
    }
  ],
  "serverTime": "2026-07-18T02:34:00Z"
}
```

### Error Responses

- `400 BAD_REQUEST`: malformed batch.
- `401 UNAUTHORIZED`
- `403 DEVICE_NOT_AUTHORIZED`
- `409 CONFLICT`: same `eventId` with different payload.
- `413 PAYLOAD_TOO_LARGE`: reduce batch size.
- `429 RATE_LIMITED`: retry later.
- `500 SERVER_ERROR` or `503 ERP_UNAVAILABLE`: retry later.

### Idempotency Rules

`eventId` is the idempotency key. Re-sending the same `eventId` with the same payload must return accepted/already accepted status and must not create duplicate ERP attendance.

If the same `eventId` is sent with different payload, backend should return `409 CONFLICT` for that event.

### Offline Retry Behavior

Android stores events locally as `PENDING`. On accepted response, mark events `SYNCED`. On retryable batch/server errors, keep events `PENDING`. On non-retryable per-event rejection, mark `FAILED` and surface for support review.

## 6. Sync Failed Recognition Logs

```http
POST /api/attendance/failed/sync
```

### Purpose

Upload failed recognition/liveness/manual-review logs for audit, support, and model improvement. These logs must not include face images or raw embeddings.

### Auth Requirement

Requires device bearer token.

### Request JSON

```json
{
  "deviceId": "GATE-DEVICE-00042",
  "schoolId": "school-demo",
  "gateId": "main-gate",
  "logs": [
    {
      "failedRecognitionId": "7f5b57fd-2b23-4f71-9bb7-4dcd42d8f992",
      "reason": "LOW_CONFIDENCE",
      "timestamp": "2026-07-18T08:05:00+05:30",
      "qualityScore": 0.72,
      "livenessScore": 0.81,
      "operatorAction": "MANUAL_REVIEW"
    }
  ]
}
```

### Response JSON

```json
{
  "accepted": [
    {
      "failedRecognitionId": "7f5b57fd-2b23-4f71-9bb7-4dcd42d8f992",
      "status": "ACCEPTED"
    }
  ],
  "rejected": []
}
```

### Error Responses

- `400 BAD_REQUEST`
- `401 UNAUTHORIZED`
- `403 DEVICE_NOT_AUTHORIZED`
- `413 PAYLOAD_TOO_LARGE`
- `429 RATE_LIMITED`
- `500 SERVER_ERROR`

### Idempotency Rules

Use `failedRecognitionId` as the idempotency key. Duplicate uploads with the same payload should be accepted without creating duplicate audit records.

### Offline Retry Behavior

Keep failed recognition logs locally until accepted. If logs are too old or rejected by policy, backend should return a non-retryable per-log rejection.

## 7. Submit Enrollment Request

```http
POST /api/enrollment/requests
```

### Purpose

Submit an enrollment request for backend/admin approval. Android may create embeddings locally, but backend should control whether they become official for school-wide distribution.

### Auth Requirement

Requires device bearer token and enrollment permission for the device/user.

### Request JSON

```json
{
  "requestId": "a0d1b4af-b71d-4ebf-bff8-5f568b5d1132",
  "deviceId": "GATE-DEVICE-00042",
  "schoolId": "school-demo",
  "gateId": "main-gate",
  "erpStudentId": "STU-2026-0001",
  "student": {
    "name": "Asha Kumar",
    "className": "7",
    "section": "A",
    "rollNumber": "12"
  },
  "embedding": {
    "modelVersion": "facenet512-v1",
    "embeddingSize": 512,
    "embeddingBase64": "AAAA...",
    "qualityScore": 0.91,
    "source": "APP_ENROLLMENT"
  },
  "submittedAt": "2026-07-18T08:10:00+05:30"
}
```

### Response JSON

```json
{
  "requestId": "a0d1b4af-b71d-4ebf-bff8-5f568b5d1132",
  "status": "PENDING_APPROVAL",
  "receivedAt": "2026-07-18T02:40:00Z"
}
```

Possible statuses:

- `PENDING_APPROVAL`
- `APPROVED`
- `REJECTED`
- `NEEDS_RE_ENROLLMENT`

### Error Responses

- `400 BAD_REQUEST`: invalid student or embedding payload.
- `401 UNAUTHORIZED`
- `403 DEVICE_NOT_AUTHORIZED`
- `404 STUDENT_NOT_FOUND`
- `409 CONFLICT`: active enrollment already exists or request payload changed for same request ID.
- `413 PAYLOAD_TOO_LARGE`
- `500 SERVER_ERROR`

### Idempotency Rules

Use `requestId` as the idempotency key. Re-sending the same request should return the same enrollment request status. Same `requestId` with different payload should return `409 CONFLICT`.

### Offline Retry Behavior

If enrollment approval is required, Android should store the request locally and submit when online. Until approved, local status should remain `PENDING_APPROVAL` unless the school explicitly allows local-only enrollment for testing.

## 8. Get Enrollment Approval/Status

```http
GET /api/enrollment/requests/{id}
```

### Purpose

Check whether an enrollment request has been approved, rejected, or requires re-enrollment.

### Auth Requirement

Requires device bearer token authorized for the request school.

### Request Example

```http
GET /api/enrollment/requests/a0d1b4af-b71d-4ebf-bff8-5f568b5d1132
```

### Response JSON

```json
{
  "requestId": "a0d1b4af-b71d-4ebf-bff8-5f568b5d1132",
  "schoolId": "school-demo",
  "erpStudentId": "STU-2026-0001",
  "status": "APPROVED",
  "reviewedAt": "2026-07-18T03:00:00Z",
  "reviewedBy": "admin-102",
  "message": "Enrollment approved",
  "embeddingDeltaVersion": 247
}
```

Rejected example:

```json
{
  "requestId": "a0d1b4af-b71d-4ebf-bff8-5f568b5d1132",
  "schoolId": "school-demo",
  "erpStudentId": "STU-2026-0001",
  "status": "NEEDS_RE_ENROLLMENT",
  "reviewedAt": "2026-07-18T03:00:00Z",
  "reviewedBy": "admin-102",
  "message": "Face quality was too low. Please enroll again in better lighting.",
  "embeddingDeltaVersion": null
}
```

### Error Responses

- `401 UNAUTHORIZED`
- `403 DEVICE_NOT_AUTHORIZED`
- `404 REQUEST_NOT_FOUND`
- `500 SERVER_ERROR`

### Idempotency Rules

GET is safe and repeatable. Backend should return the current status for the request.

### Offline Retry Behavior

Use last known local request status while offline. Retry status check when online. If approved, Android should fetch embedding deltas and update the local active embedding set.

## Backend Validation Expectations

Backend should validate:

- Device is registered and active.
- Device belongs to the submitted `schoolId` and `gateId`.
- Student exists and is active for the school.
- Attendance event timestamp is within accepted policy bounds.
- `eventId` or `requestId` idempotency is respected.
- `modelVersion` is supported.
- Scores are in expected numeric ranges.
- Payload does not include face images unless a future explicit secure enrollment API allows it.

## Android Local State Mapping

Attendance event local mapping:

```text
PENDING -> POST /api/attendance/events/sync -> SYNCED
PENDING -> retryable error -> PENDING
PENDING -> non-retryable per-event rejection -> FAILED
FAILED -> support/admin action -> retry or archive
```

Embedding local mapping:

```text
ACTIVE: usable for local matching
PENDING_APPROVAL: stored but not used for production matching unless policy allows
DELETED: ignored by matcher
```

Enrollment request mapping:

```text
LOCAL_PENDING_UPLOAD -> PENDING_APPROVAL -> APPROVED -> embedding delta refresh
LOCAL_PENDING_UPLOAD -> PENDING_APPROVAL -> REJECTED / NEEDS_RE_ENROLLMENT
```

## Security And Privacy Notes

- Do not send face images in attendance sync.
- Do not send raw debug calibration logs with images; calibration CSV should include scores and IDs only.
- Embedding packs and enrollment embeddings are biometric data and must be encrypted in transit.
- Device tokens must be revocable.
- Backend should audit all device registration, sync, and enrollment operations.
- Production APIs must use HTTPS only.
