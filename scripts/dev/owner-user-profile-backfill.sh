#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env}"
ACTION="${1:-preflight}"
MANIFEST_FILE="${OWNER_BACKFILL_MANIFEST:-$ROOT_DIR/.local/owner-user-profile-backfill.manifest}"
MIGRATION_DB_HOST_WAS_SET="${MIGRATION_DB_HOST+x}"
MIGRATION_DB_PORT_WAS_SET="${MIGRATION_DB_PORT+x}"
MIGRATION_DB_USER_WAS_SET="${MIGRATION_DB_USER+x}"
MIGRATION_DB_PASSWORD_WAS_SET="${MIGRATION_DB_PASSWORD+x}"
MIGRATION_MYSQL_CONTAINER_WAS_SET="${MIGRATION_MYSQL_CONTAINER+x}"
MIGRATION_MYSQL_CONTAINER_PORT_WAS_SET="${MIGRATION_MYSQL_CONTAINER_PORT+x}"
MIGRATION_DB_HOST_OVERRIDE="${MIGRATION_DB_HOST-}"
MIGRATION_DB_PORT_OVERRIDE="${MIGRATION_DB_PORT-}"
MIGRATION_DB_USER_OVERRIDE="${MIGRATION_DB_USER-}"
MIGRATION_DB_PASSWORD_OVERRIDE="${MIGRATION_DB_PASSWORD-}"
MIGRATION_MYSQL_CONTAINER_OVERRIDE="${MIGRATION_MYSQL_CONTAINER-}"
MIGRATION_MYSQL_CONTAINER_PORT_OVERRIDE="${MIGRATION_MYSQL_CONTAINER_PORT-3306}"

case "$ACTION" in
  preflight|contract-preflight|backfill|rollback)
    ;;
  *)
    echo "Usage: $0 [preflight|contract-preflight|backfill|rollback]" >&2
    exit 2
    ;;
esac

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing $ENV_FILE. Run ./scripts/dev/init-env.sh first." >&2
  exit 1
fi

readonly __ULTICODE_ENV_FILE_PIN="${ENV_FILE:-}"
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a
if [[ -n "${__ULTICODE_ENV_FILE_PIN:-}" ]]; then
  ENV_FILE="$__ULTICODE_ENV_FILE_PIN"
  export ENV_FILE
fi
unset -f valid_identifier owner_schema valid_port valid_container_ref mysql_container_targets_configured_host 2>/dev/null || true
unset __ULTICODE_COMMON_SOURCED 2>/dev/null || true
# shellcheck source=scripts/dev/mysql-container-target.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/mysql-container-target.sh"
# shellcheck source=scripts/dev/lib/common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/common.sh"

[[ -n "$MIGRATION_DB_HOST_WAS_SET" ]] && MIGRATION_DB_HOST="$MIGRATION_DB_HOST_OVERRIDE"
[[ -n "$MIGRATION_DB_PORT_WAS_SET" ]] && MIGRATION_DB_PORT="$MIGRATION_DB_PORT_OVERRIDE"
[[ -n "$MIGRATION_DB_USER_WAS_SET" ]] && MIGRATION_DB_USER="$MIGRATION_DB_USER_OVERRIDE"
[[ -n "$MIGRATION_DB_PASSWORD_WAS_SET" ]] && MIGRATION_DB_PASSWORD="$MIGRATION_DB_PASSWORD_OVERRIDE"
[[ -n "$MIGRATION_MYSQL_CONTAINER_WAS_SET" ]] && MIGRATION_MYSQL_CONTAINER="$MIGRATION_MYSQL_CONTAINER_OVERRIDE"
[[ -n "$MIGRATION_MYSQL_CONTAINER_PORT_WAS_SET" ]] && MIGRATION_MYSQL_CONTAINER_PORT="$MIGRATION_MYSQL_CONTAINER_PORT_OVERRIDE"

for variable in MIGRATION_DB_HOST MIGRATION_DB_PORT MIGRATION_DB_USER MIGRATION_DB_PASSWORD; do
  [[ -n "${!variable:-}" ]] || {
    echo "$variable is required for owner profile backfill" >&2
    exit 1
  }
done

SOURCE_SCHEMA="${OWNER_BACKFILL_SOURCE_SCHEMA:-ulticode}"
AUTH_SCHEMA="${OWNER_BACKFILL_AUTH_SCHEMA:-auth}"
APP_SCHEMA="${OWNER_BACKFILL_APP_SCHEMA:-app}"
MYSQL_CONTAINER="${MIGRATION_MYSQL_CONTAINER:-${MYSQL_CONTAINER:-}}"
MYSQL_CONTAINER_PORT="${MIGRATION_MYSQL_CONTAINER_PORT:-3306}"
PROFILE_COLUMNS=(name avatar bio company github location twitter website preferred_language)

valid_id() {
  [[ "$1" =~ ^[A-Za-z0-9_-]+$ ]]
}

for identifier in "$SOURCE_SCHEMA" "$AUTH_SCHEMA" "$APP_SCHEMA" "$MIGRATION_DB_USER"; do
  valid_identifier "$identifier" || {
    echo "Invalid schema/account identifier: $identifier" >&2
    exit 1
  }
done
valid_port "$MIGRATION_DB_PORT" || {
  echo "Invalid migration database port: $MIGRATION_DB_PORT" >&2
  exit 1
}
if [[ -n "$MYSQL_CONTAINER" ]]; then
  command -v docker >/dev/null 2>&1 || {
    echo "docker CLI is required when MIGRATION_MYSQL_CONTAINER is set." >&2
    exit 1
  }
  valid_port "$MYSQL_CONTAINER_PORT" || {
    echo "Invalid migration MySQL container port: $MYSQL_CONTAINER_PORT" >&2
    exit 1
  }
  [[ "$(docker inspect -f '{{.State.Running}}' "$MYSQL_CONTAINER" 2>/dev/null || true)" == "true" ]] || {
    echo "Migration MySQL container is not running: $MYSQL_CONTAINER" >&2
    exit 1
  }
  mysql_container_targets_configured_host "$MYSQL_CONTAINER" "$MYSQL_CONTAINER_PORT" \
    "$MIGRATION_DB_HOST" "$MIGRATION_DB_PORT" || {
    echo "Configured migration target $MIGRATION_DB_HOST:$MIGRATION_DB_PORT is not a published endpoint of $MYSQL_CONTAINER:$MYSQL_CONTAINER_PORT" >&2
    exit 1
  }
else
  command -v mysql >/dev/null 2>&1 || {
    echo "mysql CLI is required when MIGRATION_MYSQL_CONTAINER is empty." >&2
    exit 1
  }
fi

mysql_query() {
  if [[ -n "$MYSQL_CONTAINER" ]]; then
    docker exec -e MYSQL_PWD="$MIGRATION_DB_PASSWORD" "$MYSQL_CONTAINER" \
      mysql --protocol=tcp --default-character-set=utf8mb4 \
      --batch --skip-column-names -h 127.0.0.1 -P "$MYSQL_CONTAINER_PORT" \
      -u "$MIGRATION_DB_USER" -e "SET SESSION group_concat_max_len=16777216; $1"
  else
    MYSQL_PWD="$MIGRATION_DB_PASSWORD" mysql --protocol=tcp \
      --default-character-set=utf8mb4 --batch --skip-column-names \
      -h "$MIGRATION_DB_HOST" -P "$MIGRATION_DB_PORT" \
      -u "$MIGRATION_DB_USER" -e "SET SESSION group_concat_max_len=16777216; $1"
  fi
}

table_exists() {
  [[ "$(mysql_query "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$1' AND table_name='$2';")" == "1" ]]
}

require_tables() {
  local table_spec schema table
  for table_spec in "$SOURCE_SCHEMA.users" "$SOURCE_SCHEMA.user_profiles" \
      "$AUTH_SCHEMA.users" "$APP_SCHEMA.user_profiles"; do
    schema="${table_spec%%.*}"
    table="${table_spec#*.}"
    table_exists "$schema" "$table" || {
      echo "Required table is missing: $schema.$table" >&2
      return 1
    }
  done
}

users_count() {
  mysql_query "SELECT COUNT(*) FROM $1.users;"
}

profiles_count() {
  mysql_query "SELECT COUNT(*) FROM $1.user_profiles;"
}

profile_column_sql() {
  local column
  local quoted=()
  for column in "${PROFILE_COLUMNS[@]}"; do
    quoted+=("'$column'")
  done
  local IFS=,
  printf '%s' "${quoted[*]}"
}

auth_profile_columns_count() {
  mysql_query "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='$AUTH_SCHEMA' AND table_name='users' AND column_name IN ($(profile_column_sql));"
}

users_checksum() {
  local schema="$1"
  if [[ "$schema" == "$SOURCE_SCHEMA" ]]; then
    mysql_query "SELECT COALESCE(MD5(GROUP_CONCAT(CONCAT_WS('|', id, username, email, SHA2(COALESCE(password, ''), 256), joined_at, role, is_active, is_banned, banned_until, banned_reason, last_login_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, password_reset_token_hash, password_reset_expires_at, authz_version) ORDER BY id SEPARATOR '\\n')), MD5('')) FROM $SOURCE_SCHEMA.users;"
  else
    mysql_query "SELECT COALESCE(MD5(GROUP_CONCAT(CONCAT_WS('|', id, username, email, SHA2(COALESCE(password, ''), 256), joined_at, role, is_active, is_banned, banned_until, banned_reason, last_login_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, password_reset_token_hash, password_reset_expires_at, authz_version) ORDER BY id SEPARATOR '\\n')), MD5('')) FROM $schema.users;"
  fi
}

profiles_checksum() {
  mysql_query "SELECT COALESCE(MD5(GROUP_CONCAT(CONCAT_WS('|', account_id, name, avatar, bio, company, github, location, twitter, website, preferred_language) ORDER BY account_id SEPARATOR '\n')), MD5('')) FROM $1.user_profiles;"
}

source_missing_profiles() {
  mysql_query "SELECT COUNT(*) FROM $SOURCE_SCHEMA.users u LEFT JOIN $SOURCE_SCHEMA.user_profiles p ON p.account_id=u.id WHERE p.account_id IS NULL;"
}

source_orphan_profiles() {
  mysql_query "SELECT COUNT(*) FROM $SOURCE_SCHEMA.user_profiles p LEFT JOIN $SOURCE_SCHEMA.users u ON u.id=p.account_id WHERE u.id IS NULL;"
}

print_snapshot() {
  local label="$1"
  printf '[%s] users=%s/%s profiles=%s/%s users_checksum=%s/%s profiles_checksum=%s/%s source_missing_profiles=%s source_orphan_profiles=%s\n' \
    "$label" \
    "$(users_count "$SOURCE_SCHEMA")" "$(users_count "$AUTH_SCHEMA")" \
    "$(profiles_count "$SOURCE_SCHEMA")" "$(profiles_count "$APP_SCHEMA")" \
    "$(users_checksum "$SOURCE_SCHEMA")" "$(users_checksum "$AUTH_SCHEMA")" \
    "$(profiles_checksum "$SOURCE_SCHEMA")" "$(profiles_checksum "$APP_SCHEMA")" \
    "$(source_missing_profiles)" "$(source_orphan_profiles)"
}

assert_no_duplicates() {
  [[ "$(mysql_query "SELECT COUNT(*) FROM (SELECT id FROM $SOURCE_SCHEMA.users GROUP BY id HAVING COUNT(*) > 1) AS duplicate_users;")" == "0" ]] || {
    echo "Duplicate legacy user ids detected." >&2
    return 1
  }
  [[ "$(mysql_query "SELECT COUNT(*) FROM (SELECT account_id FROM $SOURCE_SCHEMA.user_profiles GROUP BY account_id HAVING COUNT(*) > 1) AS duplicate_profiles;")" == "0" ]] || {
    echo "Duplicate legacy profile account ids detected." >&2
    return 1
  }
}

assert_no_extra_targets() {
  [[ "$(mysql_query "SELECT COUNT(*) FROM $AUTH_SCHEMA.users a LEFT JOIN $SOURCE_SCHEMA.users u ON u.id=a.id WHERE u.id IS NULL;")" == "0" ]] || {
    echo "Auth target contains rows outside the physical legacy source." >&2
    return 1
  }
  [[ "$(mysql_query "SELECT COUNT(*) FROM $APP_SCHEMA.user_profiles p LEFT JOIN $SOURCE_SCHEMA.user_profiles s ON s.account_id=p.account_id WHERE s.account_id IS NULL;")" == "0" ]] || {
    echo "App profile target contains rows outside the legacy source." >&2
    return 1
  }
}

verify_parity() {
  local mismatch_users mismatch_profiles target_missing_profiles target_orphan_profiles
  local source_missing source_orphan
  assert_no_duplicates
  assert_no_extra_targets
  source_missing="$(source_missing_profiles)"
  source_orphan="$(source_orphan_profiles)"
  mismatch_users="$(mysql_query "SELECT COUNT(*) FROM $SOURCE_SCHEMA.users u LEFT JOIN $AUTH_SCHEMA.users a ON a.id=u.id WHERE a.id IS NULL OR NOT (a.username <=> u.username) OR NOT (a.email <=> u.email) OR NOT (a.password <=> u.password) OR NOT (a.joined_at <=> u.joined_at) OR NOT (a.role <=> u.role) OR NOT (a.is_active <=> u.is_active) OR NOT (a.is_banned <=> u.is_banned) OR NOT (a.banned_until <=> u.banned_until) OR NOT (a.banned_reason <=> u.banned_reason) OR NOT (a.last_login_at <=> u.last_login_at) OR NOT (a.created_by <=> u.created_by) OR NOT (a.updated_by <=> u.updated_by) OR NOT (a.is_deleted <=> u.is_deleted) OR NOT (a.deleted_at <=> u.deleted_at) OR NOT (a.deleted_by <=> u.deleted_by) OR NOT (a.password_reset_token_hash <=> u.password_reset_token_hash) OR NOT (a.password_reset_expires_at <=> u.password_reset_expires_at) OR NOT (a.authz_version <=> u.authz_version);")"
  mismatch_profiles="$(mysql_query "SELECT COUNT(*) FROM $SOURCE_SCHEMA.user_profiles s LEFT JOIN $APP_SCHEMA.user_profiles p ON p.account_id=s.account_id WHERE NOT (p.name <=> s.name) OR NOT (p.avatar <=> s.avatar) OR NOT (p.bio <=> s.bio) OR NOT (p.company <=> s.company) OR NOT (p.github <=> s.github) OR NOT (p.location <=> s.location) OR NOT (p.twitter <=> s.twitter) OR NOT (p.website <=> s.website) OR NOT (p.preferred_language <=> s.preferred_language);")"
  target_missing_profiles="$(mysql_query "SELECT COUNT(*) FROM $SOURCE_SCHEMA.users u LEFT JOIN $APP_SCHEMA.user_profiles p ON p.account_id=u.id WHERE p.account_id IS NULL;")"
  target_orphan_profiles="$(mysql_query "SELECT COUNT(*) FROM $APP_SCHEMA.user_profiles p LEFT JOIN $SOURCE_SCHEMA.users u ON u.id=p.account_id WHERE u.id IS NULL;")"
  [[ "$mismatch_users" == "0" && "$mismatch_profiles" == "0" && "$target_missing_profiles" == "0" && "$target_orphan_profiles" == "0" && "$source_missing" == "0" && "$source_orphan" == "0" ]] || {
    printf 'Parity failed: user_mismatch=%s profile_mismatch=%s target_missing_profiles=%s target_orphan_profiles=%s source_missing_profiles=%s source_orphan_profiles=%s\n' \
      "$mismatch_users" "$mismatch_profiles" "$target_missing_profiles" "$target_orphan_profiles" "$source_missing" "$source_orphan" >&2
    return 1
  }
}

manifest_value() {
  awk -F= -v key="$1" '$1 == key {print substr($0, index($0, "=") + 1); exit}' "$MANIFEST_FILE"
}
refresh_manifest_checksums() {
  local refreshed_manifest="${MANIFEST_FILE}.tmp"
  awk '!/^version=|^checksum_schema=|^post_users_checksum=|^post_profiles_checksum=|^rolled_back=/' "$MANIFEST_FILE" > "$refreshed_manifest"
  printf 'version=2\nchecksum_schema=account_projection_with_password_sha256\npost_users_checksum=%s\npost_profiles_checksum=%s\n' \
    "$(users_checksum "$AUTH_SCHEMA")" "$(profiles_checksum "$APP_SCHEMA")" >> "$refreshed_manifest"
  mv "$refreshed_manifest" "$MANIFEST_FILE"
}

write_manifest() {
  local id
  mkdir -p "$(dirname "$MANIFEST_FILE")"
  umask 077
  {
    printf 'version=2\nchecksum_schema=account_projection_with_password_sha256\nsource_schema=%s\nauth_schema=%s\napp_schema=%s\nusers_checksum_before=%s\nprofiles_checksum_before=%s\n' \
      "$SOURCE_SCHEMA" "$AUTH_SCHEMA" "$APP_SCHEMA" "$(users_checksum "$AUTH_SCHEMA")" "$(profiles_checksum "$APP_SCHEMA")"
    while IFS= read -r id; do
      [[ -z "$id" ]] && continue
      valid_id "$id" || { echo "Unsafe user id in manifest source: $id" >&2; return 1; }
      printf 'auth_user\t%s\n' "$id"
    done <<< "$(mysql_query "SELECT id FROM $SOURCE_SCHEMA.users ORDER BY id;")"
    while IFS= read -r id; do
      [[ -z "$id" ]] && continue
      valid_id "$id" || { echo "Unsafe profile id in manifest source: $id" >&2; return 1; }
      printf 'app_profile\t%s\n' "$id"
    done <<< "$(mysql_query "SELECT account_id FROM $SOURCE_SCHEMA.user_profiles ORDER BY account_id;")"
  } > "$MANIFEST_FILE"
}

require_quiesce() {
  [[ "${DEV_LOCAL_OWNER_BACKFILL_QUIESCE_CONFIRM:-}" == "I_UNDERSTAND_OWNER_PROFILE_QUIESCE" ]] || {
    echo 'backfill/rollback requires DEV_LOCAL_OWNER_BACKFILL_QUIESCE_CONFIRM=I_UNDERSTAND_OWNER_PROFILE_QUIESCE' >&2
    exit 1
  }
}

contract_preflight() {
  [[ "${DEV_LOCAL_OWNER_PROFILE_CONTRACT_CONFIRM:-}" == "I_UNDERSTAND_AUTH_PROFILE_CONTRACT" ]] || {
    echo 'contract-preflight requires DEV_LOCAL_OWNER_PROFILE_CONTRACT_CONFIRM=I_UNDERSTAND_AUTH_PROFILE_CONTRACT' >&2
    exit 1
  }
  require_quiesce
  [[ -f "$MANIFEST_FILE" ]] || {
    echo "Backfill manifest is required before Auth profile contract: $MANIFEST_FILE" >&2
    exit 1
  }
  [[ "$(manifest_value version)" == "2" ]] || {
    echo 'Run backfill once to upgrade the manifest to version 2 before contract.' >&2
    exit 1
  }
  [[ "$(auth_profile_columns_count)" == "${#PROFILE_COLUMNS[@]}" ]] || {
    echo 'Auth users profile columns are not in the expected expand-phase shape.' >&2
    exit 1
  }
  verify_parity
  echo 'DEV-LOCAL Auth/Profile contract preflight: PASS'
}

require_tables
print_snapshot preflight

case "$ACTION" in
  preflight)
    assert_no_duplicates
    assert_no_extra_targets
    echo 'DEV-LOCAL users/profile preflight: PASS'
    ;;
  contract-preflight)
    contract_preflight
    ;;
  backfill)
    [[ "${DEV_LOCAL_OWNER_BACKFILL_CONFIRM:-}" == "I_UNDERSTAND_DEV_LOCAL_OWNER_BACKFILL" ]] || {
      echo 'backfill requires DEV_LOCAL_OWNER_BACKFILL_CONFIRM=I_UNDERSTAND_DEV_LOCAL_OWNER_BACKFILL' >&2
      exit 1
    }
    require_quiesce
    assert_no_duplicates
    assert_no_extra_targets
    target_users="$(users_count "$AUTH_SCHEMA")"
    target_profiles="$(profiles_count "$APP_SCHEMA")"
    if [[ "$target_users" != "0" || "$target_profiles" != "0" ]]; then
      [[ -f "$MANIFEST_FILE" ]] || {
        echo 'Existing owner target rows require a prior manifest; refusing to overwrite conflicts.' >&2
        exit 1
      }
      verify_parity
      refresh_manifest_checksums
      echo 'DEV-LOCAL users/profile backfill: idempotent no-op (existing manifest and parity match)'
      exit 0
    fi
    write_manifest
    query="START TRANSACTION;
INSERT INTO $AUTH_SCHEMA.users (id, username, email, password, joined_at, role, is_active, is_banned, banned_until, banned_reason, last_login_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, password_reset_token_hash, password_reset_expires_at, authz_version)
SELECT u.id, u.username, u.email, u.password, u.joined_at, u.role, u.is_active, u.is_banned, u.banned_until, u.banned_reason, u.last_login_at, u.created_by, u.updated_by, u.is_deleted, u.deleted_at, u.deleted_by, u.password_reset_token_hash, u.password_reset_expires_at, u.authz_version
FROM $SOURCE_SCHEMA.users u;
INSERT INTO $APP_SCHEMA.user_profiles (account_id, name, avatar, bio, company, github, location, twitter, website, preferred_language)
SELECT p.account_id, p.name, p.avatar, p.bio, p.company, p.github, p.location, p.twitter, p.website, p.preferred_language
FROM $SOURCE_SCHEMA.user_profiles p JOIN $SOURCE_SCHEMA.users u ON u.id=p.account_id;
COMMIT;"
    if ! mysql_query "$query"; then
      mysql_query 'ROLLBACK;' >/dev/null 2>&1 || true
      echo 'Backfill failed; source data was not modified. Inspect target and run reconciliation before retry.' >&2
      exit 1
    fi
    verify_parity
    printf 'post_users_checksum=%s\npost_profiles_checksum=%s\n' \
      "$(users_checksum "$AUTH_SCHEMA")" "$(profiles_checksum "$APP_SCHEMA")" >> "$MANIFEST_FILE"
    print_snapshot backfill
    echo 'DEV-LOCAL users/profile backfill: PASS'
    ;;
  rollback)
    [[ "${DEV_LOCAL_OWNER_BACKFILL_CONFIRM:-}" == "I_UNDERSTAND_DEV_LOCAL_OWNER_BACKFILL" ]] || {
      echo 'rollback requires DEV_LOCAL_OWNER_BACKFILL_CONFIRM=I_UNDERSTAND_DEV_LOCAL_OWNER_BACKFILL' >&2
      exit 1
    }
    require_quiesce
    [[ -f "$MANIFEST_FILE" ]] || {
      echo "Backfill manifest is required for safe rollback: $MANIFEST_FILE" >&2
      exit 1
    }
    [[ "$(manifest_value version)" == "2" ]] || {
      echo 'Unsupported backfill manifest version.' >&2
      exit 1
    }
    [[ "$(manifest_value source_schema)" == "$SOURCE_SCHEMA" && "$(manifest_value auth_schema)" == "$AUTH_SCHEMA" && "$(manifest_value app_schema)" == "$APP_SCHEMA" ]] || {
      echo 'Backfill manifest schema scope does not match current rollback target.' >&2
      exit 1
    }
    [[ "$(manifest_value post_users_checksum)" == "$(users_checksum "$AUTH_SCHEMA")" && "$(manifest_value post_profiles_checksum)" == "$(profiles_checksum "$APP_SCHEMA")" ]] || {
      echo 'Target checksums changed after backfill; refusing destructive rollback.' >&2
      exit 1
    }
    verify_parity
    auth_ids=()
    profile_ids=()
    while IFS=$'\t' read -r kind id; do
      [[ "$kind" == "auth_user" || "$kind" == "app_profile" ]] || continue
      valid_id "$id" || { echo 'Unsafe id in backfill manifest.' >&2; exit 1; }
      if [[ "$kind" == "auth_user" ]]; then auth_ids+=("$id"); else profile_ids+=("$id"); fi
    done < "$MANIFEST_FILE"
    auth_id_list=""
    profile_id_list=""
    for id in "${auth_ids[@]}"; do auth_id_list+="'$id',"; done
    for id in "${profile_ids[@]}"; do profile_id_list+="'$id',"; done
    auth_id_list="${auth_id_list%,}"
    profile_id_list="${profile_id_list%,}"
    query="START TRANSACTION;"
    [[ -n "$profile_id_list" ]] && query+=" DELETE FROM $APP_SCHEMA.user_profiles WHERE account_id IN ($profile_id_list);"
    [[ -n "$auth_id_list" ]] && query+=" DELETE FROM $AUTH_SCHEMA.users WHERE id IN ($auth_id_list);"
    query+=" COMMIT;"
    if ! mysql_query "$query"; then
      mysql_query 'ROLLBACK;' >/dev/null 2>&1 || true
      echo 'Rollback failed; keep all writers stopped and reconcile target grants/data.' >&2
      exit 1
    fi
    [[ "$(users_count "$AUTH_SCHEMA")" == "0" && "$(profiles_count "$APP_SCHEMA")" == "0" ]] || {
      echo 'Rollback did not restore empty owner targets.' >&2
      exit 1
    }
    printf 'rolled_back=true\n' >> "$MANIFEST_FILE"
    print_snapshot rollback
    echo 'DEV-LOCAL users/profile rollback: PASS'
    ;;
esac
