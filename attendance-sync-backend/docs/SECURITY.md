# Security Hardening

This document covers the current security posture for the Schoollog AI Gate Attendance Android app and `attendance-sync-backend`.

## Android App

- Device auth token is stored through `SecureDeviceBindingRepository` using Android Keystore-backed AES/GCM encryption.
- Release builds disable debug QA screens through `BuildConfig.DEBUG` checks.
- Release builds disable mock recognition; the production gate flow cannot use placeholder recognition unless it is a debug build and the explicit debug setting is enabled.
- Release and staging builds disable mock sync with `MOCK_SYNC_ENABLED=false`.
- Sensitive screens are protected with `FLAG_SECURE` to reduce accidental screenshots/screen recording.
- Attendance and enrollment use live CameraX `ImageAnalysis` frames. The app must not use `ImageCapture` or save face photos for normal attendance.
- Face embeddings are stored locally as serialized vectors, not raw face images.
- Device unbind is available only behind debug/admin UI and should be protected by an admin flow before production rollout.

## Backend

- Device registration requires a setup token (`DEVICE_SETUP_TOKEN`) or future admin-token equivalent.
- Device JWTs are scoped to exactly one `schoolId` and `deviceId`.
- Auth guards verify that the JWT device exists, is active, and belongs to the requested school.
- Attendance sync rejects events for another device or school.
- Embedding delta sync rejects cross-school requests.
- Global validation uses whitelist mode and rejects unknown request fields.
- A lightweight rate-limit middleware protects device APIs from repeated abuse.
- Audit logs are written for device registration, heartbeat, attendance sync, failed recognition sync, enrollment requests, embedding delta fetches, student imports, and ERP sync changes.
- Raw embeddings and face images should not be written to normal logs. Bulk-import photos are processed temporarily and discarded.
- Environment validation fails startup for unsafe production configuration.

## ERP Sync Security

- Android sends internal AI attendance events only to the backend.
- The backend pushes clean `AttendanceDailySummary` records to ERP through `ErpAttendanceAdapter`.
- ERP sync is idempotent and tracked with `ErpSyncStatus`.
- ERP failures do not block Android attendance. Summaries remain pending/failed for retry.

## Required Production Configuration

- `DATABASE_URL`
- `JWT_SECRET` set to a strong secret
- `DEVICE_SETUP_TOKEN` set to a strong setup/admin token
- `ERP_ADAPTER=mock` for local/dev or `ERP_ADAPTER=http` for ERP integration
- `ERP_BASE_URL` and `ERP_API_KEY` when using the HTTP adapter
- `RATE_LIMIT_WINDOW_MS` and `RATE_LIMIT_MAX_REQUESTS` tuned for pilot traffic

## Production Blockers / Follow-ups

- Replace setup-token registration with an admin-authenticated installer flow.
- Add encrypted-at-rest storage for backend embedding payloads if required by policy.
- Add operational log redaction checks before connecting production ERP.
- Add role-based admin APIs for unbind, manual retry, and imports.
- Define data retention and deletion policy for embeddings, failed recognition logs, and audit logs.
