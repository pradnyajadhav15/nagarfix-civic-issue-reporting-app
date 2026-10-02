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
- **Status workflow:** Submitted -> Assigned -> In progress -> Resolved (with an "after" photo) -> Closed.
  The citizen confirms the fix or reopens it with a reason; officers can reject (with a reason) or mark duplicates.
  Resolved reports close automatically after 7 days without an answer.
- **Rules enforced by the API:** only officers of the report's zone (or admins) move it along, only the reporter confirms
  or reopens, and simultaneous clicks can't overwrite each other (compare-and-set update)
- **Audit trail:** every change is recorded and shown as a timeline (roles only, never names)
- **Departments and deadlines:** each category has a department and a deadline in days (editable by admins);
  late reports are marked overdue
- **Officer dashboard** ("My zone"), admin console (zone coverage, departments, background jobs), notification bell
  and optional email notifications
- **Demo mode:** about 330 clearly marked demo reports, rebuilt every day, plus one-click demo logins
- Public map with status, category and demo filters, and map-area search using the PostGIS spatial index
- Automatic build and tests on every push (GitHub Actions)

The zones are approximate (generated with PostGIS k-means + Voronoi), not official ward boundaries.

## Try the demo

Open the live site, go to **Log in** and pick **Citizen**, **Ward officer** or **Admin** under "Try a demo account".
Demo accounts only see and change demo reports; the demo admin is read-only.

## Coming next

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
- `.github/workflows/ci.yml` - automatic build and tests; `wake.yml` - daily wake-up for background jobs

## Tests

Every push and pull request runs three jobs on GitHub Actions:

| Job | What it checks |
|-----|----------------|
| API | Builds the Spring Boot app and tests it over real HTTP against a fresh PostgreSQL 18 + PostGIS 3.6 database: migrations, sign-up and login, roles, zone routing, map filters, upload signing, CORS, every allowed and blocked status change, simultaneous updates, deadlines, notifications, auto-close and demo guardrails |
| Web | ESLint and a production build |
| ML  | pytest tests for the FastAPI service |

The API integration tests create users and reports, so they only run when `INTEGRATION_TESTS=true`
(set in CI, where the database is thrown away after each run). They never touch the live database.
The status rules also have plain unit tests (`WorkflowTest`) that run everywhere.

A second workflow (`wake.yml`) wakes the free API server once a day so its background jobs run.

## Email notifications (optional)

In-app notifications always work. To also send emails, create a free [Brevo](https://www.brevo.com) account,
verify a sender address, and add `BREVO_API_KEY` and `MAIL_FROM` to the API's environment on Render.

## Run locally (Windows PowerShell)

| App | Command (from repo root) | Open |
|-----|--------------------------|------|
| API | `cd api; .\mvnw.cmd spring-boot:run` | http://localhost:8080/api/hello |
| ML  | `cd ml; .\.venv\Scripts\python -m uvicorn app.main:app --reload` | http://localhost:8000/docs |
| Web | `cd web; npm run dev` | http://localhost:3000 |

Tests: `cd ml; .\.venv\Scripts\python -m pytest` and `cd web; npm run lint`.

## Status

Phase 2 complete: status workflow, officer and admin screens, deadlines, notifications and demo mode.

## License

MIT - see [LICENSE](LICENSE).
