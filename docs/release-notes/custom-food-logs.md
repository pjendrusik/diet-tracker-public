# Release Notes — Custom Food Logs (Phase 6)
Date: 2025-01-04

## Summary
- `/foods` search/update/delete, `/logs` CRUD, and `/daily-summary` endpoints behind `feature.customFoodLogs`.
- Flyway migrations V1__init + V2__food_search_indexes applied.

## Smoke Test
1. `sbt flywayMigrate`
2. `FEATURE_FLAGS=feature.customFoodLogs sbt ~run`
3. `POST /foods` → `POST /logs` → `GET /logs` → `GET /daily-summary`
4. Verify daily totals match manual sheet (±1%).

## Rollback Plan
- Toggle off `feature.customFoodLogs` to hide routes.
- If DB rollback needed, run `flyway undo` for V2, restore backups before undoing V1.
