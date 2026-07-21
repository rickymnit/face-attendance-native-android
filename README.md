# Face Attendance Native Android Workspace

This master workspace groups the four Schoollog AI Gate Attendance projects without changing their internal project names, package names, app IDs, or module structure.

## Workspace Structure

```text
face-attendance-native-android/
├── attendance-native-android/      # Native Android gate attendance app
├── attendance-sync-backend/        # NestJS backend/API/ERP sync service
├── attendance-ai-worker/           # FastAPI embedding generation worker
└── attendance-support-dashboard/   # Next.js support dashboard
```

## Projects

### attendance-native-android

Native Android app used at the school gate. It handles CameraX live preview + ImageAnalysis, face detection, liveness, local matching, Room offline storage, WorkManager sync, enrollment, settings, pilot readiness, and debug QA/stress tools.

### attendance-sync-backend

NestJS backend for device registration, attendance event sync, failed recognition sync, student import, embedding delta packs, ERP sync queue/status, audit logs, load testing, and support monitoring APIs.

### attendance-ai-worker

FastAPI worker for generating face embeddings from imported student photos. It expects the configured face embedding model path from environment and does not store raw images.

### attendance-support-dashboard

Next.js support dashboard for pilot monitoring. It calls backend support APIs for school health, device status, today summary, failed recognitions, ERP sync health, and device logs.

## Recommended Startup Order

1. Start PostgreSQL for the backend.

```bash
cd attendance-sync-backend
docker compose up -d postgres
```

2. Configure and migrate the backend.

```bash
cp .env.example .env
npm install
npx prisma migrate deploy
npm run seed:staging
```

3. Start the AI worker when import embedding generation is needed.

```bash
cd ../attendance-ai-worker
python -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
MOCK_MODEL_ENABLED=true uvicorn app.main:app --reload --port 8000
```

For real embedding generation, set `FACE_EMBEDDING_MODEL_PATH` and matching model metadata instead of mock mode.

4. Start the backend API.

```bash
cd ../attendance-sync-backend
npm run start:dev
```

5. Start the support dashboard.

```bash
cd ../attendance-support-dashboard
npm install
npm run dev -- -p 3001
```

Use backend URL `http://localhost:3000` and role `SCHOOLLOG_SUPPORT` in the placeholder login.

6. Build or run the Android app.

```bash
cd ../attendance-native-android
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew build
```

Open the project in Android Studio from `attendance-native-android/`.

## Build/Test Commands

```bash
# Android
cd attendance-native-android
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew build

# Backend
cd ../attendance-sync-backend
npm install
npm run build
npm test

# AI worker
cd ../attendance-ai-worker
. .venv/bin/activate  # if already created
.venv/bin/python -m pytest

# Support dashboard
cd ../attendance-support-dashboard
npm install
npm run build
```

## Git Note

At the time this workspace was created, only `attendance-native-android/` had its own `.git` directory. The other project folders did not. No `.git` directories were deleted or moved outside their project folder.

## Runbook

See `RUNNING_WORKSPACE.md` for the current full-workspace startup commands, ports, verification checks, and stop commands.
