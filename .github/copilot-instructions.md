# Picture-management-website – AI Agent Guide

## Architecture Snapshot

- Monorepo hosts three deployable services: Spring Boot backend (`backend/`), Vite/Vue frontend (`frontend/`), and a Python AI microservice (`ai-service/`).
- `docker-compose.yml` wires MySQL, Redis, Nginx, backend, frontend, and AI service on the same bridge network; Nginx forwards `/` → frontend, `/api/` and `/uploads/` → backend, `/ai/` → Flask gunicorn app (`docker/nginx/conf.d/app.conf`).
- Persistent assets live in mounted volumes: MySQL/Redis state plus `/app/uploads` for user files and `/app/models` for AI weights.

## Daily Workflows

- Bootstrap everything with `npm run setup` (runs `frontend` install + backend compile) or the more thorough `scripts/setup-dev.sh` on Unix (also spins infra via Docker).
- Typical dev loop: `docker-compose up -d mysql redis`, then `cd backend && ./mvnw spring-boot:run` (uses `spring.profiles.active=dev`) and `cd frontend && npm run dev` (served on `3000` with Vite proxying `/api` to `8080`).
- Full stack via `make dev` or `npm run dev`; container-first workflows rely on `docker-compose up` plus baked images (`backend/Dockerfile`, `frontend/Dockerfile`, `ai-service/Dockerfile`).
- Environment is centralized in `.env`; copy from `.env.example` and keep secrets synced with `application.yml` placeholders (`app.jwt.secret`, `AI_SERVICE_URL`, etc.).

## Backend (Spring Boot 3.1 / Java 17)

- Project uses Maven Wrapper; never assume Maven is globally installed—shell examples should call `./mvnw`.
- Profiles (`dev`, `docker`, `prod`) live in `backend/src/main/resources/application.yml`; local code should default to `dev` so it points at `localhost` MySQL/Redis, while containers rely on service names (`mysql`, `redis`).
- Data access is JPA over MySQL; Flyway is the migration tool (`make db-migrate`, plugin configured in `pom.xml`). Migrations must respect naming expected by Flyway.
- Redis is configured for caching/session concerns—any new cache usage should follow `spring-data-redis` patterns already on the classpath.
- File uploads are constrained to 100 MB and whitelisted MIME types via `app.file.*`; new endpoints must honor `uploads` directory semantics (shared volume mounted in Docker).
- JWT auth is implemented with `io.jsonwebtoken` libs; when touching security flows, keep `app.jwt` settings aligned with `.env`.

## Frontend (Vue 3 + Vite)

- Vite config (`frontend/vite.config.ts`) defines alias `@ -> src`; keep imports consistent to leverage TS tooling.
- API calls should target `/api/...` so the dev proxy and Nginx routing remain transparent; configurable base URLs live in `VITE_API_BASE_URL`.
- UI stack is Element Plus + Pinia; prefer these components/stores over bespoke solutions for consistency.
- Quality gates: run `npm run lint`, `npm run type-check`, `npm run test:unit`, and `npm run test:e2e` (Playwright) before shipping major UI changes.

## AI Service (Flask + Gunicorn)

- Mounted at `/ai-service`, exposing `app.main:app` through Gunicorn on port `5000`; compose file maps it behind Nginx `/ai/`.
- Dependencies (torch, transformers, OpenCV) are heavy—when adding models, stage assets under `/app/models` so they survive container restarts via the named volume.
- Configuration relies on `MODEL_PATH` env and `AI_SERVICE_URL` consumed by the backend; keep any new endpoints authenticated/authorized through the backend rather than exposing directly.

## Testing, Linting, and Builds

- Root `Makefile` mirrors common pipelines: `make build`, `make test`, `make lint`, `make clean`, `make deploy-dev`, plus DB tasks (`make db-migrate`, `make db-clean`). Prefer these to ad-hoc commands so CI scripts stay aligned.
- `package.json` scripts orchestrate workspace builds/tests with `concurrently`; use them inside CI pipelines (`npm run build`, `npm run test`, etc.) to exercise both frontend and backend together.
- AI service tests expect pytest; ensure new Python modules remain pytest-discoverable and keep requirements pinned in `ai-service/requirements.txt`.

## Operational Notes

- Docker images fix timezone to `Asia/Shanghai`, so schedule-sensitive code should assume that zone unless a new profile overrides it.
- Uploaded files and generated thumbnails must land under `./uploads` (or `/app/uploads` in containers); never hardcode OS-specific paths.
- `.vscode/settings.json` enforces Prettier on save plus Java auto-build; keep generated code formatter-friendly.
