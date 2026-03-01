#!/bin/bash
# Rust code runner for the sandbox
# Compiles and executes Rust code with provided inputs

set -e

TIME_LIMIT_MS="${TIME_LIMIT_MS:-15000}"
INPUT_FILE="${INPUT_FILE:-/sandbox/input/args.json}"
CODE_FILE="${CODE_FILE:-/sandbox/code/solution.rs}"
WORK_DIR="/sandbox/code"
OUTPUT_BIN="${WORK_DIR}/solution"

# Function to output JSON error
output_error() {
    local status="$1"
    local error="$2"
    local time="${3:-0}"
    local memory="${4:-0}"
    echo "{\"status\": \"${status}\", \"error\": \"${error}\", \"time\": ${time}, \"memory\": ${memory}}"
    exit 1
}

cd "$WORK_DIR"

# Compile Rust code with optimizations
COMPILE_START=$(date +%s%3N)
if ! rustc -O -o "$OUTPUT_BIN" "$CODE_FILE" 2>compile_error.txt; then
    COMPILE_ERROR=$(cat compile_error.txt | head -20)
    output_error "Compile Error" "$COMPILE_ERROR"
fi
COMPILE_TIME=$(($(date +%s%3N) - COMPILE_START))

# Prepare input
if [ -f "$INPUT_FILE" ]; then
    STDIN_INPUT=$(cat "$INPUT_FILE" | python3 -c "
import json, sys
data = json.load(sys.stdin)
args = data.get('args', [])
for arg in args:
    if isinstance(arg, list):
        print(' '.join(map(str, arg)))
    else:
        print(str(arg))
" 2>/dev/null || echo "")
else
    STDIN_INPUT=""
fi

# Execute with timeout
TIMEOUT_SEC=$((TIME_LIMIT_MS / 1000 + 2))
EXEC_START=$(date +%s%3N)

if [ -n "$STDIN_INPUT" ]; then
    OUTPUT=$(echo "$STDIN_INPUT" | timeout ${TIMEOUT_SEC}s "$OUTPUT_BIN" 2>&1) || EXEC_STATUS=$?
else
    OUTPUT=$(timeout ${TIMEOUT_SEC}s "$OUTPUT_BIN" 2>&1) || EXEC_STATUS=$?
fi

EXEC_TIME=$(($(date +%s%3N) - EXEC_START))
EXEC_STATUS=${EXEC_STATUS:-0}

# Get memory usage
MEMORY_MB=$(ps -o rss= -p $$ 2>/dev/null | awk '{printf "%.2f", $1/1024}' || echo "0")

if [ $EXEC_STATUS -eq 124 ]; then
    output_error "Time Limit Exceeded" "Execution timed out" "$TIME_LIMIT_MS" "$MEMORY_MB"
elif [ $EXEC_STATUS -ne 0 ]; then
    CLEANED_OUTPUT=$(echo "$OUTPUT" | grep -v "^$" | head -20)
    output_error "Runtime Error" "$CLEANED_OUTPUT" "$EXEC_TIME" "$MEMORY_MB"
else
    CLEANED_OUTPUT=$(echo "$OUTPUT" | sed 's/"/\\"/g' | tr '\n' '\\n' | sed 's/\\n$//')
    printf '{"status": "Success", "output": "%s", "time": %d, "memory": %.2f}\n' \
        "$CLEANED_OUTPUT" \
        "$EXEC_TIME" \
        "$MEMORY_MB"
fi
