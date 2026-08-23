#!/usr/bin/env bash
# scripts/dev/lib/confirm.sh — write-action confirmation predicates.
#
# Internal module of scripts/dev/lib/common.sh; source common.sh, not this
# file. Helpers are frozen readonly -f so a hostile .env cannot replace them.

if ! [[ -v __ULTICODE_CONFIRM_SOURCED ]]; then
  declare -gr __ULTICODE_CONFIRM_SOURCED=1

  require_write_confirmation() {
    # require_write_confirmation <EXECUTE_VALUE> <CONFIRM_VAR_NAME> <EXPECTED_TOKEN>
    # True when a write action carries both --execute and its confirmation token.
    [[ "$1" == "--execute" && "${!2:-}" == "$3" ]]
  }

  gate_confirmed() {
    # gate_confirmed <CONFIRM_VAR_NAME> <EXPECTED_TOKEN> — true when the named
    # confirmation variable holds exactly the expected token. Use this for
    # gates without an --execute flag; pair bespoke refusal messages with it.
    [[ "${!1:-}" == "$2" ]]
  }

  readonly -f require_write_confirmation gate_confirmed
fi
