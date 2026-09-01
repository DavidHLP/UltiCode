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
if [[ -L "$admin_api_dir" || ( -e "$admin_api_dir" && ! -d "$admin_api_dir" ) ]]; then
  fail "obsolete API module source still exists: services/api/admin-api"
fi
if [[ -d "$admin_api_dir" ]]; then
  obsolete_source="$(find "$admin_api_dir" -mindepth 1 \
    ! -path "$admin_api_dir/target" \
    ! -path "$admin_api_dir/target/*" \
    ! -path "$admin_api_dir/.flattened-pom.xml" \
    -print -quit)"
  [[ -z "$obsolete_source" ]] \
    || fail "obsolete API module source exists under services/api/admin-api: $obsolete_source"
fi
tracked_admin_api="$(git -C "$ROOT_DIR" ls-files -- 'services/api/admin-api')"
[[ -z "$tracked_admin_api" ]] \
  || fail "obsolete API module has tracked files: $tracked_admin_api"

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
not_contains services/pom.xml '<module>api/admin-api</module>'
not_contains services/admin/pom.xml '<artifactId>backend-admin-api</artifactId>'
not_contains services/api/app-api/pom.xml '<artifactId>spring-context</artifactId>'
for retired in \
  services/api/submission-api/src/main/java/com/ulticode/submission/api/service/SubmissionWritePort.java \
  services/api/submission-api/src/main/java/com/ulticode/submission/api/service/SubmissionAnalyticsPort.java \
  services/submission/src/main/java/com/ulticode/submission/dubbo/provider/SubmissionWriteProvider.java; do
  [[ ! -e "$ROOT_DIR/$retired" ]] || fail "retired compatibility source still exists: $retired"
done
contains services/pom.xml '<revision>2.0.0</revision>'
contains services/pom.xml '<skip>${contract.compat.breakingRelease}</skip>'
contains .github/workflows/_contract.yml 'breaking_release=true'
contains .github/workflows/_contract.yml 'intentional major contract release; compatibility comparison skipped after repository retirement proof'
contains scripts/test/submission-compatibility-retirement-contract.sh 'virtual-14-day'

contains services/docs/CONTRACT_COMPAT_GATE.md 'breakBuildOnBinaryIncompatibleModifications=true'
contains services/pom.xml '<id>contract-compat</id>'
contains services/pom.xml '<artifactId>japicmp-maven-plugin</artifactId>'
contains services/pom.xml '<id>contract-compat-check</id>'
contains services/pom.xml '<artifactId>maven-enforcer-plugin</artifactId>'
contains services/pom.xml '<goal>cmp</goal>'
contains services/pom.xml '<contract.compat.oldVersion>__missing_contract_baseline__</contract.compat.oldVersion>'
not_contains services/pom.xml '<contract.compat.oldVersion>${project.version}</contract.compat.oldVersion>'
contains .github/workflows/_contract.yml 'ci_revision=${current_revision}-ci.${GITHUB_RUN_ID}'
contains .github/workflows/_contract.yml 'japicmp self-comparison detected'
contains .github/workflows/_contract.yml 'japicmp baseline artifact/version is missing'
contains .github/workflows/_contract.yml 'baseline has no standalone API contracts; compatibility comparison skipped'
python3 - "$ROOT_DIR/services/api/app-api/src/main/java" "$ROOT_DIR/docs/architecture/evidence/P2-APP-001-app-api-catalog.md" <<'PY'
import re
import sys
from pathlib import Path

source_root = Path(sys.argv[1])
catalog = Path(sys.argv[2]).read_text(encoding="utf-8")
interfaces = sorted(
    match.group(1)
    for source in source_root.rglob("*.java")
    for match in [re.search(r"\bpublic\s+interface\s+([A-Za-z0-9_]+)", source.read_text(encoding="utf-8"))]
    if match
)
source_names = set(interfaces)
catalog_rows = []
in_catalog = False
for line in catalog.splitlines():
    if line == "## Catalog":
        in_catalog = True
        continue
    if line == "## Retired by P2-APP-003":
        break
    if in_catalog and line.startswith("| ") and line.endswith("|"):
        name = line.split("|", 2)[1].strip()
        if name not in {"Interface", "---"}:
            catalog_rows.append((name, line))
catalog_names = {name for name, _ in catalog_rows}
extra = sorted(catalog_names - source_names)
if extra:
    raise SystemExit("app-api catalog has stale interface rows: " + ", ".join(extra))
missing = []
for name in interfaces:
    rows = [line for row_name, line in catalog_rows if row_name == name]
    if len(rows) != 1 or rows[0].count("|") < 7 or not rows[0].rstrip().endswith("|"):
        missing.append(name)
    elif "unknown" in rows[0].lower():
        missing.append(name)
if missing:
    raise SystemExit("app-api catalog missing complete ownership metadata: " + ", ".join(missing))
for retired in ("JudgeConfigPort", "JudgeEnqueuePort", "VerdictResolvePort", "ModerationUserReadPort"):
    if re.search(rf"\bpublic\s+interface\s+{retired}\b", "\n".join(
        source.read_text(encoding="utf-8") for source in source_root.rglob("*.java"))):
        raise SystemExit(f"retired app-api interface remains: {retired}")
print(f"app-api ownership catalog: PASS ({len(interfaces)} interfaces)")
PY
printf 'API provider ownership, implementation leakage, and compatibility boundary: PASS\n'
printf 'api-contract-boundary-contract: PASS\n'
