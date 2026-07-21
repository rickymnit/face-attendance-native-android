# Changelog

## 0.1.0 - Local Recognition Pilot MVP

### MVP Scope

- Native Android Kotlin app with Jetpack Compose UI.
- First-run device registration and school/gate binding.
- Production-style Gate Mode kiosk UI with full-screen live preview, guarded exit, screen wake behavior, network/battery warnings, pending sync count, device/gate identity, and current time.
- CameraX live camera pipeline using `Preview` and `ImageAnalysis`.
- No `ImageCapture`, no `takePicture()`, and no saved face photos for attendance or enrollment.
- ML Kit face detection on live frames.
- Face quality checks, stable face tracking, in-memory face crop, and liveness v0.
- TensorFlow Lite face embedding engine using `app/src/main/assets/models/face_embedding.tflite`.
- Local Room-backed face embedding storage and cosine matching with Strict/Balanced/Lenient thresholds.
- Live-frame enrollment with 3 good samples, averaged/L2-normalized embeddings, duplicate student prevention, and re-enrollment support.
- Offline-first attendance save using Room before sync.
- Retrofit sync API client with debug-only mock sync.
- Attendance event sync, failed recognition log sync, device heartbeat, and embedding delta sync.
- Periodic WorkManager jobs for attendance sync and embedding sync.
- DataStore-backed attendance rules/settings.
- Debug-only Recognition QA screen with model smoke test, calibration logs, top-3 match details, cache reload, and safe debug cleanup actions.
- Documentation for architecture, ML pipeline, API contract, testing protocol, deployment, roadmap, and MVP release checklist.

### Known Limitations

- Liveness v0 is heuristic and is not final production anti-spoofing.
- The bundled FaceNet-compatible model is suitable for pilot validation, but must be formally approved/calibrated before broad rollout.
- Recognition thresholds still require school-device calibration with real pilot data.
- Device registration uses setup-token flow, but full admin login/device management UX is not implemented.
- Release unbind/reprovision flow needs a formal admin/support authorization path.
- Backend/ERP APIs are contract-ready on Android, but must be validated against the real Schoollog backend.
- Enrollment approval flow is modeled in the API contract but not fully enforced end to end in production UX.
- Local embeddings are protected by app-private storage; additional at-rest encryption policy for biometric templates should be finalized for production.
- Liveness/recognition failure review is local-first; richer backend review tooling remains future work.
- Lock task/device-owner kiosk mode is documented but not forced by the app.

### Release Branch

- Branch: `mvp-local-recognition-pilot`
