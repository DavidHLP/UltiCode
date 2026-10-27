#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
[[ "${ACCEPTANCE_DISPOSABLE:-}" == true ]] || { echo 'Explicit disposable acceptance opt-in required' >&2; exit 2; }
[[ ! -e .env ]] || { echo 'Fresh acceptance requires a checkout without .env; existing state is preserved' >&2; exit 2; }
[[ -z "${DOCKER_HOST:-}" ]] || { echo 'Acceptance refuses an overridden Docker host' >&2; exit 2; }
endpoint="$(docker context inspect --format '{{.Endpoints.docker.Host}}')"
[[ "$endpoint" == unix://* ]] || { echo 'Acceptance requires a local Unix Docker endpoint' >&2; exit 2; }
mkdir -p .local/acceptance
exec 9>.local/acceptance.lock
flock -n 9 || { echo 'Another acceptance stack owns this checkout' >&2; exit 2; }
python3 - <<'PY'
import socket
for port in [23306, 26379, 28848, 29848, 9000, 9001, 9002, 9003, 9004, 9101, 9102, 9103, 9105, 9106, 9107, 9104]:
    with socket.socket() as s:
        try: s.bind(('127.0.0.1', port))
        except OSError: raise SystemExit(f'Acceptance port {port} is occupied; no existing stack was changed')
PY
export COMPOSE_PROJECT_NAME="ulticode-acceptance-$(date +%s)-$$"
export PM2_HOME="$ROOT/.local/acceptance/pm2"
export ENV_FILE="$ROOT/.env"
[[ -z "$(docker ps -aq --filter "label=com.docker.compose.project=$COMPOSE_PROJECT_NAME")" ]]
[[ -z "$(docker volume ls -q --filter "name=${COMPOSE_PROJECT_NAME}_")" ]]
unset MIGRATION_MYSQL_CONTAINER MIGRATION_MYSQL_CONTAINER_PORT MYSQL_CONTAINER \
  SUBMISSION_MIGRATION_DB_USER SUBMISSION_MIGRATION_DB_PASSWORD DEV_LOCAL_OWNER_BASELINE \
  DEV_LOCAL_OWNER_BASELINE_CONFIRM 2>/dev/null || true
phase=environment
prepared=false
finish() {
  status=$?
  trap - EXIT
  # Report generation must never prevent cleanup of our disposable processes.
  set +e
  export ACCEPTANCE_PHASE="$phase" ACCEPTANCE_EXIT="$status"
  python3 - <<'PY'
import json,os,re
from pathlib import Path
kinds=set()
for path in Path('.local/acceptance').glob('private-*.log'):
    text=path.read_text(errors='replace')[-1000000:]
    kinds.update(re.findall(r'\b(?:org|com|java|javax|jakarta|io)\.(?:[A-Za-z_$][\w$]*\.)*[A-Za-z_$][\w$]*(?:Exception|Error)\b',text))
Path('.local/acceptance/installation.json').write_text(json.dumps({
 'evidence':'disposable-real-services', 'phase':os.environ['ACCEPTANCE_PHASE'],
 'passed':os.environ['ACCEPTANCE_EXIT']=='0', 'project':os.environ['COMPOSE_PROJECT_NAME'],
 'raw_logs_published':False, 'exception_classes':sorted(kinds)[:20],
},indent=2)+'\n')
PY
  if [[ "$prepared" == true ]]; then
    # Stop only this newly created project/home. Keep volumes for local diagnosis;
    # hosted CI runners dispose of their own disks after the job.
    ./scripts/dev/stop.sh --scope full-stack > .local/acceptance/private-cleanup.log 2>&1 || true
  fi
  exit "$status"
}
trap finish EXIT
./scripts/dev/init-env.sh > .local/acceptance/private-environment.log 2>&1
python3 - <<'PY'
from pathlib import Path
import secrets,re
p=Path('.env');s=p.read_text()
values={'DEV_SEED_ADMIN_USERNAME':'acceptance_manager', 'DEV_SEED_ADMIN_EMAIL':'acceptance-manager@localhost.test',
 'DEV_SEED_ADMIN_PASSWORD':'CiA1_'+secrets.token_hex(20), 'DEV_SEED_ADMIN_ROLE':'SUPER_ADMIN',
 'JAVA_TOOL_OPTIONS':'"-Xms64m -Xmx384m"', 'SANDBOX_HOST_DIR':str(Path('.local/acceptance/sandbox').resolve())}
for key,value in values.items():s=re.sub(r'^'+key+r'=.*$',key+'='+value,s,flags=re.M)
s+='\nACCEPTANCE_USERNAME=acceptance_user\nACCEPTANCE_PASSWORD=CiA1_'+secrets.token_hex(20)+'\n'
p.write_text(s);p.chmod(0o600)
PY
set -a
source .env
set +a
export ACCEPTANCE_MANAGEMENT_USERNAME="$DEV_SEED_ADMIN_USERNAME" ACCEPTANCE_MANAGEMENT_PASSWORD="$DEV_SEED_ADMIN_PASSWORD"
export ACCEPTANCE_DISPOSABLE=true
# Disallow ambient migration routing; only generated loopback fixture settings win.
export MIGRATION_DB_HOST="$DB_HOST" MIGRATION_DB_PORT="$DB_PORT" MIGRATION_DB_NAME=ulticode
export MIGRATION_DB_USER=root MIGRATION_DB_PASSWORD="$MYSQL_ROOT_PASSWORD"
phase=sandbox
mkdir -p "$SANDBOX_HOST_DIR"
docker build -t ulticode-sandbox:base-17 -f docker/sandbox/Dockerfile.base docker/sandbox > .local/acceptance/private-sandbox.log 2>&1
mise exec java@zulu-17.68.203.0 -- bash docker/sandbox/harness/build.sh --no-docker >> .local/acceptance/private-sandbox.log 2>&1
docker build -t "$COMPOSE_PROJECT_NAME-sandbox" docker/sandbox >> .local/acceptance/private-sandbox.log 2>&1
export SANDBOX_IMAGE="$COMPOSE_PROJECT_NAME-sandbox"
# Persist only generated test configuration; launcher loads this file itself.
printf '\nSANDBOX_IMAGE=%s\n' "$SANDBOX_IMAGE" >> .env
phase=fresh-migrations
# Compose v2 names this newly owned service deterministically; the migrator
# still validates that the running container publishes the generated endpoint.
export MIGRATION_MYSQL_CONTAINER="$COMPOSE_PROJECT_NAME-mysql-1"
prepared=true
./scripts/dev/up.sh --scope full-stack --prepare-submission-owner --skip-install > .local/acceptance/private-prepare.log 2>&1
export MIGRATION_MYSQL_CONTAINER="$(docker compose --project-directory . --env-file .env -f docker/docker-compose.yml -f docker/docker-compose.dev.yml ps -q mysql)"
export MYSQL_CONTAINER="$MIGRATION_MYSQL_CONTAINER"
phase=backfill-and-cutover
./scripts/runbooks/submission-schema-cutover.sh backfill > .local/acceptance/private-cutover.log 2>&1
SUBMISSION_BACKFILL_CONFIRM=I_UNDERSTAND_SUBMISSION_BACKFILL \
 SUBMISSION_BACKFILL_QUIESCE_CONFIRM=I_UNDERSTAND_SUBMISSION_BACKFILL_QUIESCE_ALL_WRITERS \
 ./scripts/runbooks/submission-schema-cutover.sh backfill --execute >> .local/acceptance/private-cutover.log 2>&1
./scripts/runbooks/submission-schema-cutover.sh verify >> .local/acceptance/private-cutover.log 2>&1
SUBMISSION_CUTOVER_CONFIRM=I_UNDERSTAND_SUBMISSION_CUTOVER \
 SUBMISSION_CUTOVER_QUIESCE_CONFIRM=I_UNDERSTAND_SUBMISSION_QUIESCE_ALL_WRITERS \
 ./scripts/runbooks/submission-schema-cutover.sh cutover --execute >> .local/acceptance/private-cutover.log 2>&1
./scripts/runbooks/submission-schema-cutover.sh verify >> .local/acceptance/private-cutover.log 2>&1
export SUBMISSION_CUTOVER_COMPLETE=true
phase=launch
# Follow the documented second startup: migrations are idempotent, and the
# supported DEV-LOCAL App seed adapter runs only when migrations are enabled.
./scripts/dev/up.sh --scope full-stack --skip-infra --skip-install --rebuild > .local/acceptance/private-launch.log 2>&1
phase=fixtures
node scripts/test/acceptance-fixtures.mjs
phase=browser-base
node scripts/test/browser-acceptance.mjs
phase=browser-business
node scripts/test/browser-business-acceptance.mjs
phase=complete
echo 'Fresh install and real business acceptance: PASS'
