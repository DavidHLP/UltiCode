#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env}"

usage() {
  cat <<'USAGE'
Usage: scripts/dev/migrate-object-storage.sh [options]

Migrate legacy local avatars and completed admin backup dumps into RustFS.
The default is a read-only dry run. No object or database writes happen
without --apply. Existing local files are never deleted.

Options:
  --apply                         Upload objects and conditionally update rows.
  --legacy-avatar-dir DIR         Directory containing legacy avatar files.
                                  Overrides AVATAR_UPLOAD_DIR and volume lookup.
  --legacy-backup-dir DIR         Directory containing legacy backup dumps.
                                  Overrides BACKUP_DIR and volume lookup.
  --bucket NAME                   Override RUSTFS_BUCKET (default: ulticode).
  --limit N                       Process at most N rows (0 means unlimited).
  --only avatars|backups|all      Restrict the migration (default: all).
  --confirm-users-index-backfill  Confirm the users-index backfill completed
                                  after avatar rows were updated. With --apply
                                  the confirmation is recorded in
                                  app.storage_migration_state, which the
                                  pre-deploy gate verifies.
  --help                          Show this help and exit.

Configuration (environment or .env):
  APP_STORAGE_S3_ENDPOINT or RUSTFS_ENDPOINT
  APP_STORAGE_S3_REGION or RUSTFS_REGION
  APP_STORAGE_S3_TLS_ENABLED or RUSTFS_TLS_ENABLED
  RUSTFS_ACCESS_KEY and RUSTFS_SECRET_KEY for backup/all migrations;
  APP_STORAGE_S3_ACCESS_KEY and APP_STORAGE_S3_SECRET_KEY are an avatar-only
  fallback when the operator intentionally does not provide the root pair.
  RUSTFS_BUCKET
  RUSTFS_TLS_CERT_DIR (production TLS directory; uses rustfs_cert.pem as the CA)
  APP_DB_* / ADMIN_DB_* (host/port/user/password fall back to MIGRATION_DB_*,
                         then DB_*; owner schema names default to app/admin)
  AVATAR_UPLOAD_VOL / BACKUP_VOLUME (legacy Docker volume names)
  MIGRATION_SEARCH_BACKFILL_CONFIRMED=true (same as the confirmation flag)
  AWS_BIN (optional host aws executable override)
  MIGRATION_DOCKER_NETWORK (optional Docker network override for the container fallback)

The tool uses the host AWS CLI v2 for loopback endpoints when available.
Non-loopback endpoints always use the pinned Docker Hub amazon/aws-cli image
amazon/aws-cli:2.31.0@sha256:d5f18fde2ba3f9205e75d511ca3e6185c144e55df07e50eee16d940994557b40.
Loopback endpoints use the container fallback when the host binary is absent.
The container fallback avoids an unpinned host dependency.
When a legacy Docker volume's data directory is not readable by the current
user (the normal case for a deployment user with only Docker-group access),
its layout is probed and each object is extracted lazily through the Docker
daemon using the same pinned image; no root access is required.
USAGE
}

for argument in "$@"; do
  [[ "$argument" == "--help" || "$argument" == "-h" ]] && { usage; exit 0; }
done

# shellcheck source=scripts/dev/lib/common.sh
source "$ROOT_DIR/scripts/dev/lib/common.sh"

# Preserve explicit environment values when .env is loaded.
capture_env_vars \
  APP_STORAGE_S3_ENDPOINT APP_STORAGE_S3_REGION APP_STORAGE_S3_TLS_ENABLED \
  APP_STORAGE_S3_ACCESS_KEY APP_STORAGE_S3_SECRET_KEY APP_STORAGE_S3_BUCKET \
  APP_STORAGE_S3_CA_CERTIFICATE RUSTFS_TLS_CA_CERT \
  RUSTFS_ENDPOINT RUSTFS_S3_ENDPOINT RUSTFS_REGION RUSTFS_TLS_ENABLED \
  RUSTFS_ACCESS_KEY RUSTFS_SECRET_KEY RUSTFS_BUCKET \
  APP_DB_HOST APP_DB_PORT APP_DB_NAME APP_DB_USER APP_DB_PASSWORD \
  ADMIN_DB_HOST ADMIN_DB_PORT ADMIN_DB_NAME ADMIN_DB_USER ADMIN_DB_PASSWORD \
  MIGRATION_DB_HOST MIGRATION_DB_PORT MIGRATION_DB_NAME MIGRATION_DB_USER MIGRATION_DB_PASSWORD \
  DB_HOST DB_PORT DB_NAME DB_USER DB_PASSWORD MIGRATION_MYSQL_CONTAINER MIGRATION_MYSQL_CONTAINER_PORT \
  AVATAR_UPLOAD_DIR AVATAR_UPLOAD_VOL BACKUP_DIR BACKUP_VOLUME AWS_BIN AWS_CLI_IMAGE \
  LEGACY_AVATAR_URL_PREFIX \
  RUSTFS_TLS_CERT_DIR \
  MIGRATION_DOCKER_NETWORK COMPOSE_PROJECT_NAME MIGRATION_SEARCH_BACKFILL_CONFIRMED
if [[ -f "$ENV_FILE" ]]; then
  load_env_file
  apply_env_overrides
fi

# Legacy rows may carry a custom APP_STORAGE_PUBLIC_URL_PREFIX (for example
# /media); both the selection predicate and the gate must use the same one.
LEGACY_AVATAR_URL_PREFIX="${LEGACY_AVATAR_URL_PREFIX:-/uploads}"
[[ "$LEGACY_AVATAR_URL_PREFIX" =~ ^/[A-Za-z0-9._/-]*$ && "$LEGACY_AVATAR_URL_PREFIX" != */ ]] || {
  echo "LEGACY_AVATAR_URL_PREFIX must be an absolute path such as /uploads" >&2
  exit 2
}
# `_` is a LIKE single-character wildcard, so a custom prefix such as /media_v1
# has to be escaped to match literally. `%` cannot appear here: the prefix
# validation above rejects it.
LEGACY_AVATAR_LIKE="${LEGACY_AVATAR_URL_PREFIX//_/!_}/avatars/%"
LEGACY_AVATAR_DIR_PREFIX="${LEGACY_AVATAR_URL_PREFIX}/avatars/"

APPLY=false
ONLY="all"
LIMIT=0
AVATAR_DIR=""
BACKUP_DIR_ARG=""
BUCKET_OVERRIDE=""
AVATAR_DIR_EXPLICIT=false
BACKUP_DIR_EXPLICIT=false
CONFIRM_USERS_INDEX_BACKFILL=false

while (($#)); do
  case "$1" in
    --apply) APPLY=true; shift ;;
    --legacy-avatar-dir)
      (($# >= 2)) || { echo "--legacy-avatar-dir requires a value" >&2; exit 2; }
      AVATAR_DIR="$2"; AVATAR_DIR_EXPLICIT=true; shift 2 ;;
    --legacy-backup-dir)
      (($# >= 2)) || { echo "--legacy-backup-dir requires a value" >&2; exit 2; }
      BACKUP_DIR_ARG="$2"; BACKUP_DIR_EXPLICIT=true; shift 2 ;;
    --bucket)
      (($# >= 2)) || { echo "--bucket requires a value" >&2; exit 2; }
      BUCKET_OVERRIDE="$2"; shift 2 ;;
    --limit)
      (($# >= 2)) || { echo "--limit requires a value" >&2; exit 2; }
      LIMIT="$2"; shift 2 ;;
    --only)
      (($# >= 2)) || { echo "--only requires a value" >&2; exit 2; }
      ONLY="$2"; shift 2 ;;
    --confirm-users-index-backfill) CONFIRM_USERS_INDEX_BACKFILL=true; shift ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
  esac
done

[[ "$ONLY" == "avatars" || "$ONLY" == "backups" || "$ONLY" == "all" ]] || {
  echo "--only must be avatars, backups, or all" >&2
  exit 2
}
[[ "$LIMIT" =~ ^[0-9]+$ ]] || { echo "--limit must be a non-negative integer" >&2; exit 2; }
case "${MIGRATION_SEARCH_BACKFILL_CONFIRMED:-false}" in
  true) CONFIRM_USERS_INDEX_BACKFILL=true ;;
  false|"") ;;
  *) echo "MIGRATION_SEARCH_BACKFILL_CONFIRMED must be true or false" >&2; exit 2 ;;
esac

docker_volume_for() {
  local logical="$1" explicit="${2:-false}" labeled_output
  [[ "$logical" =~ ^[A-Za-z0-9][A-Za-z0-9_.-]*$ ]] || return 1
  command -v docker >/dev/null 2>&1 || return 1

  if [[ "$explicit" == true ]]; then
    if docker volume inspect "$logical" >/dev/null 2>&1; then
      printf '%s\n' "$logical"
      return 0
    fi
    echo "Explicit Docker volume '$logical' was not found; pass an explicit source directory." >&2
    return 1
  fi

  if [[ -n "${COMPOSE_PROJECT_NAME:-}" ]]; then
    labeled_output="$(docker volume ls -q \
      --filter "label=com.docker.compose.volume=$logical" \
      --filter "label=com.docker.compose.project=$COMPOSE_PROJECT_NAME" 2>/dev/null || true)"
  else
    labeled_output="$(docker volume ls -q \
      --filter "label=com.docker.compose.volume=$logical" 2>/dev/null || true)"
  fi
  local -a labeled_volumes=()
  if [[ -n "$labeled_output" ]]; then
    mapfile -t labeled_volumes <<<"$labeled_output"
  fi
  if ((${#labeled_volumes[@]} > 1)); then
    echo "Multiple Docker Compose volumes match legacy volume '$logical'; set COMPOSE_PROJECT_NAME or pass an explicit source directory." >&2
    return 1
  fi
  if ((${#labeled_volumes[@]} == 0)); then
    echo "No uniquely identified Docker Compose volume matches legacy volume '$logical'; pass the actual volume name or an explicit source directory." >&2
    return 1
  fi
  local resolved="${labeled_volumes[0]}"
  [[ "$resolved" =~ ^[A-Za-z0-9][A-Za-z0-9_.-]*$ ]] || {
    echo "Docker volume name '$resolved' contains characters this script cannot handle safely." >&2
    return 1
  }
  printf '%s\n' "$resolved"
}

# A 0711 root-owned mountpoint is stat-able for everyone; only -r/-x on the
# directory itself proves the current user can actually read volume contents.
volume_host_path() {
  local mountpoint
  mountpoint="$(docker volume inspect --format '{{.Mountpoint}}' "$1" 2>/dev/null)" || return 1
  [[ "$mountpoint" == /* && -d "$mountpoint" && -r "$mountpoint" && -x "$mountpoint" ]] || return 1
  realpath -e -- "$mountpoint"
}

# Resolves the effective object directory of a legacy volume. When the Docker
# data root is not host-readable (the normal case for a deployment user with
# only Docker-group access), the layout is probed through the Docker daemon
# and a temp mirror directory is registered so safe_source_file can extract
# each object lazily instead of failing the documented upgrade path.
resolve_volume_source() {
  local logical="$1" subdirectory="$2" explicit="${3:-false}"
  local volume host_path candidate probe layout="" mirror
  [[ "$subdirectory" =~ ^[A-Za-z0-9._-]*$ ]] || return 1
  volume="$(docker_volume_for "$logical" "$explicit")" || return 1

  if host_path="$(volume_host_path "$volume")"; then
    if [[ -n "$subdirectory" ]]; then
      for candidate in "$host_path/uploads/$subdirectory" "$host_path/$subdirectory"; do
        if [[ -d "$candidate" && -r "$candidate" && -x "$candidate" ]]; then
          realpath -e -- "$candidate"
          return 0
        fi
      done
    fi
    realpath -e -- "$host_path"
    return 0
  fi

  # The pinned image's entrypoint is `aws`; shell probes need /bin/sh explicitly.
  if [[ -n "$subdirectory" ]]; then
    probe="$(docker run --rm -v "$volume:/src:ro" --entrypoint /bin/sh "$AWS_CLI_IMAGE" -c \
      'for p in "uploads/$1" "$1"; do test -d "/src/$p" && echo "$p" && break; done; exit 0' _ "$subdirectory" \
      2>/dev/null)" || probe=""
    layout="$probe"
  fi
  if [[ -z "$layout" ]]; then
    docker run --rm -v "$volume:/src:ro" --entrypoint /bin/sh "$AWS_CLI_IMAGE" -c 'test -d /src' \
      >/dev/null 2>&1 || {
      echo "Docker volume '$volume' is not host-readable and could not be probed through the Docker daemon." >&2
      return 1
    }
  fi

  mirror="$TMP_DIR/volume-source-$volume"
  [[ -z "$layout" ]] || mirror="$mirror/$layout"
  mkdir -p -- "$mirror"
  printf '%s\t%s\n' "$volume" "$layout" >"$mirror/.migration-volume-source"
  printf '%s\n' "$mirror"
}

legacy_avatar_directory() {
  local directory="$1" candidate
  for candidate in "$directory/uploads/avatars" "$directory/avatars"; do
    if [[ -d "$candidate" ]]; then
      printf '%s\n' "$candidate"
      return
    fi
  done
  printf '%s\n' "$directory"
}

# The volume fallback probes legacy volumes through the Docker daemon below,
# so the helper image and the temp mirror must exist before source resolution.
TMP_DIR="$(mktemp -d)"
trap 'rm -r -- "$TMP_DIR"' EXIT

AWS_BIN="${AWS_BIN:-aws}"
AWS_CLI_IMAGE="${AWS_CLI_IMAGE:-amazon/aws-cli:2.31.0@sha256:d5f18fde2ba3f9205e75d511ca3e6185c144e55df07e50eee16d940994557b40}"

if [[ "$ONLY" == avatars || "$ONLY" == all ]]; then
  if [[ "$AVATAR_DIR_EXPLICIT" == false ]]; then
    if [[ -n "${AVATAR_UPLOAD_DIR:-}" && -d "$AVATAR_UPLOAD_DIR" ]]; then
      AVATAR_DIR="$(legacy_avatar_directory "$AVATAR_UPLOAD_DIR")"
    elif [[ -n "${AVATAR_UPLOAD_VOL:-}" && -d "$AVATAR_UPLOAD_VOL" ]]; then
      AVATAR_DIR="$(legacy_avatar_directory "$AVATAR_UPLOAD_VOL")"
    elif AVATAR_DIR="$(resolve_volume_source "${AVATAR_UPLOAD_VOL:-app_uploads}" avatars "${AVATAR_UPLOAD_VOL:+true}")"; then
      echo "Using legacy avatar volume source: $AVATAR_DIR"
    else
      echo "Legacy avatar volume '${AVATAR_UPLOAD_VOL:-app_uploads}' could not be uniquely resolved; pass --legacy-avatar-dir with the extracted source." >&2
      exit 2
    fi
  fi
fi
if [[ "$ONLY" == backups || "$ONLY" == all ]]; then
  if [[ "$BACKUP_DIR_EXPLICIT" == false ]]; then
    if [[ -n "${BACKUP_DIR:-}" && -d "$BACKUP_DIR" ]]; then
      BACKUP_DIR_ARG="$BACKUP_DIR"
    elif BACKUP_DIR_ARG="$(resolve_volume_source "${BACKUP_VOLUME:-backup_data}" "" "${BACKUP_VOLUME:+true}")"; then
      echo "Using legacy backup volume source: $BACKUP_DIR_ARG"
    else
      echo "Legacy backup volume '${BACKUP_VOLUME:-backup_data}' could not be uniquely resolved; pass --legacy-backup-dir with the extracted source." >&2
      exit 2
    fi
  fi
fi
S3_ENDPOINT="${APP_STORAGE_S3_ENDPOINT:-${RUSTFS_ENDPOINT:-${RUSTFS_S3_ENDPOINT:-http://127.0.0.1:9000}}}"
S3_REGION="${APP_STORAGE_S3_REGION:-${RUSTFS_REGION:-us-east-1}}"
S3_ACCESS_KEY="${RUSTFS_ACCESS_KEY:-${APP_STORAGE_S3_ACCESS_KEY:-}}"
S3_SECRET_KEY="${RUSTFS_SECRET_KEY:-${APP_STORAGE_S3_SECRET_KEY:-}}"
if [[ "$ONLY" == backups || "$ONLY" == all ]] \
    && { [[ -z "${RUSTFS_ACCESS_KEY:-}" ]] || [[ -z "${RUSTFS_SECRET_KEY:-}" ]]; }; then
  echo "RUSTFS_ACCESS_KEY and RUSTFS_SECRET_KEY are required for backup migrations" >&2
  exit 2
fi
S3_BUCKET="${BUCKET_OVERRIDE:-${RUSTFS_BUCKET:-${APP_STORAGE_S3_BUCKET:-ulticode}}}"
S3_TLS_ENABLED="${APP_STORAGE_S3_TLS_ENABLED:-${RUSTFS_TLS_ENABLED:-}}"
if [[ -z "$S3_TLS_ENABLED" ]]; then
  [[ "$S3_ENDPOINT" == https://* ]] && S3_TLS_ENABLED=true || S3_TLS_ENABLED=false
fi
S3_CA_CERTIFICATE="${APP_STORAGE_S3_CA_CERTIFICATE:-${RUSTFS_TLS_CA_CERT:-}}"
if [[ -z "$S3_CA_CERTIFICATE" && "$S3_TLS_ENABLED" == true && -n "${RUSTFS_TLS_CERT_DIR:-}" ]]; then
  S3_CA_CERTIFICATE="$RUSTFS_TLS_CERT_DIR/rustfs_cert.pem"
fi

case "$S3_TLS_ENABLED" in
  true|false) ;;
  *) echo "S3 TLS flag must be true or false" >&2; exit 2 ;;
esac
if [[ "$S3_TLS_ENABLED" == true && "$S3_ENDPOINT" != https://* ]]; then
  echo "TLS is enabled but S3 endpoint is not HTTPS: $S3_ENDPOINT" >&2
  exit 2
fi
if [[ "$S3_TLS_ENABLED" == false && "$S3_ENDPOINT" != http://127.0.0.1:* \
    && "$S3_ENDPOINT" != http://localhost:* && "$S3_ENDPOINT" != http://\[::1\]:* ]]; then
  echo "Plain HTTP S3 endpoints are allowed only on loopback: $S3_ENDPOINT" >&2
  exit 2
fi
[[ "$S3_BUCKET" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{2,62}$ ]] || {
  echo "Invalid S3 bucket name" >&2
  exit 2
}
if [[ -n "$S3_CA_CERTIFICATE" ]]; then
  case "$S3_CA_CERTIFICATE" in
    /*) ;;
    *) S3_CA_CERTIFICATE="$ROOT_DIR/$S3_CA_CERTIFICATE" ;;
  esac
  [[ -f "$S3_CA_CERTIFICATE" ]] || {
    echo "S3 CA certificate does not exist: $S3_CA_CERTIFICATE" >&2
    exit 2
  }
fi
case "$AVATAR_DIR" in
  /*) ;;
  *) AVATAR_DIR="$ROOT_DIR/$AVATAR_DIR" ;;
esac
if [[ "$AVATAR_DIR_EXPLICIT" == true ]]; then
  AVATAR_DIR="$(legacy_avatar_directory "$AVATAR_DIR")"
fi
case "$BACKUP_DIR_ARG" in
  /*) ;;
  *) BACKUP_DIR_ARG="$ROOT_DIR/$BACKUP_DIR_ARG" ;;
esac

if [[ "$ONLY" == avatars || "$ONLY" == all ]]; then
  APP_DB_HOST="${APP_DB_HOST:-${MIGRATION_DB_HOST:-${DB_HOST:-}}}"
  APP_DB_PORT="${APP_DB_PORT:-${MIGRATION_DB_PORT:-${DB_PORT:-3306}}}"
  APP_DB_NAME="${APP_DB_NAME:-app}"
  APP_DB_USER="${APP_DB_USER:-${MIGRATION_DB_USER:-${DB_USER:-}}}"
  APP_DB_PASSWORD="${APP_DB_PASSWORD:-${MIGRATION_DB_PASSWORD:-${DB_PASSWORD:-}}}"
fi
if [[ "$ONLY" == backups || "$ONLY" == all ]]; then
  ADMIN_DB_HOST="${ADMIN_DB_HOST:-${MIGRATION_DB_HOST:-${DB_HOST:-}}}"
  ADMIN_DB_PORT="${ADMIN_DB_PORT:-${MIGRATION_DB_PORT:-${DB_PORT:-3306}}}"
  ADMIN_DB_NAME="${ADMIN_DB_NAME:-admin}"
  ADMIN_DB_USER="${ADMIN_DB_USER:-${MIGRATION_DB_USER:-${DB_USER:-}}}"
  ADMIN_DB_PASSWORD="${ADMIN_DB_PASSWORD:-${MIGRATION_DB_PASSWORD:-${DB_PASSWORD:-}}}"
fi

require_db_config() {
  local prefix="$1" variable variable_name
  for variable in HOST PORT NAME USER PASSWORD; do
    variable_name="${prefix}_${variable}"
    [[ -n "${!variable_name:-}" ]] || {
      echo "${prefix}_$variable is required for --only $ONLY" >&2
      exit 2
    }
  done
}
if [[ "$ONLY" == avatars || "$ONLY" == all ]]; then require_db_config APP_DB; fi
if [[ "$ONLY" == backups || "$ONLY" == all ]]; then require_db_config ADMIN_DB; fi

AWS_BIN="${AWS_BIN:-aws}"

is_loopback_endpoint() {
  local authority="${1#*://}"
  authority="${authority%%/*}"
  case "$authority" in
    127.0.0.1|127.0.0.1:*|localhost|localhost:*|\[::1\]|\[::1\]:*) return 0 ;;
    *) return 1 ;;
  esac
}

S3_CLIENT_MODE=""
if is_loopback_endpoint "$S3_ENDPOINT"; then
  if [[ "$AWS_BIN" == */* && -x "$AWS_BIN" ]] || command -v "$AWS_BIN" >/dev/null 2>&1; then
    S3_CLIENT_MODE=host
  elif command -v docker >/dev/null 2>&1; then
    S3_CLIENT_MODE=container
  else
    echo "AWS CLI '$AWS_BIN' not found and Docker is unavailable" >&2
    exit 2
  fi
elif command -v docker >/dev/null 2>&1; then
  S3_CLIENT_MODE=container
else
  echo "Docker is required for non-loopback S3 endpoint '$S3_ENDPOINT'; host AWS CLI is only supported for loopback endpoints." >&2
  exit 2
fi
if [[ "$APPLY" == true && ( -z "$S3_ACCESS_KEY" || -z "$S3_SECRET_KEY" ) ]]; then
  echo "S3 access and secret keys are required with --apply" >&2
  exit 2
fi

DOCKER_NETWORK_ARGS=()
if [[ "$S3_CLIENT_MODE" == container ]]; then
  if is_loopback_endpoint "$S3_ENDPOINT"; then
    DOCKER_NETWORK_ARGS=(--network host)
  else
    MIGRATION_DOCKER_NETWORK="${MIGRATION_DOCKER_NETWORK:-${COMPOSE_PROJECT_NAME:-ulticode}_object-storage}"
    if ! docker network inspect "$MIGRATION_DOCKER_NETWORK" >/dev/null 2>&1; then
      echo "Docker network '$MIGRATION_DOCKER_NETWORK' does not exist for S3 endpoint $S3_ENDPOINT; set MIGRATION_DOCKER_NETWORK to an existing network." >&2
      exit 2
    fi
    DOCKER_NETWORK_ARGS=(--network "$MIGRATION_DOCKER_NETWORK")
  fi
fi

VERIFIED_FILE="$TMP_DIR/verified"
PENDING_FILE="$TMP_DIR/pending"
: >"$VERIFIED_FILE"
: >"$PENDING_FILE"

TOTAL=0
AVATAR_DB_UPDATED=0
SEARCH_BACKFILL_PENDING=false
UPLOADED=0
SKIPPED=0
FAILED=0
DB_UPDATED=0

aws_call() {
  local -a common=(--endpoint-url "$S3_ENDPOINT" --region "$S3_REGION")
  if [[ "$S3_CLIENT_MODE" == host ]]; then
    local -a aws_env=(
      "AWS_ACCESS_KEY_ID=$S3_ACCESS_KEY"
      "AWS_SECRET_ACCESS_KEY=$S3_SECRET_KEY"
      "AWS_DEFAULT_REGION=$S3_REGION"
    )
    if [[ -n "$S3_CA_CERTIFICATE" ]]; then
      aws_env+=("AWS_CA_BUNDLE=$S3_CA_CERTIFICATE")
    fi
    env "${aws_env[@]}" "$AWS_BIN" "${common[@]}" "$@"
    return
  fi

  local -a client_args=("$@") docker_mounts=()
  local source index
  local avatar_source_dir="" backup_source_dir=""
  if [[ "$ONLY" == avatars || "$ONLY" == all ]]; then
    avatar_source_dir="$(realpath -e -- "$AVATAR_DIR" 2>/dev/null || true)"
    [[ -n "$avatar_source_dir" ]] && docker_mounts+=(-v "$avatar_source_dir:/migration-src/avatar:ro")
  fi
  if [[ "$ONLY" == backups || "$ONLY" == all ]]; then
    backup_source_dir="$(realpath -e -- "$BACKUP_DIR_ARG" 2>/dev/null || true)"
    [[ -n "$backup_source_dir" ]] && docker_mounts+=(-v "$backup_source_dir:/migration-src/backup:ro")
  fi
  for ((index = 0; index < ${#client_args[@]}; index++)); do
    [[ "${client_args[index]}" == --body && $((index + 1)) -lt ${#client_args[@]} ]] || continue
    source="${client_args[index + 1]}"
    if [[ -n "$avatar_source_dir" && "$source" == "$avatar_source_dir"/* ]]; then
      client_args[index + 1]="/migration-src/avatar/${source#"$avatar_source_dir"/}"
    elif [[ -n "$backup_source_dir" && "$source" == "$backup_source_dir"/* ]]; then
      client_args[index + 1]="/migration-src/backup/${source#"$backup_source_dir"/}"
    fi
  done
  local -a docker_env=(
    -e "AWS_ACCESS_KEY_ID=$S3_ACCESS_KEY"
    -e "AWS_SECRET_ACCESS_KEY=$S3_SECRET_KEY"
    -e "AWS_DEFAULT_REGION=$S3_REGION"
  )
  if [[ -n "$S3_CA_CERTIFICATE" ]]; then
    docker_env+=(-e AWS_CA_BUNDLE=/tmp/ulticode-s3-ca.pem)
    docker_mounts+=(-v "$S3_CA_CERTIFICATE:/tmp/ulticode-s3-ca.pem:ro")
  fi
  docker run --rm "${DOCKER_NETWORK_ARGS[@]}" \
    "${docker_mounts[@]}" "${docker_env[@]}" "$AWS_CLI_IMAGE" \
    "${common[@]}" "${client_args[@]}"
}

sql_quote() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//\'/\'\'}"
  printf "'%s'" "$value"
}

mysql_query() {
  local prefix="$1" sql="$2" host port name user password container container_port variable_name
  variable_name="${prefix}_HOST"; host="${!variable_name}"
  variable_name="${prefix}_PORT"; port="${!variable_name}"
  variable_name="${prefix}_NAME"; name="${!variable_name}"
  variable_name="${prefix}_USER"; user="${!variable_name}"
  variable_name="${prefix}_PASSWORD"; password="${!variable_name}"
  container="${MIGRATION_MYSQL_CONTAINER:-}"; container_port="${MIGRATION_MYSQL_CONTAINER_PORT:-3306}"
  if [[ -n "$container" ]]; then
    MYSQL_PWD="$password" docker exec -e "MYSQL_PWD=$password" "$container" mysql \
      --protocol=tcp -h 127.0.0.1 -P "$container_port" -u "$user" \
      --default-character-set=utf8mb4 --batch --raw --skip-column-names "$name" -e "$sql"
  else
    MYSQL_PWD="$password" mysql --protocol=tcp -h "$host" -P "$port" -u "$user" \
      --default-character-set=utf8mb4 --batch --raw --skip-column-names "$name" -e "$sql"
  fi
}

# Deploy-gate evidence for the object-storage cutover: a rewritten avatar row
# and a confirmed users-index backfill are different states, and the gate that
# runs before the new containers take traffic can only read them from here.
record_storage_migration_state() {
  local mode="$1" statement
  case "$mode" in
    rewritten)
      statement="INSERT INTO storage_migration_state (id, avatar_rows_rewritten_at, users_index_backfill_confirmed_at) VALUES (1, NOW(3), NULL) ON DUPLICATE KEY UPDATE avatar_rows_rewritten_at = NOW(3), users_index_backfill_confirmed_at = NULL"
      ;;
    confirmed)
      statement="INSERT INTO storage_migration_state (id, users_index_backfill_confirmed_at) VALUES (1, NOW(3)) ON DUPLICATE KEY UPDATE users_index_backfill_confirmed_at = NOW(3)"
      ;;
    *)
      echo "Unknown storage migration state: $mode" >&2
      return 1
      ;;
  esac
  if [[ "$APPLY" != true ]]; then
    echo "DRY-RUN would record $mode in app.storage_migration_state"
    return 0
  fi
  if ! mysql_query APP_DB "$statement" >/dev/null 2>&1; then
    echo "ERROR app.storage_migration_state could not record '$mode'; the deploy gate cannot verify the users-index backfill from this run" >&2
    return 1
  fi
  return 0
}

safe_source_file() {
  local directory="$1" name="$2" directory_real source_real source_ref volume layout
  [[ -n "$name" && "$name" != /* && "$name" != *\\* && "$name" != *"/"* && "$name" != *".."* ]] || return 1
  [[ -d "$directory" ]] || return 1
  if [[ ! -e "$directory/$name" && -f "$directory/.migration-volume-source" ]]; then
    # Daemon-materialized mirror: pull just this object from the volume. The
    # name is DB-derived, so it may only ever be a positional argument — the
    # image entrypoint is overridden to /bin/sh because `aws` is the default.
    [[ "$name" =~ ^[A-Za-z0-9._-]+$ ]] || return 1
    source_ref="$(<"$directory/.migration-volume-source")"
    volume="${source_ref%%$'\t'*}"
    layout="${source_ref#*$'\t'}"
    { docker run --rm -v "$volume:/src:ro" --entrypoint /bin/sh "$AWS_CLI_IMAGE" -c \
      'test -f "$1" && tar -C "$2" -cf - "$3"' _ \
      "/src/${layout:+$layout/}$name" "/src/${layout:-.}" "$name" \
      | tar -x -C "$directory"; } 2>/dev/null || return 1
  fi
  directory_real="$(realpath -e -- "$directory")" || return 1
  source_real="$(realpath -e -- "$directory/$name")" || return 1
  [[ "$source_real" == "$directory_real"/* && -f "$source_real" ]] || return 1
  printf '%s\n' "$source_real"
}

head_object() {
  local key="$1" output error_file error status size etag
  error_file="$TMP_DIR/head-error"
  : >"$error_file"
  if output="$(aws_call s3api head-object --bucket "$S3_BUCKET" --key "$key" \
      --query '[ContentLength,ETag]' --output text 2>"$error_file")"; then
    read -r size etag <<<"$output"
    size="${size//$'\r'/}"
    etag="${etag//\"/}"
    [[ "$size" =~ ^[0-9]+$ ]] || return 2
    printf '%s\t%s\n' "$size" "$etag"
    return 0
  fi
  status=$?
  error="$(<"$error_file")"
  if [[ -z "$error" || "$error" == *404* || "$error" == *NotFound* || "$error" == *NoSuchKey* ]]; then
    return 1
  fi
  echo "S3 head failed for key $key: $error" >&2
  return 2
}

local_md5() { md5sum -- "$1" | awk '{print $1}'; }
local_sha256() { sha256sum -- "$1" | awk '{print $1}'; }
local_size() { stat -c '%s' -- "$1"; }

etag_status() {
  local etag="$1" expected_md5="$2"
  if [[ -z "$etag" || "$etag" == "None" || "$etag" == *-* ||
        ! "$etag" =~ ^[[:xdigit:]]{32}$ ]]; then
    return 2
  fi
  [[ "${etag,,}" == "${expected_md5,,}" ]] && return 0
  return 1
}

read_back_matches() {
  local key="$1" expected_sha="$2" actual_sha
  actual_sha="$(aws_call s3 cp "s3://$S3_BUCKET/$key" - --only-show-errors | sha256sum | awk '{print $1}')" || return 1
  [[ "$actual_sha" == "$expected_sha" ]]
}

verify_object() {
  local source="$1" key="$2" expected_size="$3" expected_sha="$4" expected_md5="$5"
  local metadata actual_size etag etag_result=0
  if ! metadata="$(head_object "$key")"; then
    echo "verification failed: object is not readable after upload ($key)" >&2
    return 1
  fi
  IFS=$'\t' read -r actual_size etag <<<"$metadata"
  [[ "$actual_size" == "$expected_size" ]] || {
    echo "verification failed: size mismatch for $key (expected $expected_size, got $actual_size)" >&2
    return 1
  }
  etag_status "$etag" "$expected_md5" || etag_result=$?
  if [[ "$etag_result" -eq 1 ]]; then
    echo "verification failed: ETag mismatch for $key" >&2
    return 1
  fi
  read_back_matches "$key" "$expected_sha" || {
    echo "verification failed: streamed checksum mismatch for $key" >&2
    return 1
  }
  return 0
}

object_needs_upload() {
  local source="$1" key="$2" expected_size="$3" expected_md5="$4"
  local metadata actual_size etag etag_result=0
  if metadata="$(head_object "$key")"; then
    IFS=$'\t' read -r actual_size etag <<<"$metadata"
    if [[ "$actual_size" == "$expected_size" ]]; then
      etag_status "$etag" "$expected_md5" || etag_result=$?
      case "$etag_result" in
        0) return 1 ;;
        1|2) return 0 ;;
        *) return 2 ;;
      esac
    fi
    return 0
  else
    case "$?" in
      1) return 0 ;;
      *) return 2 ;;
    esac
  fi
}

record_pending() { printf '%s\n' "$1" >>"$PENDING_FILE"; }
record_verified() { printf '%s\n' "$1" >>"$VERIFIED_FILE"; }

run_db_update() {
  local prefix="$1" sql="$2" output affected
  if ! output="$(mysql_query "$prefix" "$sql; SELECT ROW_COUNT();")"; then
    return 2
  fi
  affected="${output##*$'\n'}"
  [[ "$affected" == 1 ]]
}

process_avatar() {
  local account_id="$1" legacy_avatar="$2" filename source key size sha md5 mime sql metadata actual_size etag
  [[ "$legacy_avatar" == "$LEGACY_AVATAR_DIR_PREFIX"* ]] || { FAILED=$((FAILED + 1)); record_pending "avatar account=$account_id reason=not-legacy-path"; return; }
  filename="${legacy_avatar#"$LEGACY_AVATAR_DIR_PREFIX"}"
  if ! source="$(safe_source_file "$AVATAR_DIR" "$filename")"; then
    FAILED=$((FAILED + 1)); record_pending "avatar account=$account_id source=$AVATAR_DIR/$filename reason=source-missing-or-unsafe"
    echo "PENDING avatar source=$AVATAR_DIR/$filename reason=source-missing-or-unsafe"; return
  fi
  [[ "$account_id" =~ ^[A-Za-z0-9._-]+$ ]] || {
    FAILED=$((FAILED + 1)); record_pending "avatar account=$account_id reason=unsafe-account-id"; echo "PENDING avatar account=$account_id reason=unsafe-account-id"; return
  }
  # Legacy uploads may carry no extension at all (or a trailing dot); the
  # relaxed key grammar accepts them, so the migration must too.
  [[ "$filename" =~ ^[A-Za-z0-9._-]+$ ]] || {
    FAILED=$((FAILED + 1)); record_pending "avatar account=$account_id reason=unsafe-object-name"; echo "PENDING avatar account=$account_id reason=unsafe-object-name"; return
  }
  key="app/avatars/$account_id/$filename"
  size="$(local_size "$source")"; sha="$(local_sha256 "$source")"; md5="$(local_md5 "$source")"
  case "${filename##*.}" in
    jpg|jpeg|JPG|JPEG) mime=image/jpeg ;;
    png|PNG) mime=image/png ;;
    gif|GIF) mime=image/gif ;;
    webp|WEBP) mime=image/webp ;;
    *) mime=application/octet-stream ;;
  esac
  if [[ "$mime" == application/octet-stream ]]; then
    # Extensionless legacy names may hold anything the old endpoint accepted:
    # only the supported image types are preserved, everything else is stored
    # as an inert type so the same-origin proxy can never serve script content.
    case "$(file --brief --mime-type "$source" 2>/dev/null || true)" in
      image/png) mime=image/png ;;
      image/jpeg) mime=image/jpeg ;;
      image/gif) mime=image/gif ;;
      image/webp) mime=image/webp ;;
      *) mime=application/octet-stream ;;
    esac
  fi
  sql="UPDATE user_profiles SET avatar=$(sql_quote "$key") WHERE account_id=$(sql_quote "$account_id") AND avatar=$(sql_quote "$legacy_avatar")"
  if metadata="$(head_object "$key")"; then
    IFS=$'\t' read -r actual_size etag <<<"$metadata"
    if [[ "$actual_size" != "$size" ]] || ! etag_status "$etag" "$md5"; then
      echo "PLAN type=avatar source=$source target=$key db_update=$sql action=replace-object"
    elif [[ "$APPLY" == false ]]; then
      SKIPPED=$((SKIPPED + 1)); echo "PLAN type=avatar source=$source target=$key db_update=$sql action=skip reason=object-exists-size-and-checksum"; return
    else
      echo "PLAN type=avatar source=$source target=$key db_update=$sql action=verify-existing"
    fi
  else
    case "$?" in
      1) echo "PLAN type=avatar source=$source target=$key db_update=$sql action=upload" ;;
      *) FAILED=$((FAILED + 1)); record_pending "avatar account=$account_id reason=object-head-failed"; return ;;
    esac
  fi
  if [[ "$APPLY" == false ]]; then
    SKIPPED=$((SKIPPED + 1)); return
  fi
  local object_status=0
  object_needs_upload "$source" "$key" "$size" "$md5" || object_status=$?
  if [[ "$object_status" -eq 0 ]]; then
    if ! aws_call s3api put-object --bucket "$S3_BUCKET" --key "$key" --body "$source" --content-type "$mime" >/dev/null; then
      FAILED=$((FAILED + 1)); record_pending "avatar account=$account_id target=$key reason=upload-failed"; return
    fi
    UPLOADED=$((UPLOADED + 1))
  elif [[ "$object_status" -eq 2 ]]; then
    FAILED=$((FAILED + 1)); record_pending "avatar account=$account_id reason=object-head-failed"; return
  else
    SKIPPED=$((SKIPPED + 1))
  fi
  if ! verify_object "$source" "$key" "$size" "$sha" "$md5"; then
    FAILED=$((FAILED + 1)); record_pending "avatar account=$account_id target=$key reason=verification-failed"; return
  fi
  local update_status=0
  run_db_update APP_DB "$sql" || update_status=$?
  if [[ "$update_status" -ne 0 ]]; then
    if [[ "$update_status" -eq 2 ]]; then FAILED=$((FAILED + 1)); else SKIPPED=$((SKIPPED + 1)); fi
    record_pending "avatar account=$account_id target=$key reason=db-row-no-longer-legacy-or-update-failed"; return
  fi
  DB_UPDATED=$((DB_UPDATED + 1)); AVATAR_DB_UPDATED=$((AVATAR_DB_UPDATED + 1)); record_verified "avatar account=$account_id target=$key"; echo "VERIFIED avatar account=$account_id target=$key"
}

process_backup() {
  local backup_id filename legacy_size status created_at source key year month size sha md5 sql metadata actual_size etag
  backup_id="$1"; filename="$2"; legacy_size="$3"; status="$4"; created_at="$5"
  [[ "$status" == COMPLETED ]] || { SKIPPED=$((SKIPPED + 1)); record_pending "backup id=$backup_id reason=status-$status"; return; }
  if ! source="$(safe_source_file "$BACKUP_DIR_ARG" "$filename")"; then
    FAILED=$((FAILED + 1)); record_pending "backup id=$backup_id source=$BACKUP_DIR_ARG/$filename reason=source-missing-or-unsafe"; echo "PENDING backup id=$backup_id reason=source-missing-or-unsafe"; return
  fi
  [[ "$legacy_size" =~ ^[0-9]+$ ]] || { FAILED=$((FAILED + 1)); record_pending "backup id=$backup_id reason=invalid-db-size"; return; }
  size="$(local_size "$source")"
  [[ "$size" == "$legacy_size" ]] || { FAILED=$((FAILED + 1)); record_pending "backup id=$backup_id reason=size-does-not-match-db"; echo "PENDING backup id=$backup_id reason=size-does-not-match-db"; return; }
  if [[ "$created_at" =~ ^([0-9]{4})-([0-9]{2}) ]]; then
    year="${BASH_REMATCH[1]}"; month="${BASH_REMATCH[2]}"
  elif [[ "$created_at" =~ ^([0-9]{4})/([0-9]{2}) ]]; then
    year="${BASH_REMATCH[1]}"; month="${BASH_REMATCH[2]}"
  else
    FAILED=$((FAILED + 1)); record_pending "backup id=$backup_id reason=invalid-created-at"; return
  fi
  [[ "$backup_id" =~ ^[A-Za-z0-9._-]+$ ]] || { FAILED=$((FAILED + 1)); record_pending "backup id=$backup_id reason=unsafe-id"; return; }
  key="admin/backups/$year/$month/$backup_id.sql"
  sha="$(local_sha256 "$source")"; md5="$(local_md5 "$source")"
  sql="UPDATE backups SET object_key=$(sql_quote "$key"), checksum=$(sql_quote "$sha"), size=$size WHERE id=$(sql_quote "$backup_id") AND filename=$(sql_quote "$filename") AND status='COMPLETED' AND (object_key IS NULL OR object_key='')"
  if metadata="$(head_object "$key")"; then
    IFS=$'\t' read -r actual_size etag <<<"$metadata"
    if [[ "$actual_size" != "$size" ]] || ! etag_status "$etag" "$md5"; then
      echo "PLAN type=backup source=$source target=$key db_update=$sql action=replace-object"
    elif [[ "$APPLY" == false ]]; then
      SKIPPED=$((SKIPPED + 1)); echo "PLAN type=backup source=$source target=$key db_update=$sql action=skip reason=object-exists-size-and-checksum"; return
    else
      echo "PLAN type=backup source=$source target=$key db_update=$sql action=verify-existing"
    fi
  else
    case "$?" in
      1) echo "PLAN type=backup source=$source target=$key db_update=$sql action=upload" ;;
      *) FAILED=$((FAILED + 1)); record_pending "backup id=$backup_id reason=object-head-failed"; return ;;
    esac
  fi
  if [[ "$APPLY" == false ]]; then SKIPPED=$((SKIPPED + 1)); return; fi
  local object_status=0
  object_needs_upload "$source" "$key" "$size" "$md5" || object_status=$?
  if [[ "$object_status" -eq 0 ]]; then
    if ! aws_call s3api put-object --bucket "$S3_BUCKET" --key "$key" --body "$source" --content-type application/sql >/dev/null; then
      FAILED=$((FAILED + 1)); record_pending "backup id=$backup_id target=$key reason=upload-failed"; return
    fi
    UPLOADED=$((UPLOADED + 1))
  elif [[ "$object_status" -eq 2 ]]; then
    FAILED=$((FAILED + 1)); record_pending "backup id=$backup_id reason=object-head-failed"; return
  else
    SKIPPED=$((SKIPPED + 1))
  fi
  if ! verify_object "$source" "$key" "$size" "$sha" "$md5"; then
    FAILED=$((FAILED + 1)); record_pending "backup id=$backup_id target=$key reason=verification-failed"; return
  fi
  local update_status=0
  run_db_update ADMIN_DB "$sql" || update_status=$?
  if [[ "$update_status" -ne 0 ]]; then
    if [[ "$update_status" -eq 2 ]]; then FAILED=$((FAILED + 1)); else SKIPPED=$((SKIPPED + 1)); fi
    record_pending "backup id=$backup_id target=$key reason=db-row-no-longer-legacy-or-update-failed"; return
  fi
  DB_UPDATED=$((DB_UPDATED + 1)); record_verified "backup id=$backup_id target=$key"; echo "VERIFIED backup id=$backup_id target=$key"
}
if [[ "$ONLY" == avatars || "$ONLY" == all ]]; then
  avatar_limit_clause=""
  if (( LIMIT > 0 )); then
    avatar_limit_clause=" LIMIT $LIMIT"
  fi
  avatar_rows="$(mysql_query APP_DB "SELECT account_id, avatar FROM user_profiles WHERE avatar LIKE $(sql_quote "$LEGACY_AVATAR_LIKE") ESCAPE '!' ORDER BY account_id${avatar_limit_clause}")"
  if [[ "$APPLY" == true && -n "$avatar_rows" ]]; then
    # Before the first rewrite: an interrupted run must not leave rewritten
    # rows with no pending marker, because a rerun then finds no legacy rows
    # and the deploy gate could not tell the index backfill is still owed.
    record_storage_migration_state rewritten || {
      echo 'Refusing to rewrite avatar rows while the pending backfill state cannot be persisted.' >&2
      exit 2
    }
  fi
  while IFS=$'\t' read -r account_id legacy_avatar; do
    [[ -n "${account_id:-}" ]] || continue
    (( LIMIT > 0 && TOTAL >= LIMIT )) && break
    TOTAL=$((TOTAL + 1)); process_avatar "$account_id" "$legacy_avatar"
  done <<<"$avatar_rows"
fi
if [[ "$ONLY" == backups || "$ONLY" == all ]] && (( LIMIT == 0 || TOTAL < LIMIT )); then
  backup_object_key_count="$(mysql_query ADMIN_DB "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=$(sql_quote "$ADMIN_DB_NAME") AND TABLE_NAME='backups' AND COLUMN_NAME IN ('object_key','checksum')")"
  if [[ "$backup_object_key_count" != 2 ]]; then
    echo "Admin backups.object_key/backups.checksum are missing; apply the backup object-storage migration before running this tool." >&2
    exit 2
  fi
  backup_limit_clause=""
  if (( LIMIT > 0 )); then
    backup_limit_clause=" LIMIT $((LIMIT - TOTAL))"
  fi
  backup_rows="$(mysql_query ADMIN_DB "SELECT id, filename, size, status, DATE_FORMAT(created_at, '%Y-%m-%d') FROM backups WHERE status='COMPLETED' AND (object_key IS NULL OR object_key='') ORDER BY created_at, id${backup_limit_clause}")"
  while IFS=$'\t' read -r backup_id filename legacy_size status created_at; do
    [[ -n "${backup_id:-}" ]] || continue
    (( LIMIT > 0 && TOTAL >= LIMIT )) && break
    TOTAL=$((TOTAL + 1)); process_backup "$backup_id" "$filename" "$legacy_size" "$status" "$created_at"
  done <<<"$backup_rows"
fi

if (( AVATAR_DB_UPDATED > 0 )); then
  # A confirmation supplied on this invocation cannot cover rows changed by it.
  SEARCH_BACKFILL_PENDING=true
  record_pending "users-index backfill required after avatar database updates; rerun with --confirm-users-index-backfill after APP_SEARCH_BACKFILL_ENABLED=true and APP_SEARCH_BACKFILL_INDEXES=users completes"
  echo "PENDING users-index backfill required before migration can be declared complete"
elif [[ "$APPLY" == true \
    && ( "$ONLY" == avatars || "$ONLY" == all ) \
    && "$CONFIRM_USERS_INDEX_BACKFILL" == false ]]; then
  SEARCH_BACKFILL_PENDING=true
  record_pending "users-index backfill confirmation required; rerun with --confirm-users-index-backfill after APP_SEARCH_BACKFILL_ENABLED=true and APP_SEARCH_BACKFILL_INDEXES=users completes"
  echo "PENDING users-index backfill confirmation required before migration can be declared complete"
fi

if [[ "$APPLY" == true && "$CONFIRM_USERS_INDEX_BACKFILL" == true && "$AVATAR_DB_UPDATED" -eq 0 ]]; then
  if ! record_storage_migration_state confirmed; then
    # The durable confirmation is the gate's evidence: an unrecorded one must
    # not let this run report the cutover as complete.
    SEARCH_BACKFILL_PENDING=true
    record_pending "users-index backfill confirmation could not be persisted in app.storage_migration_state"
    echo "PENDING users-index backfill confirmation could not be persisted" >&2
  fi
fi

if [[ -s "$VERIFIED_FILE" ]]; then
  echo "VERIFIED_MIGRATED_BEGIN"
  while IFS= read -r line; do echo "VERIFIED_MIGRATED $line"; done <"$VERIFIED_FILE"
  echo "VERIFIED_MIGRATED_END"
else
  echo "VERIFIED_MIGRATED none"
fi
if [[ -s "$PENDING_FILE" ]]; then
  echo "PENDING_BEGIN"
  while IFS= read -r line; do echo "PENDING $line"; done <"$PENDING_FILE"
  echo "PENDING_END"
else
  echo "PENDING none"
fi
echo "MIGRATION_SUMMARY total=$TOTAL uploaded=$UPLOADED skipped=$SKIPPED failed=$FAILED db_updated=$DB_UPDATED apply=$APPLY search_backfill=$([[ "$SEARCH_BACKFILL_PENDING" == true ]] && echo required || echo confirmed-or-not-needed)"
echo "NOTE Old local files and volumes were not deleted; clean them only after the operator confirms the migration."
[[ "$FAILED" -eq 0 && "$SEARCH_BACKFILL_PENDING" == false ]]
