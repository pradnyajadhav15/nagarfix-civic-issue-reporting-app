# NagarFix

[![CI](https://github.com/pradnyajadhav15/nagarfix-civic-issue-reporting-app/actions/workflows/ci.yml/badge.svg)](https://github.com/pradnyajadhav15/nagarfix-civic-issue-reporting-app/actions/workflows/ci.yml)

Civic issue reporting for Indian cities: potholes, garbage, broken streetlights.
Pilot city: Solapur, Maharashtra.

**Live demo:** https://nagarfix-civic-issue-reporting-app.vercel.app
(the free API server sleeps when idle, so the first request can take about a minute)

> **Disclaimer:** NagarFix is an independent student project. It is not affiliated with,
> endorsed by, or operated for Solapur Municipal Corporation or any government body.
> Reports submitted here are NOT forwarded to any authority.

## What works today

- Sign up and log in (JWT) with citizen, officer and admin roles; admins create zone officers
- Report an issue with a photo and a GPS pin; the photo goes straight from the browser to Cloudinary (signed upload)
- Automatic routing to one of 26 Solapur zones with PostGIS (point-in-polygon)
- My reports, a page for each report, and a public map with status and category filters
- Map-area search that uses the PostGIS spatial index
- Automatic build and tests on every push (GitHub Actions)

The zones are approximate (generated with PostGIS k-means + Voronoi), not official ward boundaries.

## Coming next

- Status workflow with citizen confirmation, officer dashboard and audit log
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
| Photos | Cloudinary | Cloudinary free plan |

## Repository layout

- `web/` - Next.js frontend
- `api/` - Spring Boot API (database migrations in `api/src/main/resources/db/migration`)
- `ml/` - FastAPI ML/AI service
- `.github/workflows/ci.yml` - automatic build and tests

## Tests

Every push and pull request runs three jobs on GitHub Actions:

| Job | What it checks |
|-----|----------------|
| API | Builds the Spring Boot app and tests it over real HTTP against a fresh PostgreSQL 18 + PostGIS 3.6 database: migrations, sign-up and login, roles, zone routing, map filters, upload signing, CORS |
| Web | ESLint and a production build |
| ML  | pytest tests for the FastAPI service |

The API integration tests create users and reports, so they only run when `INTEGRATION_TESTS=true`
(set in CI, where the database is thrown away after each run). They never touch the live database.

## Run locally (Windows PowerShell)

| App | Command (from repo root) | Open |
|-----|--------------------------|------|
| API | `cd api; .\mvnw.cmd spring-boot:run` | http://localhost:8080/api/hello |
| ML  | `cd ml; .\.venv\Scripts\python -m uvicorn app.main:app --reload` | http://localhost:8000/docs |
| Web | `cd web; npm run dev` | http://localhost:3000 |

Tests: `cd ml; .\.venv\Scripts\python -m pytest` and `cd web; npm run lint`.

## Status

Phase 1 complete: reporting, zones, roles, public map and CI.

## License

MIT - see [LICENSE](LICENSE).
