from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)

WEBSITE = "https://nagarfix-civic-issue-reporting-app.vercel.app"


def test_root_says_hello():
    response = client.get("/")
    assert response.status_code == 200
    assert response.json() == {"app": "NagarFix ML Service", "status": "ok"}


def test_health_is_ok():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_the_website_may_call_the_service():
    response = client.get("/health", headers={"Origin": WEBSITE})
    assert response.headers.get("access-control-allow-origin") == WEBSITE


def test_other_sites_may_not():
    response = client.get("/health", headers={"Origin": "https://other-site.example.com"})
    assert "access-control-allow-origin" not in response.headers
