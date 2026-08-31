#!/usr/bin/env bash
set -euo pipefail

# ARCH-CONTRACT-001: keep provider-owned API modules implementation-free and
# leave only the documented Submission result seam in app-api.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

fail() {
  echo "api-contract-boundary-contract: FAIL: $*" >&2
  exit 1
}

contains() {
  local file="$1" text="$2"
  [[ -f "$ROOT_DIR/$file" ]] || fail "missing source: $file"
  grep -Fq -- "$text" "$ROOT_DIR/$file" || fail "$file is missing: $text"
}

not_contains() {
  local file="$1" text="$2"
  [[ -f "$ROOT_DIR/$file" ]] || fail "missing source: $file"
  local status=0
  grep -Fq -- "$text" "$ROOT_DIR/$file" || status=$?
  if (( status == 0 )); then
    fail "$file contains implementation dependency: $text"
  elif (( status != 1 )); then
    fail "could not inspect $file while checking for: $text (grep exit $status)"
  fi
}

admin_api_dir="$ROOT_DIR/services/api/admin-api"
if [[ -f "$admin_api_dir/pom.xml" || -d "$admin_api_dir/src" ]]; then
  fail "obsolete API module source still exists: services/api/admin-api"
fi

implementation_artifacts=(
  backend-auth backend-admin backend-app backend-submission
  backend-notification backend-search backend-judge backend-legacy
  backend-rpc-resilience backend-judge-config backend-problem-domain
  backend-contest-domain backend-submission-domain backend-moderation-domain
  backend-integration-inbox backend-judge-runtime backend-web-security
  backend-app-web
)

for module in auth-api submission-api notification-api app-api; do
  contains services/pom.xml "<module>api/$module</module>"
  contains "services/api/$module/pom.xml" '<artifactId>backend-common</artifactId>'
  for implementation in "${implementation_artifacts[@]}"; do
    not_contains "services/api/$module/pom.xml" "<artifactId>$implementation</artifactId>"
  done
done
for implementation in "${implementation_artifacts[@]}"; do
  not_contains services/api/app-api/pom.xml "<artifactId>$implementation</artifactId>"
done
printf 'app-api implementation dependency boundary: PASS\n'
python3 - "$ROOT_DIR/services/api/app-api/src/main/java" <<'PY'
from pathlib import Path
import sys

root = Path(sys.argv[1])
forbidden = (
    "SubmissionWritePort",
    "SubmissionIntakePort",
    "SubmissionVerdictWritePort",
    "SubmissionAdministrationService",
    "SubmissionFencePort",
    "SubmissionReconciliationReadPort",
)
for source in root.rglob("*.java"):
    text = source.read_text(encoding="utf-8")
    for name in forbidden:
        if name in text:
            raise SystemExit(
                f"{source} imports or exposes forbidden Submission implementation seam: {name}"
            )
allowed = root / "com/ulticode/app/api/service/SubmissionResultPushPort.java"
if not allowed.is_file():
    raise SystemExit("app-api is missing the documented Submission result push seam: SubmissionResultPushPort")
allowed_text = allowed.read_text(encoding="utf-8")
if "SubmissionResultPushPort" not in allowed_text or "SubmissionResultPayload" not in allowed_text:
    raise SystemExit("app-api is missing the documented Submission result push seam")
print("app-api Submission seam boundary: PASS")
PY


contains services/api/app-api/pom.xml '<artifactId>backend-submission-api</artifactId>'
contains services/api/submission-api/src/main/java/com/ulticode/submission/api/service/SubmissionAnalyticsPort.java '@Deprecated(since = "1.1.0", forRemoval = true)'
not_contains services/pom.xml '<module>api/admin-api</module>'
not_contains services/admin/pom.xml '<artifactId>backend-admin-api</artifactId>'
not_contains services/api/app-api/pom.xml '<artifactId>spring-context</artifactId>'

contains services/docs/CONTRACT_COMPAT_GATE.md 'breakBuildOnBinaryIncompatibleModifications=true'
contains services/pom.xml '<id>contract-compat</id>'
contains services/pom.xml '<artifactId>japicmp-maven-plugin</artifactId>'
contains services/pom.xml '<id>contract-compat-check</id>'
contains services/pom.xml '<artifactId>maven-enforcer-plugin</artifactId>'
contains services/pom.xml '<goal>cmp</goal>'
contains services/pom.xml '<contract.compat.oldVersion>__missing_contract_baseline__</contract.compat.oldVersion>'
not_contains services/pom.xml '<contract.compat.oldVersion>${project.version}</contract.compat.oldVersion>'
contains .github/workflows/_contract.yml '1.0.0-ci.${GITHUB_RUN_ID}'
contains .github/workflows/_contract.yml 'japicmp self-comparison detected'
contains .github/workflows/_contract.yml 'japicmp baseline artifact/version is missing'
contains .github/workflows/_contract.yml 'baseline has no standalone API contracts; compatibility comparison skipped'
printf 'API provider ownership, implementation leakage, and compatibility boundary: PASS\n'
printf 'api-contract-boundary-contract: PASS\n'
