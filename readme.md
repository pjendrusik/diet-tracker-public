# Diet Tracker Development Guidelines

Feature plans. Last updated: 2025-01-04

## Active Technologies
- Scala 2.13 on JVM 17 + ZIO 2.x, ZIO HTTP + Tapir, Doobie, zio-json (001-share-diet)
- PostgreSQL 15 with Flyway migrations, Testcontainers for integration tests (001-share-diet)
- Secure token storage (Keychain/Keystore via Expo SecureStore), AsyncStorage for cached diet summaries and feature flags; no new server DB tables (001-mobile-app)

## Project Structure

```text
build.sbt
project/
  Dependencies.scala

src/
├── main/scala/com/diettracker/
│   ├── config/
│   ├── http/
│   ├── services/
│   ├── repositories/
│   └── domain/
├── main/resources/
│   └── db/migration/
└── test/scala/com/diettracker/
    ├── services/
    ├── repositories/
    └── contract/
```

## Commands
- `sbt flywayMigrate` — apply database migrations locally
- `sbt ~run` — start ZIO HTTP server with Tapir endpoints
- `sbt test` / `sbt IntegrationTest/test` / `sbt ContractTest/test` — execute unit, integration, and contract suites
- `docker compose up -d postgres` — launch local PostgreSQL instance for development

## Code Style
- Follow scalafmt + sbt-tpolecat defaults, keep side effects in repositories/services, and expose pure domain methods wherever possible.

## Recent Changes
- 001-share-diet: Added Scala 2.13 on JVM 17 + ZIO 2.x, ZIO HTTP + Tapir, Doobie, zio-json
- 001-custom-food-logs: Adopted Scala/ZIO/Tapir stack with PostgreSQL persistence, Flyway migrations, and Testcontainers coverage.

<!-- MANUAL ADDITIONS START -->
<!-- MANUAL ADDITIONS END -->
