#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

fail() {
  echo "core-profile-contract: FAIL: $*" >&2
  exit 1
}

contains() {
  grep -F -- "$2" "$ROOT_DIR/$1" >/dev/null || fail "$1 missing: $2"
}

[[ -f "$ROOT_DIR/services/core/pom.xml" ]] || fail 'Core Maven module missing'
[[ -f "$ROOT_DIR/services/core/src/main/java/com/ulticode/core/CoreApplication.java" ]] \
  || fail 'Core boot entrypoint missing'
[[ -f "$ROOT_DIR/services/core/src/main/java/com/ulticode/core/CoreLocalAuthorizationMutationAdapter.java" ]] \
  || fail 'Core local authorization adapter missing'
[[ -f "$ROOT_DIR/services/platform/common/src/main/java/com/ulticode/common/security/LocalDelegationAssertionContext.java" ]] \
  || fail 'local delegation assertion context missing'

contains services/pom.xml '<module>core</module>'
contains services/core/src/main/java/com/ulticode/core/CoreApplication.java '@SpringBootConfiguration'
contains services/core/src/main/java/com/ulticode/core/CoreApplication.java 'basePackages = "com.ulticode.core"'
contains services/core/src/main/java/com/ulticode/core/CoreOwnerDataSourceConfiguration.java 'authTransactionManager'
contains services/core/src/main/java/com/ulticode/core/CoreOwnerDataSourceConfiguration.java 'submissionTransactionManager'
contains services/core/src/main/java/com/ulticode/core/CoreOwnerMapperConfigurations.java 'com.ulticode.auth.security.oauth.mapper'
contains services/core/src/main/java/com/ulticode/core/CoreOwnerMapperConfigurations.java 'sqlSessionFactoryRef = "appSqlSessionFactory"'
contains services/core/src/main/java/com/ulticode/core/CoreOwnerBootConfigurations.java '"com.ulticode.modules.contest"'
contains services/core/src/main/java/com/ulticode/core/CoreOwnerBootConfigurations.java '"com.ulticode.modules.event.inbox",'
! grep -F -- '"com.ulticode.modules",' "$ROOT_DIR/services/core/src/main/java/com/ulticode/core/CoreOwnerBootConfigurations.java" >/dev/null \
  || fail 'Core App child must not use broad modules scan'
! grep -F -- '"com.ulticode.modules.submission",' "$ROOT_DIR/services/core/src/main/java/com/ulticode/core/CoreOwnerBootConfigurations.java" >/dev/null \
  || fail 'Core Submission child must not use broad submission scan'
! grep -F -- '"com.ulticode.modules.notification",' "$ROOT_DIR/services/core/src/main/java/com/ulticode/core/CoreOwnerBootConfigurations.java" >/dev/null \
  || fail 'Core Notification child must not use broad notification scan'
! grep -F -- '"com.ulticode.modules.reconciliation",' "$ROOT_DIR/services/core/src/main/java/com/ulticode/core/CoreOwnerBootConfigurations.java" >/dev/null \
  || fail 'Core Admin child must not use broad reconciliation scan'
contains services/core/src/main/java/com/ulticode/core/CoreReadinessController.java '/api/v1/core/health'
contains services/core/src/main/java/com/ulticode/core/CoreSecurityConfiguration.java 'anyRequest().denyAll()'
contains services/core/src/main/java/com/ulticode/core/CoreOwnerContextManager.java 'spring.main.web-application-type=none'
contains services/core/src/main/java/com/ulticode/core/CoreOwnerContextManager.java 'core.datasource.'
contains services/core/src/main/java/com/ulticode/core/CoreOwnerContextManager.java 'dubbo.enabled=false'
contains services/core/src/main/java/com/ulticode/core/CoreOwnerContextManager.java 'security.internal-delegation.private-key='
contains services/core/src/main/java/com/ulticode/core/CoreOwnerContextManager.java 'INTERNAL_DELEGATION_PUBLIC_KEY'
! grep -F -- 'backend-judge-runtime' "$ROOT_DIR/services/core/pom.xml" >/dev/null \
  || fail 'Core must not depend on judge-runtime'

source "$ROOT_DIR/scripts/dev/devstack-manifest.sh"
[[ "$(devstack_apps_for_scope core)" == 'ulticode-core,ulticode-judge' ]] \
  || fail 'core scope app set drifted'
[[ "$(devstack_infra_for_scope core)" == 'mysql,redis,nacos,meilisearch' ]] \
  || fail 'core scope infra set drifted'
[[ "$(devstack_readiness ulticode-core)" == 'http|9108|/api/v1/core/health/ready' ]] \
  || fail 'core readiness contract drifted'
[[ "$(devstack_app_port ulticode-core)" == '9108' ]] \
  || fail 'core port drifted'

printf 'Core profile explicit assembly and isolation contract: PASS\n'
