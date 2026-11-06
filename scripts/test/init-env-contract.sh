#!/usr/bin/env bash
# Exercise the real generator in a disposable repository fixture; no Docker or network.
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
FIXTURE="$(mktemp -d)"
trap 'rm -rf "$FIXTURE"' EXIT
mkdir -p "$FIXTURE/scripts" "$FIXTURE/docker"
cp -R "$ROOT_DIR/scripts/dev" "$FIXTURE/scripts/"
cp -R "$ROOT_DIR/docker/redis" "$FIXTURE/docker/"
chmod +x "$FIXTURE/docker/redis/generate-users-acl.sh"
GENERATOR="$FIXTURE/scripts/dev/init-env.sh"
bash "$GENERATOR" --output "$FIXTURE/first.env" >"$FIXTURE/first.log" 2>&1
bash "$GENERATOR" --output "$FIXTURE/second.env" >"$FIXTURE/second.log" 2>&1
python3 - "$FIXTURE" <<'PY'
import pathlib, stat, sys
root = pathlib.Path(sys.argv[1])
def values(name):
    path = root / name
    assert stat.S_IMODE(path.stat().st_mode) == 0o600, 'Environment must be private (0600)'
    return dict(line.split('=', 1) for line in path.read_text().splitlines()
                if '=' in line and not line.startswith('#'))
a, b = values('first.env'), values('second.env')
for env in (a,b):
    assert env['DEV_SEED_ADMIN_USERNAME'] == env['VITE_TEST_USERNAME'] == 'admin'
    assert len(env['DEV_SEED_ADMIN_PASSWORD']) >= 32, 'Expected strong generated password'
    assert env['DEV_SEED_ADMIN_PASSWORD'] == env['VITE_TEST_PASSWORD'], 'Browser password out of sync'
assert a['DEV_SEED_ADMIN_PASSWORD'] != b['DEV_SEED_ADMIN_PASSWORD'], 'Password reused across runs'
for log in root.glob('*.log'):
    for env in (a,b):
        assert env['DEV_SEED_ADMIN_PASSWORD'] not in log.read_text(), 'Password leaked to output'
PY
cp "$FIXTURE/first.env" "$FIXTURE/saved.env"
bash "$GENERATOR" --output "$FIXTURE/first.env" >"$FIXTURE/preserve.log" 2>&1
cmp -s "$FIXTURE/saved.env" "$FIXTURE/first.env" || { echo 'Existing environment changed without --force' >&2; exit 1; }
bash "$GENERATOR" --output "$FIXTURE/first.env" --force >"$FIXTURE/force.log" 2>&1
python3 - "$FIXTURE" <<'PY'
import pathlib, stat, sys
root=pathlib.Path(sys.argv[1])
def values(name):
    return dict(line.split('=',1) for line in (root/name).read_text().splitlines()
                if '=' in line and not line.startswith('#'))
a,b=values('saved.env'),values('first.env')
assert a['DEV_SEED_ADMIN_PASSWORD'] != b['DEV_SEED_ADMIN_PASSWORD'], '--force did not rotate generated password'
assert b['DEV_SEED_ADMIN_PASSWORD'] == b['VITE_TEST_PASSWORD'], '--force broke browser sync'
assert stat.S_IMODE((root/'first.env').stat().st_mode)==0o600
assert b['DEV_SEED_ADMIN_PASSWORD'] not in (root/'force.log').read_text()
PY
printf '%s\n' 'PASS: init-env randomness, browser sync, private mode, safe output, preservation and --force'
