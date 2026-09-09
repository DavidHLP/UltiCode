#!/usr/bin/env bash
set -euo pipefail

# Source-only ownership and seam assertions. The architecture gate owns
# execution policy; this child owns the repository's owner/module source shape.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

fail() {
  echo "owner-architecture-source-contract: FAIL: $*" >&2
  exit 1
}

# shellcheck source=scripts/test/lib/assertions.sh
source "$ROOT_DIR/scripts/test/lib/assertions.sh"

assert_absent() {
  local file="$1"
  [[ ! -e "$ROOT_DIR/$file" ]] || fail "$file must be absent after compatibility retirement"
}

for file in \
  scripts/runbooks/notification-schema-cutover.sh \
  scripts/runbooks/submission-schema-cutover.sh \
  scripts/test/submission-backfill-contract.sh \
  scripts/runbooks/owner-user-profile-backfill.sh \
  services/app/app-web/src/main/java/com/ulticode/app/user/port/UserFactsProjection.java \
  services/app/app-web/src/main/java/com/ulticode/app/user/port/UserDirectoryProjection.java \
  services/app/app-web/src/main/java/com/ulticode/app/user/port/DefaultUserFactsReadProjection.java \
  services/api/submission-api/src/main/java/com/ulticode/submission/api/service/SubmissionReadPort.java \
  services/api/submission-api/src/main/java/com/ulticode/submission/api/service/SubmissionIntakePort.java \
  services/api/submission-api/src/main/java/com/ulticode/submission/api/service/SubmissionVerdictWritePort.java \
  services/api/submission-api/src/main/java/com/ulticode/submission/api/service/SubmissionReconciliationReadPort.java \
  services/api/notification-api/src/main/java/com/ulticode/notification/api/service/NotificationReconciliationReadPort.java \
  services/api/judge-api/src/main/java/com/ulticode/judge/api/JudgeRunService.java \
  services/api/judge-api/src/main/java/com/ulticode/judge/api/JudgeRunCommand.java \
  services/api/judge-api/src/main/java/com/ulticode/judge/api/JudgeRunResult.java \
  services/api/app-api/src/main/java/com/ulticode/app/api/service/ProblemTitleLookupPort.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/port/adapter/RemoteCodeExecutionPort.java \
  services/judge/src/main/java/com/ulticode/judge/provider/CodeExecutionProvider.java \
  services/judge/src/main/java/com/ulticode/judge/adapter/RemoteSubmissionVerdictWritePort.java \
  services/submission/src/main/java/com/ulticode/submission/port/adapter/ProblemTitleLookupDubboAdapter.java \
  services/submission/src/main/java/com/ulticode/submission/dubbo/provider/SubmissionAdministrationProvider.java \
  services/submission/src/main/java/com/ulticode/submission/dubbo/provider/SubmissionReconciliationReadProvider.java \
  services/submission/src/main/java/com/ulticode/modules/submission/mapper/SubmissionReconciliationReadMapper.java \
  services/submission/src/main/java/com/ulticode/submission/admin/SubmissionRejudgeService.java \
  services/submission/src/main/java/com/ulticode/submission/security/InternalDelegationAssertionVerifier.java \
  services/submission/src/main/java/com/ulticode/submission/idempotency/SubmissionCommandReceiptExecutor.java \
  services/submission/src/main/java/com/ulticode/submission/dubbo/provider/SubmissionIntakeProvider.java \
  services/submission/src/main/java/com/ulticode/submission/dubbo/provider/SubmissionVerdictWriteProvider.java \
  services/notification/src/main/java/com/ulticode/notification/inbox/NotificationIntegrationInboxBridge.java \
  services/notification/src/main/java/com/ulticode/notification/dubbo/provider/NotificationReconciliationReadProvider.java \
  services/notification/src/main/java/com/ulticode/modules/notification/mapper/NotificationReconciliationReadMapper.java \
  services/admin/src/main/java/com/ulticode/modules/reconciliation/port/adapter/DubboSubmissionReconciliationReadAdapter.java \
  services/admin/src/main/java/com/ulticode/modules/reconciliation/port/adapter/DubboNotificationReconciliationReadAdapter.java \
  services/admin/src/main/java/com/ulticode/modules/reconciliation/OwnerReconciler.java; do
  [[ -f "$ROOT_DIR/$file" ]] || fail "missing architecture source: $file"
done

# SVC-006: Admin user projections cross Auth/App only through the shared deep
# aggregation Module; the HTTP projection owns only VO/local concerns.
contains services/admin/src/main/java/com/ulticode/modules/admin/projection/DefaultAdminUserProjection.java \
  'private final AdminUserEnricher userEnricher;'
not_contains services/admin/src/main/java/com/ulticode/modules/admin/projection/DefaultAdminUserProjection.java \
  'private AccountQueryService'
not_contains services/admin/src/main/java/com/ulticode/modules/admin/projection/DefaultAdminUserProjection.java \
  'private UserProfileQueryService'

contains services/app/app-web/src/main/java/com/ulticode/app/user/port/UserFactsProjection.java \
  'Map<String, UserFactView> findByIds'
contains services/app/app-web/src/main/java/com/ulticode/app/user/port/UserDirectoryProjection.java \
  'UserSummaryView selectById'
not_contains services/app/app-web/src/main/java/com/ulticode/app/user/port/UserFactsProjection.java \
  'UserSummaryView selectBy'
not_contains services/app/app-web/src/main/java/com/ulticode/app/user/port/UserFactsProjection.java \
  'selectActiveUsers'
contains services/app/app-web/src/main/java/com/ulticode/app/user/port/DefaultUserFactsReadProjection.java \
  'implements UserDirectoryProjection, UserFactsProjection'

contains services/api/submission-api/src/main/java/com/ulticode/submission/api/service/SubmissionReadPort.java \
  'List<SubmissionVO> toVOs(Collection<String> submissionIds)'
contains services/app/app-web/src/main/java/com/ulticode/modules/contest/projection/DefaultContestProjection.java \
  'submissionProjection.toVOs(contestSubmissionMapper'
contains services/app/app-web/src/test/resources/application.yml 'use-judge-outbox: true'
contains services/app/app-web/src/test/resources/application.yml 'use-generation-fence: true'
contains services/app/app-web/src/test/resources/application.yml 'use-port: true'

# SVC-001: synchronous preview execution is a real App -> Judge seam. App
# controllers must not regain a concrete Docker runtime dependency.
contains services/app/app-web/src/main/java/com/ulticode/modules/submission/controller/ProblemSubmissionController.java \
  'private final InteractiveCodeRunner codeExecutionPort;'
not_contains services/app/app-web/src/main/java/com/ulticode/modules/submission/controller/ProblemSubmissionController.java \
  'CodeExecutionService'
contains services/app/app-web/src/main/java/com/ulticode/BackendAppApplication.java \
  '@SpringBootApplication(scanBasePackages = {'
contains services/app/app-web/src/main/java/com/ulticode/modules/submission/port/adapter/RemoteCodeExecutionPort.java \
  '@DubboReference(group = "backend-judge"'
contains services/api/judge-api/src/main/java/com/ulticode/judge/api/JudgeRunService.java \
  'RpcResult<JudgeRunResult> execute(JudgeRunCommand command);'
contains services/judge/src/main/java/com/ulticode/judge/provider/CodeExecutionProvider.java \
  '@DubboService(group = "backend-judge"'
contains services/judge-runtime/src/main/java/com/ulticode/modules/submission/service/CodeExecutionService.java \
  "'\${app.runtime.mode:dev-lite}' == 'legacy-rollback'"

# P4-LEGACY-006/007: current launchers and binaries fail closed for the
# retired rollback mode; the App compatibility sources are now absent.
not_contains services/app/app-web/src/main/java/com/ulticode/BackendAppApplication.java \
  'AppJudgeCompatibilityConfiguration'
not_contains services/app/app-web/src/main/resources/application.yml \
  'judge-compatibility-enabled'
not_contains ecosystem.config.cjs 'APP_FEATURES_JUDGE_COMPATIBILITY_ENABLED'
assert_absent services/app/app-web/src/main/java/com/ulticode/app/judge/AppJudgeCompatibilityConfiguration.java
assert_absent services/app/app-web/src/main/java/com/ulticode/app/judge/AppJudgeCompatibilityAdapter.java
not_contains scripts/dev/up.sh 'legacy-rollback'
not_contains services/platform/judge-config/src/main/java/com/ulticode/modules/submission/config/FlagCombinationValidator.java \
  'legacy-rollback'
contains services/platform/judge-config/src/main/java/com/ulticode/modules/submission/config/FlagCombinationValidator.java \
  'expected dev-lite, dev-full or external-full.'

# SVC-002: cross-process mutation and problem lookup contracts expose only
# the capabilities each consumer actually uses.
contains services/judge/src/main/java/com/ulticode/judge/adapter/RemoteSubmissionVerdictWritePort.java \
  'implements SubmissionVerdictWritePort'
contains services/submission/src/main/java/com/ulticode/submission/port/adapter/ProblemTitleLookupDubboAdapter.java \
  'implements ProblemTitleLookupPort'
not_contains services/judge/src/main/java/com/ulticode/judge/adapter/RemoteSubmissionVerdictWritePort.java \
  'UnsupportedOperationException'
not_contains services/submission/src/main/java/com/ulticode/submission/port/adapter/ProblemTitleLookupDubboAdapter.java \
  'UnsupportedOperationException'
assert_absent services/api/submission-api/src/main/java/com/ulticode/submission/api/service/SubmissionWritePort.java
assert_absent services/api/submission-api/src/main/java/com/ulticode/submission/api/service/SubmissionAnalyticsPort.java
assert_absent services/submission/src/main/java/com/ulticode/submission/dubbo/provider/SubmissionWriteProvider.java
contains services/docs/CONTRACT_COMPAT_GATE.md \
  'App provider first → Submission consumer second'
contains services/docs/CONTRACT_COMPAT_GATE.md \
  'Submission consumer first → App provider second'
not_contains services/app/app-web/src/main/java/com/ulticode/modules/submission/controller/ProblemSubmissionController.java \
  'import com.ulticode.submission.api.service.SubmissionWritePort;'
contains services/app/app-web/src/main/java/com/ulticode/modules/submission/port/adapter/RemoteSubmissionWritePort.java \
  'implements SubmissionIntakePort'
not_contains services/app/app-web/src/main/java/com/ulticode/modules/submission/port/adapter/RemoteSubmissionWritePort.java \
  'SubmissionVerdictWritePort'
for stale_app_mutation in \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/port/DefaultSubmissionWritePort.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/port/SubmissionWriteRoutingPort.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/port/DefaultSubmissionFencePort.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/port/SubmissionFenceRoutingPort.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/port/adapter/RemoteSubmissionFencePort.java \
  services/app/app-web/src/main/java/com/ulticode/modules/queue/outbox/dispatcher/JudgeOutboxDispatcher.java \
  services/app/app-web/src/main/java/com/ulticode/modules/queue/outbox/shadow/OutboxShadowComparator.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/reaper/JudgingLeaseReaper.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/result/SubmissionResultDispatcher.java; do
  [[ ! -e "$ROOT_DIR/$stale_app_mutation" ]] \
    || fail "stale App Submission mutation component remains: $stale_app_mutation"
done
not_contains services/judge-runtime/src/main/java/com/ulticode/modules/queue/processor/DefaultJudgeAttemptExecutor.java \
  'import com.ulticode.submission.api.service.SubmissionWritePort;'
for stale_contract in \
  services/judge/src/main/java/com/ulticode/judge/adapter/RemoteSubmissionWritePort.java \
  services/submission/src/main/java/com/ulticode/submission/port/adapter/ProblemAdminReadDubboAdapter.java; do
  [[ ! -e "$ROOT_DIR/$stale_contract" ]] || fail "stale broad contract remains: $stale_contract"
done

# P1-SEAM-001: public App contracts must not retain dead ingestion/execution
# types or a default method that only throws at runtime.
for retired_app_contract in \
  services/api/app-api/src/main/java/com/ulticode/app/api/event/FollowEventIngestionPort.java \
  services/api/app-api/src/main/java/com/ulticode/app/api/event/FollowDomainEvent.java \
  services/api/app-api/src/main/java/com/ulticode/app/api/service/JudgeExecutionPort.java \
  services/api/app-api/src/main/java/com/ulticode/app/api/service/AchievementTriggerPort.java; do
  [[ ! -e "$ROOT_DIR/$retired_app_contract" ]] \
    || fail "retired App API contract remains: $retired_app_contract"
done
not_contains services/api/app-api/src/main/java/com/ulticode/app/api/service/ContestLiveRankingReadPort.java \
  'UnsupportedOperationException'

# P1-DATA-001/P4-LEGACY-008/P4-LEGACY-009: current Submission reads cross the
# owner contracts. App-local read implementations and persistence residue are
# deleted; App-owned contest persistence remains separate from Submission.
for contraction_file in \
  init-db/flyway-contraction.conf \
  init-db/migrations/V20260830200000__Create_Owner_Contraction_Proof.sql \
  init-db/migrations/contraction/V20260830200100__Retire_Legacy_Owner_Tables.sql \
  scripts/runbooks/owner-schema-contraction.sh \
  scripts/test/owner-schema-contraction-contract.sh \
  services/api/submission-api/src/main/java/com/ulticode/submission/api/service/SubmissionAdjudicationReadPort.java \
  services/api/submission-api/src/main/java/com/ulticode/submission/api/dto/SubmissionAdjudicationFact.java \
  services/submission/src/main/java/com/ulticode/submission/dubbo/provider/SubmissionAdjudicationReadProvider.java \
  services/submission/src/main/java/com/ulticode/submission/dubbo/provider/ProblemSubmissionStatsProvider.java \
  services/app/app-web/src/main/java/com/ulticode/modules/contest/port/adapter/RemoteSubmissionAdjudicationReadAdapter.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/port/adapter/RemoteProblemSubmissionStatsAdapter.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/port/adapter/RemoteSubmissionReadAdapter.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/port/adapter/RemoteSubmissionStreakAdapter.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/port/adapter/RemoteSubmissionUserStatsAdapter.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/port/adapter/RemoteSubmissionUserQueryAdapter.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/port/RemoteSubmissionGenerationReadAdapter.java; do
  [[ -f "$ROOT_DIR/$contraction_file" ]] || fail "missing P1-DATA source: $contraction_file"
done
contains services/submission/src/main/java/com/ulticode/submission/dubbo/provider/SubmissionAdjudicationReadProvider.java \
  '@DubboService(group = "backend-submission", version = "1.0.0")'
contains services/submission/src/main/java/com/ulticode/submission/dubbo/provider/ProblemSubmissionStatsProvider.java \
  '@DubboService(group = "backend-submission", version = "1.1.0")'
not_contains services/submission/src/main/java/com/ulticode/submission/dubbo/provider/ProblemSubmissionStatsProvider.java \
  'ProblemFactsPort'
not_contains services/submission/src/main/java/com/ulticode/submission/dubbo/provider/ProblemSubmissionStatsProvider.java \
  '@DubboReference'
contains services/submission/src/main/java/com/ulticode/submission/dubbo/provider/SubmissionUserStatsProvider.java \
  '@DubboService(group = "backend-submission", version = "1.1.0")'
contains services/app/app-web/src/main/java/com/ulticode/modules/contest/port/adapter/RemoteSubmissionAdjudicationReadAdapter.java \
  'backend-submission'
contains services/app/app-web/src/main/java/com/ulticode/modules/submission/port/adapter/RemoteProblemSubmissionStatsAdapter.java \
  'backend-submission'
assert_absent services/app/app-web/src/main/java/com/ulticode/app/config/LegacySubmissionMapperScanConfig.java
assert_absent services/app/app-web/src/main/java/com/ulticode/modules/submission/mapper/SubmissionMapper.java
assert_absent services/app/modules/submission/src/main/java/com/ulticode/modules/submission/entity/Submission.java
assert_absent services/app/modules/submission/pom.xml
not_contains services/app/app-web/src/main/java/com/ulticode/app/config/MapperScanConfig.java \
  'com.ulticode.modules.submission.mapper'
not_contains services/app/app-web/src/main/java/com/ulticode/app/config/MapperScanConfig.java \
  'com.ulticode.modules.submission.outbox.mapper'
not_contains services/app/app-web/src/main/java/com/ulticode/app/config/MapperScanConfig.java \
  'com.ulticode.modules.submission.result'
not_contains services/app/app-web/src/main/java/com/ulticode/modules/problem/mapper/ProblemMapper.java \
  'FROM submissions'
not_contains services/app/app-web/src/main/java/com/ulticode/modules/problem/mapper/ProblemTagRelationMapper.java \
  'JOIN submissions'
not_contains services/app/app-web/src/main/java/com/ulticode/modules/contest/mapper/ContestAdjudicationReceiptMapper.java \
  'JOIN submissions'
not_contains services/app/app-web/src/main/java/com/ulticode/modules/notification/intent/SubmissionCompletedIntent.java \
  'import com.ulticode.modules.submission.entity.Submission'
app_java="$ROOT_DIR/services/app/app-web/src/main/java"
mapfile -t app_submission_sql_sources < <(grep -RIl --include='*.java' \
  -E 'FROM[[:space:]]+submissions|JOIN[[:space:]]+submissions|INSERT[[:space:]]+INTO[[:space:]]+submissions|UPDATE[[:space:]]+submissions|DELETE[[:space:]]+FROM[[:space:]]+submissions' \
  "$app_java" || true)
for app_submission_sql_source in "${app_submission_sql_sources[@]}"; do
  fail "normal App source contains direct Submission SQL: $app_submission_sql_source"
done
contains scripts/dev/migrate.sh \
  'OWNER_SCHEMA_CONTRACTION_CONFIRM=I_UNDERSTAND_OWNER_SCHEMA_CONTRACTION'
contains scripts/dev/migrate.sh \
  'OWNER_SCHEMA_CONTRACTION_BACKUP_CONFIRM=I_HAVE_VERIFIED_OWNER_CONTRACTION_BACKUP'
contains scripts/dev/migrate.sh \
  'OWNER_SCHEMA_CONTRACTION_QUIESCE_CONFIRM=I_HAVE_QUIESCED_OWNER_WRITERS'
contains scripts/dev/migrate.sh 'flyway-contraction.conf'
contains scripts/runbooks/owner-schema-contraction.sh 'PRECHECK PASS'
contains scripts/runbooks/owner-schema-contraction.sh 'CONTRACT PASS'
contains scripts/runbooks/owner-schema-contraction.sh 'OWNER_SCHEMA_CONTRACTION_BACKUP_REFERENCE'
contains init-db/migrations/contraction/V20260830200100__Retire_Legacy_Owner_Tables.sql 'backup_confirmed'
contains init-db/migrations/contraction/V20260830200100__Retire_Legacy_Owner_Tables.sql 'writers_quiesced_at'
contains scripts/test/owner-schema-contraction-contract.sh 'forward contraction and owner preservation: PASS'
for local_submission_source in \
  services/app/app-web/src/main/java/com/ulticode/modules/contest/port/adapter/LocalSubmissionAdjudicationReadAdapter.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/port/DefaultSubmissionGenerationReadAdapter.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/port/adapter/LocalSubmissionUserQueryAdapter.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/port/adapter/ProblemSubmissionStatsMapperAdapter.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/port/adapter/SubmissionReadAdapter.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/port/adapter/SubmissionStreakAdapter.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/port/adapter/SubmissionUserStatsMapperAdapter.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/port/SubmissionUserQueryRoutingPort.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/port/SubmissionUserStatsPort.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/port/ProblemSubmissionStatsPort.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/config/SubmissionRoutingProperties.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/projection/DefaultSubmissionProjection.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/projection/SubmissionProjection.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/service/SubmissionService.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/service/impl/SubmissionServiceImpl.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/stats/DefaultSubmissionPerformanceStats.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/stats/JdbcSubmissionStreakCalculator.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/stats/SubmissionPerformanceStats.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/stats/SubmissionStreakCalculator.java; do
  assert_absent "$local_submission_source"
done
assert_absent services/app/app-web/src/main/java/com/ulticode/modules/submission/dto
for remote_submission_source in \
  services/app/app-web/src/main/java/com/ulticode/modules/contest/port/adapter/RemoteSubmissionAdjudicationReadAdapter.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/port/RemoteSubmissionGenerationReadAdapter.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/port/adapter/RemoteSubmissionUserQueryAdapter.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/port/adapter/RemoteProblemSubmissionStatsAdapter.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/port/adapter/RemoteSubmissionReadAdapter.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/port/adapter/RemoteSubmissionStreakAdapter.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/port/adapter/RemoteSubmissionUserStatsAdapter.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/port/adapter/RemoteCodeExecutionPort.java; do
  not_contains "$remote_submission_source" 'legacy-rollback'
done

echo "Owner architecture source contract: PASS"
