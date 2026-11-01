# Tēzaurs suggestion review

Spring Boot and React application for reviewing user suggestions submitted to
Tēzaurs.

## Data model

- `incubator.suggestions` is the immutable source of submitted suggestions.
- An untouched suggestion is shown as `NEW` and is addressed by its integer
  incubator ID.
- First confirm whether the suggestion is trash. For a non-trash suggestion,
  choose an existing case or create a new case in `Apstrādē`.
- The first decision creates a preliminary case in `Skatāms` or `Atkritums`,
  so it survives reloads. Choosing a new case starts that preliminary case;
  choosing an existing case moves all its sources to the destination case.
- Merging keeps the destination's data and status. After confirmation, the source
  case and its drafts are deleted; draft differences are not merged.
- Cases link immutable sources through `review.case_suggestions`.
- A case with multiple suggestions allows detaching a suggestion back to `Jauns`.
  Its link is removed; the remaining case keeps its status and drafts.
- A review has a UUID and may contain several equal source suggestions when
  duplicates are combined.
- All later status changes and draft data belong to the review UUID.
- Drafts reference that case through `case_id`; linked source suggestions remain
  immutable.
- `dict` and `incubator` are read-only; Flyway changes only the `review` schema.

The application currently supports the review queue, duplicate linking,
Tēzaurs entry checks, and entry, lexeme, and sense drafts. It does
not write drafts back to the real Tēzaurs tables.

Confirming that an entry is absent creates its draft and first primary lexeme.
Entry, lexeme and sense edits open separate dialogs in a shared Tēzaurs-style view.
Existing entries and lexemes remain read-only. Before closing as “Jau ir”,
the reviewer may choose to edit the existing sense instead. Only saved sense
edits are copied into `review`; new senses and edits are kept per selected entry.
Completion is checked against the active drafts; readiness flags are not stored.
The schema is defined by the single V1 migration.

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

Cucumber API tests with a temporary backend and database are documented in [integration-tests/README.md](integration-tests/README.md).
