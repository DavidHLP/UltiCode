#!/usr/bin/env bash
set -euo pipefail

# P2-SC-001 contract: keep production references, Dockerfile bases, external
# Actions, and delivery evidence fail-closed. Runtime registry verification is
# exercised by image-reference-policy.sh in deploy/publish environments.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
POLICY="$ROOT_DIR/scripts/runbooks/image-reference-policy.sh"

CONTRACT_FAILURE_PREFIX="supply-chain-contract: FAIL"
# shellcheck source=scripts/test/lib/contract-harness.sh
source "$ROOT_DIR/scripts/test/lib/contract-harness.sh"

# shellcheck source=scripts/test/lib/assertions.sh
source "$ROOT_DIR/scripts/test/lib/assertions.sh"

[[ -x "$POLICY" ]] || fail "image-reference-policy.sh is not executable"

valid_digest="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
valid_refs=""
for service in \
  backend-auth backend-admin backend-app backend-submission backend-search \
  backend-notification backend-judge console management; do
  variable="${service^^}"
  variable="${variable//-/_}_IMAGE_REF"
  valid_refs+="${variable}=ghcr.io/example/ulticode/${service}@sha256:${valid_digest}"$'\n'
done
IMAGE_REF_LIST="$valid_refs" "$POLICY" validate >/dev/null

exception_file="$(mktemp)"
fake_bin="$(mktemp -d)"
fake_log="$(mktemp)"
trap 'rm -rf "$fake_bin"; rm -f "$exception_file" "$fake_log"' EXIT
printf 'CVE-0000-0000\n' > "$exception_file"
if IMAGE_REF_LIST="$valid_refs" \
  SUPPLY_CHAIN_EXCEPTION_FILE="$exception_file" \
  SUPPLY_CHAIN_EXCEPTION_EXPIRES_AT='2020-01-01T00:00:00Z' \
  "$POLICY" validate >/dev/null 2>&1; then
  fail "expired vulnerability exception was accepted"
fi

if IMAGE_REF_LIST="${valid_refs/backend-auth@sha256:${valid_digest}/backend-auth:v1}" \
  "$POLICY" validate >/dev/null 2>&1; then
  fail "tagged image reference was accepted"
fi
if IMAGE_REF_LIST="${valid_refs%$'\n'}"$'\nCONSOLE_IMAGE_REF=ghcr.io/example/ulticode/console@sha256:'"${valid_digest}" \
  "$POLICY" validate >/dev/null 2>&1; then
  fail "duplicate image reference was accepted"
fi

cat > "$fake_bin/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
if [[ "${1:-}" == "pull" ]]; then
  exit 0
fi
if [[ "${1:-}" == "image" && "${2:-}" == "inspect" ]]; then
  printf '%s\n' "${5:?image reference argument is missing}"
  exit 0
fi
exit 2
EOF
cat > "$fake_bin/cosign" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf 'cosign %s\n' "$*" >> "$FAKE_LOG"
EOF
cat > "$fake_bin/trivy" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf 'trivy %s\n' "$*" >> "$FAKE_LOG"
EOF
chmod +x "$fake_bin/docker" "$fake_bin/cosign" "$fake_bin/trivy"
FAKE_LOG="$fake_log" IMAGE_REF_LIST="$valid_refs" \
  COSIGN_CERT_IDENTITY='https://github.com/example/ulticode/.github/workflows/docker-publish.yml@refs/heads/main' \
  COSIGN_CERT_ISSUER='https://token.actions.githubusercontent.com' \
  DOCKER_BIN="$fake_bin/docker" COSIGN_BIN="$fake_bin/cosign" TRIVY_BIN="$fake_bin/trivy" \
  "$POLICY" verify >/dev/null
grep -Fq 'cosign verify-attestation' "$fake_log" \
  || fail "verify did not require signed attestations"
grep -Fq 'trivy image --exit-code 1' "$fake_log" \
  || fail "verify did not require the vulnerability scan"

contains docker/docker-compose.prod.yml 'image: mysql@sha256:0255b469f0135a0236d672d60e3154ae2f4538b146744966d96440318cc822c6'
contains docker/docker-compose.prod.yml 'image: redis@sha256:ff02b58f971e7d7d156a1267e283fcbbeee91773b6aa36c49dac28ecfe28eadf'
contains docker/docker-compose.prod.yml 'image: nacos/nacos-server@sha256:d70b20546fe59aeb86add0754db1dbc3a3067363e18599329c5163d0d8cd9cd8'
contains docker/docker-compose.prod.yml 'image: getmeili/meilisearch@sha256:bbdb723dbf83ae431ad5c10bf4970a517ca1fce1dcda9bdb99691576e963a6e5'
for variable in \
  BACKEND_AUTH BACKEND_ADMIN BACKEND_APP BACKEND_SUBMISSION BACKEND_SEARCH \
  BACKEND_NOTIFICATION BACKEND_JUDGE CONSOLE MANAGEMENT; do
  contains docker/docker-compose.prod.yml "${variable}_IMAGE_REF:?${variable}_IMAGE_REF is required"
done
not_contains docker/docker-compose.prod.yml 'IMAGE_TAG'
not_contains docker/docker-compose.prod.yml ':latest'

for dockerfile in services/Dockerfile apps/console/Dockerfile apps/management/Dockerfile; do
  contains "$dockerfile" 'RUN apk upgrade --no-cache'
  while IFS= read -r from_line; do
    [[ "$from_line" == *'@sha256:'* ]] || fail "$dockerfile has an unpinned base: $from_line"
  done < <(grep -E '^FROM ' "$ROOT_DIR/$dockerfile")
done

for workflow in "$ROOT_DIR"/.github/workflows/*.yml; do
  while IFS= read -r uses_line; do
    [[ -z "$uses_line" ]] && continue
    action_ref="${uses_line#*@}"
    action_ref="${action_ref%%[[:space:]]*}"
    [[ "$uses_line" == *'./'* || "$action_ref" =~ ^[0-9a-f]{40}$ ]] \
      || fail "external Action is not pinned to a full SHA: ${uses_line#*uses: }"
  done < <(grep -E '^[[:space:]]*uses:' "$workflow")
done

python3 - "$ROOT_DIR/.github/workflows/docker-publish.yml" <<'PY'
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import textwrap

workflow = Path(sys.argv[1]).read_text()
step = re.search(r'      - name: Normalize image repository\n        id: normalize\n        run: echo "image_name=\$\{GITHUB_REPOSITORY,,}" >> "\$GITHUB_OUTPUT"\n', workflow)
assert step, 'publish must normalize the image repository before metadata'
assert step.start() < workflow.index('      - name: Extract metadata')
with tempfile.NamedTemporaryFile() as output:
    subprocess.run(['bash', '-euo', 'pipefail', '-c', 'echo "image_name=${GITHUB_REPOSITORY,,}" >> "$GITHUB_OUTPUT"'],
                   env={**os.environ, 'GITHUB_REPOSITORY': 'DavidHLP/UltiCode',
                        'GITHUB_OUTPUT': output.name}, check=True)
    assert Path(output.name).read_text() == 'image_name=davidhlp/ulticode\n'
image = '${{ env.REGISTRY }}/${{ steps.normalize.outputs.image_name }}/${{ matrix.service.name }}'
references = re.findall(r'^\s+(?:images|image-ref|IMAGE_REF): (.+)$', workflow, re.M)
assert references == [image] + [image + '@${{ steps.build.outputs.digest }}'] * 3, \
    'metadata, scan, signing and manifest must share the normalized repository'
print('mixed-case publish repository normalization: PASS')
scan = workflow.split('      - name: Scan pushed image\n', 1)[1].split('      - name:', 1)[0]
assert "exit-code: '1'" in scan and 'continue-on-error' not in scan
upload = workflow.split('      - name: Upload image scan report\n', 1)[1].split('      - name:', 1)[0]
assert "always()" in upload and "steps.scan.outcome" in upload
assert 'path: ${{ runner.temp }}/${{ matrix.service.name }}.trivy.json' in upload
assert workflow.index('      - name: Upload image scan report') < workflow.index('      - name: Sign image')
print('failed scans retain reports and block signing: PASS')
PY

contains .github/workflows/docker-publish.yml 'sbom: true'
for entry in '_docker.yml:verify' 'docker-publish.yml:publish'; do
  workflow="${entry%:*}"
  scope="${entry#*:}"
  contains ".github/workflows/$workflow" "cache-from: type=gha,scope=$scope-\${{ matrix.service.name }}"
  contains ".github/workflows/$workflow" "cache-to: type=gha,mode=max,scope=$scope-\${{ matrix.service.name }},ignore-error=true"
  not_contains ".github/workflows/$workflow" 'continue-on-error:'
done
contains .github/workflows/docker-publish.yml 'provenance: mode=max'
contains .github/workflows/docker-publish.yml 'aquasecurity/trivy-action@'
contains .github/workflows/docker-publish.yml 'sigstore/cosign-installer@'
contains .github/workflows/docker-publish.yml 'cosign sign --yes'
contains .github/workflows/docker-publish.yml 'cosign attest --yes'
contains .github/workflows/docker-publish.yml 'steps.build.outputs.digest'
not_contains .github/workflows/docker-publish.yml 'type=raw,value=latest'

contains .github/actions/host-deploy/action.yml 'image_refs:'
contains .github/actions/host-deploy/action.yml 'image-reference-policy.sh verify'
not_contains .github/actions/host-deploy/action.yml 'image_tag'
not_contains .github/actions/host-deploy/action.yml 'service_tags'
contains .github/workflows/cd-deploy.yml 'image_refs:'
contains .github/workflows/cd-rollback.yml 'image_refs:'
not_contains .github/workflows/cd-deploy.yml 'image_tag'
not_contains .github/workflows/cd-rollback.yml 'image_tag'
not_contains .github/workflows/cd-rollback.yml 'verify-tag'

printf 'immutable production images, pinned Actions, publish evidence, and deploy policy contract: PASS\n'
