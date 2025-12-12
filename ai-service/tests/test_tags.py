from io import BytesIO


def _multipart_payload(sample):
    return {"file": (BytesIO(sample.data), sample.name)}


def test_tag_suggestions_from_real_file(client, load_sample_image):
    sample = load_sample_image("sea.jpeg")
    data = _multipart_payload(sample)
    response = client.post("/ai/v1/tags/suggest", data=data, content_type="multipart/form-data")
    assert response.status_code == 200
    payload = response.get_json()
    assert payload["status"] == "ok"
    assert len(payload["data"]["tags"]) >= 1
    dummy = client.application.extensions["tagging_service"].baidu_classifier
    assert getattr(dummy, "called", False)


def test_tag_suggestions_respect_limit_with_real_images(client, load_sample_image):
    sample = load_sample_image("tree.jpeg")
    data = _multipart_payload(sample)
    data["limit"] = "2"
    response = client.post("/ai/v1/tags/suggest", data=data, content_type="multipart/form-data")
    assert response.status_code == 200
    payload = response.get_json()
    assert len(payload["data"]["tags"]) == 2
    dummy = client.application.extensions["tagging_service"].baidu_classifier
    assert getattr(dummy, "called", False)
