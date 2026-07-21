# Running The Full Workspace

This document records how to run the projects inside the master workspace.

## Current Workspace

```text
/home/lb/face-attendance-native-android/
├── attendance-native-android/
├── attendance-sync-backend/
├── attendance-ai-worker/
└── attendance-support-dashboard/
```

## Services Started In This Run

| Project | Status | URL | Notes |
|---|---:|---|---|
| attendance-ai-worker | Running | http://localhost:8010/health | Running in mock model mode because port 8000 was already occupied. |
| attendance-sync-backend | Running | http://localhost:3000 | Started with `AI_WORKER_BASE_URL=http://localhost:8010`. |
| attendance-support-dashboard | Running | http://localhost:3001 | Next.js support dashboard. |
| attendance-native-android | Built | app/build/outputs/apk/debug/ | Android app needs Android Studio/emulator/device to run interactively. |

## Why AI Worker Uses Port 8010

Port `8000` was already used by another Docker service on this machine, so this workspace AI worker was started on `8010`.

Backend was started with:

```bash
AI_WORKER_BASE_URL=http://localhost:8010
```

## Start Commands

### AI Worker

```bash
cd /home/lb/face-attendance-native-android/attendance-ai-worker
MOCK_MODEL_ENABLED=true .venv/bin/python -m uvicorn app.main:app --host 0.0.0.0 --port 8010
```

### Backend

```bash
cd /home/lb/face-attendance-native-android/attendance-sync-backend
PORT=3000 AI_WORKER_BASE_URL=http://localhost:8010 ERP_SYNC_WORKER_ENABLED=false npm run start
```

### Support Dashboard

```bash
cd /home/lb/face-attendance-native-android/attendance-support-dashboard
npm run start -- -p 3001
```

### Android Build

```bash
cd /home/lb/face-attendance-native-android/attendance-native-android
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew build
```

Debug APK output:

```text
attendance-native-android/app/build/outputs/apk/debug/app-debug.apk
```

## Verification Commands

```bash
curl http://localhost:8010/health
curl -H 'X-Schoollog-Support-Role: SCHOOLLOG_SUPPORT' http://localhost:3000/api/support/schools/health
curl -I http://localhost:3001
```

## Android Run Options

Open the Android project in Android Studio from:

```text
/home/lb/face-attendance-native-android/attendance-native-android
```

Or install the debug APK on a connected device:

```bash
adb install -r /home/lb/face-attendance-native-android/attendance-native-android/app/build/outputs/apk/debug/app-debug.apk
```

## Build/Test Notes

- Android `./gradlew build` passes.
- Backend `npm run build` passes.
- AI worker tests pass with `.venv/bin/python -m pytest`.
- Support dashboard `npm run build` passes.
- This machine does not provide a global `python` command; use the AI worker virtualenv command above.

## Stop Commands

If these services are running as foreground/managed terminal sessions, stop each with `Ctrl+C` in its session.

If running manually in background, find and stop by port:

```bash
lsof -ti :8010 | xargs -r kill
lsof -ti :3000 | xargs -r kill
lsof -ti :3001 | xargs -r kill
```

## Support Dashboard Login

Open:

```text
http://localhost:3001
```

Use:

```text
Backend API URL: http://localhost:3000
Role: SCHOOLLOG_SUPPORT
```
