# Schoollog Attendance AI Worker

FastAPI service for generating face embeddings from bulk-imported student photos. It is designed to use the same face embedding model version as the Android app.

This worker does not store raw images. Uploaded images are decoded in memory, checked for exactly one face, cropped, embedded, and discarded.

## Configuration

Required for real inference:

```bash
FACE_EMBEDDING_MODEL_PATH=/models/face_embedding.tflite
MODEL_VERSION=multipaz-sample-3f65d0c
MODEL_INPUT_WIDTH=160
MODEL_INPUT_HEIGHT=160
MODEL_EMBEDDING_SIZE=512
MODEL_NORMALIZATION_MEAN=127.5
MODEL_NORMALIZATION_STD=128
MODEL_DISTANCE_METRIC=COSINE
```

Development-only mock mode:

```bash
MOCK_MODEL_ENABLED=true
```

For real inference, install the runtime matching the configured model format. `.tflite` models use `tflite-runtime` or TensorFlow Lite; `.onnx` models use `onnxruntime`. The worker fails clearly when the model file exists but the required runtime is not installed.

Mock mode creates deterministic fake embeddings from image bytes and must not be used for production enrollment/attendance.

## APIs

### `GET /health`

Returns model/runtime status and metadata.

### `POST /embedding/generate`

Multipart fields:

- `image`: image file
- `studentId`: ERP/student ID
- `schoolId`: school ID

Success returns:

- `embedding`: float array
- `embeddingBase64`: little-endian float32 byte payload encoded as base64
- `qualityScore`
- `modelVersion`
- `embeddingSize`
- `distanceMetric`
- `status`

Failure returns `status=FAILED` and an error code such as `MODEL_NOT_FOUND`, `NO_FACE`, `MULTIPLE_FACES`, `LOW_QUALITY`, `INVALID_IMAGE`, `INVALID_OUTPUT`, `FACE_DETECTOR_NOT_AVAILABLE`, `FACE_TOO_SMALL`, `MODEL_SHAPE_MISMATCH`, `MODEL_DTYPE_MISMATCH`, `INFERENCE_FAILED`, or `RUNTIME_NOT_INSTALLED`.

## Run Locally

```bash
python -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
MOCK_MODEL_ENABLED=true uvicorn app.main:app --reload
```

## Docker

```bash
docker build -t attendance-ai-worker .
docker run --rm -p 8000:8000   -e FACE_EMBEDDING_MODEL_PATH=/models/face_embedding.tflite   -v /path/to/models:/models:ro   attendance-ai-worker
```

## Tests

```bash
MOCK_MODEL_ENABLED=true pytest
```
