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
  scripts/runbooks/owner-user-profile-backfill.sh \
  services/app/app-web/src/main/java/com/ulticode/app/user/port/UserFactsProjection.java \
  services/app/app-web/src/main/java/com/ulticode/app/user/port/UserDirectoryProjection.java \
  services/app/app-web/src/main/java/com/ulticode/app/user/port/DefaultUserFactsReadProjection.java \
  services/api/submission-api/src/main/java/com/ulticode/submission/api/service/SubmissionReadPort.java \
  services/api/submission-api/src/main/java/com/ulticode/submission/api/service/SubmissionIntakePort.java \
  services/api/submission-api/src/main/java/com/ulticode/submission/api/service/SubmissionVerdictWritePort.java \
  services/api/submission-api/src/main/java/com/ulticode/submission/api/service/SubmissionWritePort.java \
  services/api/app-api/src/main/java/com/ulticode/app/api/service/CodeExecutionPort.java \
  services/api/app-api/src/main/java/com/ulticode/app/api/service/ProblemTitleLookupPort.java \
  services/app/app-web/src/main/java/com/ulticode/modules/submission/port/adapter/RemoteCodeExecutionPort.java \
  services/judge/src/main/java/com/ulticode/judge/provider/CodeExecutionProvider.java \
  services/judge/src/main/java/com/ulticode/judge/adapter/RemoteSubmissionVerdictWritePort.java \
  services/submission/src/main/java/com/ulticode/submission/port/adapter/ProblemTitleLookupDubboAdapter.java \
  services/submission/src/main/java/com/ulticode/submission/dubbo/provider/SubmissionIntakeProvider.java \
  services/submission/src/main/java/com/ulticode/submission/dubbo/provider/SubmissionVerdictWriteProvider.java \
  services/submission/src/main/java/com/ulticode/submission/dubbo/provider/SubmissionWriteProvider.java \
  services/app/app-web/src/main/java/com/ulticode/app/judge/AppJudgeCompatibilityConfiguration.java \
  services/notification/src/main/java/com/ulticode/notification/inbox/NotificationIntegrationInboxBridge.java; do
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
  'return submissionProjection.toVOs(submissionIds)'
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
not_contains services/app/app-web/src/main/java/com/ulticode/modules/submission/port/SubmissionWriteRoutingPort.java \
  'import com.ulticode.submission.api.service.SubmissionWritePort;'
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
replay_controller="$ROOT_DIR/services/app/app-web/src/main/java/com/ulticode/modules/event/replay/EventReplayController.java"
replay_annotations="$(grep -c '@PreAuthorize' "$replay_controller" || true)"
[[ "$replay_annotations" -eq 6 ]] || fail "EventReplayController must protect all six operations"
contains docker/redis/generate-users-acl.sh '~stream:integration'
contains docker/redis/users.acl '~stream:integration'
contains services/auth/src/main/java/com/ulticode/auth/adapter/in/web/JwksController.java 'public Map<String, Object> getJwks()'
contains services/auth/src/main/java/com/ulticode/auth/security/InternalDelegationAssertionVerifier.java 'backend-auth'
contains services/app/app-web/src/main/java/com/ulticode/app/dubbo/provider/ProblemAdministrationProvider.java 'AdminActorAuthorizer actorAuthorizer'
contains services/app/app-web/src/main/java/com/ulticode/app/dubbo/provider/ContestAdministrationProvider.java 'AdminActorAuthorizer actorAuthorizer'
contains services/app/app-web/src/main/java/com/ulticode/app/dubbo/provider/SubmissionAdministrationProvider.java 'AdminActorAuthorizer actorAuthorizer'
contains docker-compose.prod.yml 'JWT_RSA_ENABLED=true'
contains docker-compose.prod.yml 'JWT_JWKS_URI=http://backend-auth:9101/auth/jwks'
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
