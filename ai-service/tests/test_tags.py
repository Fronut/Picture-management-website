from io import BytesIO

from PIL import Image


def _make_sample_image(color=(30, 180, 240), size=(256, 128)):
    image = Image.new("RGB", size, color)
    buffer = BytesIO()
    image.save(buffer, format="PNG")
    buffer.seek(0)
    return buffer


def test_tag_suggestions_from_file(client):
    image_buffer = _make_sample_image()
    data = {"file": (image_buffer, "sample.png")}
    response = client.post("/ai/v1/tags/suggest", data=data, content_type="multipart/form-data")
    assert response.status_code == 200
    payload = response.get_json()
    assert payload["status"] == "ok"
    assert len(payload["data"]["tags"]) >= 1
    assert "metadata" in payload["data"]
