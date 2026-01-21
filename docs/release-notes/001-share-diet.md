# Release Notes — Diet Sharing and Copying

Date: 2025-01-04  
Branch: `001-share-diet`

## Summary
- Owners can share diets via `POST /diets/{dietId}/shares`, list/revoke shares, and trigger notifications + audit logs.
- Recipients access read-only diets using `/me/shared-diets` endpoints and can copy them into personal workspaces (`POST /me/shared-diets/{shareId}/copy`) with typed value-class payloads (diet/user IDs, macro docs) to prevent schema drift.
- Mobile companion app (React Native) mirrors these flows: sign-in, dashboards, share/revoke, read-only detail, copy workflow, and limited owner edits while emitting telemetry discussed below.
- Flyway migration V3 introduced `diet_share`, `diet_copy`, and `audit_log` tables; services use Doobie with transactional guarantees.

## Rollout Checklist
1. Run `sbt flywayMigrate` in staging before enabling the feature flag.
2. Deploy backend with updated `app.sharing` config defaults or override via `SHARING_*` env vars.
3. Toggle `feature.customFoodLogs` on to expose the routes (sharing piggybacks on that flag).
4. Execute validation steps outlined in `docs/validation/share-diet.md` for smoke testing (include Grafana screenshot attachments).

## Monitoring & Metrics
- Track latency timers:
  - `diet_sharing_share_latency_ms`
  - `diet_sharing_copy_latency_ms`
  - `diet_sharing_recipient_list_latency_ms`
  - `diet_sharing_recipient_view_latency_ms`
- Counters to alert on:
- `diet_sharing_share_success_total` vs `diet_sharing_copy_success_total`
- `diet_sharing_revocation_closure_total` (should increment with every successful DELETE)
- Mobile telemetry feed uses User-Agent `diet-tracker-mobile/*` and surfaces share/copy events (see `docs/observability/mobile-sharing.md`); ensure dashboards reveal platform dimension when evaluating SC-001–SC-005.
- Notification timer `diet_sharing_notification_latency_ms` surfaces queue issues; page on-call if p95 exceeds 30s for 2 consecutive windows.
- Audit log volume: estimate ~5 events per active share; ensure retention (365 days) fits storage budgets.

## Rollback Plan
- Disable feature flag to hide endpoints while leaving migrations in place.
- If DB rollback is required, snapshot before removing V3 tables; revocation audit data will be lost if rolled back.
- Remove any Grafana annotations created during rollout to avoid confusing future releases; retain runbook docs for historical reference.
