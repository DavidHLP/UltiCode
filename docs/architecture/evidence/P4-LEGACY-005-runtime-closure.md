# P4-LEGACY-005 app-web → judge-runtime Closure

> status: CLOSURE PROVEN
> source head: c344f6268084a893f0bde871da21e5130a331207 plus Batch B migrations
> scope: repository source and Maven graph; rollback-only closure remains until P4-LEGACY-010

## Normal-path migration results

| Previous runtime symbol | Current source | Result |
|---|---|---|
| `com.ulticode.app.uuid.AppUuidGenerator` | `com.ulticode.common.uuid.UuidGenerator` in Forum, Problem, Snapshot, Solution | zero AppUuidGenerator imports; App-owned adapter is local to app-web |
| `com.ulticode.modules.queue.port.SubmissionResultPushPort` | `com.ulticode.app.api.service.SubmissionResultPushPort` in WebSocket adapter | queue-local alias deleted; app-api contract remains |
| `com.ulticode.modules.submission.codec.SubmissionStatusCodec` | same package class moved to `backend-common`; Judge runtime and Submission owner duplicates deleted | App imports resolve through backend-common, not judge-runtime |
| four misplaced app-api interfaces | runtime-private ports / App moderation port | app-api no longer exports JudgeConfigPort, JudgeEnqueuePort, VerdictResolvePort, ModerationUserReadPort |

## Current reachability

- **Normal App code**: no direct import of `AppUuidGenerator`, queue-local push alias, or runtime-owned codec.
- **Compatibility closure**: `AppJudgeCompatibilityAdapter`/`AppJudgeCompatibilityConfiguration` still import queue/runtime classes, and local Submission read classes remain explicitly guarded by `legacy-rollback`. These are the only intended runtime closure and are deleted later in P4-LEGACY-007..010.
- **POM**: `services/app/app-web/pom.xml` still declares `backend-judge-runtime` until compatibility deletion; removing it now would break the rollback profile and violates the prescribed order.

## Verification

- Source search: `grep -R "AppUuidGenerator\|CommonUuidGeneratorAdapter\|com.ulticode.app.uuid\|modules.queue.port.SubmissionResultPushPort" services --include='*.java' --exclude-dir=target` => no production reference (historical evidence files intentionally mention symbols).
- App source search: `grep -R "modules.submission.codec.SubmissionStatusCodec" services/app/app-web/src/main/java` resolves only the common-package FQN; duplicate runtime codec is absent and common module owns the class.
- Retired files absent: `services/judge-runtime/.../AppUuidGenerator.java`, `CommonUuidGeneratorAdapter.java`, queue-local `SubmissionResultPushPort.java`, runtime `SubmissionStatusCodec.java`, submission duplicate `SubmissionStatusCodec.java`.
- Maven closure check: app-web keeps one explicit `backend-judge-runtime` dependency for compatibility closure; P4-LEGACY-010 removes it after P4-LEGACY-007.
- Focused source gate: `scripts/test/api-contract-boundary-contract.sh` => PASS, including `app-api ownership catalog: PASS (71 interfaces)`.
- Full App module test attempted with `rtk mise exec java@zulu-17.68.203.0 -- ./mvnw -pl app/app-web -am test -B`; any result is recorded in task ledger. Java 17 is selected explicitly.

## Evidence level

Repository Implemented. Compatibility closure is repository-only; no production mixed-version or rollback claim.
