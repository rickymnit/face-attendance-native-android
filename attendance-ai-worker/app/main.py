from fastapi import FastAPI, File, Form, UploadFile
from fastapi.responses import JSONResponse

from .embedding_service import EmbeddingError, EmbeddingService

app = FastAPI(title="Schoollog Attendance AI Worker", version="0.1.0")
service = EmbeddingService()


@app.get("/health")
def health():
    return service.health()


@app.post("/embedding/generate")
async def generate_embedding(
    image: UploadFile = File(...),
    studentId: str = Form(...),
    schoolId: str = Form(...),
):
    image_bytes = await image.read()
    try:
        return service.generate(image_bytes=image_bytes, student_id=studentId, school_id=schoolId)
    except EmbeddingError as error:
        return JSONResponse(
            status_code=422,
            content={
                "status": "FAILED",
                "studentId": studentId,
                "schoolId": schoolId,
                "errorCode": error.code,
                "message": error.message,
            },
        )
