# Worklog

## 2026-08-20 architecture review closure

- Parsed `/tmp/architecture-review-20260820-045601.html` and mapped all candidates to ARCH-REVIEW-001..005.
- Implemented Search DLQ atomic transfer with Redis Lua idempotency marker; focused worker suite passed 11/0/0/0.
- Changed Submission Projection single-row enrichment to use existing batch read seams `findAllById` and `findDisplayFactsBatch`; affected app-web reactor compile passed.
- Added Auth-owned bounded `AccountStatsSummary` contract/provider/aggregate SQL and changed Admin dashboard to consume one summary; focused Admin reactor suite passed 6/0/0/0.
- Made app runtime datasource require explicit `APP_DB_*`; retained generic `DB_*` as migration bootstrap configuration documentation.
- Passed shell, profile, Compose dev/prod, YAML, diff and graphify checks.
- Final status is development-only complete. No production acceptance or external delivery action claimed.
- Known blocker remains explicit: app-web Submission focused test command cannot compile because inherited Search contract tests still call migrated `ProblemSearchReadPort`/`AccountQueryService` APIs. This blocker was not hidden or marked passed.
