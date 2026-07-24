# ML Pipeline

## Goal

The ML pipeline recognizes gate candidates using only live camera frames. The current implementation uses ML Kit for face detection, liveness v0 over live frame sequences, TensorFlow Lite embedding generation, and local cosine matching against active Room embeddings. It does not use `ImageCapture`, does not call `takePicture()`, and does not save face photos during attendance or enrollment.

## Face Detection

Interface: `FaceDetectorEngine`

Responsibilities:

- Detect face bounding boxes from live frame input.
- Return all detected faces and select the primary candidate for the current flow.
- Return face quality metadata for the selected primary face.

Current implementation:

- Uses ML Kit Face Detection on the live `ImageProxy` from CameraX `ImageAnalysis`.
- Converts `media.Image` to `InputImage` with the frame rotation degrees.
- Returns all detected face bounding boxes, head Euler angles, eye-open probabilities, smiling probability, and tracking ID when available.
- In Gate Mode, sorts faces by bounding-box area and selects the largest/closest face as `selectedPrimaryFace`; smaller background faces are retained for debug display but do not block attendance by themselves.
- In Enrollment, also sorts detected faces by bounding-box area and uses the largest/closest face as `selectedPrimaryFace`; smaller background faces are retained for debug display and do not block enrollment by themselves.
- Rejects the selected face when there are zero usable faces, the selected face is too small, off-center, excessively tilted, heavy side-facing, or partially out of frame.
- Uses `FaceQualityEvaluator` to return `qualityPassed`, `qualityScore`, and a `FaceQualityFailureReason` for the selected primary face.
- Keeps lighting and blur as placeholder scores until real frame-quality models are added.

Future improvements:

- Add blur, lighting, occlusion, and face crop quality checks.
- Tune thresholds using real gate-device test data.

## Stable Face Tracking

Before liveness or recognition starts, `StableFaceTracker` requires the same selected, quality-passed face to remain steady for roughly 700 ms. Gate Mode and Enrollment both track the selected largest face and reset when that primary face disappears, changes suddenly, moves too much, or head angles change too much. Smaller background faces are ignored unless one becomes the new largest face, in which case stability restarts.

Tracker states:

- `WAITING_FOR_FACE`
- `FACE_DETECTED`
- `HOLD_STILL`
- `FACE_STABLE`
- `READY_FOR_LIVENESS`

This tracker does not mark attendance by itself. It only allows the pipeline to continue to the liveness stage.

## Liveness Detection

Interface: `LivenessEngine`

Responsibilities:

- Accept a short sequence of live frame metadata.
- Return a liveness score.
- Return `PASS`, `FAIL`, or `UNCERTAIN`.

Current v0 implementation:

- `LivenessEngineV0` evaluates a short sequence of live `ImageAnalysis` frame samples after stable-face tracking is ready.
- Gate Mode requires the selected primary face to remain continuously present as a quality-passed face.
- Looks for small natural variation in face center, head angle, bounding box size, and eye openness when available.
- Returns `UNCERTAIN` while collecting the 1-2 second sequence.
- Returns `FAIL` when the selected face disappears, selected-face quality fails, or the selected-face sequence is too static. Multiple background faces do not fail liveness by themselves because liveness evaluates the selected primary face sequence.

Important limitation:

- This is a first heuristic layer to prove live-frame pipeline behavior. It must not be treated as final protection against all photo, video, or replay attacks.

Future implementation:

- Replace or augment v0 with a TensorFlow Lite anti-spoof model.
- Analyze motion, blink/head pose cues, depth cues if available, or a dedicated liveness model.
- Fail suspicious or static replay attempts with thresholds tuned from real gate data.

## Face Crop And Alignment

`FaceCropper` prepares a normalized in-memory face crop from the live CameraX `ImageAnalysis` frame after face quality and stable tracking pass. Gate Mode and Enrollment both crop only `selectedPrimaryFace`, which is the largest detected face for that frame.

Current implementation:

- `CameraXFaceCropper` converts the open `ImageProxy` YUV frame to an in-memory bitmap.
- Applies frame rotation and optional front-camera mirroring metadata.
- Expands the ML Kit bounding box with a margin.
- Rejects out-of-bounds or too-small crops.
- Normalizes the crop to a small square bitmap for future tensor/model input.
- Uses `BoundingBoxFaceAlignment` as a placeholder; future landmark-based alignment can replace it behind `FaceAlignment`.

This crop is not saved to storage. Gate Mode and live enrollment use it in memory for TensorFlow Lite embedding generation, and Gate Mode shows the thumbnail only when debug metrics are enabled.

Failure reasons:

- `CROP_OUT_OF_BOUNDS`
- `FACE_TOO_SMALL`
- `ROTATION_ERROR`
- `FRAME_CONVERSION_ERROR`
- `SUCCESS`

## Embedding Generation

Interface: `FaceEmbeddingEngine`

Responsibilities:

- Select the best live frame after face quality and liveness checks pass.
- Generate an embedding vector for the detected face.

Current implementation:

- Runs the bundled TensorFlow Lite FaceNet 512 model on the in-memory live face crop.
- L2-normalizes embeddings and validates output before matching.
- Keeps a debug placeholder engine available only behind the explicit debug mock recognition setting.

## Face Embedding Model Loader

`TfliteFaceEmbeddingEngine` provides the real model-loading infrastructure for face embeddings. A compatible FaceNet 512 TensorFlow Lite asset is bundled so the live-frame embedding pipeline can run end to end during development.

Expected model path:

```text
app/src/main/assets/models/face_embedding.tflite
```

Bundled model details:

- Source family: FaceNet 512 TensorFlow Lite model
- Expected input: `160 x 160`
- Expected output embedding size: `512`
- Distance metric: cosine after L2 normalization

This asset is not a final Schoollog production recognition model. Replace it with a reviewed production model and calibrated thresholds before deployment.

Current metadata defaults:

- `modelName`: `facenet_512`
- `modelVersion`: `multipaz-sample-3f65d0c`
- `inputWidth`: `160`
- `inputHeight`: `160`
- `embeddingSize`: `512`
- `normalizationMean`: `127.5`
- `normalizationStd`: `128.0`
- `distanceMetric`: `COSINE`

Processing steps:

1. Validate the in-memory face crop.
2. Load the `.tflite` asset lazily.
3. Resize the crop to model input size.
4. Convert RGB pixels into a direct `ByteBuffer`.
5. Normalize pixels using metadata mean/std.
6. Run TensorFlow Lite inference.
7. Validate output size and finite values.
8. L2-normalize the embedding.

Failure reasons:

- `MODEL_NOT_FOUND`
- `MODEL_LOAD_FAILED`
- `INVALID_INPUT`
- `INFERENCE_FAILED`
- `INVALID_OUTPUT`
- `SUCCESS`

If the model file is missing, the app does not crash. Gate Mode shows `Face model not installed`, and production attendance is not marked. Debug builds can enable the explicit `Allow debug mock recognition` setting for development-only fallback testing.

## Local Matching

Interface: `FaceMatcher`

Responsibilities:

- Compare generated embedding with local enrolled embeddings.
- Return top matches, scores, and strict recognition decision.

Current implementation:

- `LocalFaceMatcher` loads active Room embeddings once per school/model version into an in-memory cache.
- Uses cosine similarity and keeps the top 3 matches without sorting the full school list.
- Applies Strict, Balanced, or Lenient thresholds; Strict is the default.
- Rejects low-confidence, ambiguous, empty-cache, and model-version mismatch cases.
- Refreshes the cache when embedding repository writes mark the local cache version changed.

Debug builds can still use a placeholder matcher only when the explicit debug mock setting is enabled. Release builds do not expose the setting and ignore stored debug mock values.

## Threshold Calibration Debug Mode

Recognition threshold calibration is available only in debug builds through the Recognition QA screen. It helps developers and Schoollog support compare expected identities with real gate recognition attempts before deployment.

Debug calibration records each recognition attempt that reaches local matching. The local log stores:

- Timestamp
- Expected student ID entered by the tester, when provided
- Predicted student ID
- Top 1, top 2, and top 3 student IDs and scores
- Top1-top2 margin
- Liveness and face-quality scores
- Final recognition decision and failure reason
- Recognition mode
- Embedding inference time and matching time

The CSV export intentionally excludes face images and raw embeddings. It is for score/margin analysis only. Calibration controls and logging are guarded with `BuildConfig.DEBUG`; release builds do not expose the screen and do not write calibration attempts.

Threshold modes are centralized in `RecognitionThresholdConfig`:

- `Strict`: score threshold `0.86`, top1-top2 margin `0.04`. This is the production default.
- `Balanced`: score threshold `0.80`, top1-top2 margin `0.03`.
- `Lenient`: score threshold `0.74`, top1-top2 margin `0.02` for controlled testing.

The ambiguity rule remains mandatory: if top 1 and top 2 are too close for the selected mode, recognition is rejected as `AMBIGUOUS_MATCH`. Production attendance must stay on `Strict` unless real-world validation proves a safer threshold set.

## Strict Decision Rules

Attendance must not be marked unless all gates pass:

1. A selected primary face is available from the live frame. When multiple faces are present, the largest/closest face is selected.
2. Face quality passes.
3. The same face remains stable for the required hold-still duration.
4. Liveness passes.
5. Face crop succeeds in memory from the live `ImageAnalysis` frame.
6. The TensorFlow Lite embedding model is installed and inference succeeds.
7. Local Room-backed matcher returns `MATCH_ACCEPTED`.
8. Student is not inside the duplicate scan cooldown window.
9. Attendance event is saved locally first.

Gate Mode failure handling:

- Missing model shows `Face model not installed`.
- Empty local embedding cache shows `No enrolled students found`.
- Low confidence shows `Low confidence, please contact guard`.
- Close top matches show `Multiple possible matches, manual review required`.
- Match failures, liveness failures, missing student records, and local-save failures create a local `FailedRecognitionEntity` where appropriate.

On success, Gate Mode saves an `AttendanceEventEntity` locally before showing the student name, class, section, roll number, and attendance status such as `PRESENT`, `LATE`, or `HALF_DAY`. Transient face-position/quality guidance does not mark attendance.

## Why ImageCapture Is Not Used

Normal attendance must be based on live frames, not captured photos.

Reasons:

- Live frames support fast 2-3 second gate flow.
- Liveness needs a short sequence of frames, not a single still photo.
- Avoiding photo capture reduces storage, privacy, and operational risk.
- `ImageAnalysis` lets the app drop stale frames with `STRATEGY_KEEP_ONLY_LATEST`.
- The app should never block attendance on image file writes.

Current CameraX usage:

- `Preview`: user sees live front-camera feed.
- `ImageAnalysis`: app receives live frames for analysis.
- ML Kit receives `InputImage` from `ImageProxy.image` plus frame rotation.
- `ImageProxy` instances are closed after ML Kit task completion or immediately for dropped/error frames.
- No `ImageCapture` use case is bound.
- No photos are saved for attendance.
