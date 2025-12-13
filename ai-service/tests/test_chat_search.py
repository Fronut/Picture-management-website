def test_chat_search_with_stub(client):
    response = client.post(
        "/ai/v1/search/chat",
        json={"query": "find a stub image", "limit": 2},
    )
    assert response.status_code == 200
    payload = response.get_json()
    assert payload["status"] == "ok"
    data = payload["data"]
    assert data["primaryResult"]["matches"][0]["description"] == "stub image"
    assert data["results"][0]["searchPayload"]["size"] == 2
