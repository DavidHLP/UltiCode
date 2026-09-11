#!/usr/bin/env bash
set -euo pipefail

# P2-DEVLITE-004/005 + P7-GATE-001: fake the external CLIs and prove that
# up/status/logs/health, stop, and doctor consume one scope graph. No process,
# container, or service is started.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CONTRACT_FAILURE_PREFIX="devstack control contract failed"
# shellcheck source=scripts/test/lib/contract-harness.sh
source "$ROOT_DIR/scripts/test/lib/contract-harness.sh"
# shellcheck source=scripts/dev/devstack-manifest.sh
source "$ROOT_DIR/scripts/dev/devstack-manifest.sh"

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
FAKE_BIN="$TMP_DIR/bin"
PM2_CAPTURE="$TMP_DIR/pm2.args"
DOCKER_CAPTURE="$TMP_DIR/docker.args"
CURL_CAPTURE="$TMP_DIR/curl.args"
mkdir -p "$FAKE_BIN"
: > "$PM2_CAPTURE"
: > "$DOCKER_CAPTURE"
: > "$CURL_CAPTURE"
touch "$TMP_DIR/.env"

cat > "$FAKE_BIN/pm2" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "$PM2_CAPTURE"
active=,${PM2_ACTIVE_APPS},
case "${1:-}" in
  describe|pid)
    app="${2:-}"
    [[ "$active" == *",$app,"* ]] || exit 1
    if [[ "$1" == pid ]]; then printf '12345\n'; fi
    exit 0
    ;;
  jlist)
    case "${PM2_JLIST_MODE:-online}" in
      fail) exit 1 ;;
      malformed) printf '{"broken"\n'; exit 0 ;;
    esac
    printf '['
    first=true
    IFS=',' read -ra apps <<< "$PM2_ACTIVE_APPS"
    for app in "${apps[@]}"; do
      [[ -n "$app" ]] || continue
      [[ "$first" == true ]] || printf ','
      if [[ "${PM2_JLIST_MODE:-online}" == missing-fields ]]; then
        printf '{"name":"%s","pid":12345,"pm2_env":{}}' "$app"
      elif [[ "${PM2_JLIST_MODE:-online}" == invalid-restarts ]]; then
        printf '{"name":"%s","pid":12345,"pm2_env":{"status":"online","unstable_restarts":"bad"}}' "$app"
      else
        printf '{"name":"%s","pid":12345,"pm2_env":{"status":"%s","unstable_restarts":%s}}' \
          "$app" "${PM2_STATUS:-online}" "${PM2_RESTARTS:-0}"
      fi
      first=false
    done
    printf ']\n'
    ;;
  *) exit 0 ;;
esac
EOF

cat > "$FAKE_BIN/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "$DOCKER_CAPTURE"
case "${1:-}" in
  ps) exit 0 ;;
  inspect) printf 'healthy\n' ;;
  image) exit 0 ;;
  compose) exit 0 ;;
  *) exit 0 ;;
esac
EOF

cat > "$FAKE_BIN/curl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "$CURL_CAPTURE"
printf '000\n'
EOF
chmod +x "$FAKE_BIN/pm2" "$FAKE_BIN/docker" "$FAKE_BIN/curl"
export PM2_CAPTURE DOCKER_CAPTURE CURL_CAPTURE
export PM2_ACTIVE_APPS="$(devstack_apps_csv "${DEVSTACK_ALL_APPS[@]}")"
export ENV_FILE="$TMP_DIR/.env"
export PATH="$FAKE_BIN:$PATH"

scope="app-journey"
expected_apps="$(devstack_scope_apps "$scope")"
expected_infra="$(devstack_infra_for_scope "$scope")"

# Resolver readiness records drive the fake HTTP probes; this checks every
# selected HTTP port while leaving PM2-only worker readiness untouched.
while IFS='|' read -r app kind port path; do
  [[ "$kind" == http ]] || continue
  curl --max-time 1 "http://127.0.0.1:${port}${path}" >/dev/null
  grep -F -- "http://127.0.0.1:${port}${path}" "$CURL_CAPTURE" >/dev/null \
    || fail "curl did not capture resolver readiness endpoint for $app"
done < <(devstack_readiness_for_selection "$scope" "$expected_apps")

# The PM2 adapter owns state parsing and fail-closed readiness semantics for
# both up.sh and doctor.sh. Exercise the real callers through the fake CLI.
first_app="${expected_apps%%,*}"
assert_pm2_state() {
  local mode="$1" status="$2" restarts="$3" expected_ready="$4" path_override="${5:-$PATH}"
  local expected_json_restarts="$restarts"
  [[ "$expected_json_restarts" == unknown ]] && expected_json_restarts=null
  PM2_JLIST_MODE="$mode" PM2_STATUS="$status" PM2_RESTARTS="$restarts" \
    PATH="$path_override" NO_COLOR=1 \
    bash "$ROOT_DIR/scripts/dev/doctor.sh" --scope "$scope" --json >"$TMP_DIR/pm2-$mode.json"
  node - "$TMP_DIR/pm2-$mode.json" "$first_app" "$status" "$expected_json_restarts" <<'EOF'
const fs = require('fs')
const [jsonPath, appName, expectedStatus, expectedRestarts] = process.argv.slice(2)
const report = JSON.parse(fs.readFileSync(jsonPath, 'utf8'))
const app = report.apps.find((item) => item.name === appName)
if (!app) throw new Error(`PM2 app missing: ${appName}`)
if (app.status !== expectedStatus) throw new Error(`PM2 status: ${app.status} != ${expectedStatus}`)
if (String(app.restarts) !== expectedRestarts) throw new Error(`PM2 restarts: ${app.restarts} != ${expectedRestarts}`)
EOF

  if PM2_JLIST_MODE="$mode" PM2_STATUS="$status" PM2_RESTARTS="$restarts" \
    PATH="$path_override" bash -c \
      'source "$1/scripts/dev/lib/pm2.sh"; pm2_load_records; pm2_record_is_online "$2"' \
      bash "$ROOT_DIR" "$first_app"; then
    actual_ready=ready
  else
    actual_ready=not-ready
  fi
  [[ "$actual_ready" == "$expected_ready" ]] \
    || fail "PM2 readiness for $mode: $actual_ready != $expected_ready"
}

assert_pm2_state online online 0 ready
assert_pm2_state stopped stopped 0 not-ready
assert_pm2_state high-restarts online 5 not-ready
assert_pm2_state missing-fields unknown unknown not-ready
assert_pm2_state invalid-restarts online unknown not-ready
assert_pm2_state malformed unknown unknown not-ready
assert_pm2_state fail unknown unknown not-ready
assert_pm2_state online unknown unknown not-ready
assert_pm2_state tool-missing absent 0 not-ready "/usr/bin:/bin"
printf 'PM2 state adapter contract: PASS (online/stopped/restarts/malformed/missing/read-failure/tool-missing)\n'

# status and health are explicit up.sh actions and delegate to doctor with the
# same scope. Their machine outputs must match exactly.
NO_COLOR=1 bash "$ROOT_DIR/scripts/dev/up.sh" status --scope "$scope" --json >"$TMP_DIR/status.json"
NO_COLOR=1 bash "$ROOT_DIR/scripts/dev/up.sh" health --scope "$scope" --json >"$TMP_DIR/health.json"
cmp -s "$TMP_DIR/status.json" "$TMP_DIR/health.json" \
  || fail 'up status and health resolved different machine service sets'

node - "$TMP_DIR/status.json" "$expected_apps" "$expected_infra" <<'EOF'
const fs = require('fs')
const [jsonPath, expectedApps, expectedInfra] = process.argv.slice(2)
const report = JSON.parse(fs.readFileSync(jsonPath, 'utf8'))
const apps = report.apps.map((item) => item.name).join(',')
const infra = report.infra.map((item) => item.service).join(',')
if (apps !== expectedApps) throw new Error(`doctor apps differ: ${apps} != ${expectedApps}`)
if (infra !== expectedInfra) throw new Error(`doctor infra differ: ${infra} != ${expectedInfra}`)
if (JSON.stringify(report).includes('secret-value')) throw new Error('doctor emitted a secret')
EOF

# The old mode alias must select the full resolver graph, not silently fall
# back to the default dev-lite scope.
NO_COLOR=1 bash "$ROOT_DIR/scripts/dev/doctor.sh" --mode dev-full --json >"$TMP_DIR/mode-full-doctor.json" 2>"$TMP_DIR/mode-full-doctor.err"
node - "$TMP_DIR/mode-full-doctor.json" <<'EOF'
const fs = require('fs')
const report = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'))
if (report.scope !== 'dev-full') throw new Error(`mode alias scope: ${report.scope}`)
if (report.apps.length !== 9) throw new Error(`mode alias app count: ${report.apps.length}`)
if (!report.infra.some((item) => item.service === 'meilisearch')) throw new Error('mode alias omitted MeiliSearch')
EOF

# logs receives the same ordered app set; no unselected Search/Admin/Management
# process may leak into a named app journey.
: > "$PM2_CAPTURE"
bash "$ROOT_DIR/scripts/dev/up.sh" logs --scope "$scope" >/dev/null
log_line="$(sed -n '$p' "$PM2_CAPTURE")"
[[ "$log_line" == logs\ * ]] || fail "up logs did not call pm2 logs: $log_line"
for app in ${expected_apps//,/ }; do
  [[ "$log_line" == *"$app"* ]] || fail "up logs omitted selected app $app"
done
for app in ulticode-admin ulticode-search ulticode-9003; do
  [[ "$log_line" != *"$app"* ]] || fail "up logs included unselected app $app"
done

# stop with a named scope deletes selected apps only. The active fake PM2 state
# includes every app, so an accidental all-app loop is observable.
: > "$PM2_CAPTURE"
bash "$ROOT_DIR/scripts/dev/stop.sh" --scope "$scope" >/dev/null
for app in ${expected_apps//,/ }; do
  grep -F -- "delete $app" "$PM2_CAPTURE" >/dev/null \
    || fail "stop omitted selected app $app"
done
for app in ulticode-admin ulticode-search ulticode-9003; do
  ! grep -F -- "delete $app" "$PM2_CAPTURE" >/dev/null \
    || fail "stop touched unselected app $app"
done

: > "$PM2_CAPTURE"
bash "$ROOT_DIR/scripts/dev/stop.sh" --mode dev-full >/dev/null 2>"$TMP_DIR/mode-full-stop.err"
for app in ${DEVSTACK_DEV_FULL_APPS[*]}; do
  grep -F -- "delete $app" "$PM2_CAPTURE" >/dev/null \
    || fail "dev-full mode alias stop omitted $app"
done

# The scope graph itself is stable and includes the worker ports that the old
# doctor omitted.
[[ "$(devstack_app_port ulticode-notification)" == 9105 ]]
[[ "$(devstack_app_port ulticode-submission)" == 9106 ]]
[[ "$(devstack_app_port ulticode-judge)" == 9104 ]]
[[ "$(devstack_app_port ulticode-search)" == 9107 ]]
[[ "$(devstack_app_port ulticode-core)" == 9108 ]]

printf 'DevStack lifecycle control contract: PASS\n'
