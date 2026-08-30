#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

for action in "$ROOT_DIR/.github/actions/host-deploy/action.yml" "$ROOT_DIR/.github/actions/host-health/action.yml"; do
  grep -Fq 'StrictHostKeyChecking=yes' "$action" || {
    echo "missing strict SSH host-key checking: $action" >&2
    exit 1
  }
  grep -Fq 'UserKnownHostsFile=' "$action" || {
    echo "missing explicit known_hosts file: $action" >&2
    exit 1
  }
  if grep -Fq 'StrictHostKeyChecking=no' "$action"; then
    echo "disabled SSH host-key checking: $action" >&2
    exit 1
  fi
  if grep -Fq 'ssh-keyscan' "$action"; then
    echo "automatic SSH host-key acceptance: $action" >&2
    exit 1
  fi
done

grep -Fq 'known_hosts:' "$ROOT_DIR/.github/workflows/cd-deploy.yml"
grep -Fq 'known_hosts:' "$ROOT_DIR/.github/workflows/cd-rollback.yml"
grep -Fq 'required: true' <(sed -n '/known_hosts:/,/^[[:space:]]*port:/p' "$ROOT_DIR/.github/actions/host-deploy/action.yml")
grep -Fq 'required: true' <(sed -n '/known_hosts:/,/^[[:space:]]*user:/p' "$ROOT_DIR/.github/actions/host-health/action.yml")

printf '%s\n' "SSH host identity contract: PASS"
