from fastapi import FastAPI

app = FastAPI(title="NagarFix ML Service", version="0.1.0")


@app.get("/")
def root() -> dict:
    return {"app": "NagarFix ML Service", "status": "ok"}


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}
