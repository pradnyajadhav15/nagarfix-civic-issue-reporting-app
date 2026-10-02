import os

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

app = FastAPI(title="NagarFix ML Service", version="0.1.0")

# Websites allowed to call this service from a browser
allowed_origins = [
    origin.strip()
    for origin in os.getenv("CORS_ORIGINS", "http://localhost:3000").split(",")
    if origin.strip()
]
app.add_middleware(
    CORSMiddleware,
    allow_origins=allowed_origins,
    allow_origin_regex=os.getenv("CORS_ORIGIN_REGEX", r"https://nagarfix.*\.vercel\.app"),
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/")
def root() -> dict:
    return {"app": "NagarFix ML Service", "status": "ok"}


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}
