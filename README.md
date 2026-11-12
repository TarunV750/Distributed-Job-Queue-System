# Distributed Job Queue & Task Processing System

Java 21 / Spring Boot 3 / PostgreSQL / SQS (LocalStack locally) / Redis / ECS Fargate.

> **Status: Phase 1 of 7.** The API accepts and tracks jobs and writes them to a transactional outbox.
> Workers, SQS publishing, retries, DLQ, Redis and AWS come in later phases. No benchmark numbers exist yet.
> See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the design.

## Phase 1 endpoints

| Method | Path | Notes |
|---|---|---|
| POST | `/api/v1/jobs` | 202 new, 200 idempotent replay, 409 key reuse with different request |
| GET | `/api/v1/jobs/{id}` | status, timestamps, result, error |
| GET | `/api/v1/jobs` | `status`, `jobType`, `page`, `size` (max 100), `sort` |
| POST | `/api/v1/jobs/{id}/cancel` | only PENDING / QUEUED / RETRYING; else 409 |
| GET | `/api/v1/jobs/{id}/attempts` | empty until workers exist |

Swagger UI: `http://localhost:8080/swagger-ui.html`. Health: `/actuator/health`.

### Payload shapes

| jobType | payload |
|---|---|
| EMAIL | `recipient`, `subject`, `message` |
| DATA_PROCESSING | `numbers`: non-empty array of numbers |
| REPORT_GENERATION | `reportType` (+ optional parameters) |
| WEBHOOK | `url` (http/https) and optional `body` |
| COMPUTATION | `iterations`: 1 .. 1,000,000,000 |

## Run locally

Requires Docker.

```bash
cp .env.example .env          # then edit DB_PASSWORD
docker compose up --build
./scripts/smoke-test.sh
```

Without Docker for the API (needs a local PostgreSQL and Maven):

```bash
export DB_PASSWORD=...        # DB_URL / DB_USERNAME have local defaults
mvn -pl api-service -am spring-boot:run
```

## Tests

```bash
mvn test       # unit tests (state machine, validator, submission/idempotency service)
mvn verify     # also runs *IT integration tests with Testcontainers (needs Docker)
```

## Known gaps (planned)

- No authentication/authorization or rate limiting yet (Phase 4). Do not expose this publicly.
- Jobs stay PENDING: nothing publishes the outbox until Phase 2.
- `POST /jobs/{id}/retry`, `/workers`, `/stats` endpoints arrive in Phases 3-4.
