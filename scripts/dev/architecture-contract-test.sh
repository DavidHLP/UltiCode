#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

fail() {
  echo "Architecture contract failed: $*" >&2
  exit 1
}

contains() {
  local file="$1" text="$2"
  [[ -f "$ROOT_DIR/$file" ]] || fail "missing guarded file: $file"
  grep -F -- "$text" "$ROOT_DIR/$file" >/dev/null \
    || fail "$file does not contain: $text"
}

not_contains() {
  local file="$1" text="$2"
  [[ -f "$ROOT_DIR/$file" ]] || fail "missing guarded file: $file"
  ! grep -F -- "$text" "$ROOT_DIR/$file" >/dev/null \
    || fail "$file contains stale or bypass text: $text"
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
  services/api/submission-api/src/main/java/com/ulticode/submission/api/service/SubmissionWritePort.java \
  services/api/submission-api/src/main/java/com/ulticode/submission/api/service/SubmissionReconciliationReadPort.java \
  services/api/notification-api/src/main/java/com/ulticode/notification/api/service/NotificationReconciliationReadPort.java \
  services/api/app-api/src/main/java/com/ulticode/app/api/service/CodeExecutionPort.java \
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
  services/submission/src/main/java/com/ulticode/submission/dubbo/provider/SubmissionWriteProvider.java \
  services/app/app-web/src/main/java/com/ulticode/app/judge/AppJudgeCompatibilityConfiguration.java \
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

bash "$ROOT_DIR/scripts/dev/devstack-manifest-test.sh"

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
contains services/app/app-web/src/main/java/com/ulticode/app/judge/AppJudgeCompatibilityConfiguration.java \
  'app.runtime.mode:dev-lite'
contains services/app/app-web/src/test/resources/application.yml 'use-judge-outbox: true'
contains services/app/app-web/src/test/resources/application.yml 'use-generation-fence: true'
contains services/app/app-web/src/test/resources/application.yml 'use-port: true'

# SVC-001: synchronous preview execution is a real App -> Judge seam. App
# controllers must not regain a concrete Docker runtime dependency.
contains services/app/app-web/src/main/java/com/ulticode/modules/submission/controller/ProblemSubmissionController.java \
  'private final CodeExecutionPort codeExecutionPort;'
not_contains services/app/app-web/src/main/java/com/ulticode/modules/submission/controller/ProblemSubmissionController.java \
  'CodeExecutionService'
contains services/app/app-web/src/main/java/com/ulticode/BackendAppApplication.java \
  '@SpringBootApplication(scanBasePackages = {'
contains services/app/app-web/src/main/java/com/ulticode/modules/submission/port/adapter/RemoteCodeExecutionPort.java \
  '@DubboReference(group = "backend-judge"'
contains services/judge/src/main/java/com/ulticode/judge/provider/CodeExecutionProvider.java \
  '@DubboService(group = "backend-judge"'
contains services/judge-runtime/src/main/java/com/ulticode/modules/submission/service/CodeExecutionService.java \
  "'\${app.runtime.mode:dev-lite}' == 'legacy-rollback'"
contains services/app/app-web/src/main/java/com/ulticode/modules/submission/port/adapter/RemoteCodeExecutionPort.java \
  "'\${app.runtime.mode:dev-lite}' != 'legacy-rollback'"

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
contains services/api/submission-api/src/main/java/com/ulticode/submission/api/service/SubmissionWritePort.java \
  '@Deprecated(forRemoval = true)'
contains services/submission/src/main/java/com/ulticode/submission/dubbo/provider/SubmissionWriteProvider.java \
  'implements SubmissionWritePort'
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

# SVC-005: every backend image in the release matrix must remain selectable
# by both manual deploy and rollback entry points.
mapfile -t release_services < <(python3 - "$ROOT_DIR/.github/services-matrix.json" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as source:
    services = json.load(source)
for service in services:
    name = service.get("name", "")
    if name.startswith("backend-"):
        print(name)
PY
)
(( ${#release_services[@]} > 0 )) || fail "services matrix contains no backend runtimes"
for release_service in "${release_services[@]}"; do
  contains .github/workflows/cd-deploy.yml "          - $release_service"
  contains .github/workflows/cd-rollback.yml "\"$release_service\""
  contains .github/actions/host-health/action.yml "      $release_service "
  otlp_endpoint_count="$(awk -v service="$release_service" '
    $0 == "  " service ":" { inside = 1; next }
    inside && /^  [^ ]/ { exit }
    inside && /MANAGEMENT_OTLP_TRACING_ENDPOINT=/ { count++ }
    END { print count + 0 }
  ' "$ROOT_DIR/docker-compose.prod.yml")"
  [[ "$otlp_endpoint_count" -eq 1 ]] \
    || fail "production Compose service $release_service must have exactly one OTLP endpoint"
done

not_contains scripts/dev/doctor.sh 'pm2 start ecosystem.config.cjs'

# The root-level start/stop compatibility aliases are deleted; only the
# pitstop Windows adapter (still consumed by pitstop.yaml) remains at the root.
for stale_alias in scripts/start.sh scripts/stop.sh scripts/start.bat scripts/stop.bat; do
  [[ ! -e "$ROOT_DIR/$stale_alias" ]] || fail "stale root-level alias still present: $stale_alias"
done
contains scripts/pitstop-start-backend.ps1 'scripts/dev/up.sh'
contains scripts/dev/stop.sh 'pm2 delete'
not_contains scripts/pitstop-start-backend.ps1 'mvn spring-boot:run'
not_contains services/admin/src/main/java/com/ulticode/admin/security/jwt/AccountReadAdapter.java 'UserFactsProjection'

# Security and repair regressions: keep the executable boundaries aligned with
# the source-level fixes so future migrations cannot silently reopen them.
for csrf_config in \
  services/auth/src/main/java/com/ulticode/auth/security/AuthSecurityConfig.java \
  services/app/app-web/src/main/java/com/ulticode/app/security/AppSecurityConfig.java \
  services/admin/src/main/java/com/ulticode/admin/security/AdminSecurityConfig.java \
  services/notification/src/main/java/com/ulticode/notification/security/NotificationSecurityConfig.java; do
  contains "$csrf_config" 'new CookieCsrfFilter()'
done
for stale_csrf in \
  services/auth/src/main/java/com/ulticode/auth/security/csrf/CsrfValidationFilter.java \
  services/auth/src/main/java/com/ulticode/auth/security/csrf/CsrfService.java; do
  [[ ! -e "$ROOT_DIR/$stale_csrf" ]] || fail "stale Auth-only CSRF implementation remains: $stale_csrf"
done
for route_config in \
  services/app/app-web/src/main/java/com/ulticode/app/security/AppSecurityConfig.java \
  services/admin/src/main/java/com/ulticode/admin/security/AdminSecurityConfig.java \
  services/notification/src/main/java/com/ulticode/notification/security/NotificationSecurityConfig.java \
  services/app/app-web/src/test/java/com/ulticode/app/security/AppTestSecurityConfig.java; do
  not_contains "$route_config" '.anyRequest().permitAll()'
done
contains services/app/app-web/src/main/java/com/ulticode/app/security/AppSecurityConfig.java \
  '.anyRequest().authenticated()'
contains services/admin/src/main/java/com/ulticode/admin/security/AdminSecurityConfig.java \
  '.requestMatchers("/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")'
contains services/admin/src/main/java/com/ulticode/admin/security/AdminSecurityConfig.java \
  '.anyRequest().denyAll()'
contains services/notification/src/main/java/com/ulticode/notification/security/NotificationSecurityConfig.java \
  '.anyRequest().authenticated()'
for shared_jwt in \
  services/platform/web-security/src/main/java/com/ulticode/websecurity/jwt/AccessTokenVerifier.java \
  services/platform/web-security/src/main/java/com/ulticode/websecurity/jwt/AccessTokenClaims.java \
  services/platform/web-security/src/main/java/com/ulticode/websecurity/jwt/JwtAuthenticationFilter.java \
  services/platform/web-security/src/main/java/com/ulticode/websecurity/jwt/JwksPublicKeyProvider.java \
  services/platform/web-security/src/main/java/com/ulticode/websecurity/jwt/ResourceServerJwtVerifier.java; do
  [[ -f "$ROOT_DIR/$shared_jwt" ]] || fail "missing shared JWT security source: $shared_jwt"
done
for stale_jwt in \
  services/auth/src/main/java/com/ulticode/auth/security/jwt/JwtAuthenticationFilter.java \
  services/app/app-web/src/main/java/com/ulticode/app/security/jwt/JwtAuthenticationFilter.java \
  services/app/app-web/src/main/java/com/ulticode/app/security/jwt/JwksPublicKeyProvider.java \
  services/app/app-web/src/main/java/com/ulticode/app/security/jwt/ResourceServerJwtVerifier.java \
  services/admin/src/main/java/com/ulticode/admin/security/jwt/JwtAuthenticationFilter.java \
  services/admin/src/main/java/com/ulticode/admin/security/jwt/JwksPublicKeyProvider.java \
  services/admin/src/main/java/com/ulticode/admin/security/jwt/ResourceServerJwtVerifier.java \
  services/notification/src/main/java/com/ulticode/notification/security/jwt/JwtAuthenticationFilter.java \
  services/notification/src/main/java/com/ulticode/notification/security/jwt/JwksPublicKeyProvider.java \
  services/notification/src/main/java/com/ulticode/notification/security/jwt/ResourceServerJwtVerifier.java; do
  [[ ! -e "$ROOT_DIR/$stale_jwt" ]] || fail "stale owner-local JWT security source remains: $stale_jwt"
done
contains docker-compose.prod.yml 'JWT_JWKS_URI=https://backend-auth:9101/auth/jwks'
not_contains docker-compose.prod.yml 'JWT_JWKS_URI=http://backend-auth:9101/auth/jwks'
# P0-SEC-005: transport assertions are RS256-only. Private signing material
# stays in Admin; every verifier receives only public key material.
for delegation_source in services/admin/src/main/java/com/ulticode/admin/security/DelegationAssertionSigner.java services/auth/src/main/java/com/ulticode/auth/security/InternalDelegationAssertionVerifier.java services/app/app-web/src/main/java/com/ulticode/app/security/InternalDelegationAssertionVerifier.java services/notification/src/main/java/com/ulticode/notification/security/InternalDelegationAssertionVerifier.java services/submission/src/main/java/com/ulticode/submission/security/InternalDelegationAssertionVerifier.java; do
  not_contains "$delegation_source" 'Keys.hmacShaKeyFor'
done
contains services/admin/src/main/java/com/ulticode/admin/security/DelegationAssertionSigner.java 'Jwts.SIG.RS256'
contains services/admin/src/main/resources/application.yml 'INTERNAL_DELEGATION_PRIVATE_KEY'
contains services/admin/src/main/resources/application.yml 'BOOTSTRAP_DELEGATION_PRIVATE_KEY'
for delegation_config in services/auth/src/main/resources/application.yml services/app/app-web/src/main/resources/application.yml services/notification/src/main/resources/application.yml; do
  not_contains "$delegation_config" 'INTERNAL_DELEGATION_SECRET'
  contains "$delegation_config" 'INTERNAL_DELEGATION_PUBLIC_KEY'
done
not_contains services/auth/src/main/resources/application.yml 'BOOTSTRAP_DELEGATION_SECRET'
contains services/auth/src/main/resources/application.yml 'BOOTSTRAP_DELEGATION_PUBLIC_KEY'
not_contains services/admin/src/main/resources/application.yml 'INTERNAL_DELEGATION_SECRET'
not_contains services/admin/src/main/resources/application.yml 'BOOTSTRAP_DELEGATION_SECRET'
not_contains docker-compose.prod.yml 'INTERNAL_DELEGATION_SECRET='
contains docker-compose.prod.yml 'INTERNAL_DELEGATION_PRIVATE_KEY='
contains docker-compose.prod.yml 'BOOTSTRAP_DELEGATION_PRIVATE_KEY='
contains docker-compose.prod.yml 'INTERNAL_DELEGATION_PUBLIC_KEY='
contains docker-compose.prod.yml 'BOOTSTRAP_DELEGATION_PUBLIC_KEY='
replay_controller="$ROOT_DIR/services/app/app-web/src/main/java/com/ulticode/modules/event/replay/EventReplayController.java"
replay_annotations="$(grep -c '@PreAuthorize' "$replay_controller" || true)"
[[ "$replay_annotations" -eq 6 ]] || fail "EventReplayController must protect all six operations"
contains docker/redis/generate-users-acl.sh '~stream:integration'
contains docker/redis/users.acl '~stream:integration'
bash "$ROOT_DIR/scripts/test/redis-acl-contract.sh"
bash "$ROOT_DIR/scripts/test/ssh-host-identity-contract.sh"
bash "$ROOT_DIR/scripts/test/nacos-security-contract.sh"
bash "$ROOT_DIR/scripts/test/submission-backfill-contract.sh"
bash "$ROOT_DIR/scripts/test/owner-schema-contraction-contract.sh"
contains services/auth/src/main/java/com/ulticode/auth/adapter/in/web/JwksController.java 'public Map<String, Object> getJwks()'
contains services/auth/src/main/java/com/ulticode/auth/security/InternalDelegationAssertionVerifier.java 'backend-auth'
contains services/app/app-web/src/main/java/com/ulticode/app/dubbo/provider/ProblemAdministrationProvider.java 'AdminActorAuthorizer actorAuthorizer'
contains services/app/app-web/src/main/java/com/ulticode/app/dubbo/provider/ContestAdministrationProvider.java 'AdminActorAuthorizer actorAuthorizer'
for stale_rejudge in \
  services/app/app-web/src/main/java/com/ulticode/app/dubbo/provider/SubmissionAdministrationProvider.java \
  services/app/app-web/src/main/java/com/ulticode/app/dubbo/provider/RejudgePolicyProvider.java \
  services/admin/src/main/java/com/ulticode/modules/admin/service/AdminSubmissionService.java \
  services/admin/src/main/java/com/ulticode/modules/admin/service/impl/AdminSubmissionServiceImpl.java; do
  [[ ! -e "$ROOT_DIR/$stale_rejudge" ]] \
    || fail "stale rejudge compatibility source remains: $stale_rejudge"
done
contains services/submission/src/main/java/com/ulticode/submission/dubbo/provider/SubmissionAdministrationProvider.java 'implements SubmissionAdministrationService'
contains services/submission/src/main/java/com/ulticode/submission/admin/SubmissionRejudgeService.java '@Transactional'
contains services/submission/src/main/java/com/ulticode/submission/security/InternalDelegationAssertionVerifier.java 'expectedAudience'
contains services/submission/src/main/java/com/ulticode/submission/security/InternalDelegationAssertionVerifier.java 'backend-submission'
contains services/submission/src/main/java/com/ulticode/modules/submission/mapper/SubmissionMapper.java 'current_attempt_id = NULL'
contains services/submission/src/main/java/com/ulticode/submission/idempotency/mapper/SubmissionCommandReceiptMapper.java 'INSERT IGNORE INTO submission_command_receipt'
contains services/admin/src/main/java/com/ulticode/modules/admin/service/SubmissionCutoverService.java 'group = "backend-submission"'
not_contains services/admin/src/main/java/com/ulticode/modules/admin/service/SubmissionCutoverService.java 'app.features.submission-dubbo-cutover'
contains services/admin/src/main/java/com/ulticode/modules/reconciliation/port/adapter/DubboSubmissionReconciliationReadAdapter.java 'group = "backend-submission"'
contains services/admin/src/main/java/com/ulticode/modules/reconciliation/port/adapter/DubboNotificationReconciliationReadAdapter.java 'group = NotificationServiceContract.DUBBO_GROUP'
contains services/notification/src/main/java/com/ulticode/notification/dubbo/provider/NotificationReconciliationReadProvider.java 'NotificationServiceContract.DUBBO_GROUP'
contains services/notification/src/main/java/com/ulticode/modules/notification/mapper/NotificationReconciliationReadMapper.java 'FROM notifications'
contains services/admin/src/main/java/com/ulticode/modules/reconciliation/OwnerReconciler.java 'runIncrementalReconciliation'
contains services/admin/src/main/java/com/ulticode/modules/reconciliation/OwnerReconciler.java 'tryAcquireLease'
contains services/admin/src/main/java/com/ulticode/modules/reconciliation/OwnerReconciler.java 'notificationOrphans'
not_contains services/app/app-web/src/main/java/com/ulticode/modules/reconciliation/port/AppReconciliationReadMapper.java 'submissions'
not_contains services/app/app-web/src/main/java/com/ulticode/modules/reconciliation/port/DefaultAppReconciliationReadPort.java 'submissionUserCounts'
not_contains services/app/app-web/src/main/java/com/ulticode/modules/reconciliation/port/AppReconciliationReadMapper.java 'notifications'
not_contains services/app/app-web/src/main/java/com/ulticode/modules/reconciliation/port/DefaultAppReconciliationReadPort.java 'notificationUserCounts'
app_java="$ROOT_DIR/services/app/app-web/src/main/java"
if grep -REn --include='*.java' \
  'INSERT[[:space:]]+INTO[[:space:]]+`?(notifications|notification_preferences|notification_delivery_ledger)|UPDATE[[:space:]]+`?(notifications|notification_preferences|notification_delivery_ledger)|DELETE[[:space:]]+FROM[[:space:]]+`?(notifications|notification_preferences|notification_delivery_ledger)|com\.ulticode\.modules\.notification\.(channel|consumer|dispatcher|ledger|mapper|service)([^[:alnum:]_]|$)|com\.ulticode\.modules\.notification\.entity\.(Notification|NotificationPreference)([^[:alnum:]_]|$)|com\.ulticode\.modules\.notification\.dto([^[:alnum:]_]|$)' \
  "$app_java" >/dev/null; then
  fail "App contains Notification-owned persistence or runtime implementation references"
fi

# P1-AUDIT-001: every producer writes only its owner-local audit outbox and
# Admin consumes the event through a durable, idempotent inbox.
for audit_source in \
  services/app/app-web/src/main/java/com/ulticode/app/audit/AppAuditOutboxRecord.java \
  services/app/app-web/src/main/java/com/ulticode/app/audit/AppAuditOutboxMapper.java \
  services/app/app-web/src/main/java/com/ulticode/app/audit/AppAuditSinkAdapter.java \
  services/app/app-web/src/main/java/com/ulticode/app/audit/AppAuditOutboxDispatcher.java \
  services/auth/src/main/java/com/ulticode/auth/audit/AuthAuditOutboxRecord.java \
  services/auth/src/main/java/com/ulticode/auth/audit/AuthAuditOutboxMapper.java \
  services/auth/src/main/java/com/ulticode/auth/audit/AuthAuditSinkAdapter.java \
  services/auth/src/main/java/com/ulticode/auth/audit/AuthAuditOutboxDispatcher.java \
  services/admin/src/main/java/com/ulticode/modules/admin/audit/AdminAuditEventConsumer.java \
  services/admin/src/main/java/com/ulticode/modules/admin/audit/AdminAuditRecordedPayload.java \
  services/admin/src/main/java/com/ulticode/modules/admin/audit/AdminAuditIntegrationInboxBridge.java \
  services/admin/src/main/java/com/ulticode/modules/admin/mapper/AuditLogMapper.java \
  init-db/migrations/auth/V20260831100000__Create_Auth_Audit_Outbox.sql \
  init-db/migrations/app/V20260831100100__Create_App_Audit_Outbox.sql \
  init-db/migrations/admin/V20260831100200__Create_Admin_Audit_Inbox.sql \
  init-db/migrations/admin/V20260831100300__Widen_Audit_Action.sql \
  scripts/test/audit-owner-boundary-contract.sh; do
  [[ -f "$ROOT_DIR/$audit_source" ]] || fail "missing P1-AUDIT source: $audit_source"
done
for owner_audit_source in \
  services/app/app-web/src/main/java/com/ulticode/app/audit/AppAuditOutboxRecord.java \
  services/app/app-web/src/main/java/com/ulticode/app/audit/AppAuditOutboxMapper.java \
  services/app/app-web/src/main/java/com/ulticode/app/audit/AppAuditSinkAdapter.java \
  services/app/app-web/src/main/java/com/ulticode/app/audit/AppAuditOutboxDispatcher.java \
  services/auth/src/main/java/com/ulticode/auth/audit/AuthAuditOutboxRecord.java \
  services/auth/src/main/java/com/ulticode/auth/audit/AuthAuditOutboxMapper.java \
  services/auth/src/main/java/com/ulticode/auth/audit/AuthAuditSinkAdapter.java \
  services/auth/src/main/java/com/ulticode/auth/audit/AuthAuditOutboxDispatcher.java; do
  not_contains "$owner_audit_source" 'admin.audit_outbox'
done
contains services/app/app-web/src/main/java/com/ulticode/app/audit/AppAuditOutboxDispatcher.java 'stream:integration'
contains services/auth/src/main/java/com/ulticode/auth/audit/AuthAuditOutboxDispatcher.java 'stream:integration'
contains services/admin/src/main/java/com/ulticode/modules/admin/audit/AdminAuditIntegrationInboxBridge.java 'Admin-Audit'
contains services/admin/src/main/java/com/ulticode/modules/admin/audit/AdminAuditIntegrationInboxBridge.java 'AuditRecorded'
contains services/admin/src/main/java/com/ulticode/modules/admin/mapper/AuditLogMapper.java 'ON DUPLICATE KEY UPDATE id = id'
contains init-db/migrations/auth/V20260831100000__Create_Auth_Audit_Outbox.sql 'REVOKE INSERT ON `admin`.`audit_outbox`'
contains init-db/migrations/app/V20260831100100__Create_App_Audit_Outbox.sql 'REVOKE INSERT ON `admin`.`audit_outbox`'
contains init-db/migrations/admin/V20260831100300__Widen_Audit_Action.sql 'MODIFY COLUMN `action` VARCHAR(64) NOT NULL'
bash "$ROOT_DIR/scripts/test/audit-owner-boundary-contract.sh"

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

# P1-DATA-001: all normal Submission reads cross the owner contract. The
# local mapper/adapters remain only behind the explicit legacy rollback mode.
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
  services/app/app-web/src/main/java/com/ulticode/modules/submission/port/RemoteSubmissionGenerationReadAdapter.java \
  services/app/app-web/src/main/java/com/ulticode/modules/contest/port/adapter/LocalSubmissionAdjudicationReadAdapter.java; do
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
contains services/app/app-web/src/main/java/com/ulticode/modules/submission/port/SubmissionUserQueryRoutingPort.java \
  'selectOwnerRead'
contains services/app/app-web/src/main/java/com/ulticode/app/config/LegacySubmissionMapperScanConfig.java \
  '@MapperScan(value = "com.ulticode.modules.submission.mapper"'
not_contains services/app/app-web/src/main/java/com/ulticode/app/config/MapperScanConfig.java \
  'com.ulticode.modules.submission.mapper'
not_contains services/app/app-web/src/main/java/com/ulticode/modules/problem/mapper/ProblemMapper.java \
  'FROM submissions'
not_contains services/app/app-web/src/main/java/com/ulticode/modules/problem/mapper/ProblemTagRelationMapper.java \
  'JOIN submissions'
not_contains services/app/app-web/src/main/java/com/ulticode/modules/contest/mapper/ContestAdjudicationReceiptMapper.java \
  'JOIN submissions'
not_contains services/app/app-web/src/main/java/com/ulticode/modules/notification/intent/SubmissionCompletedIntent.java \
  'import com.ulticode.modules.submission.entity.Submission'
mapfile -t app_submission_sql_sources < <(grep -RIl --include='*.java' \
  -E 'FROM[[:space:]]+submissions|JOIN[[:space:]]+submissions|INSERT[[:space:]]+INTO[[:space:]]+submissions|UPDATE[[:space:]]+submissions|DELETE[[:space:]]+FROM[[:space:]]+submissions' \
  "$app_java" || true)
for app_submission_sql_source in "${app_submission_sql_sources[@]}"; do
  [[ "$app_submission_sql_source" == "$app_java/com/ulticode/modules/submission/mapper/SubmissionMapper.java" ]] \
    || fail "normal App source contains direct Submission SQL: $app_submission_sql_source"
done
contains services/app/app-web/src/main/resources/application.yml \
  'mode: ${APP_SUBMISSION_ROUTING_MODE:remote}'
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
legacy_route="'\${app.runtime.mode:dev-lite}' == 'legacy-rollback'"
for local_submission_source in \
  services/app/app-web/src/main/java/com/ulticode/modules/contest/port/adapter/LocalSubmissionAdjudicationReadAdapter.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/port/DefaultSubmissionGenerationReadAdapter.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/port/adapter/DefaultSubmissionAdminReadAdapter.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/port/adapter/ProblemSubmissionStatsMapperAdapter.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/port/adapter/SubmissionActivityAnalyticsMapperAdapter.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/port/adapter/SubmissionAnalyticsMapperAdapter.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/port/adapter/SubmissionReadAdapter.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/port/adapter/SubmissionStreakAdapter.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/port/adapter/SubmissionUserStatsMapperAdapter.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/projection/DefaultSubmissionProjection.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/service/impl/SubmissionServiceImpl.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/stats/DefaultSubmissionPerformanceStats.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/stats/JdbcSubmissionStreakCalculator.java; do
  contains "$local_submission_source" "$legacy_route"
done
contains docker-compose.prod.yml 'JWT_RSA_ENABLED=true'
contains docker-compose.prod.yml 'JWT_JWKS_URI=https://backend-auth:9101/auth/jwks'
not_contains docker-compose.prod.yml 'DUBBO_NAMESPACE:-dev'
contains docker/initdb/02-nacos-user.sh 'NACOS_DB_USER'
contains services/admin/src/main/java/com/ulticode/admin/security/DelegationAssertionSigner.java 'app.bootstrap-admin.enabled:false'
contains services/admin/src/main/java/com/ulticode/admin/security/DelegationAssertionSigner.java 'app.dev-users.enabled:false'
contains services/admin/src/main/java/com/ulticode/admin/security/DelegationAssertionSigner.java 'issueForBootstrap'
contains services/auth/src/main/java/com/ulticode/auth/security/InternalDelegationAssertionVerifier.java 'BOOTSTRAP_CLAIM'
not_contains docker-compose.prod.yml 'BOOTSTRAP_DELEGATION_SECRET='

# Documentation-drift assertions live in docs-contract-test.sh; run it here so
# existing callers of this script keep covering both halves of the contract.
bash "$ROOT_DIR/scripts/dev/docs-contract-test.sh"

echo "Architecture contract: PASS"
