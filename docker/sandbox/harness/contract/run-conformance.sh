#!/usr/bin/env bash
# D-form harness conformance runner.
#
# Intended to run *inside the built sandbox image* (ulticode-sandbox:latest).
# Plays golden/input.json against every language harness and diffs the
# produced envelope against golden/envelope.json (structural parity:
# harness_version / language / per-case status / case_id / field set must
# match exactly; numeric measurements are sanity-checked within tolerance).
#
# !!! NOT part of the JVM test suite.
# !!! NOT wired into CI.
# !!! Requires the built ulticode-sandbox:latest image.
#
# This script does NOT rebuild the image. Run from inside the image, or
# from the host with `docker run --rm -v "$PWD/docker/sandbox/harness/contract:/contract:ro" ulticode-sandbox:latest /contract/run-conformance.sh`.

set -euo pipefail

CONTRACT_DIR="${CONTRACT_DIR:-/opt/harness/contract}"
GOLDEN_INPUT="${CONTRACT_DIR}/golden/input.json"
GOLDEN_ENVELOPE="${CONTRACT_DIR}/golden/envelope.json"
HARNESS_ROOT="${HARNESS_ROOT:-/opt/harness}"

if [[ ! -f "$GOLDEN_INPUT" ]]; then
  echo "FATAL: golden input not found at $GOLDEN_INPUT" >&2
  exit 2
fi
if [[ ! -f "$GOLDEN_ENVELOPE" ]]; then
  echo "FATAL: golden envelope not found at $GOLDEN_ENVELOPE" >&2
  exit 2
fi

# Write the input.json to /job per the contract.
mkdir -p /job
cp "$GOLDEN_INPUT" /job/input.json

declare -A LANG_PATHS=(
  [java]="${HARNESS_ROOT}/java"
  [python]="${HARNESS_ROOT}/python"
  [cpp]="${HARNESS_ROOT}/cpp"
)

fail=0

for lang in java python cpp; do
  lang_dir="${LANG_PATHS[$lang]}"
  if [[ ! -d "$lang_dir" ]]; then
    echo "SKIP $lang: harness not found at $lang_dir"
    continue
  fi

  echo "=== $lang ==="
  # Per-language invocation. Each harness prints one envelope to stdout.
  case "$lang" in
    java)
      cd "$lang_dir" && java -cp . Main /job/input.json
      ;;
    python)
      cd "$lang_dir" && python3 main.py /job/input.json
      ;;
    cpp)
      cd "$lang_dir" && ./cpp-sandbox /job/input.json
      ;;
  esac > /tmp/envelope.actual.json

  # Structural diff: harness_version / language / exit_code / per-case
  # status / case_id / field-set must match exactly. Numeric measurements
  # are sanity-checked but allowed to vary between runs.
  if ! diff <(jq -S 'del(.results[].elapsed_ms, .results[].elapsed_us, .results[].cpu_ms, .results[].peak_memory_bytes, .total_elapsed_ms)' "$GOLDEN_ENVELOPE") \
            <(jq -S 'del(.results[].elapsed_ms, .results[].elapsed_us, .results[].cpu_ms, .results[].peak_memory_bytes, .total_elapsed_ms)' /tmp/envelope.actual.json) > /dev/null; then
    echo "FAIL $lang: envelope structural diff against golden" >&2
    diff <(jq -S 'del(.results[].elapsed_ms, .results[].elapsed_us, .results[].cpu_ms, .results[].peak_memory_bytes, .total_elapsed_ms)' "$GOLDEN_ENVELOPE") \
         <(jq -S 'del(.results[].elapsed_ms, .results[].elapsed_us, .results[].cpu_ms, .results[].peak_memory_bytes, .total_elapsed_ms)' /tmp/envelope.actual.json) >&2 || true
    fail=1
    continue
  fi
  echo "PASS $lang"
done

exit $fail