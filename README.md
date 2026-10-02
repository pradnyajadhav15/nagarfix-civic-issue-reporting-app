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
| Web   | Next.js | Vercel |
| API   | Spring Boot, PostgreSQL + PostGIS | Render, Neon |
| ML/AI | Python, FastAPI | Render, Hugging Face ZeroGPU |

## Repository layout

- `web/` - Next.js frontend
- `api/` - Spring Boot API
- `ml/` - FastAPI ML/AI service
- `analysis/` - data analysis and model-training notebooks
- `docs/` - architecture notes and decisions

## Status

Phase 0 - foundations.

## License

MIT - see [LICENSE](LICENSE).
