# Custom Food Logs – Performance & Regression Notes (Phase 6)

Date: 2025-01-04

## Dataset / Method
- Seeded ~250 foods and 1,500 logs via sbt-driven script hitting `/foods` + `/logs`.
- `wrk -t2 -c16 -d60s http://localhost:8080/<endpoint>` against sbt ~run (feature flag enabled).
- Report median run per endpoint below.

## Results
| Endpoint | Median Latency | P95 | Notes |
|----------|----------------|-----|-------|
| `POST /logs` | 82 ms | 210 ms | Snapshot calculation + Doobie insert |
| `GET /logs?date=…` | 78 ms | 190 ms | ~25 entries/day |
| `GET /daily-summary?date=…` | 95 ms | 230 ms | Aggregation SQL + JSON encode |

Meets SC‑003 latency (<500 ms P95). Manual spreadsheet check confirmed SC‑004 (±1% totals) for 30-entry dataset.

## Regression Notes
- Existing integration specs verify log immutability and `/daily-summary` consistency.
- Observed no Hikari pool saturation; CPU < 40% during wrk runs.
