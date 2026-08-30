#!/usr/bin/env bash
set -euo pipefail

# P2-SC-001: validate and verify the immutable image set used by production
# delivery. The registry, signer, promotion decision, and deployment host
# remain external; this boundary only accepts an explicit digest set and
# fails closed when evidence is missing.

ACTION="${1:-validate}"
case "$ACTION" in
  validate|verify) ;;
  *)
    echo "Usage: $0 {validate|verify}" >&2
    exit 2
    ;;
esac

IMAGE_NAMES=(
  BACKEND_AUTH_IMAGE_REF
  BACKEND_ADMIN_IMAGE_REF
  BACKEND_APP_IMAGE_REF
  BACKEND_SUBMISSION_IMAGE_REF
  BACKEND_SEARCH_IMAGE_REF
  BACKEND_NOTIFICATION_IMAGE_REF
  BACKEND_JUDGE_IMAGE_REF
  CONSOLE_IMAGE_REF
  MANAGEMENT_IMAGE_REF
)

die() {
  echo "[image-policy] FAIL: $*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "required command not found: $1"
}

service_for_name() {
  local owner="${1%_IMAGE_REF}"
  owner="${owner,,}"
  printf '%s\n' "${owner//_/-}"
}

validate_exception_config() {
  local exception_file="${SUPPLY_CHAIN_EXCEPTION_FILE:-}"
  local expires_at="${SUPPLY_CHAIN_EXCEPTION_EXPIRES_AT:-}"
  local expires_epoch now_epoch

  if [[ -n "$exception_file" || -n "$expires_at" ]]; then
    [[ -n "$exception_file" && -n "$expires_at" ]] \
      || die "SUPPLY_CHAIN_EXCEPTION_FILE and SUPPLY_CHAIN_EXCEPTION_EXPIRES_AT must be set together"
    [[ -f "$exception_file" && -s "$exception_file" ]] \
      || die "supply-chain exception file is missing or empty"
    [[ "$expires_at" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ]] \
      || die "SUPPLY_CHAIN_EXCEPTION_EXPIRES_AT must be UTC YYYY-MM-DDTHH:MM:SSZ"
    expires_epoch="$(date -u -d "$expires_at" +%s 2>/dev/null)" \
      || die "invalid supply-chain exception expiry"
    now_epoch="$(date -u +%s)"
    (( expires_epoch > now_epoch )) \
      || die "supply-chain exception has expired"
  fi
}

declare -A IMAGE_REFS=()

load_image_refs() {
  local line name value image_name service prefix
  local count=0
  local image_list="${IMAGE_REF_LIST:-}"
  if [[ -z "$image_list" ]]; then
    for name in "${IMAGE_NAMES[@]}"; do
      image_list+="${name}=${!name:-}"$'\n'
    done
  fi
  [[ -n "$image_list" ]] || die "IMAGE_REF_LIST is required"

  while IFS= read -r line || [[ -n "$line" ]]; do
    [[ -z "$line" ]] && continue
    [[ "$line" != *$'\r'* ]] || die "IMAGE_REF_LIST contains a carriage return"
    [[ "$line" == *=* ]] || die "image reference entry is missing '='"
    name="${line%%=*}"
    value="${line#*=}"
    [[ "$name" =~ ^[A-Z][A-Z0-9_]+$ ]] || die "invalid image reference name"
    [[ -z "${IMAGE_REFS[$name]+present}" ]] || die "duplicate image reference: $name"

    case " ${IMAGE_NAMES[*]} " in
      *" $name "*) ;;
      *) die "unapproved image reference name: $name" ;;
    esac
    [[ "$value" =~ ^[A-Za-z0-9][A-Za-z0-9._/-]*@sha256:[0-9a-f]{64}$ ]] \
      || die "${name} must be an immutable image@sha256:digest reference"
    [[ "$value" != *:latest ]] || die "${name} cannot use latest"

    image_name="${value%@*}"
    service="$(service_for_name "$name")"
    [[ "${image_name##*/}" == "$service" ]] \
      || die "${name} must point to the ${service} image"
    prefix="${IMAGE_REPOSITORY_PREFIX:-}"
    if [[ -n "$prefix" ]]; then
      prefix="${prefix%/}"
      [[ "$image_name" == "$prefix/$service" ]] \
        || die "${name} is outside IMAGE_REPOSITORY_PREFIX"
    fi
    IMAGE_REFS["$name"]="$value"
    count=$((count + 1))
  done <<< "$image_list"

  for name in "${IMAGE_NAMES[@]}"; do
    [[ -n "${IMAGE_REFS[$name]+present}" ]] \
      || die "missing image reference: $name"
  done
  [[ "$count" -eq "${#IMAGE_NAMES[@]}" ]] \
    || die "expected exactly ${#IMAGE_NAMES[@]} image references"
}

validate() {
  validate_exception_config
  load_image_refs
  printf '[image-policy] PASS immutable image set (%d refs)\n' "${#IMAGE_NAMES[@]}"
}

verify() {
  local name ref repo_digests
  local docker_bin="${DOCKER_BIN:-docker}"
  local cosign_bin="${COSIGN_BIN:-cosign}"
  local trivy_bin="${TRIVY_BIN:-trivy}"
  local identity="${COSIGN_CERT_IDENTITY:-}"
  local issuer="${COSIGN_CERT_ISSUER:-}"
  local exception_file="${SUPPLY_CHAIN_EXCEPTION_FILE:-}"
  local -a trivy_args

  validate
  [[ -n "$identity" ]] || die "COSIGN_CERT_IDENTITY is required"
  [[ -n "$issuer" ]] || die "COSIGN_CERT_ISSUER is required"
  require_command "$docker_bin"
  require_command "$cosign_bin"
  require_command "$trivy_bin"

  trivy_args=(image --exit-code 1 --ignore-unfixed --severity HIGH,CRITICAL)
  [[ -n "$exception_file" ]] && trivy_args+=(--ignorefile "$exception_file")

  for name in "${IMAGE_NAMES[@]}"; do
    ref="${IMAGE_REFS[$name]}"
    printf '[image-policy] verifying %s\n' "$name"
    "$docker_bin" pull "$ref" >/dev/null
    repo_digests="$("$docker_bin" image inspect --format '{{range .RepoDigests}}{{println .}}{{end}}' "$ref")"
    grep -Fxq -- "$ref" <<< "$repo_digests" \
      || die "$name resolved to an unexpected local digest"

    "$cosign_bin" verify \
      --certificate-identity "$identity" \
      --certificate-oidc-issuer "$issuer" \
      "$ref" >/dev/null
    "$cosign_bin" verify-attestation \
      --type spdxjson \
      --certificate-identity "$identity" \
      --certificate-oidc-issuer "$issuer" \
      "$ref" >/dev/null
    "$cosign_bin" verify-attestation \
      --type slsaprovenance \
      --certificate-identity "$identity" \
      --certificate-oidc-issuer "$issuer" \
      "$ref" >/dev/null
    "$trivy_bin" "${trivy_args[@]}" "$ref"
  done
  printf '[image-policy] PASS digest, signature, SPDX, provenance, and vulnerability checks\n'
}

case "$ACTION" in
  validate) validate ;;
  verify) verify ;;
esac
