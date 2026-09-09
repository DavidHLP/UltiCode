#!/usr/bin/env bash
set -euo pipefail

# Source-only ownership and seam assertions. The architecture gate owns
# execution policy; this child owns the repository's source-contract registry.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RULES_FILE="$ROOT_DIR/scripts/test/owner-architecture-source-contract.rules"

fail() {
  echo "owner-architecture-source-contract: FAIL: $*" >&2
  exit 1
}

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

while IFS=$'\t' read -r kind text files; do
  [[ -z "$kind" || "$kind" == \#* ]] && continue
  IFS='|' read -r -a paths <<< "$files"
  for file in "${paths[@]}"; do
    case "$kind" in
      exists) assert_exists "$file" ;;
      absent) assert_absent "$file" ;;
      contains) contains "$file" "$text" ;;
      not_contains) not_contains "$file" "$text" ;;
      compact_contains) compact_contains "$file" "$text" ;;
      *) fail "unknown registry rule: $kind" ;;
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
