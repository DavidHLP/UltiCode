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
  services/app/app-web/src/main/java/com/ulticode/app/judge/AppJudgeCompatibilityConfiguration.java \
  services/notification/src/main/java/com/ulticode/notification/inbox/NotificationIntegrationInboxBridge.java; do
  [[ -f "$ROOT_DIR/$file" ]] || fail "missing architecture source: $file"
done

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

# Documentation-drift assertions live in docs-contract-test.sh; run it here so
# existing callers of this script keep covering both halves of the contract.
bash "$ROOT_DIR/scripts/dev/docs-contract-test.sh"

echo "Architecture contract: PASS"
