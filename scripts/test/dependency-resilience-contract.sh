#!/usr/bin/env bash
set -euo pipefail

# P3-RES-001 contract: every backend Dubbo/direct-HTTP consumer has a bounded
# timeout/retry/concurrency/circuit/fallback policy, and writes never gain an
# automatic retry.

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"

fail() {
  echo "dependency-resilience-contract: FAIL: $*" >&2
  exit 1
}

contains() {
  local file="$1" text="$2"
  grep -Fq -- "$text" "$ROOT_DIR/$file" \
    || fail "$file is missing: $text"
}

for pom in \
  services/admin/pom.xml \
  services/app/app-web/pom.xml \
  services/notification/pom.xml \
  services/submission/pom.xml \
  services/judge/pom.xml; do
  contains "$pom" '<artifactId>backend-rpc-resilience</artifactId>'
done

while IFS= read -r source; do
  case "$source" in
    services/admin/*) pom=services/admin/pom.xml ;;
    services/app/app-web/*) pom=services/app/app-web/pom.xml ;;
    services/notification/*) pom=services/notification/pom.xml ;;
    services/submission/*) pom=services/submission/pom.xml ;;
    services/judge/*) pom=services/judge/pom.xml ;;
    *) fail "unclassified production Dubbo consumer: $source" ;;
  esac
  contains "$pom" '<artifactId>backend-rpc-resilience</artifactId>'
done < <(grep -RIl --include='*.java' \
  --exclude-dir=target --exclude-dir=test \
  '^[[:space:]]*@DubboReference' "$ROOT_DIR/services" \
  | sed "s#^$ROOT_DIR/##")

for config in \
  services/admin/src/main/resources/application.yml \
  services/app/app-web/src/main/resources/application.yml \
  services/notification/src/main/resources/application.yml \
  services/submission/src/main/resources/application.yml \
  services/judge/src/main/resources/application.yml; do
  consumer_block="$(awk '
    /^dubbo:/ { in_dubbo=1; next }
    in_dubbo && /^  consumer:/ { in_consumer=1; next }
    in_consumer && /^  [a-zA-Z]/ { exit }
    in_consumer { print }
  ' "$ROOT_DIR/$config")"
  grep -Eq '^[[:space:]]+timeout:[[:space:]]+3000$' <<<"$consumer_block" \
    || fail "$config Dubbo consumer timeout is not the 3000ms write-safe default"
  grep -Eq '^[[:space:]]+retries:[[:space:]]+0$' <<<"$consumer_block" \
    || fail "$config Dubbo consumer retries is not the write-safe zero default"
done

contains services/platform/rpc-resilience/src/main/resources/META-INF/dubbo/internal/org.apache.dubbo.rpc.cluster.filter.ClusterFilter \
  'dependency-resilience=com.ulticode.rpc.resilience.DubboDependencyResilienceFilter'
contains services/platform/common/src/main/java/com/ulticode/common/resilience/DependencyGuard.java \
  'enum State'
contains services/platform/common/src/main/java/com/ulticode/common/rpc/RpcPolicy.java \
  'QUERY_TOTAL_BUDGET_MS'

for source in \
  services/auth/src/main/java/com/ulticode/auth/security/oauth/OAuthHttp.java \
  services/app/app-web/src/main/java/com/ulticode/app/storage/S3Storage.java \
  services/app/app-web/src/main/java/com/ulticode/modules/search/projection/DefaultSearchReadProjection.java \
  services/app/app-web/src/main/java/com/ulticode/modules/search/backfill/SearchBackfillRunner.java \
  services/search/src/main/java/com/ulticode/search/SearchDocumentIndexWorker.java \
  services/search/src/main/java/com/ulticode/search/SearchWorkerReadinessHeartbeat.java; do
  contains "$source" 'DependencyGuard'
done

contains services/platform/web-security/src/main/java/com/ulticode/websecurity/jwt/JwtResourceServerConfiguration.java \
  'jwksRestClient'
contains services/platform/web-security/src/main/java/com/ulticode/websecurity/jwt/JwksPublicKeyProvider.java \
  'staleIfErrorSeconds'
for config in \
  services/admin/src/main/resources/application.yml \
  services/app/app-web/src/main/resources/application.yml \
  services/notification/src/main/resources/application.yml; do
  contains "$config" 'stale-if-error-seconds: ${JWT_JWKS_STALE_IF_ERROR_SECONDS:300}'
  contains "$config" 'http-timeout-ms: ${JWT_JWKS_HTTP_TIMEOUT_MS:800}'
done
contains services/app/app-web/src/main/java/com/ulticode/app/storage/S3Storage.java \
  'READ_ATTEMPTS = 2'
contains services/auth/src/main/java/com/ulticode/auth/security/oauth/OAuthHttp.java \
  'READ_TIMEOUT_MS = 10_000'

printf 'dependency timeout/retry/circuit/bulkhead/fallback wiring: PASS\n'

(
  cd "$ROOT_DIR/services"
  if command -v mise >/dev/null 2>&1; then
    mise exec java@zulu-17.68.203.0 -- bash ./mvnw \
      -pl platform/common,platform/rpc-resilience,platform/web-security,auth,admin,app/modules/problem,app/modules/contest,app/modules/moderation,app/app-web,notification,submission,search,judge -am \
      -Dtest='DependencyGuardTest,RpcPolicyBudgetTest,DubboDependencyResilienceFilterTest,JwksPublicKeyProviderTest,OAuthHttpTest,S3StorageTest,IdentityBanCheckAdapterTest,SearchDocumentIndexWorkerTest,DefaultSearchReadProjectionTest,SearchBackfillRunnerTest,RpcPolicyArchTest' \
      -Dsurefire.failIfNoSpecifiedTests=false test -B
  else
    bash ./mvnw \
      -pl platform/common,platform/rpc-resilience,platform/web-security,auth,admin,app/modules/problem,app/modules/contest,app/modules/moderation,app/app-web,notification,submission,search,judge -am \
      -Dtest='DependencyGuardTest,RpcPolicyBudgetTest,DubboDependencyResilienceFilterTest,JwksPublicKeyProviderTest,OAuthHttpTest,S3StorageTest,IdentityBanCheckAdapterTest,SearchDocumentIndexWorkerTest,DefaultSearchReadProjectionTest,SearchBackfillRunnerTest,RpcPolicyArchTest' \
      -Dsurefire.failIfNoSpecifiedTests=false test -B
  fi
) >/dev/null

printf 'dependency refusal/timeout/open/half-open/recovery/saturation tests: PASS\n'
printf 'dependency-resilience-contract: PASS\n'
