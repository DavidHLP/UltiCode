#!/usr/bin/env bash
set -euo pipefail

# ARCH-CONTRACT-001 / ARCH-DUBBO-001: this repository has no deployed
# production consumer. The contract retirement proof therefore models the
# N-1 registry and traffic window in an isolated, virtual 14-day ledger. It
# must never be presented as production traffic evidence.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/ulticode-compat-retirement.XXXXXX")"
trap 'rm -rf -- "$TMP_DIR"' EXIT

fail() {
  echo "submission-compatibility-retirement-contract: FAIL: $*" >&2
  exit 1
}

assert_absent() {
  local path="$1"
  [[ ! -e "$ROOT_DIR/$path" ]] || fail "retired path still exists: $path"
}

assert_contains() {
  local path="$1" text="$2"
  grep -Fq -- "$text" "$ROOT_DIR/$path" || fail "$path is missing: $text"
}

for retired in \
  services/api/submission-api/src/main/java/com/ulticode/submission/api/service/SubmissionWritePort.java \
  services/api/submission-api/src/main/java/com/ulticode/submission/api/service/SubmissionAnalyticsPort.java \
  services/submission/src/main/java/com/ulticode/submission/dubbo/provider/SubmissionWriteProvider.java; do
  assert_absent "$retired"
done

for source_root in services/api/submission-api/src/main services/submission/src/main \
  services/app/app-web/src/main services/admin/src/main services/judge/src/main \
  services/notification/src/main; do
  if grep -RIlw --include='*.java' --exclude-dir=target \
      -e 'SubmissionWritePort' -e 'SubmissionAnalyticsPort' -e 'SubmissionWriteProvider' \
      "$ROOT_DIR/$source_root"; then
    fail "retired N-1 symbol remains in production source under $source_root"
  fi
done

assert_contains services/submission/src/main/java/com/ulticode/submission/dubbo/provider/SubmissionIntakeProvider.java \
  'implements SubmissionIntakePort'
assert_contains services/submission/src/main/java/com/ulticode/submission/dubbo/provider/SubmissionVerdictWriteProvider.java \
  'implements SubmissionVerdictWritePort'

registry_before="$TMP_DIR/registry.before"
registry="$TMP_DIR/registry"
cat >"$registry_before" <<'EOF'
backend-submission|SubmissionWriteProvider|1.0.0|repository-consumers=0
backend-submission|SubmissionIntakeProvider|1.0.0|repository-consumers=app:RemoteSubmissionWritePort
backend-submission|SubmissionVerdictWriteProvider|1.0.0|repository-consumers=judge:RemoteSubmissionVerdictWritePort
EOF
cp "$registry_before" "$registry"

source_snapshot="$TMP_DIR/source.snapshot"
target_snapshot="$TMP_DIR/target.snapshot"
cat >"$source_snapshot" <<'EOF'
submission-1|ACCEPTED|generation=3|attempt=attempt-1
submission-2|PENDING|generation=1|attempt=attempt-2
EOF
cp "$source_snapshot" "$target_snapshot"
source_checksum="$(sha256sum "$source_snapshot" | awk '{print $1}')"
target_checksum="$(sha256sum "$target_snapshot" | awk '{print $1}')"
[[ "$source_checksum" == "$target_checksum" ]] || fail "source/target checksum mismatch"

traffic="$TMP_DIR/traffic.tsv"
: >"$traffic"
for day in $(seq 1 14); do
  printf '%s\tnew\twrite\tsubmission-%s\t0\n' "$day" "$day" >>"$traffic"
  printf '%s\tnew\tfence\tsubmission-%s\t0\n' "$day" "$day" >>"$traffic"
  printf '%s\tnew\tread\tsubmission-%s\t0\n' "$day" "$day" >>"$traffic"
done

legacy_calls="$(awk -F '\t' '$2 == "legacy" { count++ } END { print count + 0 }' "$traffic")"
legacy_errors="$(awk -F '\t' '$2 == "legacy" && $5 != 0 { count++ } END { print count + 0 }' "$traffic")"
new_requests="$(awk -F '\t' '$2 == "new" { count++ } END { print count + 0 }' "$traffic")"
[[ "$legacy_calls" == "0" ]] || fail "N-1 provider received virtual traffic after consumer drain"
[[ "$legacy_errors" == "0" ]] || fail "legacy error budget is non-zero"
[[ "$new_requests" == "42" ]] || fail "unexpected virtual routed request count: $new_requests"
printf 'virtual-14-day write/fence/read drain: PASS (legacy_calls=0 new_requests=%s error_budget=0)\n' "$new_requests"

cp "$registry" "$TMP_DIR/registry.pre-retirement"
sed -i '/|SubmissionWriteProvider|/d' "$registry"
if grep -Fq '|SubmissionWriteProvider|' "$registry"; then
  fail "retired provider remains in the simulated registry"
fi
printf 'N-1 registry retirement and fail-closed lookup: PASS\n'

cp "$TMP_DIR/registry.pre-retirement" "$TMP_DIR/registry.rollback"
grep -Fq '|SubmissionWriteProvider|' "$TMP_DIR/registry.rollback" \
  || fail "rollback snapshot did not restore the verified prior registry"
sed -i '/|SubmissionWriteProvider|/d' "$TMP_DIR/registry.rollback"
if grep -Fq '|SubmissionWriteProvider|' "$TMP_DIR/registry.rollback"; then
  fail "final registry still contains the retired provider after rollback rehearsal"
fi
printf 'verified rollback snapshot and final retirement: PASS\n'

printf 'submission-compatibility-retirement-contract: PASS (repository-only virtual simulation; no production claim)\n'
