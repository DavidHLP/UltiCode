#!/usr/bin/env bash
# scripts/dev/lib/validate.sh — trusted input validators shared by dev tooling.
#
# Internal module of scripts/dev/lib/common.sh; source common.sh, not this
# file. Helpers are frozen readonly -f so a hostile .env cannot replace them.

if ! [[ -v __ULTICODE_VALIDATE_SOURCED ]]; then
  declare -gr __ULTICODE_VALIDATE_SOURCED=1

  owner_schema() {
    case "$1" in
      auth|admin|app|notification|submission) return 0 ;;
      *) return 1 ;;
    esac
  }

  valid_identifier() {
    [[ "$1" =~ ^[A-Za-z0-9_]+$ ]]
  }

  valid_port() {
    [[ "$1" =~ ^[0-9]+$ ]] && ((1 <= 10#$1 && 10#$1 <= 65535))
  }

  valid_container_ref() {
    [[ "$1" =~ ^[A-Za-z0-9_.-]+$ ]]
  }

  readonly -f owner_schema valid_identifier valid_port valid_container_ref
fi
