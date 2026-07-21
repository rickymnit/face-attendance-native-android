import base64
import hashlib
import math
import os
from dataclasses import asdict
from io import BytesIO
from typing import Any

from PIL import Image, ImageStat

from .config import FACE_EMBEDDING_MODEL_PATH, MOCK_MODEL_ENABLED, ModelMetadata, load_metadata


class EmbeddingError(Exception):
    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code
        self.message = message


class EmbeddingService:
    def __init__(self, metadata: ModelMetadata | None = None):
        self.metadata = metadata or load_metadata()
        self.model_path = FACE_EMBEDDING_MODEL_PATH
        self.mock_enabled = MOCK_MODEL_ENABLED
        self.runtime = self._detect_runtime()
        self.model_loaded = self.mock_enabled or bool(self.model_path and os.path.exists(self.model_path))

    def health(self) -> dict[str, Any]:
        runtime_ready = self.mock_enabled or self.runtime in {"TFLITE", "ONNX"}
        return {
            "status": "ok" if self.model_loaded and runtime_ready else ("model_not_found" if not self.model_loaded else "runtime_not_installed"),
            "modelLoaded": self.model_loaded,
            "mockModelEnabled": self.mock_enabled,
            "runtime": self.runtime,
            "modelPath": self.model_path,
            "metadata": asdict(self.metadata),
        }

    def generate(self, image_bytes: bytes, student_id: str, school_id: str) -> dict[str, Any]:
        if not self.model_loaded:
            raise EmbeddingError("MODEL_NOT_FOUND", "FACE_EMBEDDING_MODEL_PATH is missing or does not exist")
        image = self._load_image(image_bytes)
        quality_score = self._quality_score(image)
        if quality_score < 0.35:
            raise EmbeddingError("LOW_QUALITY", "Image quality is too low for embedding generation")
        face_box = self._detect_single_face_box(image)
        crop = self._crop_face(image, face_box)
        embedding = self._mock_embedding(crop) if self.mock_enabled else self._real_embedding(crop)
        normalized = self._l2_normalize(embedding)
        payload = self._float_array_to_base64(normalized)
        return {
            "status": "SUCCESS",
            "studentId": student_id,
            "schoolId": school_id,
            "embedding": normalized,
            "embeddingBase64": payload,
            "qualityScore": quality_score,
            "modelVersion": self.metadata.model_version,
            "embeddingSize": self.metadata.embedding_size,
            "distanceMetric": self.metadata.distance_metric,
        }

    def _detect_runtime(self) -> str:
        if self.mock_enabled:
            return "MOCK"
        if not self.model_path:
            return "UNKNOWN"
        if self.model_path.endswith(".tflite"):
            try:
                self._load_tflite_interpreter_class()
                return "TFLITE"
            except EmbeddingError:
                return "TFLITE_RUNTIME_NOT_INSTALLED"
        if self.model_path.endswith(".onnx"):
            try:
                import onnxruntime  # noqa: F401
                return "ONNX"
            except Exception:
                return "ONNX_RUNTIME_NOT_INSTALLED"
        return "UNKNOWN_MODEL_FORMAT"

    def _load_image(self, image_bytes: bytes) -> Image.Image:
        try:
            return Image.open(BytesIO(image_bytes)).convert("RGB")
        except Exception as exc:
            raise EmbeddingError("INVALID_IMAGE", "Image could not be decoded") from exc

    def _quality_score(self, image: Image.Image) -> float:
        grayscale = image.convert("L")
        stat = ImageStat.Stat(grayscale)
        mean = stat.mean[0] / 255.0
        contrast = min(stat.stddev[0] / 64.0, 1.0)
        lighting = 1.0 - min(abs(mean - 0.5) * 1.8, 1.0)
        size_score = min((image.width * image.height) / float(self.metadata.input_width * self.metadata.input_height), 1.0)
        return max(0.0, min((lighting * 0.45) + (contrast * 0.35) + (size_score * 0.20), 1.0))

    def _detect_single_face_box(self, image: Image.Image) -> tuple[int, int, int, int]:
        if self.mock_enabled:
            return self._mock_single_face_box(image)
        try:
            import cv2
            import numpy as np
        except Exception as exc:
            raise EmbeddingError("FACE_DETECTOR_NOT_AVAILABLE", "Install OpenCV for worker-side face detection") from exc
        cascade_path = cv2.data.haarcascades + "haarcascade_frontalface_default.xml"
        detector = cv2.CascadeClassifier(cascade_path)
        if detector.empty():
            raise EmbeddingError("FACE_DETECTOR_NOT_AVAILABLE", "OpenCV face detector cascade could not be loaded")
        rgb = np.asarray(image)
        gray = cv2.cvtColor(rgb, cv2.COLOR_RGB2GRAY)
        faces = detector.detectMultiScale(gray, scaleFactor=1.1, minNeighbors=5, minSize=(48, 48))
        if len(faces) == 0:
            raise EmbeddingError("NO_FACE", "No face detected")
        if len(faces) > 1:
            raise EmbeddingError("MULTIPLE_FACES", "Multiple faces detected")
        x, y, width, height = [int(value) for value in faces[0]]
        return x, y, width, height

    def _mock_single_face_box(self, image: Image.Image) -> tuple[int, int, int, int]:
        # Development-only heuristic for unit tests and local wiring without a model/runtime.
        if image.width < 48 or image.height < 48:
            raise EmbeddingError("NO_FACE", "No face detected")
        if image.width / max(image.height, 1) > 3.2:
            raise EmbeddingError("MULTIPLE_FACES", "Multiple faces detected")
        side = min(image.width, image.height)
        return (image.width - side) // 2, (image.height - side) // 2, side, side

    def _crop_face(self, image: Image.Image, face_box: tuple[int, int, int, int]) -> Image.Image:
        x, y, width, height = face_box
        if width < 48 or height < 48:
            raise EmbeddingError("FACE_TOO_SMALL", "Detected face is too small")
        margin_x = int(width * 0.25)
        margin_y = int(height * 0.30)
        left = max(0, x - margin_x)
        top = max(0, y - margin_y)
        right = min(image.width, x + width + margin_x)
        bottom = min(image.height, y + height + margin_y)
        if right <= left or bottom <= top:
            raise EmbeddingError("CROP_FAILED", "Face crop bounds are invalid")
        return image.crop((left, top, right, bottom)).resize((self.metadata.input_width, self.metadata.input_height))

    def _mock_embedding(self, image: Image.Image) -> list[float]:
        digest = hashlib.sha256(image.tobytes()).digest()
        values: list[float] = []
        while len(values) < self.metadata.embedding_size:
            for byte in digest:
                values.append((byte / 127.5) - 1.0)
                if len(values) == self.metadata.embedding_size:
                    break
            digest = hashlib.sha256(digest).digest()
        return values

    def _real_embedding(self, image: Image.Image) -> list[float]:
        if self.runtime == "TFLITE":
            return self._run_tflite(image)
        if self.runtime == "ONNX":
            return self._run_onnx(image)
        raise EmbeddingError("RUNTIME_NOT_INSTALLED", f"Runtime is not installed or unsupported for model format: {self.runtime}")

    def _preprocess_float_input(self, image: Image.Image):
        try:
            import numpy as np
        except Exception as exc:
            raise EmbeddingError("RUNTIME_NOT_INSTALLED", "numpy is required for model preprocessing") from exc
        array = np.asarray(image, dtype=np.float32)
        array = (array - float(self.metadata.normalization_mean)) / float(self.metadata.normalization_std)
        return array.reshape((1, self.metadata.input_height, self.metadata.input_width, 3))

    def _load_tflite_interpreter_class(self):
        try:
            from tflite_runtime.interpreter import Interpreter
            return Interpreter
        except Exception:
            try:
                from tensorflow.lite.python.interpreter import Interpreter
                return Interpreter
            except Exception as exc:
                raise EmbeddingError("RUNTIME_NOT_INSTALLED", "Install tflite-runtime or TensorFlow for .tflite embedding generation") from exc

    def _run_tflite(self, image: Image.Image) -> list[float]:
        try:
            import numpy as np
            Interpreter = self._load_tflite_interpreter_class()
            interpreter = Interpreter(model_path=self.model_path)
            interpreter.allocate_tensors()
            input_details = interpreter.get_input_details()[0]
            output_details = interpreter.get_output_details()[0]
            expected_shape = [1, self.metadata.input_height, self.metadata.input_width, 3]
            actual_shape = list(input_details["shape"])
            if actual_shape != expected_shape:
                raise EmbeddingError("MODEL_SHAPE_MISMATCH", f"Expected input shape {expected_shape}, got {actual_shape}")
            input_data = self._preprocess_float_input(image)
            if input_details["dtype"] != np.float32:
                raise EmbeddingError("MODEL_DTYPE_MISMATCH", f"Expected float32 input, got {input_details[dtype]}")
            interpreter.set_tensor(input_details["index"], input_data.astype(np.float32))
            interpreter.invoke()
            output = interpreter.get_tensor(output_details["index"]).reshape(-1).astype(np.float32)
            values = output.tolist()
            if len(values) != self.metadata.embedding_size:
                raise EmbeddingError("INVALID_OUTPUT", f"Expected embedding size {self.metadata.embedding_size}, got {len(values)}")
            return values
        except EmbeddingError:
            raise
        except Exception as exc:
            raise EmbeddingError("INFERENCE_FAILED", "TFLite inference failed") from exc

    def _run_onnx(self, image: Image.Image) -> list[float]:
        try:
            import numpy as np
            import onnxruntime as ort
            session = ort.InferenceSession(self.model_path, providers=["CPUExecutionProvider"])
            model_input = session.get_inputs()[0]
            expected_shape = [1, self.metadata.input_height, self.metadata.input_width, 3]
            actual_shape = [int(dim) if isinstance(dim, int) else dim for dim in model_input.shape]
            if len(actual_shape) != 4 or any(actual not in (expected, None, "None") and not isinstance(actual, str) for actual, expected in zip(actual_shape, expected_shape)):
                raise EmbeddingError("MODEL_SHAPE_MISMATCH", f"Expected input shape compatible with {expected_shape}, got {model_input.shape}")
            input_data = self._preprocess_float_input(image).astype(np.float32)
            output = session.run(None, {model_input.name: input_data})[0].reshape(-1).astype(np.float32)
            values = output.tolist()
            if len(values) != self.metadata.embedding_size:
                raise EmbeddingError("INVALID_OUTPUT", f"Expected embedding size {self.metadata.embedding_size}, got {len(values)}")
            return values
        except EmbeddingError:
            raise
        except Exception as exc:
            raise EmbeddingError("INFERENCE_FAILED", "ONNX inference failed") from exc

    def _l2_normalize(self, values: list[float]) -> list[float]:
        if len(values) != self.metadata.embedding_size:
            raise EmbeddingError("INVALID_OUTPUT", "Embedding output size does not match metadata")
        if not all(math.isfinite(value) for value in values):
            raise EmbeddingError("INVALID_OUTPUT", "Embedding contains NaN or Infinity")
        norm = math.sqrt(sum(value * value for value in values))
        if not math.isfinite(norm) or norm <= 0:
            raise EmbeddingError("INVALID_OUTPUT", "Embedding norm is invalid")
        return [value / norm for value in values]

    def _float_array_to_base64(self, values: list[float]) -> str:
        import struct

        return base64.b64encode(b"".join(struct.pack("<f", value) for value in values)).decode("ascii")
