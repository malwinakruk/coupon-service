# coupon-service

REST service for managing discount coupons.

Design decisions and rationale: [`docs/design_doc.md`](docs/design_doc.md).

## Prerequisites

- Java 24
- Docker (used both by tests via Testcontainers, and for local Postgres via `docker-compose.yml`)

## Running locally

```
mvn spring-boot:run
```

Starts the app on port 8080. Spring Boot auto-starts `docker-compose.yml`'s Postgres container and wires the connection — no manual setup needed.

Running the packaged jar directly (`java -jar target/coupon-service-*.jar`) does **not** auto-start Postgres — Spring Boot's Docker Compose support is a development-time feature, excluded from the packaged jar by design. Use `mvn spring-boot:run` for local development.

## Building and testing

```
mvn clean verify
```

Runs the full test suite, including integration tests backed by Testcontainers.
