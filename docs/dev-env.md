# Development Environment

## Local Database

1. Start PostgreSQL:
   ```bash
   docker compose -f docker-compose.dev.yml up -d postgres
   ```
2. Connection details:
   - Host: `localhost`
   - Port: `5433`
   - Database: `diettracker`
   - User: `diettracker`
   - Password: `postgres`
3. Stop the stack when done:
   ```bash
   docker compose -f docker-compose.dev.yml down
   ```

## Environment Variables

Create a `.env` file or export the following variables before running the app:

```
DATABASE_URL=jdbc:postgresql://localhost:5433/diettracker
DATABASE_USER=diettracker
DATABASE_PASSWORD=postgres
FEATURE_FLAGS="feature.customFoodLogs,feature.dietSharing"
SHARING_NOTIFICATION_TOPIC=diet-sharing-events
SHARING_AUDIT_RETENTION_DAYS=365
```

## Diet Sharing Prerequisites

The diet sharing feature (see `specs/001-share-diet/quickstart.md`) assumes the following before you follow the quickstart steps:

1. **Tooling**: Install JDK 17 and sbt 1.9+ locally; verify `java -version` and `sbt about` report the expected versions.
2. **Docker/Postgres**: Ensure Docker Desktop/Compose is running; start the Postgres service via `docker compose -f docker-compose.dev.yml up -d postgres`.
3. **Migrations**: Run `sbt flywayMigrate` after pulling latest migrations so sharing tables (`diet_shares`, `diet_copies`) exist.
4. **Feature flags**: Export `FEATURE_FLAGS="feature.customFoodLogs,feature.dietSharing"` (the custom food flag is still required and the `feature.dietSharing` flag is now documented explicitly so CLI quickstarts can reference it).
5. **Config overrides**: If you need to change notification topics, audit retention, or metric names, set the `SHARING_*` environment variables referenced in `application.conf` before starting the app.

## Useful Commands

- `sbt ~run` — start the ZIO HTTP server with hot reload
- `sbt test` — run the test suite
- `sbt fmt` — format code, `sbt fmtCheck` to verify formatting
- `sbt flywayMigrate` — apply DB migrations before enabling sharing endpoints
- `sbt scalafmtAll` — format Scala sources before pushing changes
- `cd mobile && yarn install` — install mobile dependencies
- `cd mobile && yarn ios|android` — run the mobile app in simulators
- `cd mobile && yarn lint && yarn test` — lint/unit tests for mobile client
- `cd mobile && yarn build:e2e:ios && yarn test:e2e:ios` — Detox runs (Android equivalents available)

## Monitoring & Alerting Primer

- **Dashboards**: Import the shared “Diet Sharing” Grafana dashboard (JSON in `/docs/observability/diet-sharing-dashboard.json`, see repo wiki) and pin the following panels when working locally:
  - `diet_sharing_share_latency_ms` (p50/p95)
  - `diet_sharing_copy_latency_ms`
  - `diet_sharing_notification_latency_ms`
  - Counters `diet_sharing_share_success_total`, `diet_sharing_copy_success_total`, `diet_sharing_revocation_closure_total`
- **Alert hooks**:
  - Page `#diet-sharing-alerts` if copy latency exceeds 2s for 3 consecutive intervals or if success counters stop incrementing while HTTP 2xx responses still appear.
  - Silence copy/share alerts while running load tests; log silence IDs in `docs/validation/share-diet.md`.
- **Local verification**: When running `sbt ~run`, logs now emit `[diet_sharing_*]` entries with latency; use them to validate instrumentation even without Prometheus.
