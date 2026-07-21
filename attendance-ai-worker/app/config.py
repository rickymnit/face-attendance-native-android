from dataclasses import dataclass
import os


@dataclass(frozen=True)
class ModelMetadata:
    model_version: str
    input_width: int
    input_height: int
    embedding_size: int
    normalization_mean: float
    normalization_std: float
    distance_metric: str


def load_metadata() -> ModelMetadata:
    return ModelMetadata(
        model_version=os.getenv("MODEL_VERSION", "multipaz-sample-3f65d0c"),
        input_width=int(os.getenv("MODEL_INPUT_WIDTH", "160")),
        input_height=int(os.getenv("MODEL_INPUT_HEIGHT", "160")),
        embedding_size=int(os.getenv("MODEL_EMBEDDING_SIZE", "512")),
        normalization_mean=float(os.getenv("MODEL_NORMALIZATION_MEAN", "127.5")),
        normalization_std=float(os.getenv("MODEL_NORMALIZATION_STD", "128")),
        distance_metric=os.getenv("MODEL_DISTANCE_METRIC", "COSINE"),
    )


FACE_EMBEDDING_MODEL_PATH = os.getenv("FACE_EMBEDDING_MODEL_PATH")
MOCK_MODEL_ENABLED = os.getenv("MOCK_MODEL_ENABLED", "false").lower() == "true"
