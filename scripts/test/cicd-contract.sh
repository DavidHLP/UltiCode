#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
python3 "$ROOT_DIR/scripts/test/test_cicd_selection.py" -v
if python3 -c 'import yaml' >/dev/null 2>&1; then
  python3 "$ROOT_DIR/scripts/test/workflow-policy-contract.py"
elif [[ "${CI:-}" == 'true' || "${GITHUB_ACTIONS:-}" == 'true' ]]; then
  printf '%s\n' 'CI workflow policy requires scripts/ci/requirements.txt' >&2
  exit 1
else
  printf '%s\n' 'Workflow policy tests: SKIPPED (install scripts/ci/requirements.txt; mandatory in CI)'
fi
python3 "$ROOT_DIR/scripts/test/fetch-buildkit-provenance-contract.py"
bash "$ROOT_DIR/scripts/test/host-deploy-contract.sh"
bash "$ROOT_DIR/scripts/test/deploy-runtime-contract.sh"
bash "$ROOT_DIR/scripts/test/ssh-host-identity-contract.sh"
python3 "$ROOT_DIR/scripts/test/verified-release-package-contract.py" -v
python3 "$ROOT_DIR/scripts/test/test_acceptance_report.py" -v
node "$ROOT_DIR/scripts/test/browser-auth-diagnostics.test.mjs"
