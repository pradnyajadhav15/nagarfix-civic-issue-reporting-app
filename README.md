# NagarFix

Civic issue reporting for Indian cities: potholes, garbage, broken streetlights.
Pilot city: Solapur, Maharashtra.

> **Disclaimer:** NagarFix is an independent student project. It is not affiliated with,
> endorsed by, or operated for Solapur Municipal Corporation or any government body.
> Reports submitted here are NOT forwarded to any authority.

## Planned features

- Photo + GPS reporting with automatic ward routing
- Citizen, officer and admin roles; status workflow with citizen confirmation
- AI photo tagging, severity scoring and duplicate detection
- Marathi voice complaints
- Ward dashboards and BBMP grievance data analysis
- Issue-type image classifier and fix-time prediction

## Stack

| Layer | Tech | Free hosting |
|-------|------|--------------|
| Web   | Next.js 16 | Vercel |
| API   | Spring Boot 4 (Java 25), PostgreSQL + PostGIS | Render, Neon |
| ML/AI | Python, FastAPI | Render, Hugging Face ZeroGPU |

## Repository layout

- `web/` - Next.js frontend
- `api/` - Spring Boot API
- `ml/` - FastAPI ML/AI service
- `analysis/` - data analysis and model-training notebooks
- `docs/` - architecture notes and decisions

## Run locally (Windows PowerShell)

| App | Command (from repo root) | Open |
|-----|--------------------------|------|
| API | `cd api; .\mvnw.cmd spring-boot:run` | http://localhost:8080/api/hello |
| ML  | `cd ml; .\.venv\Scripts\python -m uvicorn app.main:app --reload` | http://localhost:8000/docs |
| Web | `cd web; npm run dev` | http://localhost:3000 |

## Status

Phase 0 - foundations: apps scaffolded.

## License

MIT - see [LICENSE](LICENSE).
