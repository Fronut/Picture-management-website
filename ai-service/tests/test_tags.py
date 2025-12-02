from io import BytesIO

import pytest


def _multipart_payload(sample):
    return {"file": (BytesIO(sample.data), sample.name)}


def _expected_orientation(width: int, height: int) -> str:
    aspect = width / max(height, 1)
    if aspect > 1.2:
        return "orientation:landscape"
    if aspect < 0.85:
        return "orientation:portrait"
    return "orientation:square"


def test_tag_suggestions_from_real_file(client, load_sample_image):
    sample = load_sample_image("sea.jpeg")
    data = _multipart_payload(sample)
    response = client.post("/ai/v1/tags/suggest", data=data, content_type="multipart/form-data")
    assert response.status_code == 200
    payload = response.get_json()
    assert payload["status"] == "ok"
    metadata = payload["data"]["metadata"]
    assert metadata["width"] == sample.width
    assert metadata["height"] == sample.height
    assert len(payload["data"]["tags"]) >= 1


@pytest.mark.parametrize("filename", ["beach.jpeg", "man2.png", "cloud.jpg"])
def test_tag_suggestions_respect_orientation(client, load_sample_image, filename):
    sample = load_sample_image(filename)
    expected_orientation = _expected_orientation(sample.width, sample.height)
    response = client.post(
        "/ai/v1/tags/suggest",
        data=_multipart_payload(sample),
        content_type="multipart/form-data",
    )
    assert response.status_code == 200
    payload = response.get_json()
    tags = {item["name"] for item in payload["data"]["tags"]}
    assert expected_orientation in tags


def test_tag_suggestions_respect_limit_with_real_images(client, load_sample_image):
    sample = load_sample_image("tree.jpeg")
    data = _multipart_payload(sample)
    data["limit"] = "2"
    response = client.post("/ai/v1/tags/suggest", data=data, content_type="multipart/form-data")
    assert response.status_code == 200
    payload = response.get_json()
    assert len(payload["data"]["tags"]) == 2
