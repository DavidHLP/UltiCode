#!/usr/bin/env bash
set -euo pipefail

: "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required}"
: "${MYSQL_CONTAINER:?MYSQL_CONTAINER is required}"
: "${NACOS_USERNAME:?NACOS_USERNAME is required}"
: "${NACOS_PASSWORD:?NACOS_PASSWORD is required}"

readonly NACOS_SERVICE_USER_PREFIXES=(AUTH ADMIN APP SUBMISSION NOTIFICATION JUDGE)
declare -rA NACOS_OWNER_METADATA_PREFIX=(
  [AUTH]='com.ulticode.auth'
  [ADMIN]='com.ulticode.admin'
  [APP]='com.ulticode.app'
  [SUBMISSION]='com.ulticode.submission'
  [NOTIFICATION]='com.ulticode.notification'
  [JUDGE]='com.ulticode.judge'
)
NACOS_PERMISSION_RESOURCE_MAX_LENGTH=128
NACOS_LONGEST_RESOURCE_SUFFIX=':DEFAULT_GROUP:naming/providers:com.ulticode.notification*'
NACOS_RESOURCE_NAMESPACE="${DUBBO_NAMESPACE:-${NACOS_NAMESPACE:-}}"
[[ "$NACOS_RESOURCE_NAMESPACE" =~ ^[A-Za-z0-9._-]{1,128}$ ]] || {
  echo "DUBBO_NAMESPACE or NACOS_NAMESPACE must be a safe non-empty namespace id" >&2
  exit 1
}
NACOS_EXPECTED_DOCKER_PROJECT="${NACOS_EXPECTED_DOCKER_PROJECT:-}"
[[ "$NACOS_EXPECTED_DOCKER_PROJECT" =~ ^[A-Za-z0-9][A-Za-z0-9_-]{0,62}$ ]] || {
  echo "NACOS_EXPECTED_DOCKER_PROJECT must be supplied by the Compose caller" >&2
  exit 1
}
(( ${#NACOS_RESOURCE_NAMESPACE} + ${#NACOS_LONGEST_RESOURCE_SUFFIX}
    <= NACOS_PERMISSION_RESOURCE_MAX_LENGTH )) || {
  echo "Nacos namespace is too long for the permissions.resource column" >&2
  exit 1
}

validate_username() {
  local name="$1" label="$2"
  [[ "$name" =~ ^[A-Za-z0-9._-]{3,50}$ ]] || {
    echo "$label must contain only letters, digits, dot, underscore, or hyphen" >&2
    exit 1
  }
  [[ "${name,,}" != "nacos" ]] || {
    echo "$label must not use the disabled built-in Nacos username" >&2
    exit 1
  }
}

validate_password() {
  local password="$1" label="$2"
  (( ${#password} >= 16 )) || {
    echo "$label must be at least 16 characters" >&2
    exit 1
  }
  [[ "$password" != *$'\n'* && "$password" != *$'\r'* ]] || {
    echo "$label must not contain newlines" >&2
    exit 1
  }
}

mysql_nacos() {
  {
    printf '%s\n' "$MYSQL_ROOT_PASSWORD"
    cat
  } | docker exec -i "$MYSQL_CONTAINER" \
    sh -c 'IFS= read -r MYSQL_PWD; export MYSQL_PWD; exec mysql -uroot nacos_config'
}

hash_password() {
  local username="$1" password="$2"
  printf '%s\n' "$password" \
    | docker run --rm -i httpd@sha256:1b766f17b84026429b7cb243317b142921b24432336e798bc881c43f45ed9567 htpasswd -niBC 12 "$username" \
    | sed 's/^[^:]*://' \
    | tr -d '\r\n'
}

provision_admin_sql() {
  local password_hash="$1"
  cat <<SQL
INSERT INTO users (username, password, enabled)
VALUES ('$NACOS_USERNAME', '$password_hash', TRUE)
ON DUPLICATE KEY UPDATE password = VALUES(password), enabled = TRUE;
INSERT INTO roles (username, role)
VALUES ('$NACOS_USERNAME', 'ROLE_ADMIN')
ON DUPLICATE KEY UPDATE role = VALUES(role);
UPDATE users SET enabled = FALSE WHERE username = 'nacos';
DELETE FROM roles WHERE username = 'nacos';
SQL
}

provision_service_sql() {
  local prefix="$1" username="$2" password_hash="$3" role
  # Nacos 2.x config clients can use either the configured tenant or the
  # historical empty namespace segment. Dubbo's config-service readiness probe
  # also uses its fixed Dubbo-Nacos-Test group, so keep both known groups
  # explicit rather than granting every Nacos group. Naming clients use the
  # configured namespace below.
  local config_test_resource="${NACOS_RESOURCE_NAMESPACE}:Dubbo-Nacos-Test:config/*"
  local metadata_prefix="${NACOS_OWNER_METADATA_PREFIX[$prefix]:-}"
  [[ -n "$metadata_prefix" ]] || {
    echo "No Nacos metadata prefix is configured for service owner: $prefix" >&2
    exit 1
  }
  local metadata_read_resource="${NACOS_RESOURCE_NAMESPACE}:mapping:config/*"
  local metadata_write_resource="${NACOS_RESOURCE_NAMESPACE}:mapping:config/${metadata_prefix}*"
  local metadata_empty_read_resource=":mapping:config/*"
  local metadata_empty_write_resource=":mapping:config/${metadata_prefix}*"
  local config_default_resource="${NACOS_RESOURCE_NAMESPACE}:DEFAULT_GROUP:config/*"
  local config_dubbo_resource="${NACOS_RESOURCE_NAMESPACE}:dubbo:config/*"
  local config_application_resource="${NACOS_RESOURCE_NAMESPACE}:backend-${prefix,,}:config/*"
  local config_test_default_resource=":Dubbo-Nacos-Test:config/*"
  local naming_read_resource="${NACOS_RESOURCE_NAMESPACE}:DEFAULT_GROUP:naming/*"
  local naming_app_write_resource="${NACOS_RESOURCE_NAMESPACE}:DEFAULT_GROUP:naming/backend-${prefix,,}"
  local naming_provider_write_resource="${NACOS_RESOURCE_NAMESPACE}:DEFAULT_GROUP:naming/providers:${metadata_prefix}*"
  role="ROLE_ULTICODE_${prefix}_REGISTRY"
  cat <<SQL
INSERT INTO users (username, password, enabled)
VALUES ('$username', '$password_hash', TRUE)
ON DUPLICATE KEY UPDATE password = VALUES(password), enabled = TRUE;
DELETE FROM roles WHERE username = '$username';
INSERT INTO roles (username, role)
VALUES ('$username', '$role')
ON DUPLICATE KEY UPDATE role = VALUES(role);
DELETE FROM permissions WHERE role = '$role';
INSERT INTO permissions (role, resource, action) VALUES
  ('$role', '$config_test_resource', 'r'),
  ('$role', '$config_default_resource', 'r'),
  ('$role', '$config_dubbo_resource', 'r'),
  ('$role', '$config_application_resource', 'r'),
  ('$role', '$metadata_read_resource', 'r'),
  ('$role', '$metadata_write_resource', 'w'),
  ('$role', '$metadata_empty_read_resource', 'r'),
  ('$role', '$metadata_empty_write_resource', 'w'),
  ('$role', '$config_test_default_resource', 'r'),
  ('$role', '$naming_read_resource', 'r'),
  ('$role', '$naming_app_write_resource', 'w'),
  ('$role', '$naming_provider_write_resource', 'w');
SQL
}

validate_username "$NACOS_USERNAME" NACOS_USERNAME
validate_password "$NACOS_PASSWORD" NACOS_PASSWORD

declare -A seen_nacos_users=()
declare -A nacos_users_by_prefix=()
declare -A nacos_passwords_by_prefix=()
admin_username_key="${NACOS_USERNAME,,}"
for prefix in "${NACOS_SERVICE_USER_PREFIXES[@]}"; do
  username_var="${prefix}_NACOS_USERNAME"
  password_var="${prefix}_NACOS_PASSWORD"
  username="${!username_var:-}"
  password="${!password_var:-}"
  if [[ -z "$username" || -z "$password" ]]; then
    echo "$username_var and $password_var are required" >&2
    exit 1
  fi
  validate_username "$username" "$username_var"
  validate_password "$password" "$password_var"
  username_key="${username,,}"
  [[ "$username_key" != "$admin_username_key" ]] || {
    echo "$username_var must not reuse NACOS_USERNAME" >&2
    exit 1
  }
  [[ -z "${seen_nacos_users[$username_key]:-}" ]] || {
    echo "$username_var duplicates ${seen_nacos_users[$username_key]}; service usernames must be unique" >&2
    exit 1
  }
  seen_nacos_users["$username_key"]="$username_var"
  nacos_users_by_prefix["$prefix"]="$username"
  nacos_passwords_by_prefix["$prefix"]="$password"
done

[[ "$MYSQL_CONTAINER" =~ ^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$ ]] || {
  echo "MYSQL_CONTAINER must be a safe container name or id" >&2
  exit 1
}
container_service="$(docker inspect -f '{{index .Config.Labels "com.docker.compose.service"}}' \
  "$MYSQL_CONTAINER" 2>/dev/null || true)"
container_project="$(docker inspect -f '{{index .Config.Labels "com.docker.compose.project"}}' \
  "$MYSQL_CONTAINER" 2>/dev/null || true)"
container_image="$(docker inspect -f '{{.Config.Image}}' "$MYSQL_CONTAINER" 2>/dev/null || true)"
container_running="$(docker inspect -f '{{.State.Running}}' "$MYSQL_CONTAINER" 2>/dev/null || true)"
[[ "$container_service" == "mysql" && "$container_running" == "true" ]] || {
  echo "MYSQL_CONTAINER must be the running Compose mysql service" >&2
  exit 1
}
[[ "$container_project" == "$NACOS_EXPECTED_DOCKER_PROJECT" ]] || {
  echo "MYSQL_CONTAINER must belong to Compose project $NACOS_EXPECTED_DOCKER_PROJECT" >&2
  exit 1
}
[[ "$container_image" == mysql:* || "$container_image" == mysql@sha256:* ]] || {
  echo "MYSQL_CONTAINER must use a MySQL image" >&2
  exit 1
}

admin_password_hash="$(hash_password "$NACOS_USERNAME" "$NACOS_PASSWORD")"
declare -A nacos_password_hashes_by_prefix=()
for prefix in "${NACOS_SERVICE_USER_PREFIXES[@]}"; do
  nacos_password_hashes_by_prefix["$prefix"]="$(hash_password \
    "${nacos_users_by_prefix[$prefix]}" "${nacos_passwords_by_prefix[$prefix]}")"
done

{
  printf 'START TRANSACTION;\n'
  provision_admin_sql "$admin_password_hash"
  for prefix in "${NACOS_SERVICE_USER_PREFIXES[@]}"; do
    provision_service_sql "$prefix" "${nacos_users_by_prefix[$prefix]}" \
      "${nacos_password_hashes_by_prefix[$prefix]}"
  done
  printf 'COMMIT;\n'
} | mysql_nacos
echo "Nacos administrator and service registry users provisioned without exposing passwords."
