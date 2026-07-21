# Local End-to-End Test

This guide uses fake local staging data only. Do not use real student data or real face photos.

## 1. Start PostgreSQL

```bash
cd attendance-sync-backend
docker compose up -d postgres
```

The local database runs on host port `5433`.

## 2. Run Migrations

```bash
npm install
npx prisma generate
npx prisma migrate deploy
```

## 3. Seed Staging Data

```bash
npm run seed:staging
```

The seed creates:

- School code: `LOCAL-STAGING`
- School ID: `school-staging-local`
- Gate ID: `main-gate`
- Device ID: `GATE-STAGING-0001`
- 20 fake students: `STAGE-STU-0001` through `STAGE-STU-0020`

The command prints a device JWT token for direct API smoke tests.

## 4. Start Backend

```bash
npm run start
```

Backend base URL:

```text
http://localhost:3000/api
```

## 5. Configure Android Staging URL

For emulator:

```bash
cd attendance-native-android
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew assembleStaging   -PSCHOOLLOG_STAGING_API_BASE_URL=http://10.0.2.2:3000/
```

For a physical phone, replace `10.0.2.2` with your computer LAN IP.

## 6. Register Device

In the Android first-run setup screen enter:

```text
School code: LOCAL-STAGING
Gate ID: main-gate
Device name: Local Staging Gate Phone
Setup token: value of DEVICE_SETUP_TOKEN from backend .env
```

## 7. Sync One Attendance Event

Use the app Gate Mode after enrollment/model setup, or send a direct API request with the device token printed by `npm run seed:staging`.

`POST /api/attendance/events/sync` accepts a batch and returns per-event `accepted`, `duplicate`, or `rejected` status.

## 8. Test Duplicate Event

Send the same `eventId` twice. Expected result:

- first request: `accepted`
- second request: `duplicate/already_synced`

The batch must not fail only because one event is duplicate.

## 9. Test Offline Retry

1. Stop the backend.
2. Mark attendance on Android.
3. Confirm pending sync count increases.
4. Restart backend.
5. Tap `Sync Now` or wait for WorkManager.
6. Confirm pending sync count decreases and backend receives the event.

Attendance must remain saved locally while the backend is down.
