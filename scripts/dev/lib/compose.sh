#!/usr/bin/env bash
# scripts/dev/lib/compose.sh — one Compose command builder for dev tooling.
#
# Internal module of scripts/dev/lib/common.sh; source common.sh, not this
# file. The caller selects optional profiles and override files; the base
# development stack stays here.

if ! [[ -v __ULTICODE_COMPOSE_SOURCED ]]; then
  declare -gr __ULTICODE_COMPOSE_SOURCED=1

  devstack_compose_args() {
    local output_name="$1"
    shift
    local include_dev=true include_observability=false compose_file
    local -a extra_files=()
    local -n output="$output_name"

    while (($# > 0)); do
      case "$1" in
        --base-only)
          include_dev=false
          ;;
        --observability)
          include_observability=true
          ;;
        --*)
          echo "Unknown Compose option: $1" >&2
          return 2
          ;;
        *)
          extra_files+=("$1")
          ;;
      esac
      shift
    done

    output=(
      docker compose --project-directory "$ROOT_DIR" --env-file "$ENV_FILE"
      -f "$ROOT_DIR/docker/docker-compose.yml"
    )
    if [[ "$include_dev" == true ]]; then
      output+=(-f "$ROOT_DIR/docker/docker-compose.dev.yml")
    fi
    if [[ "$include_observability" == true ]]; then
      output+=(--profile observability -f "$ROOT_DIR/docker/docker-compose.observability.yml")
    fi
    for compose_file in "${extra_files[@]}"; do
      output+=(-f "$compose_file")
    done
  }

  readonly -f devstack_compose_args
fi
