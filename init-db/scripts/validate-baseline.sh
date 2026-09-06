#!/usr/bin/env bash
# validate-baseline.sh — ensure baseline.sql matches a fully-migrated disposable DB (all 5 owners, per-schema)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BASELINE="$ROOT/init-db/baseline/baseline.sql"
if [ ! -f "$BASELINE" ]; then echo "[validate] $BASELINE not found — run ./init-db/scripts/generate-baseline.sh first"; exit 1; fi
echo "[validate] baseline exists: $(wc -l < "$BASELINE") lines, $(grep -c "CREATE TABLE" "$BASELINE") tables, $(grep -c "CREATE DATABASE" "$BASELINE") schemas"
for T in users problems submissions contests audit_logs audit_outbox; do if ! grep -q "CREATE TABLE.*\`$T\`" "$BASELINE"; then echo "[validate] warning: $T not found in baseline (may be owner-scoped)"; fi; done
echo "[validate] running disposable migration for comparison..."
TMP_DUMP=$(mktemp); TMP_BASELINE_PER_SCHEMA=$(mktemp); TMP_MIGRATED_PER_SCHEMA=$(mktemp)
cleanup() { rm -f "$TMP_DUMP" "$TMP_BASELINE_PER_SCHEMA" "$TMP_MIGRATED_PER_SCHEMA"; }
trap cleanup EXIT
# Generation owns disposable MySQL, ordered migration and history-free dumping;
# validation owns comparison only. Never regenerate the checked-in baseline here.
"$ROOT/init-db/scripts/generate-baseline.sh" "$TMP_DUMP" >/dev/null
# Per-schema validation: extract schema.table pairs (using USE + CREATE TABLE context)
extract_per_schema() {
  local file="$1"
  local out="$2"
  awk '
    /^CREATE DATABASE/ { if (match($0, /`[^`]+`/)) { db = substr($0, RSTART+1, RLENGTH-2) } next }
    /^USE / { if (match($0, /`[^`]+`/)) { db = substr($0, RSTART+1, RLENGTH-2) } next }
    /^CREATE TABLE/ { if (match($0, /`[^`]+`/)) { tbl = substr($0, RSTART+1, RLENGTH-2); print db"."tbl } }
  ' "$file" | sort > "$out"
}
extract_per_schema "$BASELINE" "$TMP_BASELINE_PER_SCHEMA"
extract_per_schema "$TMP_DUMP" "$TMP_MIGRATED_PER_SCHEMA"
echo "[validate] baseline per-schema tables: $(wc -l < "$TMP_BASELINE_PER_SCHEMA"), migrated per-schema tables: $(wc -l < "$TMP_MIGRATED_PER_SCHEMA")"
if diff -u "$TMP_BASELINE_PER_SCHEMA" "$TMP_MIGRATED_PER_SCHEMA"; then
  echo "[validate] PASS — all owner schemas per-table match (no duplicate-insensitive)"
  # Also ensure no flyway history leaked
  if grep -qE "flyway_(schema|post_owner)_history" "$BASELINE"; then echo "[validate] FAIL — baseline should not contain Flyway history"; exit 1; fi
  exit 0
else
  echo "[validate] FAIL — per-schema table set mismatch"
  exit 1
fi
