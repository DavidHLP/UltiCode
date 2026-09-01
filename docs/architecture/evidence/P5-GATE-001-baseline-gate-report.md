# P5-GATE-001 Baseline Gate (Report-Only)

> status: REPORT-ONLY PASS (initial)
> head: c344f6268084a893f0bde871da21e5130a331207
> gate: GATE-BASELINE-FROZEN
> mode: report-only (fail does not block Batch B, but recorded)

## Checks

| Check | Result | Evidence |
|-------|--------|----------|
| Ownership topology (5 owners + 2 workers, Judge independent) | PASS | `AGENTS.md:14`, `services/docs/SERVICES_ISSUES.md:14-18`, `docs/project/current-status.md:9`, `services/judge/pom.xml` no app-web dep |
| Interface ownership (app-api 153 files, 75 ports, 4 misplaced) | PASS | `P0-002` graph, `find services/api/app-api -name "*.java" | xargs grep -l interface` |
| Maven reactor (18 modules, app 5 submodules, per-service versions) | PASS | `services/pom.xml:modules`, `services/app/pom.xml:modules`, `mvn dependency:tree` consistent |
| Profile closure (legacy-rollback reachability graph, 3 non-rollback leakages, 18 conditional files) | PASS | `P0-005` graph, `grep -rn legacy-rollback` 18 hits, `grep -rn AppUuidGenerator|SubmissionStatusCodec|SubmissionResultPushPort` 9 hits |
| Compose base+dev config | PASS | `docker compose --env-file .env -f docker-compose.yml -f docker-compose.dev.yml config >/dev/null` PASS |
| Compose base+prod config | BLOCKED_EXTERNAL (expected) | Requires `BACKEND_*_IMAGE_REF`, `MANAGEMENT_OTLP_TRACING_ENDPOINT`, `JWT_RSA_PRIVATE_KEY`, `JUDGE_DOCKER_HOST`, etc. — fail-closed, not repo defect |
| Architecture contract | PASS_WITH_EXTERNAL_BLOCKERS | `scripts/dev/architecture-contract-test.sh` PASS, externals BLOCKED_EXTERNAL as designed |
| Docs contract | PASS | `scripts/dev/docs-contract-test.sh` (see logs) |
| git diff --check | PASS | no whitespace errors |

## Gate Decision

- GATE-BASELINE-FROZEN: **PASS (report-only)**
- Proceed to Batch B (low-risk interface & non-rollback) allowed.
- Full fail-closed gate will be re-run before P4-LEGACY-006 (via P5-GATE-001 full).

## Evidence Level

Repository Implemented + Disposable Validatable. No production evidence.

