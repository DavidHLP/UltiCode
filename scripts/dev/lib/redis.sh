#!/usr/bin/env bash
# scripts/dev/lib/redis.sh — runtime Redis ACL materialization.
#
# Internal module of scripts/dev/lib/common.sh; source common.sh, not this
# file. The helper is frozen before any .env is sourced.

if ! [[ -v __ULTICODE_REDIS_SOURCED ]]; then
  declare -gr __ULTICODE_REDIS_SOURCED=1

  materialize_redis_acl() {
    if (($# < 1 || $# > 2)); then
      echo "Usage: materialize_redis_acl <default-dir> [--warn-missing]" >&2
      return 2
    fi

    local default_dir="$1" warn_missing=false
    if (($# == 2)); then
      [[ "$2" == "--warn-missing" ]] || {
        echo "Unknown Redis ACL option: $2" >&2
        return 2
      }
      warn_missing=true
    fi
    [[ -n "$default_dir" ]] || {
      echo "materialize_redis_acl requires a default directory" >&2
      return 2
    }

    local acl_dir="${REDIS_ACL_DIR:-$default_dir}"
    local acl_file="${REDIS_ACL_FILE:-}"
    [[ "$acl_dir" == /* ]] || acl_dir="$ROOT_DIR/$acl_dir"
    if [[ -z "$acl_file" ]]; then
      acl_file="$acl_dir/users.acl"
    else
      [[ "$acl_file" == /* ]] || acl_file="$ROOT_DIR/$acl_file"
    fi

    REDIS_ACL_DIR="$acl_dir"
    REDIS_ACL_FILE="$acl_file"
    export REDIS_ACL_DIR REDIS_ACL_FILE
    REDIS_ACL_MATERIALIZED=false

    mkdir -p "$REDIS_ACL_DIR" || return $?
    chmod 755 "$REDIS_ACL_DIR" || return $?

    local generator="$ROOT_DIR/docker/redis/generate-users-acl.sh"
    if [[ ! -f "$generator" || ! -x "$generator" ]]; then
      if [[ "$warn_missing" == true ]]; then
        echo "WARNING: docker/redis/generate-users-acl.sh not found; runtime Redis ACL was not materialized." >&2
        return 0
      fi
      echo "Missing Redis ACL generator: docker/redis/generate-users-acl.sh" >&2
      return 1
    fi

    "$generator" "$REDIS_ACL_FILE" || return "$?"
    REDIS_ACL_MATERIALIZED=true
  }

  readonly -f materialize_redis_acl
fi
