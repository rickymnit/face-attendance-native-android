# Schoollog Attendance Sync Backend

NestJS backend for Android gate devices. It receives offline-first attendance events, stores them idempotently, and prepares clean records for Schoollog ERP sync.

## Tech Stack

- Node.js + NestJS
- TypeScript
- PostgreSQL
- Prisma ORM
- JWT device auth
- REST APIs

## Implemented Foundation

- Device registration, heartbeat, and config APIs
- JWT guard for device-authenticated APIs
- Attendance event sync with idempotency on `schoolId + eventId`
- Failed recognition sync
- Enrollment request API
- Embedding delta API with school-scoped monotonic sync versions, checksums, pagination, and device auth
- Local staging seed for E2E testing with 20 fake students
- Student CSV + ZIP import with row-level validation/errors, embedding job creation, and optional AI worker processing
- ERP adapter boundary with mock/http adapters and retryable `ErpSyncStatus` queue
- Audit log service
- Prisma production-oriented schema and initial migration
- Seed school/device/student/rules for local testing

No raw face images are stored by this backend.

## Start Locally

```bash
cp .env.example .env
docker compose up -d postgres
npm install
npx prisma generate
npx prisma migrate deploy
npm run seed
# Optional local staging data for Android/backend E2E checks
npm run seed:staging
npm run start:dev
```

Server defaults to `http://localhost:3000` and all app APIs are under `/api`.

## Build And Test

```bash
npm run build
npm test
```

## Seed Data

The seed creates:

- School: `school-demo`, code `SCHOOL-DEMO`
- Device: `GATE-DEVICE-00042`, gate `main-gate`
- Student: `STU-2026-0001`

For local device registration, use setup token `DEV-SETUP-TOKEN`.

For local staging E2E tests, run `npm run seed:staging`; it creates school code `LOCAL-STAGING`, device `GATE-STAGING-0001`, gate `main-gate`, and 20 fake students. See `docs/LOCAL_E2E_TEST.md`.

## Student Import

The import foundation exposes:

- `POST /api/schools/:schoolId/import/students`
- `GET /api/schools/:schoolId/imports/:importId/status`
- `GET /api/schools/:schoolId/imports/:importId/errors`

Upload multipart fields `csv` and `zip`. The CSV columns are `erpStudentId`, `name`, `className`, `section`, `rollNumber`, and `photoFileName`. Raw face photos are not stored permanently. Valid rows create/update students and create embedding jobs. When `AI_WORKER_BASE_URL` is configured, the backend sends each photo to the AI worker in memory, stores the returned embedding payload, increments the school embedding sync version, and marks the import item `EMBEDDING_DONE` or `EMBEDDING_FAILED`. Without a configured worker, jobs remain `PENDING` for later processing.

## AI Worker Integration

Bulk import embedding generation is handled by the sibling `attendance-ai-worker` service. Configure these values in `.env` when running imports that should generate embeddings immediately:

```bash
AI_WORKER_BASE_URL="http://localhost:8000"
AI_WORKER_EXPECTED_MODEL_VERSION="multipaz-sample-3f65d0c"
```

The backend sends uploaded photos to the worker only during processing and does not permanently store raw face images. Worker errors such as `NO_FACE`, `MULTIPLE_FACES`, `LOW_QUALITY`, worker unavailability, or model version mismatch are recorded on the embedding job/import item.

## Embedding Delta Sync

`GET /api/schools/:schoolId/embeddings/delta?sinceVersion=<version>` returns changed embedding records for the authenticated device school only. Each embedding add/update/delete increments the school monotonic embedding sync version. Responses include compact embedding payloads, deleted records, per-record checksums, pagination via `hasMore`, and `newSyncVersion` for Android to persist locally.

## Security

Device JWTs are scoped to one school/device, registration requires `DEVICE_SETUP_TOKEN`, validation rejects unknown fields, and a lightweight rate limit protects device APIs. See `docs/SECURITY.md`.

## API Notes

Registration is unauthenticated but requires a setup token. All other endpoints expect:

```http
Authorization: Bearer <deviceAccessToken>
X-Schoollog-Device-Id: <deviceId>
X-Schoollog-School-Id: <schoolId>
```

ERP writes are mocked behind `ErpSyncService` for this foundation milestone.
