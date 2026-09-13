#!/usr/bin/env bash
set -euo pipefail

# Source-only ownership and seam assertions. The architecture gate owns
# execution policy; this child owns the repository's source-contract registry.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RULES_FILE="$ROOT_DIR/scripts/test/owner-architecture-source-contract.rules"

CONTRACT_FAILURE_PREFIX="owner-architecture-source-contract: FAIL"
# shellcheck source=scripts/test/lib/contract-harness.sh
source "$ROOT_DIR/scripts/test/lib/contract-harness.sh"

# shellcheck source=scripts/test/lib/assertions.sh
source "$ROOT_DIR/scripts/test/lib/assertions.sh"

assert_exists() {
  [[ -f "$ROOT_DIR/$1" ]] || fail "missing guarded file: $1"
}

assert_absent() {
  [[ ! -e "$ROOT_DIR/$1" ]] || fail "$1 must be absent after compatibility retirement"
}

compact_contains() {
  local file="$1" text="$2"
  [[ -f "$ROOT_DIR/$file" ]] || fail "missing guarded file: $file"
  python3 - "$ROOT_DIR/$file" "$text" <<'PYTHON'
import re
import sys
from pathlib import Path

path = Path(sys.argv[1])
needle = re.sub(r"\s+", "", sys.argv[2])
source = re.sub(r"\s+", "", path.read_text(encoding="utf-8"))
if needle not in source:
    raise SystemExit(f"{path}: missing guarded symbol chain {sys.argv[2]}")
PYTHON
}

compact_not_contains() {
  local file="$1" text="$2"
  [[ -f "$ROOT_DIR/$file" ]] || fail "missing guarded file: $file"
  if python3 - "$ROOT_DIR/$file" "$text" <<'PYTHON'
import re
import sys
from pathlib import Path

path = Path(sys.argv[1])
needle = re.sub(r"\s+", "", sys.argv[2])
source = re.sub(r"\s+", "", path.read_text(encoding="utf-8"))
if needle in source:
    raise SystemExit(1)
PYTHON
  then
    return 0
  fi
  fail "$file contains forbidden compact text: $text"
}

parse_registry_line() {
  local line="$1" remainder
  [[ "$line" == *$'\t'* ]] || fail "malformed registry line: $line"
  registry_kind="${line%%$'\t'*}"
  remainder="${line#*$'\t'}"
  [[ "$remainder" == *$'\t'* ]] || fail "malformed registry line: $line"
  registry_text="${remainder%%$'\t'*}"
  registry_files="${remainder#*$'\t'}"
}

parse_registry_line $'exists\t\tparser-regression'
[[ "$registry_kind" == "exists" && -z "$registry_text" && "$registry_files" == "parser-regression" ]] \
  || fail "registry parser does not preserve empty literal fields"

while IFS= read -r registry_line; do
  [[ -z "$registry_line" || "$registry_line" == \#* ]] && continue
  parse_registry_line "$registry_line"
  IFS='|' read -r -a paths <<< "$registry_files"
  for file in "${paths[@]}"; do
    case "$registry_kind" in
      exists) assert_exists "$file" ;;
      absent) assert_absent "$file" ;;
      contains) contains "$file" "$registry_text" ;;
      not_contains) not_contains "$file" "$registry_text" ;;
      compact_contains) compact_contains "$file" "$registry_text" ;;
      compact_not_contains) compact_not_contains "$file" "$registry_text" ;;
      *) fail "unknown registry rule: $registry_kind" ;;
    esac
  done
done < "$RULES_FILE"

app_java="$ROOT_DIR/services/app/app-web/src/main/java"
mapfile -t app_submission_sql_sources < <(grep -RIl --include='*.java' \
  -E 'FROM[[:space:]]+submissions|JOIN[[:space:]]+submissions|INSERT[[:space:]]+INTO[[:space:]]+submissions|UPDATE[[:space:]]+submissions|DELETE[[:space:]]+FROM[[:space:]]+submissions' \
  "$app_java" || true)
for app_submission_sql_source in "${app_submission_sql_sources[@]}"; do
  fail "normal App source contains direct Submission SQL: $app_submission_sql_source"
done

replay_controller="$ROOT_DIR/services/app/app-web/src/main/java/com/ulticode/modules/event/replay/EventReplayController.java"
replay_annotations="$(grep -c '@PreAuthorize' "$replay_controller" || true)"
[[ "$replay_annotations" -eq 6 ]] || fail "EventReplayController must protect all six operations"

if grep -REn --include='*.java' \
  'INSERT[[:space:]]+INTO[[:space:]]+`?(notifications|notification_preferences|notification_delivery_ledger)|UPDATE[[:space:]]+`?(notifications|notification_preferences|notification_delivery_ledger)|DELETE[[:space:]]+FROM[[:space:]]+`?(notifications|notification_preferences|notification_delivery_ledger)|com\.ulticode\.modules\.notification\.(channel|consumer|dispatcher|ledger|mapper|service)([^[:alnum:]_]|$)|com\.ulticode\.modules\.notification\.entity\.(Notification|NotificationPreference)([^[:alnum:]_]|$)|com\.ulticode\.modules\.notification\.dto([^[:alnum:]_]|$)' \
  "$app_java" >/dev/null; then
  fail "App contains Notification-owned persistence or runtime implementation references"
fi

echo "Owner architecture source contract: PASS"
