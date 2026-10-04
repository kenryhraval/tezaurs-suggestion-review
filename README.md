# Tēzaurs suggestion review

A Spring Boot and React application for reviewing existing Tēzaurs.lv user suggestions.

The application:

- reads source suggestions and their workflow status from `incubator.suggestions`;
- creates richer data in the separate `review` schema when a suggestion first becomes
  `READY_FOR_REVIEW` or goes directly to `IN_PROGRESS`;
- validates the agreed human-selected status transitions;
- records whether an entry was found in Tēzaurs;
- saves links to corpus and other usage evidence;
- reviews candidate meanings for a suggestion.

The suggestion list reads only `incubator.suggestions`. Opening one suggestion
loads its separate UUID-based review; meanings and corpus evidence use that review
UUID rather than the incubator integer ID.

The source statuses are `NEW`, `READY_FOR_REVIEW`, `GARBAGE`, `IN_PROGRESS`,
`INVENTED`, `ALREADY_EXISTS`, `INSUFFICIENT_DATA`, `NEEDS_EXPERT` and
`COMPLETED`. `dict` is read-only from the application's perspective; Flyway owns
only the `review` schema.

Each suggestion has one current meaning and a linear revision history. The
submitted definition is preserved as the first `SUBMITTER` version. Revising it
creates a new `REVIEWER` version, marks the previous version as `SUPERSEDED` and
links the new version to the one it replaced; text is never overwritten. There
are no submeanings, additional meaning branches or meaning-level approval states.

Credential validation is performed by the deployment's reverse proxy. `GET /api/me`
returns the username from the proxy-validated HTTP Basic Authorization header. The
backend must therefore not be exposed directly. There is currently no reviewer
management, review history, external Tēzaurs or corpus connection, evidence source
classification or export process. These can be introduced later as separate,
understandable increments.

## Run locally

Requirements: Java 21 or newer and Docker with Docker Compose.

The Compose file starts PostgreSQL only. The Spring Boot application is run with
Maven so the backend remains easy to develop and debug.

Place the `dict` and `incubator.suggestions` dumps next to the repository as
`tezaurs_2026_03-public.pgsql.gz` and `suggestions-2026-09-04.pgsql.gz`. They are
restored automatically when the PostgreSQL volume is first created. Then:

```bash
cp backend/.env.example backend/.env
# Replace the example password in backend/.env.
docker compose --env-file backend/.env up -d postgres
docker compose --env-file backend/.env ps
```

Then start the application:

```bash
cd backend
set -a
source .env
set +a
./mvnw spring-boot:run
```

The backend is available at `http://localhost:8080`.

Interactive Swagger documentation is available at
`http://localhost:8080/swagger-ui.html`. The generated OpenAPI document is
available at `http://localhost:8080/v3/api-docs`.

To inspect PostgreSQL logs or stop it later, run these commands from the repository
root:

```bash
docker compose --env-file backend/.env logs -f postgres
docker compose --env-file backend/.env down
```

The database is stored in the named Docker volume `tezaurs-postgres`, so ordinary
`docker compose down` does not delete or re-import it.

## Deploy to Hetzner

The repository includes a production Docker image, Compose configuration with
PostgreSQL and automatic HTTPS, and a GitHub Actions deployment workflow. See
[deploy/README.md](deploy/README.md) for the one-time Hetzner and GitHub setup.

## API

- `GET /api/me`
- `GET /api/suggestions`
- `POST /api/suggestions/{id}/status`
- `GET /api/reviews/by-source-suggestion/{sourceSuggestionId}`
- `POST /api/reviews/{reviewId}/term-correction`
- `POST /api/reviews/{reviewId}/tezaurs-check`
- `GET /api/reviews/{reviewId}/corpus-examples`
- `POST /api/reviews/{reviewId}/corpus-examples`
- `GET /api/reviews/{reviewId}/meanings`
- `POST /api/reviews/{reviewId}/meanings/{meaningId}/revision`
- `GET /actuator/health`
- `GET /swagger-ui.html`
- `GET /v3/api-docs`

## Tests

Run the small unit-test suite and generate the JaCoCo report:

```bash
cd backend
./mvnw clean verify
```

Flyway owns the PostgreSQL `review` schema.
