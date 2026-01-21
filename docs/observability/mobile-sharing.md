# Observability Guide — Mobile Diet Sharing

## Metrics

| Metric | Description | Target |
|--------|-------------|--------|
| `diet_sharing_share_latency_ms` | Share creation latency (mobile → API) | ≤500 ms p95 |
| `diet_sharing_copy_latency_ms` | Copy workflow latency | ≤5 s p95 |
| `diet_sharing_recipient_list_latency_ms` | Mobile list fetch latency | ≤2 s p95 |
| `diet_sharing_recipient_view_latency_ms` | Detail screen fetch latency | ≤2 s p95 |
| `diet_sharing_notification_latency_ms` | Push delivery latency | ≤30 s |
| `diet_sharing_share_success_total` | Count of successful shares | Monotonic increase |
| `diet_sharing_copy_success_total` | Count of successful copies | 80% completion rate |
| `diet_sharing_revocation_closure_total` | Revocation closures triggered | Equals number of revokes |
| `diet_sharing_mobile_perf_events_total` | Count of `[PerfTelemetry]` cold sign-in + dashboard load events | Matches number of app cold starts |
| `diet_sharing_mobile_queue_depth` | Size of offline mutation queue (share/revoke/copy) | ≤5 pending items for beta |
| `diet_sharing_copy_funnel_started_total` | Copy flow entries emitted by mobile | Matches number of “Copy to workspace” taps |
| `diet_sharing_copy_funnel_completed_total` | Copy flow completions emitted by mobile | ≥80% of started |

## Dashboards
- Grafana → “Diet Sharing” → Mobile tab.
  - **Performance row**: Cold sign-in, dashboard load, share latency, and copy latency panels with budget lines pulled from `mobile/app/services/telemetry/thresholds.ts`.
  - **Error Budget row**: Panel comparing share/copy failure ratios against the 2% ceiling mandated by SC-004 plus queue depth overlay.
  - **Adoption & CSAT row**: Panels for copy funnel progression and average CSAT scores.
- Add annotations for pilot releases: `diet-tracker-mobile/v0.x` referencing `docs/release-notes/001-share-diet.md`.

## Alerting
- **Performance (SC-001/SC-002)**: Alert `#diet-sharing-alerts` when cold sign-in or share/copy latency panels exceed their budgets for ≥2 consecutive 5-minute windows; include `[PerfBudget]` log excerpts.
- **Push delivery**: Warn channel if `diet_sharing_notification_latency_ms` >60 s for 3 windows.
- **Mobile error rate (SC-004)**: Page on-call when share/copy failure ratio >2% for 30 minutes, especially if queue depth grows.
- **Copy adoption**: Notify PM when `diet_sharing_copy_funnel_completed_total` falls below 80% of `*_started_total` in any rolling hour.

## Troubleshooting Steps
1. Check mobile logs (React Native packager or `adb logcat`) for API errors.
2. Verify backend health (latency dashboards + alert history).
3. Inspect `/specs/001-mobile-app/quickstart.md` for setup validation steps and perf telemetry sampling commands.
4. Re-run manual validation script in `docs/validation/share-diet.md`.
