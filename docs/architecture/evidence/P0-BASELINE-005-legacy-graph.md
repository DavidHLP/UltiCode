# P0-BASELINE-005 Legacy Reachability & Deletion Closure Graph

> status: FROZEN
> head: c344f6268084a893f0bde871da21e5130a331207
> deliverables: legacy reachability graph + dependency closure + safe deletion sequence

## 1. Profile & Bean Reachability

| Profile Value | Guard | Beans Enabled | Impact |
|---------------|-------|---------------|--------|
| `dev-lite` (default) | `${app.runtime.mode:dev-lite} != 'legacy-rollback'` | Remote adapters: `RemoteSubmissionReadAdapter`, `RemoteSubmissionUserQueryAdapter`, `RemoteSubmissionStreakAdapter`, `RemoteProblemSubmissionStatsAdapter`, `RemoteSubmissionGenerationReadAdapter`, `RemoteSubmissionUserStatsAdapter`, `RemoteSubmissionStreakAdapter`, `RemoteCodeExecutionPort`, `RemoteSubmissionAdjudicationReadAdapter` | Normal path (Submission Owner) |
| `legacy-rollback` | `== 'legacy-rollback'` | Local adapters: `LocalSubmissionUserQueryAdapter`, `ProblemSubmissionStatsMapperAdapter`, `SubmissionReadAdapter`, `SubmissionStreakAdapter`, `SubmissionUserStatsMapperAdapter`, `LocalSubmissionAdjudicationReadAdapter`, `DefaultSubmissionGenerationReadAdapter` (condition), `JdbcSubmissionStreakCalculator`, plus `LegacySubmissionMapperScanConfig`, `AppJudgeCompatibilityConfiguration` | Rollback-only |
| `AppJudgeCompatibilityConfiguration` | `&& '${app.runtime.mode:dev-lite}' == 'legacy-rollback'` | `AppJudgeCompatibilityAdapter`, old RQueue path | Compatibility closure |
| `LegacySubmissionMapperScanConfig` | `'${app.runtime.mode:dev-lite}' == 'legacy-rollback'` | `SubmissionMapper` scan | Mapper only in rollback |

Entry points:
- `BackendAppApplication.java:7,16` imports `AppJudgeCompatibilityConfiguration`
- `services/app/app-web/src/main/java/com/ulticode/app/config/LegacySubmissionMapperScanConfig.java:10`
- `services/app/app-web/src/main/java/com/ulticode/app/judge/AppJudgeCompatibilityConfiguration.java:22`

DevStack manifest: `scripts/dev/devstack-manifest.sh:195-202` — `legacy-rollback` must be explicitly enabled via DevStack flag, not default.

Routing logic: `SubmissionRoutingProperties.java:47,53,58` — `if (!"legacy-rollback".equals(runtimeMode) && (!isRemote() || !cutoverComplete))` and `selected = "legacy-rollback".equals(runtimeMode) ? "Local" : "Remote"` (owner is source of truth).

## 2. Non-Rollback Runtime Leakage (must migrate before P4-LEGACY-005)

These are in **main** (non-conditional) and break normal path if `backend-judge-runtime` is removed prematurely:

| Symbol | Current Location (runtime) | App Normal-Path Consumers | Replacement Target (P4) |
|--------|---------------------------|---------------------------|--------------------------|
| `AppUuidGenerator` (`com.ulticode.app.uuid.AppUuidGenerator`) | `judge-runtime` (shared) | `ForumPostServiceImpl.java:5,50`, `DefaultProblemDetailPort.java:10,61`, `ProblemSnapshotServiceImpl.java:10,70`, `SolutionServiceImpl.java:7,48` (4 files) | `backend-common` `UuidGenerator` (dependency-free shared atoms) — `P4-LEGACY-002` |
| `SubmissionResultPushPort` alias (`com.ulticode.modules.queue.port.SubmissionResultPushPort extends com.ulticode.app.api.service.SubmissionResultPushPort`) | `judge-runtime/src/main/java/com/ulticode/modules/queue/port/SubmissionResultPushPort.java:13` | `WebSocketSubmissionResultPushAdapter.java:3,17` implements queue-local, `SubmissionJudgedWebSocketConsumer.java:4,22` injects `app.api.service.SubmissionResultPushPort` | Delete queue-local alias, use `app-api` contract directly — `P4-LEGACY-003` |
| `SubmissionStatusCodec` (`com.ulticode.modules.submission.codec.SubmissionStatusCodec`) | `judge-runtime` (wire conversion) | `SubmissionJudgedAchievementConsumer.java:8,35`, `SubmissionJudgedWebSocketConsumer.java:6,34` | Move to Owner or App private util, remove runtime import — `P4-LEGACY-004` |

Verification: `grep -rn "AppUuidGenerator\|SubmissionResultPushPort\|SubmissionStatusCodec" services/app/app-web/src/main/java` => 9 hits across 3 families; after migration expected 0 for normal source (compatibility closure excepted).

## 3. Legacy-Only Reachability (behind `legacy-rollback`)

File inventory (current source, not stale graph):

- **Conditional adapters**: `LocalSubmissionUserQueryAdapter.java:32`, `ProblemSubmissionStatsMapperAdapter.java:24`, `SubmissionReadAdapter.java:28`, `SubmissionStreakAdapter.java:16`, `SubmissionUserStatsMapperAdapter.java:22`, `LocalSubmissionAdjudicationReadAdapter.java:15`, `DefaultSubmissionGenerationReadAdapter.java:11`, `DefaultSubmissionProjection.java:46`, `SubmissionServiceImpl.java:50`, `DefaultSubmissionPerformanceStats.java:31`, `JdbcSubmissionStreakCalculator.java:25` — all `@ConditionalOnExpression("... == 'legacy-rollback'")`
- **Remote counterparts**: `RemoteSubmissionReadAdapter.java:17`, `RemoteSubmissionUserQueryAdapter.java:31`, `RemoteSubmissionStreakAdapter.java:13`, `RemoteSubmissionUserStatsAdapter.java:17`, `RemoteProblemSubmissionStatsAdapter.java:19`, `RemoteSubmissionGenerationReadAdapter.java:13`, `RemoteCodeExecutionPort.java:19` — `!= 'legacy-rollback'`
- **Mapper & Entity**: `services/app/app-web/src/main/java/com/ulticode/modules/submission/mapper/SubmissionMapper.java`, entity/DTOs under `modules/submission/dto/`, `mapper/`, `projection/`
- **Config**: `LegacySubmissionMapperScanConfig.java`, `AppJudgeCompatibilityConfiguration.java`, `SubmissionRoutingProperties.java`, `SubmissionUserQueryRoutingPort.java:12,37`
- **Submission local module**: `services/app/app-web/src/main/java/com/ulticode/modules/submission/**` — 60+ files (includes controller, DTOs, mappers, adapters) vs `services/app/modules/submission` private domain shallow (see P0-002)
- **Judge compatibility**: `services/app/app-web/src/main/java/com/ulticode/app/judge/AppJudgeCompatibilityAdapter.java`

Count: `grep -rn "legacy-rollback" services/app/app-web/src/main/java | wc -l` => 18 files (matches plan snapshot 18 legacy-conditioned), total Submission-related Java files in `app-web` => 59+ (via `find services/app/app-web/src/main/java/com/ulticode/modules/submission -name "*.java" | wc -l`).

## 4. Dependency Closure (POM + Mapper Scan + Private Module Residue)

- **POM**: `services/app/app-web/pom.xml:68` `backend-judge-runtime` — must be removed only after P4-LEGACY-005 proves zero normal-path imports (P4-LEGACY-010).
- **Mapper scan**: `LegacySubmissionMapperScanConfig.java` enables `SubmissionMapper` only in `legacy-rollback`; after local read deletion, scan itself can be deleted (P4-LEGACY-009).
- **Entity/DTO residue**: local `SubmissionDetailVO`, `SubmissionListItemVO`, `SubmissionVO`, `CreateSubmissionDTO` etc. under `modules/submission/dto` — some overlap with `submission-api` DTOs; deletion must preserve Owner contract.
- **Database**: local tables referenced by `SubmissionMapper` — contraction via new `V{timestamp}__Contract_Submission_Local_Artifacts.sql` only on disposable (P4-LEGACY-011), never production.

## 5. Safe Deletion Sequence (enforced order)

1. Migrate normal-path runtime leakage: `P4-LEGACY-002` (Uuid) -> `003` (Push alias) -> `004` (Codec) — parallelizable, each separate commit.
2. Prove closure: `P4-LEGACY-005` — `grep -rn backend-judge-runtime` in `app-web/src/main/java` excluding compatibility closure == 0, `mvn dependency:tree` shows only compatibility closure.
3. Close profile: `P4-LEGACY-006` — depends on `P4-LEGACY-001` (owner/expiry/floor) + `P4-LEGACY-005` + `P5-GATE-001`; change `devstack-manifest.sh:195-202` and `application-legacy-rollback.yml` to close default reachability, keep explicit flag with warning.
4. Delete implementations in order: `P4-LEGACY-007` (AppJudgeCompatibilityAdapter + RQueue) -> `008` (local Submission read adapters/projections) -> `009` (Mapper/entity/scan/residue) -> `010` (POM dependency) -> `011` (schema contraction disposable).
5. Gates: `P5-GATE-001` (profile/dependency) before close, `P5-GATE-004` before schema contraction.

Rollback per step: each step is a single commit revertible; full old release descriptor retained for rollback (not current binary carrying old impl).

## 6. Verification

- `grep -rn "legacy-rollback" services/app --include="*.java" | wc -l` => 18+ (current)
- `grep -rn "AppJudgeCompatibilityConfiguration\|LegacySubmissionMapperScanConfig" services/app` => 2 configs
- `grep -rn "backend-judge-runtime" services/app/app-web/pom.xml` => 1 (to be zero after P4-010)
- `find services/app/app-web/src/main/java/com/ulticode/modules/submission -name "*.java" | wc -l` => ~60
- `check_index_coverage` on `services/app/app-web/src/main/java/com/ulticode/app/config/**`, `services/app/app-web/src/main/java/com/ulticode/modules/submission/**`, `judge-runtime/src/main/java/com/ulticode/modules/**` — stale graph disclosure applies, direct read authoritative
- No deletion of WebSocket/achievement normal path mistaken for legacy (explicitly excluded via §2 leakage table).

## Evidence Level

Repository Implemented. Deletion sequence is repository/disposable verifiable; production schema contraction excluded (P4-011).

