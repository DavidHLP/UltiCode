# P0-BASELINE-002 Maven Module & Contract Consumer Graph

> status: FROZEN
> head: c344f6268084a893f0bde871da21e5130a331207
> deliverables: Maven dependency graph + contract consumer matrix + misplaced-interface candidates

## 1. Reactor Modules (services/pom.xml)

```
platform/common, platform/rpc-resilience, platform/web-security, platform/integration-inbox, platform/judge-config,
api/auth-api, api/submission-api, api/notification-api, api/app-api,
judge-runtime, auth, admin, search, app, submission, notification, judge
```

App parent (services/app/pom.xml) modules:
```
modules/problem, modules/contest, modules/submission, modules/moderation, app-web (boot shell)
```

Versions: `revision=2.0.0` (platform/api), per-service `service.version.*=1.0.0` (auth, admin, app, submission, notification, judge) — see `services/pom.xml:16-29`, `services/app/pom.xml:16-29`.

## 2. Direct Compile Dependency Trees (main scope)

### backend-app-web (App Owner boot shell) — services/app/app-web/pom.xml:18-72
- `backend-problem-domain` (private)
- `backend-contest-domain` (private)
- `backend-submission-domain` (private)
- `backend-moderation-domain` (private)
- `backend-common` (platform)
- `backend-rpc-resilience` (platform)
- `backend-integration-inbox` (platform)
- `backend-web-security` (platform)
- `backend-app-api` (contract, provider-owned)
- `backend-submission-api` (contract)
- `backend-notification-api` (contract)
- `backend-judge-runtime` (shared storage-free sandbox+queue runtime) — **contains non-rollback leakage (see §4)**
- `backend-auth-api` (contract)
- `meilisearch-java:0.20.1` (optional gated)
- `backend-judge:test` (test scope only)
- Spring Boot starters (web, actuator, security, aop, validation, data-redis, mybatis-plus)

### backend-judge (Worker) — services/judge/pom.xml
- `backend-judge-runtime` (storage-free)
- `backend-app-api` (for contest/problem reads)
- `backend-submission-api` (for verdict write)
- `backend-rpc-resilience`
- No dependency on `backend-app` / `app-web` — **independent Worker verified**

### backend-submission (Owner) — services/submission/pom.xml
- `backend-app-api`, `backend-submission-api`, `backend-judge-config` + Spring Boot

## 3. Contract Consumer Matrix (app-api)

Total Java files in `services/api/app-api/src/main/java`: **153** (includes DTOs/commands/events). The baseline graph found **78 interface labels**, but direct source verification found 75 public declarations; three labels were stale/deleted graph records. After P2-APP-003, current source exports 71 public interfaces. The plan's 75 is the baseline snapshot.

Sample public interfaces (all under `com.ulticode.app.api.service`):
- `JudgeConfigPort`, `JudgeEnqueuePort`, `VerdictResolvePort`, `ModerationUserReadPort` — **misplaced candidates (App/Judge internal)**
- `CodeExecutionPort` (correct: App -> Judge provider)
- `Problem*`, `Contest*`, `Forum*`, `Solution*`, `User*`, `Dashboard*` etc.

Consumer mapping method: `search_graph(name_pattern=".*Port")` -> `trace_path(direction=inbound)` -> `get_code_snippet`, fallback `grep -rn "implements.*Port\|@DubboReference.*app-api"` + `check_index_coverage`. Test-only consumers via `src/test` excluded from production need per acceptance.

| Interface | Declared Owner | Main Production Consumer(s) | Transport | Lifecycle | Evidence |
|-----------|---------------|----------------------------|-----------|-----------|----------|
| JudgeConfigPort | app-api (should be judge-runtime/internal) | judge, app-web | Dubbo | internal | `services/api/app-api/src/main/java/.../JudgeConfigPort.java` |
| JudgeEnqueuePort | app-api (should be judge-runtime) | app-web -> judge | Dubbo | internal | same |
| VerdictResolvePort | app-api (should be submission/judge internal) | submission, judge | Dubbo | internal | same |
| ModerationUserReadPort | app-api (should be auth/app internal) | admin, moderation module | Dubbo | internal | same |
| CodeExecutionPort | app-api (correct cross-owner) | app -> judge provider | Dubbo | stable | `CodeExecutionPort.java` |
| Problem* / Contest* / Forum* | app | admin (61 refs), judge | Dubbo | stable | `services/admin/src/main/java/**/@DubboReference(group=backend-app)` |
| Submission* (intake) | submission-api | app, admin | Dubbo | stable | `submission-api` |

Misplaced-interface candidate list: at least 4 (plan §3), full list deferred to P2-APP-001 catalog.

## 4. Non-rollback Leakage (P0->P4 bridge)

App-web main imports from `backend-judge-runtime` at the frozen baseline (current source, now migrated in Batch B):
- `com.ulticode.app.uuid.AppUuidGenerator` — used in 4 files; migrated to `com.ulticode.common.uuid.UuidGenerator` by P4-LEGACY-002.
- `com.ulticode.modules.submission.codec.SubmissionStatusCodec` — used in 2 files; centralized in `backend-common` by P4-LEGACY-004.
- `SubmissionResultPushPort` alias — queue-local alias used by websocket adapter; deleted by P4-LEGACY-003, app-api contract retained.

The baseline leakage inventory is historical-at-head evidence; current source verification after each migration appears in the P4 task evidence. `backend-judge-runtime` must remain until P4-LEGACY-010 because rollback-only classes still use it.

## 5. Test vs Main Scope Distinction

- `backend-judge` in `app-web` is `test` scope — not production need (plan acceptance: test-only consumer != production need).
- Same-package imports (`com.ulticode.app.*` within app-web) without explicit import are covered via `grep -rn` package scan, not import inventory alone (mitigates P0-002 risk).

## 6. Verification

- `grep -rn @DubboReference services/admin` = 61 (see P0-003 graph)
- `find services/api/app-api -name "*.java" | wc -l` = 153
- `find services/app/modules -name "*.java" | wc -l` = 25 (private modules shallow, main impl in app-web 566 files)
`grep` inventory at baseline was 9 hits across 3 families; current source after Batch B migration is checked by `grep` and the P4 evidence documents.
- `check_index_coverage` on cited paths: if stale/missing, direct source read authoritative (see §4 evidence)
- `mvn dependency:tree -B` with Java 17 produces tree consistent with POM inventory above (reactor-wide 18 modules, app subgraph 5 submodules). Full tree output retained in disposable run; not claimed as production evidence.

## Evidence Level

Repository Implemented. No production split claim.

