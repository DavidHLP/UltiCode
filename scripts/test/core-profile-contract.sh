#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CONTRACT_FAILURE_PREFIX="core-profile-contract: FAIL"
# shellcheck source=scripts/test/lib/contract-harness.sh
source "$ROOT_DIR/scripts/test/lib/contract-harness.sh"

# shellcheck source=scripts/test/lib/assertions.sh
source "$ROOT_DIR/scripts/test/lib/assertions.sh"

[[ -f "$ROOT_DIR/services/core/pom.xml" ]] || fail 'Core Maven module missing'
[[ -f "$ROOT_DIR/services/core/src/main/java/com/ulticode/core/CoreApplication.java" ]] \
  || fail 'Core boot entrypoint missing'
[[ -f "$ROOT_DIR/services/core/src/main/java/com/ulticode/core/CoreLocalAuthorizationMutationAdapter.java" ]] \
  || fail 'Core local authorization adapter missing'
[[ -f "$ROOT_DIR/services/core/src/main/java/com/ulticode/core/CoreLocalContractAssembly.java" ]] \
  || fail 'Core local contract assembly missing'
[[ -f "$ROOT_DIR/services/platform/common/src/main/java/com/ulticode/common/security/LocalDelegationAssertionContext.java" ]] \
  || fail 'local delegation assertion context missing'

contains services/core/src/main/java/com/ulticode/core/CoreLocalContractAssembly.java 'final class CoreLocalContractAssembly'
contains services/core/src/main/java/com/ulticode/core/CoreLocalContractAssembly.java 'static void register('
contains services/core/src/main/java/com/ulticode/core/CoreLocalContractAssembly.java 'private static final String ADMIN_MODULE = "admin";'
contains services/core/src/main/java/com/ulticode/core/CoreLocalContractAssembly.java 'if (!ADMIN_MODULE.equals(module.name()))'
contains services/core/src/main/java/com/ulticode/core/CoreLocalContractAssembly.java '"coreOwnerContextManager"'
contains services/core/src/main/java/com/ulticode/core/CoreLocalContractAssembly.java '"coreLocalIdentityQueryAdapter"'
contains services/core/src/main/java/com/ulticode/core/CoreLocalContractAssembly.java '"coreLocalAuthorizationMutationAdapter"'
contains services/core/src/main/java/com/ulticode/core/CoreLocalContractAssembly.java '"coreLocalAccountQueryAdapter"'
contains services/core/src/main/java/com/ulticode/core/CoreLocalContractAssembly.java 'static void validate('
contains services/core/src/main/java/com/ulticode/core/CoreLocalContractAssembly.java \
  'LOCAL_CONTRACTS_ENABLED_PROPERTY'
contains services/core/src/main/java/com/ulticode/core/CoreOwnerContextManager.java \
  'CoreLocalContractAssembly.LOCAL_CONTRACTS_ENABLED_PROPERTY'
contains services/admin/src/main/java/com/ulticode/modules/admin/port/adapter/AdminDubboReferenceRegistry.java \
  '@ConditionalOnProperty'
contains services/admin/src/main/java/com/ulticode/modules/admin/port/adapter/AdminDubboReferenceRegistry.java \
  'core.local-contracts.enabled'

contains services/core/src/main/java/com/ulticode/core/CoreOwnerContextManager.java 'CoreLocalContractAssembly.register(child, module, this)'
contains services/core/src/main/java/com/ulticode/core/CoreOwnerContextManager.java 'CoreLocalContractAssembly.validate(context, module)'
not_contains services/core/src/main/java/com/ulticode/core/CoreOwnerContextManager.java 'registerChildContracts'

contains services/pom.xml '<module>core</module>'
contains services/core/src/main/java/com/ulticode/core/CoreApplication.java '@SpringBootConfiguration'
contains services/core/src/main/java/com/ulticode/core/CoreApplication.java 'basePackages = "com.ulticode.core"'
contains services/core/src/main/java/com/ulticode/core/CoreOwnerDataSourceConfiguration.java 'authTransactionManager'
contains services/core/src/main/java/com/ulticode/core/CoreOwnerDataSourceConfiguration.java 'submissionTransactionManager'
contains services/core/src/main/java/com/ulticode/core/CoreModuleRegistry.java '"backend-auth", true'
contains services/core/src/main/java/com/ulticode/core/CoreModuleRegistry.java '"backend-admin", true'
contains services/core/src/main/java/com/ulticode/core/CoreModuleRegistry.java '"backend-app-web", false'
contains services/core/src/main/java/com/ulticode/core/CoreModuleRegistry.java 'enabledModules()'
contains services/core/src/main/java/com/ulticode/core/CoreOwnerClassLoaders.java 'not a class/resource isolation boundary'
contains services/core/src/main/resources/application.yml 'enabled: ${CORE_OWNER_CONTEXTS_ENABLED:false}'
contains services/core/src/main/resources/application.yml 'required: ${CORE_JUDGE_REQUIRED:false}'
contains services/core/src/main/java/com/ulticode/core/CoreOwnerMapperConfigurations.java 'com.ulticode.auth.security.oauth.mapper'
contains services/core/src/main/java/com/ulticode/core/CoreOwnerMapperConfigurations.java 'sqlSessionFactoryRef = "appSqlSessionFactory"'
contains services/core/src/main/java/com/ulticode/core/CoreOwnerBootConfigurations.java '"com.ulticode.modules.contest"'
contains services/core/src/main/java/com/ulticode/core/CoreOwnerBootConfigurations.java '"com.ulticode.modules.event.inbox",'
not_contains services/core/src/main/java/com/ulticode/core/CoreOwnerBootConfigurations.java '"com.ulticode.modules",'
not_contains services/core/src/main/java/com/ulticode/core/CoreOwnerBootConfigurations.java '"com.ulticode.modules.submission",'
not_contains services/core/src/main/java/com/ulticode/core/CoreOwnerBootConfigurations.java '"com.ulticode.modules.notification",'
not_contains services/core/src/main/java/com/ulticode/core/CoreOwnerBootConfigurations.java '"com.ulticode.modules.reconciliation",'
contains services/core/src/main/java/com/ulticode/core/CoreReadinessController.java '/api/v1/core/health'
contains services/core/src/main/java/com/ulticode/core/CoreSecurityConfiguration.java 'anyRequest().denyAll()'
contains services/core/src/main/java/com/ulticode/core/CoreOwnerContextManager.java 'spring.main.web-application-type=none'
contains services/core/src/main/java/com/ulticode/core/CoreOwnerContextManager.java 'core.datasource.'
contains services/core/src/main/java/com/ulticode/core/CoreOwnerContextManager.java 'dubbo.enabled=false'
contains services/core/src/main/java/com/ulticode/core/CoreOwnerContextManager.java 'security.internal-delegation.private-key='
contains services/core/src/main/java/com/ulticode/core/CoreOwnerContextManager.java 'INTERNAL_DELEGATION_PUBLIC_KEY'
not_contains services/core/pom.xml 'backend-judge-runtime'

source "$ROOT_DIR/scripts/dev/devstack-manifest.sh"
[[ "$(devstack_apps_for_scope core)" == 'ulticode-core,ulticode-judge' ]] \
  || fail 'core scope app set drifted'
[[ "$(devstack_infra_for_scope core)" == 'mysql,redis,nacos,meilisearch' ]] \
  || fail 'core scope infra set drifted'
[[ "$(devstack_readiness ulticode-core)" == 'http|9108|/api/v1/core/health/ready' ]] \
  || fail 'core readiness contract drifted'
[[ "$(devstack_app_port ulticode-core)" == '9108' ]] \
  || fail 'core port drifted'
printf 'Core profile explicit assembly and bounded lifecycle contract: PASS\n'
