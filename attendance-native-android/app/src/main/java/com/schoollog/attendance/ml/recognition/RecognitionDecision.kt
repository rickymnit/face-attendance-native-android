package com.schoollog.attendance.ml.recognition

enum class RecognitionDecision {
    MATCH_ACCEPTED,
    LOW_CONFIDENCE,
    AMBIGUOUS_MATCH,
    NO_EMBEDDINGS_LOADED,
    MODEL_VERSION_MISMATCH,
    NO_MATCH,
    MULTIPLE_FACES,
    FACE_QUALITY_FAILED,
    LIVENESS_FAILED,
    UNCERTAIN,
    ERROR,
}
