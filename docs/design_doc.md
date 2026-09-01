# Design Doc — Discount Coupon Service (Empik)

A REST service for managing discount coupons — create coupons with a usage limit and a country restriction, and redeem them safely under concurrent, multi-instance production load.

---

## 1. Functional Requirements

### 1.1 Definitions

**Entity model: Coupon**

| Field | Type | Description |
|---|---|---|
| `id` | `BIGINT GENERATED ALWAYS AS IDENTITY` (primary key) | Surrogate identifier |
| `code` | `VARCHAR(64)` | Coupon code, normalized to lowercase, unique |
| `created_at` | `TIMESTAMPTZ` | Creation date |
| `max_uses` | `INTEGER` (`CHECK (max_uses > 0)`) | Maximum number of uses |
| `current_uses` | `INTEGER` (`CHECK (current_uses BETWEEN 0 AND max_uses)`) | Current number of uses |
| `country` | `CHAR(2)` | Country the coupon is intended for, as an ISO 3166-1 alpha-2 code (this format is what ADR-4's chosen geolocation provider returns, so no conversion is needed when comparing) |

**Entity model: Coupon usage (`coupon_usage`)**

| Field | Type | Description |
|---|---|---|
| `id` | `BIGINT GENERATED ALWAYS AS IDENTITY` (primary key) | Surrogate identifier |
| `coupon_id` | `BIGINT REFERENCES coupons(id)` | Which coupon this refers to |
| `user_id` | `VARCHAR(255)` | Who used it (arbitrary identifier from the request; no format restriction like `code` has, but still length-bounded to avoid unbounded client input) |
| `used_at` | `TIMESTAMPTZ` | When the usage was registered |

Unique constraint on (`coupon_id`, `user_id`) — enforces one use per user per coupon (NFR2 — one use per user under concurrency).

**Actors / roles**

- **Admin** — any caller, no authentication; creates coupons.
- **End user** — uses coupons; identified by an arbitrary `user_id` from the request + IP address.

**Glossary**

- **Coupon** — a record with a code, a usage limit, and a country restriction.
- **Coupon usage** — a single, successful registration of a coupon being used by a specific user.
- **Coupon code** — the coupon's identifier, compared case-insensitively (stored normalized to lowercase).
- **Coupon country** — the country the coupon is intended for; the user's country is determined from their IP address at the moment of use.

### 1.2 Use Cases

### UC1: Create a coupon

- **Actor:** Admin (no authentication)
- **Preconditions:** none

**Variant A — success:**
1. The client sends a request: coupon code, maximum number of uses, target country.
2. The system validates the input: code is non-empty and contains only letters, digits, `-`, and `_`; max_uses > 0; country is a valid country code. Data is written to the database via parameterized queries (prepared statements / ORM), never via string concatenation into SQL — this protects against SQL injection independently of the character restriction on the coupon code (the same applies to `user_id` in UC2 (Use a coupon), which has no such character restriction).
3. The system normalizes the code to lowercase (the database identifier — original casing is not preserved, since the task doesn't require reproducing it, and this simplifies uniqueness).
4. The system saves the new coupon: normalized code, creation date (now), max_uses, current_uses = 0, country.
5. Response: `201 Created` with the created coupon's data.

**Variant B — invalid input:**
2b. Validation fails (branches after step 2 of Variant A; e.g. max_uses ≤ 0, missing code, code with a disallowed character, unknown country) → response `400 INVALID_REQUEST`. End.

**Variant C — code already exists with different data (case-insensitive):**
4c. The save violates the unique constraint on the normalized code (branches after step 4 of Variant A; `WIOSNA` conflicts with the existing `wiosna`), and the existing coupon's data differs from what was requested → response `409 CODE_ALREADY_EXISTS`. End.

**Variant D — retry with identical data (idempotency of retries):**
4d. The save violates the unique constraint on the normalized code (branches after step 4 of Variant A), but the existing coupon's max_uses and country match exactly what was requested — this is the same request retried (e.g. after a client-side timeout), not a genuine conflict → response `200 OK` with the existing coupon's data. End.

### UC2: Use a coupon

- **Actor:** end user, identified by a user ID (arbitrary identifier) + the request's IP address
- **Preconditions:** none (the coupon not existing is one of the paths, not a precondition)

**Rationale for step ordering:** the coupon is checked before the external geolocation API is called. Reason: geolocation is a dependency outside our control (rate limits, cost, latency) — there's no point calling it for a request that will fail anyway with `COUPON_NOT_FOUND` (e.g. a typo in the code). Checking coupon existence is a cheap local database read, so it comes first.

**Shared prefix (every variant):**
1. The client sends a request: coupon code + user_id.
2. The system extracts the IP address from the request.
3. The system looks up the coupon by its normalized (lowercase) code.

**Variant A — success: coupon used and usage registered in the system:**
4. Coupon found → the system calls the external geolocation API with the IP address.
5. Geolocation returns the user's country.
6. Country matches → the system attempts to register the usage: `INSERT` into `coupon_usage(coupon_id, user_id)`.
7. Insert succeeded → the system runs an atomic `UPDATE current_uses = current_uses + 1 WHERE current_uses < max_uses`.
8. Update changed 1 row → commit the transaction.
9. Response: `200 OK` confirming the usage.

**Variant B — coupon does not exist:**
3b. No result (branches after step 3 of the shared prefix) → response `404 COUPON_NOT_FOUND`. End.

**Variant C — geolocation service is not responding:**
4c. Timeout/service error (branches after step 4 of Variant A) → fail-closed → response `503 GEO_UNAVAILABLE`. End.

**Variant D — wrong country (triggered by requests from different locations):**
5d. User's country ≠ coupon's country (branches after step 5 of Variant A) → response `403 COUNTRY_NOT_ALLOWED`. End.

**Variant E — user already used this coupon:**
6e. The insert violates the unique constraint (branches after step 6 of Variant A; this user + this coupon already exists) → response `409 ALREADY_USED`. End.

**Variant F — usage limit reached (blocking further attempts):**
7f. 0 rows changed (branches after step 7 of Variant A) → roll back the insert from step 6 → response `409 LIMIT_REACHED`. End.

---

### Optional extensions (beyond this task's required scope)

- **UC-O1: Get coupon status (GET)** — check current_uses/max_uses without using it; useful for the frontend/support.
- **UC-O2: List/search coupons** — admin panel.
- **UC-O3: Deactivate a coupon before its limit is reached** — withdrawing a promotion.
- **UC-O4: Coupon expiry date** — the task only defines max_uses, not an expiry date; deliberately not adding this field.
- **UC-O5: Reversing a usage** — no such requirement in the task, no business justification to add it.

---

## 2. Non-Functional Requirements — Measurable Architecture Characteristics

The task speaks generally of "scalable" and "multi-threaded production environment" — below, these phrases are turned into concrete, testable requirements.

### NFR1: Exact usage limit under concurrency

Even with N concurrent requests for the same coupon (regardless of how many service instances handle them), `current_uses` will never exceed `max_uses`. Exactly `max_uses` requests will succeed; the rest get a limit error.

### NFR2: One use per user under concurrency

If the same user sends N concurrent requests for the same coupon (double-click, retry), only one will succeed — the rest get an "already used" error, with no double usage.

### NFR3: Database-enforced code uniqueness

Two concurrent attempts to create a coupon with the same code (including a case difference) — one succeeds, the other gets a conflict error. Checking "does the code already exist" in application code before saving is not enough (TOCTOU).

### NFR4: Stateless, horizontally scalable service

The service works correctly across N parallel instances behind a load balancer, with no memory shared between them. All state (counters, usage history) lives in the database, not in application memory — otherwise NFR1 (exact usage limit under concurrency) / NFR2 (one use per user under concurrency) break down under horizontal scaling.

### NFR5: Geolocation service failure resilience

The external geolocation API is a dependency outside our control (limits, outages). A request must not wait indefinitely when the service isn't responding — an explicit timeout, a bounded number of retries with exponential backoff, and fail-closed behavior are needed (deny by default, since country matching is a hard business requirement from the task), rather than fail-open.

### NFR6: Distinguishable denial reasons

The four denial cases from the task (limit / code doesn't exist / wrong country / already used) must be distinguishable on the client side — different error codes, not one generic "400 Bad Request".

### NFR7: Logged usage attempts

Every attempt to use a coupon — success and every type of denial — must be logged with context (code, user_id, outcome, reason). Support must be able to resolve a complaint ("my code didn't work") without asking the customer to reproduce the request.

### NFR8: SQL-injection-safe input handling

No data from the request (coupon code, user_id) reaches SQL queries via string concatenation — only via parameterized queries/ORM. The coupon code is additionally restricted to a safe character set (letters, digits, `-`, `_`).

### NFR9: Rate-limited coupon creation

Coupon creation (UC1 — Create a coupon) requires no authentication, so it must be rate-limited (e.g. per IP) to prevent spam creation of junk coupons and brute-force guessing of valid codes. Without this, anyone can flood the database with coupons.

---

### Recommendations (beyond this task's required scope)

- **Authorization on coupon creation** — the task explicitly states authentication is not required for creating coupons, so this isn't built. In a real production system, restricting coupon creation to authorized callers (JWT / Azure token) would be the recommended follow-up, since an unauthenticated create endpoint is otherwise open to anyone. Noted here rather than implemented, to respect the task's explicit scope.
- **Latency/throughput target** — not defined here. Committing to a number (e.g. p95 latency for the redeem endpoint) without real load-test data would be false precision. Add a measurable target once load testing/benchmarking exists.
- **Health checks for orchestration** — liveness/readiness endpoints so the load balancer/orchestrator (K8s) only routes traffic to instances actually able to serve requests, and can restart instances that are stuck (readiness tied to real dependencies like DB connectivity, not just "process is running"). Standard production practice, not something this task's grading targets directly.
- **Static analysis and dependency/security scanning** — covered in section 4.3, alongside the rest of the test strategy.
- **Load/performance and chaos testing** — covered in sections 4.4 and 4.5.

---

### Toolbox: general solutions for TOCTOU (Time-Of-Check-to-Time-Of-Use)

Reference for section 3 — the window between checking a state and writing based on that state, during which someone else may change the state in the meantime.

1. **Atomic conditional statement** (what UC2, Use a coupon, already uses for the limit/duplicate checks — to be formalized in section 3) — the condition goes into the `WHERE` of the write itself, so there's no window between check and write, because it's one operation.
2. **Pessimistic lock** (`SELECT FOR UPDATE`) — works, but risky if there's an external call between the read and the write (already rejected for geolocation).
3. **Optimistic locking** (`@Version`) — retry on conflict, expensive under heavy contention on the same row.
4. **Database-level constraint** (UNIQUE/CHECK) — makes the illegal state physically impossible to persist, regardless of timing (what we use for NFR2 — one use per user — and NFR3 — database-enforced code uniqueness).
5. **SERIALIZABLE isolation** — the database itself detects the conflict and aborts one transaction; requires retry logic in application code, overhead on every transaction in the system, not just the sensitive one.
6. **Distributed application-level lock** (Redis/ZooKeeper) — rejected outright: a new infrastructure dependency for nothing, since the database already gives sufficient guarantees.

---

## 3. Architectural Decisions — mini-ADRs

### ADR-1: Application framework

**Context:** we need a framework to build the REST API, wire everything together, and talk to the database, without spending the task's time budget rebuilding that plumbing from scratch.

**References:** UC1, UC2

**Alternatives considered:**
1. Plain Java with a minimal embedded server (e.g. raw HTTP server or a micro-framework like Javalin). Full control, but dependency injection, input validation, and JSON handling all have to be hand-built and tested ourselves.
2. Micronaut or Quarkus. Similar feature set to Spring Boot, but a smaller ecosystem and less documentation for this exact combination (JPA + Postgres + validation), meaning more time spent working around unfamiliar edges instead of on the actual business rules.
3. **Spring Boot — chosen, see Decision.** Batteries-included: dependency injection, Spring Data JPA, request validation, an embedded server, and structured logging, all mature and heavily documented.

**Decision:** option 3 — Spring Boot with Spring Web MVC and Spring Data JPA for persistence. It's the standard, best-documented choice for exactly this shape of service — a REST API backed by a relational database — so it lets the effort go into the actual business rules instead of re-implementing dependency injection, validation, and JSON handling that mature libraries already solve well.

### ADR-2: Client IP extraction behind a load balancer

**Context:** the service needs a user's real IP address to check their country, even though NFR4 puts a load balancer in front of every request.

**References:** UC2, NFR4

**Alternatives considered:**
1. Read the IP directly off the incoming connection (e.g. `request.getRemoteAddr()`). Behind a load balancer this always returns the load balancer's own IP, never the client's — country-checking would be broken for every single request.
2. Trust the first address in the `X-Forwarded-For` header. That value is supplied by whoever sent the request — including the client itself — so anyone can put any country's IP there and bypass the restriction entirely.
3. **Trust only the IP appended by our own load balancer (the last hop added by infrastructure we control), not anything the caller supplied — chosen, see Decision.**

**Decision:** option 3 — extract the client IP from the header value our own load balancer appends, never from a value a caller could set directly. This is the only option that can't be forged by the caller — trusting the connection IP breaks the moment there's a load balancer, and trusting the client-supplied header value directly means anyone can claim to be calling from an allowed country.

### ADR-3: Geolocation strategy

**Context:** we need a reliable way to find out which country a user is in from their IP address, without letting a slow or broken external service block or break the rest of the system.

**References:** UC2, NFR5

**Alternatives considered:**
1. Call a specific provider's SDK directly from inside the UC2 redeem logic. Fastest to write, but ties business logic to one vendor's shapes and makes the redeem flow hard to unit-test (every test would need a real or fully-mocked HTTP call baked into the business code).
2. **Define a small `GeoLocationService` interface — chosen, see Decision** (e.g. "given an IP, return a country or a failure") with one HTTP-based implementation behind it, wired in via dependency injection.

**Decision:** option 2 — a `GeoLocationService` interface with an HTTP-based adapter calling a free provider (which one is decided in ADR-4), with an explicit timeout and a small, bounded number of retries using exponential backoff. The retry/backoff is implemented declaratively via Spring Retry's `@Retryable`/`@Backoff` annotations rather than a hand-rolled retry loop — a pattern already proven in production for exactly this kind of external-call resilience. If all retries fail, the call reports failure and UC2 Variant C fails the request closed (`503 GEO_UNAVAILABLE`) rather than hanging or letting the request through unchecked. It's easy to test and swap providers later, unlike calling a specific vendor's code directly from the business logic, which would lock us into one provider and make testing much harder.

### ADR-4: Geolocation provider choice

**Context:** ADR-3 decided to call an external provider through an interface — now we need to pick which free provider that adapter actually calls.

**References:** UC2, NFR5

**Alternatives considered:**
1. **ip-api.com** — no signup or API key, simple flat JSON response. But the free tier is HTTP-only (no HTTPS), limited to non-commercial use, and capped at 45 requests/minute.
2. **ipapi.co** — no API key needed for basic free use, HTTPS supported, but a lower free cap of 1,000 requests/day.
3. **ipwho.is — chosen, see Decision.** No signup or API key, HTTPS supported on the free tier, and the highest free daily limit of the three (2,000 requests/day).

**Decision:** option 3, ipwho.is, called via the `GeoLocationService` adapter from ADR-3. It's the only one of the three that combines no signup, HTTPS, and the largest free quota — ip-api.com forces plain HTTP and a non-commercial restriction, and ipapi.co halves the daily request budget for no real benefit. ipwho.is returns the country as an ISO 3166-1 alpha-2 code, which is why the `Coupon.country` field (1.1 Definitions) is stored in that same format — no conversion needed to compare the two.

### ADR-5: Concurrency-control mechanism

**Context:** a coupon's usage limit must never be exceeded, and no one should be able to use it twice, even when many people try to use it at the same time.

**References:** UC2, NFR1, NFR2, NFR4

**Alternatives considered:**
1. In-memory lock — rejected outright: breaks NFR4, a lock in one instance's memory doesn't stop a different instance from racing it.
2. **Atomic conditional statement (Toolbox #1) — chosen**, combined with #5 below; see Decision.
3. Pessimistic lock (Toolbox #2) — would need to be held across the external geolocation call (UC2 steps 4-5 happen before the write), since we don't know the final decision until after it returns. Holding a DB lock for the duration of an external HTTP call means every other redeemer of that same coupon queues up behind it — a self-inflicted bottleneck on exactly the coupons that get the most traffic.
4. Optimistic locking / `@Version` (Toolbox #3) — retries on conflict; under heavy concurrent load on one popular coupon, this turns into a storm of failed transactions and retries instead of one clean write.
5. **Database-level constraint (Toolbox #4) — chosen**, combined with #2 above; see Decision.
6. SERIALIZABLE isolation (Toolbox #5) — correct, but adds transaction-retry logic to the whole codebase and overhead on every transaction, not just this one sensitive path.
7. Distributed lock via Redis/ZooKeeper (Toolbox #6) — a new infrastructure dependency for something the database already solves natively.

**Decision:** alternatives 2 and 5 above, combined — an atomic conditional write using two DB-level guarantees inside one transaction per redeem request:
- A unique constraint on `coupon_usage(coupon_id, user_id)` — the `INSERT` in UC2 step 6 either succeeds or fails immediately with a constraint violation (enforces NFR2).
- An atomic `UPDATE coupons SET current_uses = current_uses + 1 WHERE current_uses < max_uses` (UC2 step 7) — the `WHERE` is evaluated against the row's live value at that exact instant, not against anything read earlier (enforces NFR1).

The insert runs first, so a repeat request from a user who already redeemed fails fast without ever touching the coupon row. This is the simplest option that works: it needs no locks, no retries, and no extra infrastructure — and unlike holding a lock, it never blocks other requests while waiting on the external country lookup.

### ADR-6: Persistence approach for the concurrency-critical write

**Context:** the way we'd normally use the database (load a record, change it, save it back) would accidentally undo the exact concurrency fix from ADR-5 — the save writes back whatever value the app already computed in memory, instead of re-checking the live count against `max_uses` at the moment of the write.

**References:** UC2, NFR1, NFR2

**Alternatives considered:**
1. Use Spring Data JPA/Hibernate's normal entity lifecycle everywhere, including the redeem write (load the entity, change a field, `save()`). Fits naturally with ADR-1, but Hibernate's usual save flow reads a value and writes it back later — exactly the TOCTOU gap ADR-5 exists to close.
2. Bypass JPA entirely and use plain JDBC/`JdbcTemplate` for every database operation. Full control over every statement, but throws away Spring Data's convenience for the simple, non-critical parts of the service (creating a coupon, looking one up).
3. **Spring Data JPA repositories for everything except the redeem update, which uses a `@Modifying` JPQL/native query to issue the exact atomic `UPDATE ... WHERE` directly — chosen, see Decision.**

**Decision:** option 3. This keeps Spring Boot's normal JPA repositories everywhere they're safe, and only steps around the ORM's usual load-then-save pattern for the one write where that pattern would silently undo ADR-5's concurrency guarantee.

### ADR-7: Case-insensitive code uniqueness

**Context:** coupon codes must stay unique no matter how they're capitalized, even if two people try to create the same code at the same time.

**References:** UC1, UC2, NFR3

**Alternatives considered:**
1. Store the code exactly as submitted, and compare case-insensitively at query time (e.g. `WHERE LOWER(code) = LOWER(:input)`) with a functional unique index on `LOWER(code)`. Preserves the admin's original casing, but every query needs the function wrapper and the index is a less common "expression index" type.
2. **Normalize the code to lowercase before storing — chosen, see Decision** (this is what UC1 — Create a coupon — step 3 and UC2 — Use a coupon — step 3 already describe) and put a plain `UNIQUE` constraint directly on `code`. Simpler schema, but the original casing typed by the admin is not preserved anywhere.

**Decision:** option 2 — normalize to lowercase on write, plain `UNIQUE` constraint on `code`, and every lookup normalizes its input the same way before querying. It's the simplest schema that still makes two identical codes impossible to save at the same time — the other approach would need a special kind of index everywhere just to preserve the original letter casing, which nothing actually asks for.

### ADR-8: Schema management

**Context:** the database schema — including the unique constraints the whole concurrency design depends on — needs to change in a controlled, reviewable way, not be inferred automatically every time the app starts.

**References:** NFR1, NFR2, NFR3

**Alternatives considered:**
1. Hibernate's `ddl-auto` (`update`/`create`). Convenient for prototyping, but it infers the schema from entity annotations and can alter or drop a column or constraint without anyone explicitly reviewing that change.
2. **Flyway versioned SQL migration scripts — chosen, see Decision.**

**Decision:** option 2 — every schema change, including the unique constraints from ADR-5/ADR-7, is an explicit, ordered, version-controlled SQL file. Auto-generated DDL could silently change or drop one of those constraints without anyone noticing until it's already happened in a running database.

### ADR-9: HTTP error model

**Context:** every reason a request gets rejected needs to be clearly identifiable by whoever is calling the API, not lumped together as one generic error.

**References:** UC1, UC2, NFR6

**Alternatives considered:**
1. HTTP status code only, no structured body. Simple, but multiple distinct reasons mapping to the same status code become indistinguishable to the client.
2. **Status code plus a stable, machine-readable error code — chosen, see Decision** in the response body (e.g. `{"error": "LIMIT_REACHED"}`), alongside a human-readable message.
3. A human-readable message only, no machine-readable code. Bad for any automated client — wording can change and silently break anything parsing the message text.

**Decision:** option 2. Every error response carries both the HTTP status already assigned per variant across UC1/UC2 (`400`, `403`, `404`, `409`, `429`, `503`) and the exact error-code strings already used throughout this document (`INVALID_REQUEST`, `CODE_ALREADY_EXISTS`, `COUPON_NOT_FOUND`, `GEO_UNAVAILABLE`, `COUNTRY_NOT_ALLOWED`, `ALREADY_USED`, `LIMIT_REACHED`, `RATE_LIMITED`) — no new names are introduced here, this ADR just formalizes the shape of the response body around names the use cases already fixed. It's the only option that lets a program reliably tell two different failures apart even when they share the same status code, without depending on message text that could change.

### ADR-10: Rate limiting mechanism for coupon creation

**Context:** UC1 (Create a coupon) requires no authentication (NFR9), so it needs a limit on requests per IP, without breaking NFR4 (stateless service, N instances behind a load balancer).

**References:** UC1, NFR9

**Alternatives considered:**
1. In-memory counter (e.g. Bucket4j with a local bucket). Rejected outright — a counter in one instance's memory doesn't stop requests landing on a different instance.
2. Distributed lock/counter via Redis. Rejected outright — a new infrastructure dependency for something the database can already do natively.
3. **Counter row in Postgres, updated with a single atomic UPSERT — chosen, see Decision.** Reuses the atomic-conditional-write pattern already established in ADR-5/Toolbox #1 — no lock, no TOCTOU window, and the state lives in the shared database, so it's correct across every instance (satisfies NFR4).

**Decision:** option 3 — a `creation_rate_limit(ip_address INET PRIMARY KEY, window_start TIMESTAMPTZ, request_count INTEGER)` table, fixed-window algorithm (a configurable limit, e.g. 10 requests/minute per IP). Every UC1 request issues one atomic upsert before the coupon is created:

```sql
INSERT INTO creation_rate_limit (ip_address, window_start, request_count)
VALUES (:ip, now(), 1)
ON CONFLICT (ip_address) DO UPDATE SET
  request_count = CASE
    WHEN creation_rate_limit.window_start < now() - interval '1 minute' THEN 1
    ELSE creation_rate_limit.request_count + 1
  END,
  window_start = CASE
    WHEN creation_rate_limit.window_start < now() - interval '1 minute' THEN now()
    ELSE creation_rate_limit.window_start
  END
RETURNING request_count;
```

If the returned `request_count` exceeds the configured limit, the request is rejected before touching the `coupons` table, with a new `429 RATE_LIMITED` error code (same shape as ADR-9's error model). The IP is extracted via the same trust boundary as ADR-2 (the load balancer's own header, never a caller-supplied value).

This is the simplest option that works within the stack already chosen elsewhere in this document: no new infrastructure, no lock, and one atomic statement instead of a separate check-then-write. Fixed window allows up to 2x the limit right at a window boundary (e.g. the limit again at the start of the next minute) — acceptable for spam/brute-force deterrence; a sliding window would only be worth the extra complexity if this boundary effect turns out to matter in practice.

Per-request cost is negligible — a single indexed UPSERT on the primary key, the same cost class as the atomic `UPDATE` ADR-5 already uses on UC2's hotter redeem path. The actual concern is table growth, not per-request latency: one row is kept per distinct IP forever, since nothing ever deletes old rows. A scheduled cleanup job (e.g. Spring `@Scheduled`, hourly) deleting rows where `window_start` is older than the window size keeps the table bounded, without affecting the correctness of the rate limit itself.

---

## 4. Test Strategy

**Test doubles policy:** the database is never mocked — its features (unique constraints, the atomic `UPDATE`) are what this whole design relies on, so mocking it would test nothing real. The only mocked dependency is `GeoLocationService` — it's external, outside our control, and hitting the real API in tests would be flaky.

### 4.1. Scope-based tests

Organized by test scope. Concurrency, geolocation adapter, idempotency-under-concurrency, and rate-limiting are broken out from "integration" into their own levels.

#### 4.1.1: Unit tests

Pure business logic, `GeoLocationService` and repositories mocked; no Spring context. Covers: input validation and character-set restriction (UC1 Variant B, NFR8 — also the SQL-injection defense for the coupon code), code normalization (ADR-7), idempotency comparison logic (UC1 Variant C vs D), client-IP trust-boundary parsing (ADR-2), and HTTP error-model consistency across all 8 error codes from ADR-9 (NFR6, via a `@WebMvcTest` slice), including `RATE_LIMITED`'s response shape — its trigger condition is covered separately in 4.1.6. *JUnit 5 + Mockito.*

**Additional approach:** property-based testing (jqwik) generates randomized inputs against the validation logic; mutation testing (PIT) breaks the production code to check the suite catches it. Both audit existing coverage rather than add new cases.

#### 4.1.2: Integration tests

Testcontainers Postgres + real Flyway migrations, `GeoLocationService` mocked/stubbed. Covers: every UC1/UC2 variant through the real JPA/SQL stack, the unique constraint (ADR-7), and Flyway migrations applying cleanly (ADR-8, verified implicitly by every test in this tier booting). *JUnit 5 + Testcontainers.*

#### 4.1.3: Concurrency tests

Verifies ADR-5's atomic concurrency mechanism. Real database (Testcontainers), N threads fired simultaneously via `ExecutorService` + `CountDownLatch` at the same coupon code — assert exactly `max_uses` succeed and the rest get `LIMIT_REACHED` (NFR1). Separate test: N simultaneous identical requests from the same user — assert exactly one succeeds (NFR2). *JUnit 5 + Testcontainers.*

#### 4.1.4: Geolocation adapter tests

WireMock, isolated from business logic. Covers: success, timeout → retry per ADR-3's Spring Retry config, exhausted retries → fail-closed (`GEO_UNAVAILABLE`, NFR5), and response mapping against a recorded ipwho.is fixture (ADR-4). *JUnit 5 + WireMock.*

**Additional approach:** contract testing (Pact) on the same boundary — verifies this service's expectations against the provider's actual response shape via a shared contract, instead of a hand-written fixture.

#### 4.1.5: Idempotency-under-concurrency test

Two simultaneous identical `POST /coupons` requests with the same code — one gets `201`, the other gets Variant D's `200`, not a race-corrupted result. *JUnit 5 + Testcontainers.*

#### 4.1.6: Rate-limiting test (NFR9, ADR-10)

Real database (Testcontainers). N+1 simultaneous `POST /coupons` requests from the same IP where N is the configured per-minute limit — assert exactly N succeed (`201`/`200`) and the rest get `429 RATE_LIMITED`. Separate test: advance past the window (or use a short test-only window) and confirm the counter resets. *JUnit 5 + Testcontainers.*

#### 4.1.7: E2E/smoke layer

Full HTTP round-trip through the running application, single process. `@SpringBootTest(webEnvironment = RANDOM_PORT)` tests for the two golden paths (create then redeem). Not exhaustive variant coverage — that's 4.1.2. *JUnit 5 + Testcontainers + WireMock.*

### 4.2. Tool-based tests

The same coverage as 4.1, regrouped by which tool/infrastructure runs it — reflects how the test code is physically organized in the repo (separate test source sets), not the risk-coverage story 4.1 tells. The last two rows aren't separate numbered tests of their own — they're the additional approaches already noted inline in 4.1.1 and 4.1.4, grouped here by their own distinct tooling.

- **JUnit 5 + Mockito** (no external infra) — 4.1.1 Unit tests
- **JUnit 5 + Testcontainers (Postgres)** — 4.1.2 Integration tests, 4.1.3 Concurrency tests, 4.1.5 Idempotency-under-concurrency test, 4.1.6 Rate-limiting test
- **JUnit 5 + WireMock** — 4.1.4 Geolocation adapter tests
- **JUnit 5 + Testcontainers + WireMock** — 4.1.7 E2E/smoke layer
- **jqwik + PIT** (additional approach, layered onto 4.1.1's existing suite) — property-based and mutation testing
- **Pact** (additional approach, layered onto 4.1.4's boundary) — contract testing

### 4.3. Static analysis

- **Static code analysis** — SonarQube (SonarCloud free tier for public repos): code smells, bugs, maintainability.
- **Test coverage gate** — SonarQube, fed by a JaCoCo XML report generated during the build.
- **Dependency & CVE scanning** — GitHub Dependabot. Not two separate concerns: Dependabot's alerts are already CVE-based (GitHub Advisory Database), so one free tool covers both "which dependency is outdated" and "which dependency has a known CVE." A paid SCA tool like BlackDuck would duplicate this for no gain at this scale.
- **Secret scanning** — Gitleaks (free, open-source, runs as a GitHub Actions step), rather than relying on GitHub's native secret scanning — that only applies for free on *public* repos, and this repo is currently private (see TODO). Gitleaks works the same way regardless of the repo's visibility.
- **Container image scanning** — Trivy (base-image/OS-package CVE scanning). No `Dockerfile` exists in this repo yet, but a Spring Boot service like this is expected to run in a container eventually, so this is planned now rather than deferred.

### 4.4. Load/performance testing

Needs the full multi-instance system under real infrastructure — beyond 4.1.7's single-process e2e scope, and its tooling (e.g. k6, Gatling) isn't JUnit-based. Drives realistic concurrent traffic against the service to measure actual throughput and latency, rather than the synthetic thread counts used in the concurrency tests (4.1.3). Confirms NFR1/NFR2's concurrency guarantees hold under production-scale load.

### 4.5. Chaos/production-fault testing

Needs real multi-instance infrastructure and non-JUnit tooling (e.g. Chaos Mesh, Toxiproxy). Deliberately injects real failures (dependency outage, network latency, DB slowdown, an instance dying mid-request) instead of a mocked timeout — surfacing failure-recovery interactions across instances that unit/integration tests, limited to failure paths someone wrote a test for, don't reach.

---

## TODO

- **Repo visibility** — `coupon-service` is currently private (temporary, for working on it without it being public yet). The task requires a publicly accessible repo — switch it back to public before sending the link (GitHub → repo Settings → Danger Zone → Change repository visibility → Make public).
