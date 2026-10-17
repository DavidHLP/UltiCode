#!/usr/bin/env bash
set -euo pipefail

readonly services=(backend-auth backend-admin backend-app backend-submission backend-notification backend-search backend-judge console management)

validate_selection() {
  local raw="${1-all}" require_pair="${2:-false}" require_admin="${3:-false}" service normalized
  [[ "$require_pair" == true || "$require_pair" == false ]] || {
    echo "::error::pair requirement must be true or false" >&2
    return 2
  }
  [[ "$require_admin" == true || "$require_admin" == false ]] || {
    echo "::error::migration requirement must be true or false" >&2
    return 2
  }
  [[ -n "${raw//[[:space:],]/}" && "$raw" != *$'\n'* && "$raw" != *$'\r'* ]] || {
    echo "::error::service selection must be a non-empty single line" >&2
    return 1
  }
  normalized="${raw//,/ }"
  read -r -a selected <<< "$normalized"
  [[ ${#selected[@]} -gt 0 ]] || return 0
  [[ "${selected[0]}" != all || ${#selected[@]} -eq 1 ]] || {
    echo "::error::'all' cannot be combined with individual services" >&2
    return 1
  }
  [[ "${selected[0]}" != all ]] || return 0

  local -A seen=()
  for service in "${selected[@]}"; do
    case "$service" in
      backend-auth|backend-admin|backend-app|backend-submission|backend-notification|backend-search|backend-judge|console|management) ;;
      *) echo "::error::Invalid deployable service: $service" >&2; return 1 ;;
    esac
    [[ -z "${seen[$service]:-}" ]] || {
      echo "::error::Duplicate deployable service: $service" >&2
      return 1
    }
    seen[$service]=1
  done

  if [[ "$require_admin" == true && -z "${seen[backend-admin]:-}" ]]; then
    echo "::error::backend-admin must be included when migrations run" >&2
    return 1
  fi

  if [[ "$require_pair" == true ]]; then
    if [[ -n "${seen[backend-admin]:-}" && -z "${seen[backend-app]:-}" ]]; then
      echo "::error::backend-app must be included when backend-admin is selected" >&2
      return 1
    fi
    if [[ -n "${seen[backend-app]:-}" && -z "${seen[backend-admin]:-}" ]]; then
      echo "::error::backend-admin must be included when backend-app is selected" >&2
      return 1
    fi
  fi
}

case "${1:-}" in
  validate) validate_selection "${2-all}" "${3:-false}" "${4:-false}" ;;
  list) printf '%s\n' "${services[@]}" ;;
  *) echo "usage: $0 {validate <selection> [require-admin-app-pair]|list}" >&2; exit 2 ;;
esac
