# Tēzaurs suggestion review

A small Spring Boot backend for the first stage of reviewing Tēzaurs.lv user suggestions.

The current increment deliberately supports only:

- submitting, listing and viewing suggestions;
- changing a human-selected review status;
- recording whether an entry was found in Tēzaurs;
- saving links to corpus and other usage evidence;
- reviewing candidate meanings for a suggestion.

The review statuses are `NEW`, `IN_PROGRESS`, `POSTPONED`, `NEEDS_EXPERT`, `COMPLETED` and `GARBAGE`. A Tēzaurs check distinguishes an existing meaning from a missing meaning in an existing entry. Confirming that both the entry and submitted meaning already exist completes the suggestion automatically; changing that result reopens the suggestion. When the submitted meaning is absent, the corpus result defaults to not found and changes to found when a persisted evidence link is added.

Each suggestion has one current meaning and a linear revision history. The
submitted definition is preserved as the first `SUBMITTER` version. Revising it
creates a new `REVIEWER` version, marks the previous version as `SUPERSEDED` and
links the new version to the one it replaced; text is never overwritten. There
are no submeanings, additional meaning branches or meaning-level approval states.

There is currently no authentication, reviewer management, review history,
external Tēzaurs or corpus connection, evidence source classification or export process. These
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
- `GET /api/suggestions/{id}/corpus-examples`
- `POST /api/suggestions/{id}/corpus-examples`
- `GET /api/suggestions/{id}/meanings`
- `POST /api/suggestions/{id}/meanings/{meaningId}/revision`
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
