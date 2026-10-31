# Tēzaurs suggestion review

Spring Boot and React application for reviewing user suggestions submitted to
Tēzaurs.

## Data model

- `incubator.suggestions` is the immutable source of submitted suggestions.
- An untouched suggestion is shown as `NEW` and is addressed by its integer
  incubator ID.
- The first workflow action creates `review.suggestions` and links the source
  through `review.suggestion_sources`.
- A review has a UUID and may contain several equal source suggestions when
  duplicates are combined.
- All later status changes and draft data belong to the review UUID.
- `dict` and `incubator` are read-only; Flyway changes only the `review` schema.

The application currently supports the review queue, duplicate linking,
Tēzaurs entry checks, and entry, lexeme, and sense drafts. It does
not write drafts back to the real Tēzaurs tables.

Authentication is performed by the deployment reverse proxy. The backend reads
the authenticated username from the proxy-validated Basic Authorization header
through `GET /api/me`, so the backend must not be published without that proxy.

## Run locally

Requirements: Java 21+, Node.js, Docker, and Docker Compose.

Place the `dict` and `incubator.suggestions` dumps next to the repository as
`tezaurs_2026_03-public.pgsql.gz` and `suggestions-2026-09-04.pgsql.gz`. They are
imported only when the PostgreSQL volume is first created.

```bash
cp backend/.env.example backend/.env
# Set DATABASE_PASSWORD in backend/.env.
docker compose --env-file backend/.env up -d postgres

cd backend
set -a
source .env
set +a
./mvnw spring-boot:run
```

In another terminal:

```bash
cd frontend
npm install
npm run dev
```

Swagger is available at `http://localhost:8080/swagger-ui.html`.

Because the initial migration is still intentionally kept as one clean file,
drop the disposable review schema before running a changed migration:

```bash
docker compose --env-file backend/.env exec -T postgres \
  psql -U tezaurs -d tezaurs_suggestions -c 'DROP SCHEMA review CASCADE;'
```

This does not change `dict` or `incubator`.

## Verify

```bash
cd backend && ./mvnw clean verify
cd ../frontend && npm run lint && npm run build
```

Deployment setup is documented in [deploy/README.md](deploy/README.md).
