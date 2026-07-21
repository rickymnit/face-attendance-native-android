# Roadmap

## Phase 1: Live Camera + Mocked Pipeline

Status: implemented, with real ML Kit face detection, TFLite embedding generation, local matching, and live-frame enrollment added.

- Native Android app foundation.
- Compose navigation: Home, Gate Mode, Enrollment, Settings.
- CameraX front-camera preview.
- ImageAnalysis live frame analyzer with throttling.
- ML Kit face detection with liveness v0, TFLite embedding generation, and local matching pipeline.
- Gate attendance state machine.
- Room offline-first attendance events.
- Mock sync with WorkManager.
- Enrollment form with live-frame embedding capture.
- DataStore attendance rules.

## Phase 2: Face Detection Hardening

- Tune ML Kit thresholds for school gate lighting and device placement.
- Add quality checks for blur, lighting, occlusion, and face crop usability.
- Keep clear UI states for no face, multiple faces, and unstable face.
- Benchmark performance on target low-end Android devices.

## Phase 3: Real Liveness Model

- Replace `PlaceholderLivenessEngine` with a real liveness model or robust heuristic/model combination.
- Use short live frame sequences from `ImageAnalysis`.
- Tune PASS/FAIL/UNCERTAIN thresholds.
- Add anti-spoof testing with printed photos, phone displays, and videos.
- Keep the pipeline fast enough for 2-3 second gate attendance.

## Phase 4: Face Embedding Model

- Replace the bundled development FaceNet asset with a reviewed Schoollog-approved production model if required.
- Tune enrollment sample guidance and thresholds on real gate devices.
- Store/import production embeddings locally with model version and quality score.
- Support model upgrades and embedding re-enrollment strategy.

## Phase 5: ERP Integration

- Replace `MockSyncApi` with authenticated Schoollog ERP API client.
- Add device provisioning and secure credentials.
- Sync student roster and enrolled embeddings from ERP when needed.
- Push attendance events with retry/backoff and server acknowledgements.
- Add conflict handling and observability for sync failures.

## Phase 6: Production Deployment

- Add kiosk hardening and device startup behavior.
- Add monitoring, local logs, diagnostics, and support export.
- Add battery/thermal/performance testing on target phones.
- Add privacy/security review for biometric data handling.
- Add release signing, CI builds, QA test plans, and school rollout checklist.
