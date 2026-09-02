# coupon-service

REST service for creating and redeeming discount coupons, safely under concurrent, multi-instance load.

## Table of Contents

- [Overview](#overview)
  - [Design doc](#design-doc)
- [Installation](#installation)
  - [Prerequisites](#prerequisites)
  - [Configuration](#configuration)
  - [Build](#build)
  - [Run](#run)
  - [Containerization](#containerization)
- [Usage](#usage)
  - [Create a coupon](#create-a-coupon)
  - [Redeem a coupon](#redeem-a-coupon)
  - [Error responses](#error-responses)
- [Testing](#testing)
- [Deploying to Kubernetes](#deploying-to-kubernetes)

## Overview

Two use cases:

- **Create a coupon** — an admin submits a code, a usage limit, and a target country. The code is
  stored lowercase-normalized so `WIOSNA` and `wiosna` are the same coupon; retrying an identical
  request is idempotent.
- **Redeem a coupon** — a user submits a code and their user ID. The service geolocates the
  caller's IP, rejects the request if the country doesn't match the coupon's, and registers the
  usage. A coupon's usage limit and one-use-per-user rule are both enforced at the database level,
  so they hold even under concurrent requests across multiple service instances.

Stack: Java 24, Spring Boot 4 (Web MVC, Spring Data JPA), PostgreSQL, Flyway for schema migrations,
`RestClient` + Spring's native `@Retryable` for the geolocation adapter (calls
[ipwho.is](https://ipwho.is)), Log4j2 for logging.

### Design doc

This was a design-first exercise: [`docs/design_doc.md`](docs/design_doc.md) was written before any
code, then kept in sync with the code as it evolved. It covers:

- the functional requirements (both use cases, with every success and failure variant spelled out)
- the non-functional requirements (concurrency safety, resilience, error handling)
- a set of mini-ADRs — for each significant decision (framework, concurrency mechanism,
  persistence approach, error model, etc.), the alternatives considered and why the chosen one won
- the test strategy by scope (unit, integration, concurrency, adapter, E2E), laid out before any
  tests were written

The service itself was then built incrementally, one layer or use case per branch (schema →
repositories → service → controller for UC1, then the same for UC2), each verified against a real
Postgres instance before merging.

Not everything in the design doc is implemented. The optional extensions and the recommendations
section are explicitly out of the task's required scope. On top of that, given the tight schedule,
a few test-strategy items (the E2E layer, the same-user concurrency test, and the property-based/
mutation/contract-testing extras) were documented but not yet written.

## Installation

### Prerequisites

- Java 24
- Docker (used both by tests via Testcontainers, and for local Postgres via `docker-compose.yml`)

### Configuration

The service only has two application properties, set in `src/main/resources/application.yml`:

| Property | Purpose |
|---|---|
| `spring.application.name` | Service name (standard Spring Boot identifier) |
| `spring.jpa.open-in-view` | Disabled — the persistence context doesn't stay open for the view layer |

Database connection details aren't set explicitly: `spring-boot-docker-compose` auto-starts
`docker-compose.yml`'s Postgres container and wires the datasource for local development.

### Build

```
mvn clean verify
```

Runs the full test suite, including integration tests backed by Testcontainers, and produces the
runnable jar at `target/coupon-service-*.jar`.

To build the runnable jar without running tests:

```
mvn clean package -DskipTests
```

### Run

```
mvn spring-boot:run
```

Starts the app on port 8080. Spring Boot auto-starts `docker-compose.yml`'s Postgres container and
wires the connection — no manual setup needed.

Running the packaged jar directly (`java -jar target/coupon-service-*.jar`) does **not**
auto-start Postgres — Spring Boot's Docker Compose support is a development-time feature, excluded
from the packaged jar by design. Use `mvn spring-boot:run` for local development.

### Containerization

The `Dockerfile` packages an already-built jar — it doesn't compile anything itself, so build the
jar first:

```
mvn clean package -DskipTests
docker build -t coupon-service .
```

Run it against a Postgres instance it can reach, passing connection details as environment
variables (`spring.datasource.*` isn't set in `application.yml` — see [Configuration](#configuration)):

```
docker run -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:5432/coupon_service \
  -e SPRING_DATASOURCE_USERNAME=coupon_service \
  -e SPRING_DATASOURCE_PASSWORD=coupon_service \
  coupon-service
```

## Usage

### Create a coupon

```
curl -X POST localhost:8080/coupons \
  -H 'Content-Type: application/json' \
  -d '{"code":"SUMMER","maxUses":5,"country":"PL"}'
```

Returns `201` with the created coupon, or `200` with the existing one if this is an identical
request retried:

```json
{"id":1,"code":"summer","createdAt":"2026-01-01T12:00:00Z","maxUses":5,"currentUses":0,"country":"PL"}
```

### Redeem a coupon

```
curl -X POST localhost:8080/coupons/redeem \
  -H 'Content-Type: application/json' \
  -d '{"code":"summer","userId":"user-1"}'
```

Returns `200` confirming the usage:

```json
{"code":"summer","userId":"user-1","usedAt":"2026-01-01T12:05:00Z"}
```

### Error responses

Every rejected request returns a stable, machine-readable error code alongside a human-readable
message:

```json
{"error":"LIMIT_REACHED","message":"Coupon has reached its usage limit: summer"}
```

| Status | Error code | When |
|---|---|---|
| 400 | `INVALID_REQUEST` | Invalid coupon code, usage limit, country, or user ID |
| 403 | `COUNTRY_NOT_ALLOWED` | Caller's geolocated country doesn't match the coupon's |
| 404 | `COUPON_NOT_FOUND` | No coupon exists for the given code |
| 409 | `CODE_ALREADY_EXISTS` | Coupon code already exists with different data |
| 409 | `ALREADY_USED` | This user has already redeemed this coupon |
| 409 | `LIMIT_REACHED` | Coupon has reached its usage limit |
| 503 | `GEO_UNAVAILABLE` | Geolocation provider unreachable, even after retries |

## Testing

- **Unit** (JUnit 5 + Mockito) — business logic in isolation, no Spring context, no database:
  validation, normalization, and the create/redeem branching logic.
- **Integration** (JUnit 5 + Testcontainers) — real Postgres and real Flyway migrations, covering
  every use-case variant through the actual JPA/SQL stack.
- **Concurrency** (JUnit 5 + Testcontainers) — real threads racing the same coupon, asserting the
  usage limit is never exceeded.
- **Geolocation adapter** (JUnit 5 + MockServer) — success, retry, and fail-closed behavior against
  a stubbed HTTP server, isolated from business logic.
- **Controller** (`@WebMvcTest`) — HTTP status and error-code mapping, with the services mocked.

```
mvn clean verify
```

## Deploying to Kubernetes

The service is stateless by design (ADR-4, NFR4) — every concurrency guarantee lives in Postgres,
not in application memory, so running multiple pods behind the same database needs no code change.

Only the `Dockerfile` exists so far. The rest — a Helm chart for the Kubernetes manifests, Terraform
for the cluster/database/registry, secrets, health-check probes, and CI/CD — is documented, not yet
built, and deliberately not tied to a specific cloud provider: see
[`docs/design_doc.md`](docs/design_doc.md), section 5.
