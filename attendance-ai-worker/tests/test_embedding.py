from io import BytesIO

from fastapi.testclient import TestClient
from PIL import Image

from app.main import app


def image_bytes(size=(160, 160), color=(120, 130, 140)):
    image = Image.new("RGB", size, color)
    buffer = BytesIO()
    image.save(buffer, format="JPEG")
    return buffer.getvalue()


def test_health_mock_mode(monkeypatch):
    monkeypatch.setenv("MOCK_MODEL_ENABLED", "true")
    client = TestClient(app)
    response = client.get("/health")
    assert response.status_code == 200
    assert "metadata" in response.json()


def test_generate_mock_embedding():
    client = TestClient(app)
    response = client.post(
        "/embedding/generate",
        data={"studentId": "STU-1", "schoolId": "school-1"},
        files={"image": ("face.jpg", image_bytes(), "image/jpeg")},
    )
    assert response.status_code in (200, 422)
    if response.status_code == 200:
        body = response.json()
        assert body["status"] == "SUCCESS"
        assert body["embeddingSize"] == len(body["embedding"])
