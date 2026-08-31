#!/usr/bin/env bash
set -euo pipefail

# P3-JUDGE-001: production Judge must use a dedicated remote/rootless Docker
# daemon over client TLS. The socket profile is deliberately dev-only. Static
# checks prove the repository boundary; the optional remote smoke is read-only
# and requires operator-supplied endpoint/cert/image values.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PROD_COMPOSE="$ROOT_DIR/docker-compose.prod.yml"
DEV_COMPOSE="$ROOT_DIR/docker-compose.judge-dev.yml"
EXECUTOR="$ROOT_DIR/services/judge-runtime/src/main/java/com/ulticode/modules/submission/sandbox/executor/SandboxExecutorImpl.java"
DOCKER_BIN="${DOCKER_BIN:-docker}"

fail() {
  echo "judge-sandbox-contract: FAIL: $*" >&2
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
  ! grep -Fq -- "$text" "$ROOT_DIR/$file" || fail "$file contains forbidden value: $text"
}

contains docker-compose.prod.yml 'DOCKER_HOST=${JUDGE_DOCKER_HOST:?JUDGE_DOCKER_HOST is required for production sandbox}'
contains docker-compose.prod.yml 'DOCKER_TLS_VERIFY=1'
contains docker-compose.prod.yml 'DOCKER_CERT_PATH=/run/secrets/judge-docker'
contains docker-compose.prod.yml 'JUDGE_DOCKER_CERT_DIR:?JUDGE_DOCKER_CERT_DIR is required for production sandbox}:/run/secrets/judge-docker:ro'
contains docker-compose.prod.yml 'SANDBOX_HOST_DIR:?SANDBOX_HOST_DIR is required for production sandbox}'
not_contains docker-compose.prod.yml 'docker.sock'
not_contains docker-compose.prod.yml 'JUDGE_DOCKER_SOCK'
not_contains docker-compose.prod.yml 'DOCKER_GID'
not_contains docker-compose.prod.yml 'group_add:'

contains docker-compose.judge-dev.yml 'profiles: [judge-socket]'
contains docker-compose.judge-dev.yml 'JUDGE_DOCKER_SOCK'
contains docker-compose.judge-dev.yml ':/var/run/docker.sock'
contains docker-compose.judge-dev.yml 'DOCKER_HOST='
contains docker-compose.judge-dev.yml 'DOCKER_TLS_VERIFY='
contains docker-compose.judge-dev.yml 'DOCKER_CERT_PATH='
contains docker-compose.judge-dev.yml 'Never include this file in a production deployment'

for argument in \
  '"--network", "none"' \
  '"--cap-drop", "ALL"' \
  '"--read-only"' \
  '"--user", "1000:1000"' \
  '"--pids-limit"' \
  '"--memory"' \
  '"--cpus"' \
  '"seccomp=" + resolveSeccompProfileFilePath()' \
  'SECCOMP_NO_NEW_PRIVS'; do
  contains services/judge-runtime/src/main/java/com/ulticode/modules/submission/sandbox/executor/SandboxExecutorImpl.java "$argument"
done
contains services/judge-runtime/src/main/java/com/ulticode/modules/submission/sandbox/executor/SandboxExecutorImpl.java '"--tmpfs"'
contains services/judge-runtime/src/main/java/com/ulticode/modules/submission/sandbox/executor/SandboxExecutorImpl.java '":/job:ro"'
contains services/judge-runtime/src/main/java/com/ulticode/modules/submission/sandbox/executor/SandboxExecutorImpl.java 'dFormEnvelopeCodec.parseDEnvelope'
contains services/judge-runtime/src/main/java/com/ulticode/modules/submission/sandbox/executor/SandboxExecutorImpl.java 'outcome.timedOut()'
contains services/judge-runtime/src/main/java/com/ulticode/modules/submission/sandbox/executor/SandboxExecutorImpl.java 'SHARED_WORKSPACE_POSIX'
contains services/judge-runtime/src/main/java/com/ulticode/modules/submission/sandbox/executor/SandboxExecutorImpl.java 'Files.setPosixFilePermissions(jobDir, SHARED_WORKSPACE_POSIX)'
contains services/judge-runtime/src/main/java/com/ulticode/modules/submission/sandbox/executor/SandboxExecutorImpl.java '--cidfile'
contains services/judge-runtime/src/main/java/com/ulticode/modules/submission/sandbox/executor/SandboxExecutorImpl.java 'cmd.add("--name");'
contains services/judge-runtime/src/main/java/com/ulticode/modules/submission/sandbox/executor/SandboxExecutorImpl.java 'cmd.add("--cidfile");'
contains services/judge-runtime/src/test/java/com/ulticode/modules/submission/sandbox/executor/DockerProcessRunnerTest.java 'timeoutRemovesContainerThroughCidfile'
contains services/judge-runtime/src/main/java/com/ulticode/modules/submission/sandbox/executor/DockerProcessRunner.java 'CIDFILE_OPTION'
contains services/judge-runtime/src/main/java/com/ulticode/modules/submission/sandbox/executor/DockerProcessRunner.java 'cleanupTimedOutContainer'
contains services/judge-runtime/src/main/java/com/ulticode/modules/submission/sandbox/executor/DockerProcessRunner.java '"rm", "-f"'
contains services/judge-runtime/src/main/java/com/ulticode/modules/submission/sandbox/executor/DockerProcessRunner.java 'Files.readString'
contains services/judge-runtime/src/main/java/com/ulticode/modules/submission/sandbox/executor/DockerProcessRunner.java 'destroyForcibly'
contains docker-compose.prod.yml 'test -d \"$${SANDBOX_HOST_DIR}/workspace\"'
contains docker-compose.judge-dev.yml 'SANDBOX_SECCOMP_PROFILE=${SANDBOX_HOST_DIR:-/opt/ulticode/sandbox}/seccomp-profile.json'
not_contains .github/actions/host-deploy/action.yml 'docker/sandbox/harness/build.sh'
contains .github/actions/host-deploy/action.yml 'judge-sandbox-preflight.sh'
contains scripts/runbooks/judge-sandbox-preflight.sh 'config", "--format", "json"'
contains scripts/runbooks/judge-sandbox-preflight.sh 'rootless'
contains scripts/runbooks/judge-sandbox-preflight.sh 'workspace'
contains scripts/runbooks/judge-sandbox-preflight.sh 'seccomp-profile.json'
contains scripts/runbooks/judge-sandbox-preflight.sh 'cert.pem'
contains .github/actions/host-deploy/action.yml 'IMAGE_REF_LIST: ${{ inputs.image_refs }}'
contains .github/actions/host-deploy/action.yml 'IMAGE_REF_LIST=$(shell_quote'
contains scripts/runbooks/judge-sandbox-preflight.sh 'compose_env'
contains docker-compose.prod.yml 'SANDBOX_HOST_DIR=${SANDBOX_HOST_DIR:?SANDBOX_HOST_DIR is required for production sandbox}'
contains services/judge-runtime/src/main/java/com/ulticode/modules/submission/sandbox/executor/SandboxExecutorImpl.java 'cmd.add("--volume");'
contains services/judge-runtime/src/main/java/com/ulticode/modules/submission/sandbox/executor/SandboxExecutorImpl.java '"--security-opt"'
preflight_line="$(grep -n 'judge-sandbox-preflight.sh' "$ROOT_DIR/.github/actions/host-deploy/action.yml" | cut -d: -f1)"
migration_line="$(grep -n 'Run ordered owner database migrations' "$ROOT_DIR/.github/actions/host-deploy/action.yml" | cut -d: -f1)"
[[ "$preflight_line" -lt "$migration_line" ]] || fail 'Judge remote preflight must precede migrations'

printf 'production Judge remote TLS/no-socket boundary: PASS\n'
printf 'sandbox resource, network, readonly, seccomp and result-channel controls: PASS\n'
printf 'development socket profile is explicit and isolated from production: PASS\n'

if [[ "${JUDGE_REMOTE_SMOKE:-0}" == "1" ]]; then
  [[ -n "${JUDGE_DOCKER_HOST:-}" ]] || fail 'JUDGE_DOCKER_HOST is required for remote smoke'
  [[ -n "${JUDGE_DOCKER_CERT_DIR:-}" ]] || fail 'JUDGE_DOCKER_CERT_DIR is required for remote smoke'
  [[ -d "$JUDGE_DOCKER_CERT_DIR" ]] || fail 'JUDGE_DOCKER_CERT_DIR does not exist'
  for file in ca.pem cert.pem key.pem; do
    [[ -s "$JUDGE_DOCKER_CERT_DIR/$file" ]] || fail "missing remote TLS material: $file"
  done
  [[ -n "${SANDBOX_IMAGE:-}" ]] || fail 'SANDBOX_IMAGE is required for remote smoke'
  "$DOCKER_BIN" --host "$JUDGE_DOCKER_HOST" --tlsverify \
    --tlscacert "$JUDGE_DOCKER_CERT_DIR/ca.pem" \
    --tlscert "$JUDGE_DOCKER_CERT_DIR/cert.pem" \
    --tlskey "$JUDGE_DOCKER_CERT_DIR/key.pem" version >/dev/null \
    || fail 'remote Docker TLS version check failed'
  security_options="$("$DOCKER_BIN" --host "$JUDGE_DOCKER_HOST" --tlsverify \
    --tlscacert "$JUDGE_DOCKER_CERT_DIR/ca.pem" \
    --tlscert "$JUDGE_DOCKER_CERT_DIR/cert.pem" \
    --tlskey "$JUDGE_DOCKER_CERT_DIR/key.pem" \
    info --format '{{json .SecurityOptions}}')" \
    || fail 'remote Docker TLS info check failed'
  [[ "$security_options" == *rootless* ]] \
    || fail 'remote Docker daemon is not proven rootless'
  "$DOCKER_BIN" --host "$JUDGE_DOCKER_HOST" --tlsverify \
    --tlscacert "$JUDGE_DOCKER_CERT_DIR/ca.pem" \
    --tlscert "$JUDGE_DOCKER_CERT_DIR/cert.pem" \
    --tlskey "$JUDGE_DOCKER_CERT_DIR/key.pem" image inspect "$SANDBOX_IMAGE" >/dev/null \
    || fail 'remote sandbox image is unavailable'
  printf 'remote/rootless Docker TLS smoke: PASS\n'
else
  printf 'remote/rootless Docker TLS smoke: BLOCKED_EXTERNAL (set JUDGE_REMOTE_SMOKE=1 with operator endpoint/certs/image)\n'
fi

printf 'judge-sandbox-contract: PASS\n'
