# Validation Runbook — Diet Sharing & Copying

Date: 2025-01-04  
Feature Branch: `001-share-diet`

## Preconditions
1. Local Postgres running (`docker compose -f docker-compose.dev.yml up -d postgres`)
2. Apply migrations: `sbt flywayMigrate`
3. Start server with feature flag enabled:
   ```bash
   FEATURE_FLAGS=feature.customFoodLogs sbt ~run
   ```
4. Have two user IDs ready (e.g., `OWNER_ID`, `RECIPIENT_ID`) to send requests via HTTP client.
5. Monitoring: open Grafana dashboards that plot
   - `diet_sharing_share_latency_ms`, `diet_sharing_copy_latency_ms`
   - counters `diet_sharing_share_success_total`, `diet_sharing_copy_success_total`, `diet_sharing_revocation_closure_total`
   - notification latency timer `diet_sharing_notification_latency_ms`
6. Alerting: confirm Prometheus alert rules for >5% failure on sharing/copy flows are temporarily silenced (if validating in lower env) or acknowledged (prod).

## Test Matrix

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | `POST /diets/{dietId}/shares` with owner header `X-User-Id: OWNER_ID` and JSON `{ "recipientUserId": "RECIPIENT_ID" }` | `201 Created`, response shows `status: ACTIVE`; notification log line printed |
| 2 | `GET /diets/{dietId}/shares` with owner header | Share list includes the new recipient with `ACTIVE` status |
| 3 | `GET /me/shared-diets` with `X-User-Id: RECIPIENT_ID` | Response lists the shared diet with owner metadata |
| 4 | `GET /me/shared-diets/{shareId}` with recipient header | Returns read-only payload; editing attempt (e.g., `PATCH /diets/{dietId}`) should fail with `validation_error` |
| 5 | `POST /me/shared-diets/{shareId}/copy` with recipient header | `201 Created` copy response; verify `newDietId` differs from source |
| 6 | Edit the copied diet via owner APIs using `newDietId` | Changes remain isolated; original owner diet remains untouched |
| 7 | `DELETE /diets/{dietId}/shares/{shareId}` with owner header | `204 No Content`; recipient loses access (`GET /me/shared-diets/{shareId}` returns 404) |
| 8 | Check audit log table (`SELECT event_type, metadata FROM audit_log ORDER BY occurred_at DESC LIMIT 5`) | Entries include `SHARE_CREATED`, `DIET_COPIED`, and `SHARE_REVOKED` with correct metadata |
| 9 | Inspect metrics dashboard after share/copy/revoke | Latency timers stay ≤500 ms (p95) and revocation closure counter increments once per DELETE |
| 10 | Use Grafana annotation to record this manual run | Screenshot stored with timestamp and link to alert channel |

## Manual Notes
- Monitor server logs to confirm notification messages when shares are created/revoked (look for `diet_sharing_notification_latency_ms` output).
- If copy flow needs cleanup, delete rows by `new_diet_id` and rerun steps from both web and mobile (ensure mobile copy CTA surfaces progress + success screen).
- Record rollback verification:
  - Disable `feature.dietSharing`, ensure endpoints hide.
  - Re-enable and re-run steps 1–5 to prove forward toggle works.
- Document any deviations or bugs here, including metric screenshots/links and mobile device logs so on-call engineers can triangulate regression scope.
