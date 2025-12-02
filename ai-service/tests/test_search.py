def test_search_interpretation(client):
    response = client.post(
        "/ai/v1/search/interpret",
        json={"query": "sunset beach 4k portrait"},
    )
    assert response.status_code == 200
    payload = response.get_json()
    assert payload["status"] == "ok"
    data = payload["data"]
    assert "sunset" in data["tags"]
    assert data["filters"]["minWidth"] >= 1920
