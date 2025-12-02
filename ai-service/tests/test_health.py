def test_health_endpoint(client):
    response = client.get("/ai/v1/health")
    assert response.status_code == 200
    payload = response.get_json()
    assert payload["status"] == "ok"
    assert payload["data"]["status"] == "healthy"
