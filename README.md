# Tēzaurs suggestion review

A small Spring Boot backend for the first stage of reviewing Tēzaurs.lv user suggestions.

The current increment deliberately supports only:

- submitting, listing and viewing suggestions;
- changing a human-selected review status;
- recording whether an entry was found in Tēzaurs;
- recording whether usage was found in corpus data;
- reviewing candidate meanings for a suggestion.

The review statuses are `NEW`, `IN_PROGRESS`, `POSTPONED`, `NEEDS_EXPERT`, `COMPLETED` and `GARBAGE`. Tēzaurs and corpus checks are separate fields with `NOT_CHECKED`, `FOUND` and `NOT_FOUND` values. Status changes are intentionally unrestricted in this first increment.

Each submitted definition is preserved as a `SUBMITTER` meaning. A reviewer can add
a separate meaning, arrange meanings hierarchically with an optional parent, or
create a revision. Revising creates new `REVIEWER` text and marks the source as
`SUPERSEDED`; it never overwrites the source. Meanings can be `PROPOSED`,
`APPROVED`, `REJECTED` or `SUPERSEDED`. Imported Tēzaurs meanings use the
`EXISTING` origin and start as `APPROVED`; they may later be revised or rejected.
A reviewer revision points back to the meaning it supersedes, while the optional
parent ID represents the meaning hierarchy rather than revision history. The
`GENERATED` and `EXISTING` origins are reserved for later integrations and are not
currently created through the public API.

There is currently no authentication, reviewer management, review history,
external Tēzaurs or corpus connection, evidence storage or export process. These
can be introduced later as separate, understandable increments.

## Run locally

Requirements: Java 21 or newer and Docker with Docker Compose.

The Compose file starts PostgreSQL only. The Spring Boot application is run with
Maven so the backend remains easy to develop and debug.

First, from the repository root, prepare the local environment file and start the
database:

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
`docker compose down` does not delete the data. There is currently no Docker image
or Dockerfile for the Spring Boot application in the local-development Compose
file.

## Deploy to Hetzner

The repository includes a production Docker image, Compose configuration with
PostgreSQL and automatic HTTPS, and a GitHub Actions deployment workflow. See
[deploy/README.md](deploy/README.md) for the one-time Hetzner and GitHub setup.

## API

- `POST /api/suggestions`
- `GET /api/suggestions`
- `GET /api/suggestions/{id}`
- `POST /api/suggestions/{id}/status`
- `POST /api/suggestions/{id}/term-correction`
- `POST /api/suggestions/{id}/tezaurs-check`
- `POST /api/suggestions/{id}/corpus-check`
- `GET /api/suggestions/{id}/meanings`
- `POST /api/suggestions/{id}/meanings`
- `POST /api/suggestions/{id}/meanings/{meaningId}/revision`
- `POST /api/suggestions/{id}/meanings/{meaningId}/approve`
- `POST /api/suggestions/{id}/meanings/{meaningId}/reject`
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
