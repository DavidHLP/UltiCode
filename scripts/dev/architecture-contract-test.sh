#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

STATIC_ONLY="${ULTI_STATIC_ONLY:-0}"

# Static-safe children are source/POM assertions or shell self-tests whose
# disposable binaries are created inside the child. Dynamic children (Docker,
# Compose, network, or Maven test/tree shapes) remain non-static.
#
# Source/POM: app-judge-runtime (its Maven tree section is gated separately).
# Shell self-tests: DevStack manifest, compatibility retirement, SSH identity,
# Nacos duplicate-user guard, submission backfill, supply-chain, deployment
# integrity, and owner-migration manifest. Redis ACL assertions remain in the
# non-static gate because their contract generates ACL material.
#
# The API catalog/provider-reference/docs children are pure source assertions
# and run in static mode; the underlying catalog/consumer/docs drift was
# resolved by the App interface locality and release-control convergence.
# Redis ACL assertions remain non-static because their contract generates ACL
# material.
#
run_child() {
  local child="$1"
  if [[ "$STATIC_ONLY" == "1" ]]; then
    case "$child" in
      scripts/dev/devstack-manifest-test.sh \
        |scripts/test/app-judge-runtime-dependency-contract.sh \
        |scripts/test/submission-compatibility-retirement-contract.sh \
        |scripts/test/ssh-host-identity-contract.sh \
        |scripts/test/nacos-security-contract.sh \
        |scripts/test/submission-backfill-contract.sh \
        |scripts/test/supply-chain-contract.sh \
        |scripts/test/deployment-integrity-contract.sh \
        |scripts/test/owner-migration-manifest-contract.sh \
        |scripts/test/owner-architecture-source-contract.sh \
        |scripts/test/api-contract-boundary-contract.sh \
        |scripts/test/dubbo-provider-reference-contract.sh \
        |scripts/dev/docs-contract-test.sh \
        |scripts/test/devlite-minimal-contract.sh \
        |scripts/test/core-profile-contract.sh \
        |scripts/test/devstack-control-contract.sh \
      )
        printf 'Architecture child %s: running static-safe checks\n' "$child"
        ;;
      *)
        printf 'Architecture child %s: skipped in static-only mode\n' "$child"
        return 0
        ;;
    esac
  fi
  bash "$ROOT_DIR/$child"
}

fail() {
  echo "Architecture contract failed: $*" >&2
  exit 1
}

# shellcheck source=scripts/test/lib/assertions.sh
source "$ROOT_DIR/scripts/test/lib/assertions.sh"

assert_absent() {
  local file="$1"
  [[ ! -e "$ROOT_DIR/$file" ]] || fail "$file must be absent after compatibility retirement"
}

compact_contains() {
  local file="$1" text="$2"
  [[ -f "$ROOT_DIR/$file" ]] || fail "missing guarded file: $file"
  python3 - "$ROOT_DIR/$file" "$text" <<'PY'
import re
import sys
from pathlib import Path

path = Path(sys.argv[1])
needle = re.sub(r"\s+", "", sys.argv[2])
source = re.sub(r"\s+", "", path.read_text(encoding="utf-8"))
if needle not in source:
    raise SystemExit(f"{path}: missing guarded symbol chain {sys.argv[2]}")
PY
}

compact_not_contains() {
  local file="$1" text="$2"
  [[ -f "$ROOT_DIR/$file" ]] || fail "missing guarded file: $file"
  if python3 - "$ROOT_DIR/$file" "$text" <<'PY'
import re
import sys
from pathlib import Path

path = Path(sys.argv[1])
needle = re.sub(r"\s+", "", sys.argv[2])
source = re.sub(r"\s+", "", path.read_text(encoding="utf-8"))
raise SystemExit(0 if needle not in source else 1)
PY
  then
    return 0
  fi
  fail "$file contains forbidden guarded symbol chain: $text"
}

run_child scripts/test/owner-architecture-source-contract.sh
run_child scripts/dev/devstack-manifest-test.sh
run_child scripts/test/core-profile-contract.sh
run_child scripts/test/devlite-minimal-contract.sh
run_child scripts/test/devstack-control-contract.sh
run_child scripts/test/app-judge-runtime-dependency-contract.sh
run_child scripts/test/submission-compatibility-retirement-contract.sh

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

# Retired aliases and the obsolete three-service experiment must stay absent;
# the pitstop Windows adapter is still consumed by pitstop.yaml.
for stale_alias in scripts/start.sh scripts/stop.sh scripts/start.bat scripts/stop.bat \
  services/scripts/dev/start-service-shells.sh; do
  [[ ! -e "$ROOT_DIR/$stale_alias" ]] || fail "retired launcher still present: $stale_alias"
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
  services/auth/src/main/java/com/ulticode/auth/security/AuthSecurityConfig.java \
  services/app/app-web/src/main/java/com/ulticode/app/security/AppSecurityConfig.java \
  services/admin/src/main/java/com/ulticode/admin/security/AdminSecurityConfig.java \
  services/notification/src/main/java/com/ulticode/notification/security/NotificationSecurityConfig.java \
  services/app/app-web/src/test/java/com/ulticode/app/security/AppTestSecurityConfig.java; do
  compact_not_contains "$route_config" '.anyRequest().permitAll()'
done
compact_contains services/auth/src/main/java/com/ulticode/auth/security/AuthSecurityConfig.java \
  '.anyRequest().authenticated()'
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
not_contains services/admin/src/main/java/com/ulticode/admin/security/DelegationAssertionSigner.java 'Keys.hmacShaKeyFor'
for delegation_source in services/auth/src/main/java/com/ulticode/auth/security/InternalDelegationAssertionVerifier.java services/app/app-web/src/main/java/com/ulticode/app/security/InternalDelegationAssertionVerifier.java services/notification/src/main/java/com/ulticode/notification/security/InternalDelegationAssertionVerifier.java services/submission/src/main/java/com/ulticode/submission/security/InternalDelegationAssertionVerifier.java; do
  not_contains "$delegation_source" 'Keys.hmacShaKeyFor'
  contains "$delegation_source" 'DelegationAssertionVerifierSupport.verifyTrusted'
  not_contains "$delegation_source" 'DelegationAssertionVerifierSupport.verify('
  not_contains "$delegation_source" 'private static RSAPublicKey loadOptionalPublicKey'
done
contains services/platform/web-security/src/main/java/com/ulticode/websecurity/jwt/DelegationAssertionVerifierSupport.java 'public static boolean verifyTrusted'
contains services/platform/web-security/src/main/java/com/ulticode/websecurity/jwt/RsaKeyMaterial.java 'loadOptionalPublicKey'
contains services/notification/src/main/java/com/ulticode/notification/BackendNotificationApplication.java 'RedisDelegationAssertionReplayGuard.class'
contains services/admin/src/main/java/com/ulticode/admin/security/DelegationAssertionSigner.java 'Jwts.SIG.RS256'
contains services/admin/src/main/resources/application.yml 'INTERNAL_DELEGATION_PRIVATE_KEY'
contains services/admin/src/main/resources/application.yml 'BOOTSTRAP_DELEGATION_PRIVATE_KEY'
for delegation_config in services/auth/src/main/resources/application.yml services/app/app-web/src/main/resources/application.yml services/notification/src/main/resources/application.yml services/submission/src/main/resources/application.yml; do
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
[[ ! -e "$ROOT_DIR/docker/redis/users.acl" ]] || fail "tracked Redis ACL verifier remains"
contains docker-compose.yml 'REDIS_ACL_DIR'
contains docker/redis/generate-users-acl.sh '<PREFIX>_REDIS_PASSWORD_PREVIOUS'
contains scripts/runbooks/redis-acl-rotation.sh 'next-overlap-current'
contains scripts/runbooks/redis-acl-rotation.sh 'current-overlap-next'
contains scripts/runbooks/redis-acl-rotation.sh 'runtime ACL drift detected'
contains .github/actions/host-deploy/action.yml 'redis-acl-rotation.sh materialize'
contains .github/workflows/_backend.yml 'redis-acl-rotation-contract.sh'
run_child scripts/test/redis-acl-contract.sh
run_child scripts/test/redis-acl-rotation-contract.sh
run_child scripts/test/ssh-host-identity-contract.sh
run_child scripts/test/nacos-security-contract.sh
run_child scripts/test/submission-backfill-contract.sh
run_child scripts/test/owner-schema-contraction-contract.sh
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
contains services/admin/src/main/java/com/ulticode/modules/reconciliation/OwnerReconciler.java 'fencedJobLeaseService.tryAcquire'
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
  init-db/flyway-post-owner.conf \
  init-db/migrations/post-owner/V20260831100400__Revoke_Cross_Owner_Audit_Grants.sql \
  scripts/test/audit-owner-boundary-contract.sh \
  scripts/runbooks/admin-audit-stream-migration.sh \
  scripts/test/admin-audit-stream-migration-contract.sh; do
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
contains services/app/app-web/src/main/java/com/ulticode/app/audit/AppAuditOutboxDispatcher.java 'APP_AUDIT_STREAM_KEY'
contains services/auth/src/main/java/com/ulticode/auth/audit/AuthAuditOutboxDispatcher.java 'AUTH_AUDIT_STREAM_KEY'
contains services/admin/src/main/java/com/ulticode/modules/admin/audit/AdminAuditIntegrationInboxBridge.java 'STREAM_KEYS'
contains services/platform/common/src/main/java/com/ulticode/common/event/IntegrationEventEnvelopeContract.java 'APP_AUDIT_STREAM_KEY'
contains services/platform/common/src/main/java/com/ulticode/common/event/IntegrationEventEnvelopeContract.java 'AUTH_AUDIT_STREAM_KEY'
contains scripts/runbooks/admin-audit-stream-migration.sh 'I_HAVE_VERIFIED_ADMIN_AUDIT_STREAM_MIGRATION'
contains services/admin/src/main/java/com/ulticode/modules/admin/audit/AdminAuditIntegrationInboxBridge.java 'AuditRecorded'
contains services/admin/src/main/java/com/ulticode/modules/admin/mapper/AuditLogMapper.java 'ON DUPLICATE KEY UPDATE id = id'
not_contains init-db/migrations/auth/V20260831100000__Create_Auth_Audit_Outbox.sql 'REVOKE INSERT ON `admin`.`audit_outbox`'
not_contains init-db/migrations/app/V20260831100100__Create_App_Audit_Outbox.sql 'REVOKE INSERT ON `admin`.`audit_outbox`'
contains init-db/migrations/post-owner/V20260831100400__Revoke_Cross_Owner_Audit_Grants.sql 'REVOKE INSERT ON `admin`.`audit_outbox`'
contains init-db/migrations/admin/V20260831100300__Widen_Audit_Action.sql 'MODIFY COLUMN `action` VARCHAR(64) NOT NULL'
run_child scripts/test/audit-owner-boundary-contract.sh
run_child scripts/test/admin-audit-stream-migration-contract.sh

# P2-MIG-001: CD must execute the same ordered owner-manifest seam that local
# migration documentation describes; rollback must keep the schema untouched.
contains scripts/runbooks/owner-migration-manifest.sh \
  'OWNER_MIGRATION_ORDER=(auth admin app notification submission)'
contains scripts/runbooks/owner-migration-manifest.sh 'flock -n'
contains scripts/runbooks/owner-migration-manifest.sh 'no repair is attempted'
contains scripts/runbooks/owner-migration-manifest.sh 'skip_migrations=true preserves schema'
contains scripts/runbooks/owner-migration-manifest.sh '-baselineOnMigrate=true'
contains init-db/flyway-post-owner.conf 'flyway.baselineOnMigrate=true'
contains scripts/dev/up.sh 'migrate-post-owner.sh'
contains scripts/dev/migrate-post-owner.sh 'flyway-post-owner.conf'
contains init-db/scripts/generate-baseline.sh 'flyway_post_owner_history'
contains init-db/scripts/validate-baseline.sh '"$ROOT/init-db/scripts/generate-baseline.sh" "$TMP_DUMP"'
contains scripts/runbooks/owner-backup-restore.sh 'OWNER_SCHEMAS=(auth admin app notification submission)'
contains scripts/runbooks/owner-backup-restore.sh 'openssl enc -aes-256-cbc -salt -pbkdf2'
contains scripts/runbooks/owner-backup-restore.sh 'flock -n'
contains scripts/runbooks/owner-backup-restore.sh 'rto_seconds'
contains .github/workflows/_backend.yml 'owner-backup-restore-contract.sh'
contains .github/workflows/_backend.yml 'redis-acl-rotation-contract.sh'
contains .github/workflows/_docker.yml 'supply-chain-contract.sh'
contains .github/workflows/_docker.yml 'deployment-integrity-contract.sh'
contains .github/workflows/_backend.yml 'scheduler-contract.sh'
contains .github/workflows/_backend.yml 'fenced-lease-contract.sh'
contains .github/workflows/_backend.yml 'graceful-drain-contract.sh'
contains .github/workflows/_backend.yml 'dependency-resilience-contract.sh'
contains .github/workflows/_backend.yml 'stream-resilience-contract.sh'
contains scripts/test/stream-resilience-contract.sh 'stream-resilience-contract: PASS'
run_child scripts/test/stream-resilience-contract.sh
contains .github/workflows/_backend.yml 'scale-topology-contract.sh'
contains scripts/test/scale-topology-contract.sh 'scale-topology-contract: PASS'
run_child scripts/test/scale-topology-contract.sh
run_child scripts/test/ha-profile-contract.sh
contains .github/workflows/_backend.yml 'dubbo-mtls-contract.sh'
contains .github/workflows/_docker.yml 'dubbo-mtls-contract.sh'
contains services/platform/rpc-resilience/src/main/java/com/ulticode/rpc/resilience/DubboMtlsIdentityFilter.java 'DUBBO_MTLS_ALLOWED_CALLERS'
contains services/platform/rpc-resilience/src/main/java/com/ulticode/rpc/resilience/DubboMtlsIdentityFilter.java 'h2StreamChannel'
contains services/platform/rpc-resilience/src/main/java/com/ulticode/rpc/resilience/DubboMtlsIdentityFilter.java 'getChannel", Boolean.class'
contains services/platform/rpc-resilience/src/main/resources/META-INF/dubbo/internal/org.apache.dubbo.rpc.Filter 'workload-mtls='
run_child scripts/test/dubbo-mtls-contract.sh
contains .github/workflows/_backend.yml 'network-reachability-contract.sh'
contains .github/workflows/_docker.yml 'network-reachability-contract.sh'
contains scripts/test/network-reachability-contract.sh 'network-reachability-contract: PASS'
run_child scripts/test/network-reachability-contract.sh
contains .github/workflows/_backend.yml 'judge-sandbox-contract.sh'
contains .github/workflows/_docker.yml 'judge-sandbox-contract.sh'
contains scripts/test/judge-sandbox-contract.sh 'judge-sandbox-contract: PASS'
run_child scripts/test/judge-sandbox-contract.sh
run_child scripts/test/owner-backup-restore-contract.sh
run_child scripts/test/supply-chain-contract.sh
run_child scripts/test/observability-contract.sh
run_child scripts/test/deployment-integrity-contract.sh
run_child scripts/test/scheduler-contract.sh
run_child scripts/test/fenced-lease-contract.sh
run_child scripts/test/graceful-drain-contract.sh
run_child scripts/test/dependency-resilience-contract.sh
contains apps/console/nginx.conf 'include /etc/nginx/conf.d/includes/tls-listener.conf;'
contains apps/management/nginx.conf 'include /etc/nginx/conf.d/includes/tls-listener.conf;'
contains infrastructure/nginx/includes/tls-listener.prod.conf 'listen 8443 ssl;'
contains infrastructure/nginx/includes/security-headers.conf 'Strict-Transport-Security'
contains docker-compose.prod.yml 'TLS_CERT_DIR'
contains .github/workflows/_backend.yml 'tls-profile-contract.sh'
run_child scripts/test/tls-profile-contract.sh
contains .github/actions/host-deploy/action.yml 'owner-migration-manifest.sh migrate'
contains .github/actions/host-deploy/action.yml 'MIGRATION_DB_PASSWORD'
contains .github/actions/host-deploy/action.yml "inputs.skip_migrations != 'true'"
contains .github/workflows/cd-deploy.yml 'migration_db_user:'
contains .github/workflows/cd-deploy.yml 'submission_migration_db_password:'
contains .github/workflows/cd-rollback.yml "skip_migrations: 'true'"
contains .github/workflows/_backend.yml 'owner-migration-manifest-contract.sh'
run_child scripts/test/owner-migration-manifest-contract.sh

contains docker-compose.prod.yml 'JWT_RSA_ENABLED=true'
contains docker-compose.prod.yml 'JWT_JWKS_URI=https://backend-auth:9101/auth/jwks'
not_contains docker-compose.prod.yml 'DUBBO_NAMESPACE:-dev'
contains docker/initdb/02-nacos-user.sh 'NACOS_DB_USER'
contains services/admin/src/main/java/com/ulticode/admin/security/DelegationAssertionSigner.java 'app.bootstrap-admin.enabled:false'
contains services/admin/src/main/java/com/ulticode/admin/security/DelegationAssertionSigner.java 'app.dev-users.enabled:false'
contains services/admin/src/main/java/com/ulticode/admin/security/DelegationAssertionSigner.java 'issueForBootstrap'
contains services/platform/web-security/src/main/java/com/ulticode/websecurity/jwt/DelegationAssertionVerifierSupport.java 'DelegationAssertionContract.BOOTSTRAP_CLAIM'
not_contains docker-compose.prod.yml 'BOOTSTRAP_DELEGATION_SECRET='

contains .github/workflows/_contract.yml 'api-contract-boundary-contract.sh'
contains scripts/test/api-contract-boundary-contract.sh 'api-contract-boundary-contract: PASS'
run_child scripts/test/api-contract-boundary-contract.sh
run_child scripts/test/dubbo-provider-reference-contract.sh
run_child scripts/dev/docs-contract-test.sh
# Documentation-drift assertions live in docs-contract-test.sh; run it here so
# existing callers of this script keep covering both halves of the contract.

echo "Architecture contract: PASS"
