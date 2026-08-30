#!/usr/bin/env bash
set -euo pipefail

# P2-OBS-001: publish a release marker to Grafana without putting deployment
# credentials or image metadata into the repository. The release manifest is
# read-only evidence produced by Docker Publish.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RELEASE_ID="${RELEASE_ID:-}"
ENVIRONMENT="${OBS_ENVIRONMENT:-local}"
GRAFANA_URL="${GRAFANA_URL:-}"
GRAFANA_API_TOKEN="${GRAFANA_API_TOKEN:-}"
IMAGE_REF_MANIFEST="${IMAGE_REF_MANIFEST:-}"
CURL_BIN="${CURL_BIN:-curl}"
JQ_BIN="${JQ_BIN:-jq}"

die() {
  echo "[observability-release] FAIL: $*" >&2
  exit 1
}

[[ -n "$RELEASE_ID" ]] || die "RELEASE_ID is required"
[[ "$RELEASE_ID" =~ ^[A-Za-z0-9._/-]+$ ]] || die "RELEASE_ID contains unsupported characters"
[[ "$ENVIRONMENT" =~ ^[A-Za-z0-9._-]+$ ]] || die "OBS_ENVIRONMENT contains unsupported characters"
[[ "$GRAFANA_URL" =~ ^https?://[A-Za-z0-9._:-]+/?$ ]] \
  || die "GRAFANA_URL must be an http(s) origin without a path"
[[ -n "$GRAFANA_API_TOKEN" ]] || die "GRAFANA_API_TOKEN is required"
[[ -f "$IMAGE_REF_MANIFEST" && -s "$IMAGE_REF_MANIFEST" ]] \
  || die "IMAGE_REF_MANIFEST is missing or empty"
command -v "$CURL_BIN" >/dev/null 2>&1 || die "curl is required"
command -v "$JQ_BIN" >/dev/null 2>&1 || die "jq is required"
IMAGE_REF_LIST="$(< "$IMAGE_REF_MANIFEST")" \
  "$ROOT_DIR/scripts/runbooks/image-reference-policy.sh" validate >/dev/null

payload="$($JQ_BIN -n \
  --arg release "$RELEASE_ID" \
  --arg environment "$ENVIRONMENT" \
  --rawfile manifest "$IMAGE_REF_MANIFEST" \
  '{time:((now * 1000)|floor), tags:["release", $environment, $release], text:("UltiCode release " + $release + "\n" + $manifest)}')"

"$CURL_BIN" --fail-with-body --silent --show-error \
  -H "Authorization: Bearer $GRAFANA_API_TOKEN" \
  -H 'Content-Type: application/json' \
  --data "$payload" \
  "${GRAFANA_URL%/}/api/annotations" >/dev/null

printf '[observability-release] PASS release=%s environment=%s\n' "$RELEASE_ID" "$ENVIRONMENT"
