#!/bin/bash
# Java code runner for the sandbox
# Compiles and executes Java code with provided inputs

set -e

TIME_LIMIT_MS="${TIME_LIMIT_MS:-15000}"
INPUT_FILE="${INPUT_FILE:-/sandbox/input/args.json}"
CODE_FILE="${CODE_FILE:-/sandbox/code/Solution.java}"
WORK_DIR="/sandbox/code"

# Extract class name from the Java file
CLASS_NAME=$(grep -oP 'public\s+class\s+\K\w+' "$CODE_FILE" 2>/dev/null || echo "Solution")

# Function to output JSON error
output_error() {
    local status="$1"
    local error="$2"
    local time="${3:-0}"
    local memory="${4:-0}"
    echo "{\"status\": \"${status}\", \"error\": \"${error}\", \"time\": ${time}, \"memory\": ${memory}}"
    exit 1
}

# Function to output JSON success
output_success() {
    local output="$1"
    local time="$2"
    local memory="$3"
    printf '{"status": "Success", "output": "%s", "time": %d, "memory": %.2f}\n' \
        "$(echo "$output" | sed 's/"/\\"/g' | tr '\n' '\\n')" \
        "$time" \
        "$memory"
}

# Compile Java code
cd "$WORK_DIR"

COMPILE_START=$(date +%s%3N)
if ! javac "$CODE_FILE" 2>compile_error.txt; then
    COMPILE_ERROR=$(cat compile_error.txt | head -20)
    output_error "Compile Error" "$COMPILE_ERROR"
fi
COMPILE_TIME=$(($(date +%s%3N) - COMPILE_START))

# Detect memory limit from environment
MEMORY_LIMIT="${MEMORY_LIMIT:-512m}"

# Create input args file for Java
if [ -f "$INPUT_FILE" ]; then
    # Parse JSON args and convert to Java-friendly format
    ARGS=$(cat "$INPUT_FILE" | python3 -c "
import json, sys
data = json.load(sys.stdin)
args = data.get('args', [])
# Convert args to command line arguments
for arg in args:
    if isinstance(arg, (list, dict)):
        print(json.dumps(arg).replace('\"', '\\\\\"'))
    else:
        print(str(arg))
" 2>/dev/null || echo "")
else
    ARGS=""
fi

# Execute with timeout and memory limits
EXEC_START=$(date +%s%3N)

# Use timeout command to enforce time limits
TIMEOUT_SEC=$((TIME_LIMIT_MS / 1000 + 2))

# Run Java with security manager disabled and resource limits
OUTPUT=$(timeout ${TIMEOUT_SEC}s java \
    -Xmx${MEMORY_LIMIT} \
    -XX:+UseSerialGC \
    -Djava.security.manager=allow \
    "$CLASS_NAME" $ARGS 2>&1) || EXEC_STATUS=$?

EXEC_TIME=$(($(date +%s%3N) - EXEC_START))
EXEC_STATUS=${EXEC_STATUS:-0}

# Get memory usage (approximate)
MEMORY_MB=$(ps -o rss= -p $$ 2>/dev/null | awk '{printf "%.2f", $1/1024}' || echo "0")

if [ $EXEC_STATUS -eq 124 ]; then
    output_error "Time Limit Exceeded" "Execution timed out after ${TIMEOUT_SEC}s" "$TIME_LIMIT_MS"
elif [ $EXEC_STATUS -ne 0 ]; then
    # Clean up error output
    CLEANED_OUTPUT=$(echo "$OUTPUT" | grep -v "^$" | head -30)
    output_error "Runtime Error" "$CLEANED_OUTPUT" "$EXEC_TIME" "$MEMORY_MB"
else
    # Success - output the result
    CLEANED_OUTPUT=$(echo "$OUTPUT" | sed 's/"/\\"/g' | tr '\n' '\\n' | sed 's/\\n$//')
    printf '{"status": "Success", "output": "%s", "time": %d, "memory": %.2f}\n' \
        "$CLEANED_OUTPUT" \
        "$EXEC_TIME" \
        "$MEMORY_MB"
fi
