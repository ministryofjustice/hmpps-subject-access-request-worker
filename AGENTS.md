# AGENTS.md

Guidance for AI coding agents working in this repository.

## Project overview

`hmpps-subject-access-request-worker` is a Spring Boot application written in Kotlin. It performs the
heavy lifting of extracting data from upstream HMPPS services and generating PDF reports for Subject
Access Requests (SAR). It interacts with the
[Subject Access Request API](https://github.com/ministryofjustice/hmpps-subject-access-request-api).

Confluence overview: https://dsdmoj.atlassian.net/wiki/spaces/SARS/pages/4771479564/Overview

## Tech stack

- JAVA 25
- Kotlin / Spring Boot (Gradle Kotlin DSL, `build.gradle.kts`)
- Spring Data JPA + Flyway migrations, PostgreSQL in production, H2 for tests
- WebFlux/WebClient for upstream HTTP calls
- PDF generation via iText7 / openhtmltopdf, Mustache/Handlebars templating
- WireMock for stubbing upstream services in integration tests
- Testcontainers + JUnit Jupiter for tests
- Sentry and Azure Application Insights for alerting/telemetry

## Repository layout

- `src/main/kotlin/uk/gov/justice/digital/hmpps/subjectaccessrequestworker/`
  - `alerting/` — Sentry/alerting integration
  - `backlog/` — SAR backlog processing
  - `client/` — WebClient clients for upstream services
  - `config/` — Spring configuration
  - `controller/` — REST controllers
  - `events/` — application events
  - `exception/` — exception handling
  - `health/` — health indicators
  - `models/` — domain models/entities
  - `repository/` — Spring Data JPA repositories
  - `scheduled/` — scheduled jobs
  - `services/` — business logic (data extraction, PDF generation, etc.)
  - `utils/` — helpers
- `src/main/resources/db/` — Flyway migrations (`sar`, `sar_h2`, `sar_postgresql`, `dev`)
- `src/test/kotlin/...` — unit and integration tests, mirroring the main package structure
- `src/test/resources/integration-tests/` — WireMock stubs, HTML/PDF fixtures used by integration tests
- `helm_deploy/` — Kubernetes Helm chart for deployment
- `docker-compose.yml` / `docker-compose-local.yml` — local dependencies (e.g. Postgres, LocalStack)

## Build, test, and lint commands

Run these from the repository root.

```bash
# Build (skip tests)
./gradlew clean build -x test

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "uk.gov.justice.digital.hmpps.subjectaccessrequestworker.SomeTestClass"

# Lint (Kotlin style)
./gradlew ktlintCheck
./gradlew ktlintFormat   # auto-fix

# Dependency checks
./gradlew dependencyCheckAnalyze --info
./gradlew dependencyUpdates --warning-mode all

# Start local dependencies (Postgres, etc.)
docker-compose up -d
```

Always prefer the smallest targeted Gradle invocation that covers a change (e.g. a single test class)
before running the full `test` suite.

## Conventions

- Kotlin style is enforced by `ktlintCheck`/`ktlintFormat` — run these before committing.
- Tests mirror the main source package structure under `src/test/kotlin`.
- Flyway migrations are append-only: never edit a migration that has already been merged; add a new
  versioned migration file instead. There are separate migration sets for H2 and PostgreSQL — keep them
  in sync when schema changes are needed.
- Integration tests rely on WireMock stubs in `src/test/resources/integration-tests/html-stubs` and
  reference PDFs in `src/test/resources/integration-tests/reference-pdfs` — update these fixtures
  deliberately when behaviour intentionally changes.
- Required roles for the worker: `ROLE_SAR_DATA_ACCESS`, `ROLE_DOCUMENT_TYPE_SAR`,
  `ROLE_PROBATION_API__SUBJECT_ACCESS_REQUEST__DETAIL`, `ROLE_VIEW_PRISONER_DATA`,
  `ROLE_DOCUMENT_WRITER`.

## Pull requests

- Keep changes focused and avoid unrelated refactors.
- Ensure `./gradlew ktlintCheck` and relevant tests pass before opening a PR.
- Update Helm chart values (`helm_deploy/`) if configuration/environment variables change.

## Dependency injection

Prefer constructor injection over field injection in Spring components, including test classes.

- In production code, declare dependencies as constructor parameters (e.g. `private val fooRepository: FooRepository`) rather than using `@Autowired` on a field.
- In integration test classes, pass `@Autowired` repositories/beans as primary constructor parameters instead of using `@Autowired` on a `lateinit var` field.

## Tests

Prefer `@BeforeAll`/`@AfterAll` over `@BeforeEach`/`@AfterEach` where possible in nested integration test classes.

- Use `@TestInstance(TestInstance.Lifecycle.PER_CLASS)` on the nested test class so that `@BeforeAll`/`@AfterAll` can be non-static (instance) methods.
- Build shared fixture data once in `@BeforeAll` and clean it up once in `@AfterAll`, rather than rebuilding it before every test, when the fixture is not mutated in a way that would leak between tests.
- When copying or adapting an integration test, preserve the full nested test structure, including `Security`, `Validation`, and `HappyPath` scenarios for each endpoint.
- Place each `@AfterAll` method immediately after the corresponding `@BeforeAll` setup method.
- OAuth2 propagation tests must verify the exact WireMock header value with `equalTo("Bearer ABCDE")`; do not weaken this assertion to a regex or non-empty-header matcher.

## Linting

Do not run `ktlintCheck` (or any ktlint gradle task) in this repository. Skip ktlint validation entirely.
