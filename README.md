# Tēzaurs suggestion review

Spring Boot and React application for reviewing user suggestions submitted to
Tēzaurs.

## Data model

- `incubator.suggestions` is the immutable source of submitted suggestions.
- The backend imports unlinked suggestions when it starts, then checks again
  10 seconds after each import finishes. Each suggestion receives a case and UUID.
  Its original status initializes the case (`0`–`8`); missing status defaults to
  `NEW`. Later source status changes do not overwrite case decisions.
- Automatic imports have no status-change author. Human status changes record
  the proxy-authenticated username. “Paņemt apstrādē?” starts work;
  “Atteikties no apstrādes” returns to `Skatāms` and preserves saved work.
  `last_status_changed_by` identifies who took an active case; there is no
  separate ownership lock.
- Merging keeps the destination's data and status. After confirmation, the source
  case and its drafts are deleted; draft differences are not merged.
- Cases link immutable sources through `review.case_suggestions`. Their `type` is
  `UNKNOWN`, `ENTRY` or `SENSE`; `/api/reviews/{id}/type` saves the selected path
  and matching entry, closing the case as `Jau ir` when its meaning already exists.
- A case with multiple suggestions allows detaching a suggestion back to `Jauns`.
  It moves to a fresh `NEW` case; the remaining case keeps its status and drafts.
- A review has a UUID and may contain several equal source suggestions when
  duplicates are combined.
- All status changes and draft data belong to the review UUID.
- Drafts reference that case through `case_id`; linked source suggestions remain
  immutable.
- `dict` and `incubator` are read-only; Flyway changes only the `review` schema.

The application currently supports the review queue, duplicate linking,
Tēzaurs entry checks, and entry, lexeme, and sense drafts. It does
not write drafts back to the real Tēzaurs tables.

For a new entry, choose a usage decision in “Lietojuma pārbaude”. “Lietojums atzīts”
immediately creates its draft and first primary lexeme. “Trūkst datu” and “Izdomāts vārds” require confirmation and pause
work; saved evidence stays readable. Resume in `Apstrādē` to change evidence or the
saved decision. Automatic search checks the 42 corpora
listed by [korpuss.lv](https://korpuss.lv/search), using word forms and lemmas where
available. Results appear progressively; unavailable corpora are marked separately.
Press Enter to search a changed word; click a corpus count to open its examples.
The corpus with the most matches opens by default after the search finishes.
Marked examples stay in the results and in a paginated selection across all search phrases.
The corpus list and server URLs are maintained in `Corpus.java`.
Findings and external citations are saved separately from dictionary examples.
Entry, lexeme and sense drafts are edited in popup forms. Existing entries are
shown as a name and link; TIP only prepares new content. New sense drafts retain
which existing entry they belong to, without copying that entry into `review`.
Completion is checked against the active drafts; readiness flags are not stored.
The schema is defined by the single V1 migration.

Section rules are in `frontend/src/review/workflow.ts`: `Jauns`/`Skatāms` open intake,
`Apstrādē` opens the unfinished step, and postponed/finished cases start collapsed.
Workflow changes reset section UI, while saved checks, evidence, and drafts are preserved.

Authentication is performed by the deployment reverse proxy. The backend reads
the authenticated username from the proxy-validated Basic Authorization header
through `GET /api/me`, so the backend must not be published without that proxy.
All status changes, including usage and Tēzaurs decisions, require this header
and a readable username. Full status-change history is not implemented yet.

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

The Vite development proxy identifies API requests as the test user `local`.
Deployed environments continue to use their reverse-proxy login.

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

Cucumber API tests with a temporary backend and database are documented in [integration/README.md](integration/README.md).
