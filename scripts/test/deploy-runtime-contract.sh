#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TEST_DIR="$(mktemp -d)"
trap 'rm -rf "$TEST_DIR"' EXIT
mkdir -p "$TEST_DIR/bin" "$TEST_DIR/home" "$TEST_DIR/runner"
export PATH="$TEST_DIR/bin:$PATH" HOME="$TEST_DIR/home" RUNNER_TEMP="$TEST_DIR/runner"
export GITHUB_WORKSPACE="$ROOT_DIR" GITHUB_TOKEN_VALUE=test-token DEPLOYMENT_COMMIT=deadbeef
EXPECTED_SCHEMA_MANIFEST_CHECKSUM="$(printf a%.0s {1..64})"
export EXPECTED_SCHEMA_MANIFEST_CHECKSUM COSIGN_CERT_IDENTITY=test COSIGN_CERT_ISSUER=test \
  DEPLOY_REGISTRY=ghcr.io DEPLOY_ACTOR=test GITHUB_RUN_ID=deploy-fault-test GITHUB_RUN_ATTEMPT=1
export FAKE_SSH_LOG="$TEST_DIR/ssh.log"

cat >"$TEST_DIR/bin/ssh" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
command="${@: -1}"
printf '%s\n' "$command" >>"$FAKE_SSH_LOG"
if [[ -n "${SSH_FAIL_MATCH:-}" && "$command" == *"$SSH_FAIL_MATCH"* ]]; then exit 42; fi
case "$command" in
  *'docker compose'*' ps -q backend-'*) printf 'abcdef0123456789\n' ;;
  *"docker inspect -f '{{.State.Running}}'"*) printf 'true\n' ;;
  *"docker inspect -f '{{.State.Health.Status}}'"*) printf 'unhealthy\n'; exit 1 ;;
  *'assert-admin-backup-drained.sh'*) exit 0 ;;
  *) exit 0 ;;
esac
SH
chmod +x "$TEST_DIR/bin/ssh"
cat >"$TEST_DIR/bin/ssh-keygen" <<'SH'
#!/usr/bin/env bash
exit 0
SH
chmod +x "$TEST_DIR/bin/ssh-keygen"
mkdir -p "$HOME/.ssh"
printf 'fake pinned key\\n' >"$HOME/.ssh/known_hosts"

base_env=(DEPLOY_HOST=deploy.example.test DEPLOY_PORT=22 DEPLOY_USER=deployer DEPLOY_PATH=/opt/ulticode
  DEPLOYMENT_DESCRIPTOR_FILE=.local/deploy/deployment.json
  SUPPLY_CHAIN_EXCEPTION_FILE='' SUPPLY_CHAIN_EXCEPTION_EXPIRES_AT=''
  DEPLOY_SERVICES='backend-admin backend-app' SKIP_MIGRATIONS=false
  IMAGE_REF_LIST=$'BACKEND_AUTH_IMAGE_REF=ghcr.io/acme/backend-auth@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\nBACKEND_ADMIN_IMAGE_REF=ghcr.io/acme/backend-admin@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\nBACKEND_APP_IMAGE_REF=ghcr.io/acme/backend-app@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\nBACKEND_SUBMISSION_IMAGE_REF=ghcr.io/acme/backend-submission@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\nBACKEND_SEARCH_IMAGE_REF=ghcr.io/acme/backend-search@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\nBACKEND_NOTIFICATION_IMAGE_REF=ghcr.io/acme/backend-notification@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\nBACKEND_JUDGE_IMAGE_REF=ghcr.io/acme/backend-judge@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\nCONSOLE_IMAGE_REF=ghcr.io/acme/console@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\nMANAGEMENT_IMAGE_REF=ghcr.io/acme/management@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
  MIGRATION_DB_HOST=db MIGRATION_DB_PORT=3306 MIGRATION_DB_NAME=ulticode
  MIGRATION_DB_USER=migrator MIGRATION_DB_PASSWORD=test-password
  MIGRATION_MYSQL_BIN=mysql MIGRATION_MYSQL_CONTAINER='' MIGRATION_MYSQL_CONTAINER_PORT=3306
  SUBMISSION_MIGRATION_DB_USER=submission_migrator SUBMISSION_MIGRATION_DB_PASSWORD=test-password
  AUTH_DB_USER=auth_rw ADMIN_DB_USER=admin_rw APP_DB_USER=app_rw
  NOTIFICATION_DB_USER=notification_rw SUBMISSION_DB_USER=submission_rw
  MIGRATION_REPORT_DIR=.local/migration-audit MIGRATION_LOCK_FILE=.local/migration-audit/owner-migrations.lock)

# Preflight failure must happen before any writer stop/migration/pull/up.
: >"$FAKE_SSH_LOG"
if env "${base_env[@]}" SSH_FAIL_MATCH='deployment-integrity.sh preflight' \
  "$ROOT_DIR/scripts/deploy/deploy.sh" validate-release-and-deployment-integrity-before-mutation \
  >"$TEST_DIR/preflight.log" 2>&1; then
  echo 'deploy-runtime-contract: preflight fault unexpectedly passed' >&2; exit 1
fi
cat "$TEST_DIR/preflight.log" >&2
if ! grep -Fq 'deployment-integrity.sh preflight' "$FAKE_SSH_LOG"; then
  printf 'deploy-runtime-contract: expected remote preflight; SSH log follows:\n' >&2
  cat "$FAKE_SSH_LOG" >&2
  exit 1
fi
if grep -Eq 'compose stop|owner-migration-manifest.sh migrate|compose.*(pull|up)' "$FAKE_SSH_LOG"; then
  echo 'deploy-runtime-contract: preflight failure allowed mutation' >&2
  exit 1
fi

# Quiesce records the old state. Migration failure is intentionally not auto-restarted.
: >"$FAKE_SSH_LOG"
env "${base_env[@]}" "$ROOT_DIR/scripts/deploy/deploy.sh" quiesce-legacy-owner-writers >/dev/null
state="$RUNNER_TEMP/ulticode-backend-admin-quiesce-deploy-fault-test-1.state"
grep -q '^phase=stopped$' "$state"

# If stopping the second writer fails, recover only the first already-stopped writer.
export GITHUB_RUN_ID=quiesce-fault
: >"$FAKE_SSH_LOG"
if env "${base_env[@]}" SSH_FAIL_MATCH='stop backend-app' \
  "$ROOT_DIR/scripts/deploy/deploy.sh" quiesce-legacy-owner-writers 2>/dev/null; then
  echo 'deploy-runtime-contract: quiesce fault unexpectedly passed' >&2; exit 1
fi
if ! env "${base_env[@]}" "$ROOT_DIR/scripts/deploy/deploy.sh" restore-quiesced-services-after-deploy-failure; then
  echo 'deploy-runtime-contract: pre-migration quiesce recovery failed' >&2; exit 1
fi
grep -q 'docker start' "$FAKE_SSH_LOG"
export GITHUB_RUN_ID=deploy-fault-test
: >"$FAKE_SSH_LOG"
if env "${base_env[@]}" SSH_FAIL_MATCH='owner-migration-manifest.sh migrate' \
  "$ROOT_DIR/scripts/deploy/deploy.sh" run-ordered-owner-database-migrations 2>/dev/null; then
  echo 'deploy-runtime-contract: migration fault unexpectedly passed' >&2; exit 1
fi
if env "${base_env[@]}" "$ROOT_DIR/scripts/deploy/deploy.sh" restore-quiesced-services-after-deploy-failure 2>"$TEST_DIR/recovery.err"; then
  echo 'deploy-runtime-contract: post-migration recovery unexpectedly restarted services' >&2; exit 1
fi
grep -q 'owner migration began' "$TEST_DIR/recovery.err"
if grep -q 'docker start' "$FAKE_SSH_LOG"; then
  echo 'deploy-runtime-contract: migration failure restarted a quiesced service' >&2
  exit 1
fi

# Pull/up failure after migrations also must not restart the old containers.
: >"$FAKE_SSH_LOG"
printf 'phase=migration-complete\nadmin_container_ids=abcdef0123456789\nadmin_prior_running=1\napp_container_ids=abcdef0123456789\napp_prior_running=1\n' >"$state"
if env "${base_env[@]}" IMAGE_REF_LIST='BACKEND_AUTH_IMAGE=x' DEPLOYMENT_COMMIT=deadbeef \
  EXPECTED_SCHEMA_MANIFEST_CHECKSUM=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa \
  COSIGN_CERT_IDENTITY=test COSIGN_CERT_ISSUER=test SUPPLY_CHAIN_EXCEPTION_FILE='' \
  SUPPLY_CHAIN_EXCEPTION_EXPIRES_AT='' DEPLOYMENT_DESCRIPTOR_FILE=.local/deploy/deployment.json \
  SSH_FAIL_MATCH='docker compose' "$ROOT_DIR/scripts/deploy/deploy.sh" pull-and-deploy-services 2>/dev/null; then
  echo 'deploy-runtime-contract: pull/up fault unexpectedly passed' >&2; exit 1
fi
grep -Fq 'docker compose' "$FAKE_SSH_LOG"
if env "${base_env[@]}" "$ROOT_DIR/scripts/deploy/deploy.sh" restore-quiesced-services-after-deploy-failure 2>"$TEST_DIR/recovery.err"; then
  echo 'deploy-runtime-contract: post-migration pull/up recovery unexpectedly passed' >&2; exit 1
fi
if grep -q 'docker start' "$FAKE_SSH_LOG"; then
  echo 'deploy-runtime-contract: migration failure restarted a quiesced service' >&2
  exit 1
fi

# Health failure must mark the descriptor FAILED and return non-zero.
: >"$FAKE_SSH_LOG"
if env HOST=deploy.example.test USER=deployer DEPLOY_PORT=22 ATTEMPTS=1 INTERVAL=0 \
  SERVICES='backend-auth inspect - -' DEPLOYMENT_PATH=/opt/ulticode \
  DEPLOYMENT_DESCRIPTOR_FILE=.local/deploy/deployment.json DEPLOYMENT_COMMIT=deadbeef \
  EXPECTED_SCHEMA_MANIFEST_CHECKSUM=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa \
  "$ROOT_DIR/scripts/deploy/health.sh" check-service-health 2>/dev/null; then
  echo 'deploy-runtime-contract: unhealthy service unexpectedly passed' >&2; exit 1
fi
grep -q 'DEPLOYMENT_STATUS=FAILED' "$FAKE_SSH_LOG"

printf 'deploy-runtime-contract: PASS (preflight, quiesce, migration, pull/up, health faults)\n'
